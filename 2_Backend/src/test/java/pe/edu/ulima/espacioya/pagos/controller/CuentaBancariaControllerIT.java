package pe.edu.ulima.espacioya.pagos.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.pagos.dto.GuardarCuentaBancariaDTO;
import pe.edu.ulima.espacioya.pagos.service.CuentaBancariaService;

/** HU-18 por la API contra PostgreSQL real: CA1, CA2 y CA3, la contraseña, el acceso y la concurrencia. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PagosSoporte.Dobles.class})
class CuentaBancariaControllerIT extends PagosSoporte {

    private static final String RUTA = "/api/arrendadores/actual/cuenta-bancaria";
    private static final String BANCO = "Banco Andino";
    private static final String NUMERO_A = "00112233445566771111";
    private static final String NUMERO_B = "99887766554433222222";
    private static final String IDENTIDAD_NO_VERIFICADA = CuentaBancariaService.MENSAJE_IDENTIDAD_NO_VERIFICADA;
    private static final Duration RETENCION = Duration.ofHours(72);

    private static Instant instante(Object valor) {
        return valor instanceof Timestamp marca ? marca.toInstant() : ((java.time.OffsetDateTime) valor).toInstant();
    }

    private void verificarIdentidad() {
        jdbc.update("UPDATE perfil_arrendador SET estado_identidad = 'VERIFICADA' WHERE usuario_id = ?",
                arrendadorId);
    }

    private static String cuerpo(String banco, String numero, boolean declara, String contrasena) {
        return "{\"banco\":\"" + banco + "\",\"numero\":\"" + numero + "\",\"declaraTitularidad\":" + declara
                + (contrasena == null ? "" : ",\"contrasena\":\"" + contrasena + "\"") + "}";
    }

    private ResultActions guardar(String sesion, String cuerpo) throws Exception {
        return mvc.perform(con(sesion, put(RUTA)).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ResultActions guardar(String sesion, String banco, String numero) throws Exception {
        return guardar(sesion, cuerpo(banco, numero, true, CONTRASENA));
    }

    private ResultActions consultar(String sesion) throws Exception {
        return mvc.perform(con(sesion, get(RUTA)));
    }

    private int filasDeCuenta() {
        return filas("cuenta_bancaria");
    }

    // ── CA1 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-18 R5, R11, R17, R19, R20, R21, R23: CA1 registra la cuenta, la consulta enmascarada y la guarda "
            + "cifrada, sin retención, sin correo y con auditoría")
    void ca1RegistraYConsulta() throws Exception {
        verificarIdentidad();
        var sesion = token(CORREO_ARRENDADOR);

        consultar(sesion).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.identidadVerificada").value(true))
                .andExpect(jsonPath("$.cuenta").doesNotExist());

        var respuesta = guardar(sesion, BANCO, NUMERO_A).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.banco").value(BANCO))
                .andExpect(jsonPath("$.numeroEnmascarado").value("****1111"))
                .andExpect(jsonPath("$.titular").value("Inversiones Ocultas SAC"))
                .andExpect(jsonPath("$.estado").value("DISPONIBLE"))
                .andExpect(jsonPath("$.retenidaHasta").doesNotExist());
        assertThat(texto(respuesta)).doesNotContain(NUMERO_A).doesNotContain(CONTRASENA);

        var consulta = consultar(sesion).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.identidadVerificada").value(true))
                .andExpect(jsonPath("$.cuenta.numeroEnmascarado").value("****1111"))
                .andExpect(jsonPath("$.cuenta.estado").value("DISPONIBLE"));
        assertThat(texto(consulta)).doesNotContain(NUMERO_A);

        var fila = jdbc.queryForMap("SELECT * FROM cuenta_bancaria");
        assertThat(fila.get("arrendador_id")).isEqualTo(arrendadorId);
        assertThat(fila.get("numero").toString()).startsWith("v1:").doesNotContain(NUMERO_A);
        assertThat(cifrador.descifrar(fila.get("numero").toString())).isEqualTo(NUMERO_A);
        assertThat(fila.get("retener_hasta")).isNull();
        assertThat(fila.get("titular")).isEqualTo("Inversiones Ocultas SAC");
        assertThat(notificador.cambiosCuenta()).isEmpty();
        var registros = auditorias("CUENTA_BANCARIA_REGISTRADA");
        assertThat(registros).hasSize(1);
        assertThat(registros.getFirst().get("usuario_id")).isEqualTo(arrendadorId);
        assertThat(registros.getFirst().get("ip")).isEqualTo(ip);
        assertThat(registros.getFirst().get("entidad")).isEqualTo("cuenta_bancaria");
        assertThat(registros.getFirst().get("detalle")).isEqualTo("banco=" + BANCO + ";cuenta=****1111");
    }

    // ── CA2 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-18 R12, R13, R18, R24: CA2 cambia la cuenta con la contraseña, retiene 72 h, avisa por correo y "
            + "audita lo anterior y lo nuevo")
    void ca2Cambia() throws Exception {
        verificarIdentidad();
        var sesion = token(CORREO_ARRENDADOR);
        guardar(sesion, BANCO, NUMERO_A).andExpect(status().isOk());
        var cambioEn = reloj.instant();

        var respuesta = guardar(sesion, "Otro Banco", NUMERO_B).andExpect(status().isOk())
                .andExpect(jsonPath("$.banco").value("Otro Banco"))
                .andExpect(jsonPath("$.numeroEnmascarado").value("****2222"))
                .andExpect(jsonPath("$.estado").value("RETENIDA"))
                .andExpect(jsonPath("$.retenidaHasta").value(cambioEn.plus(RETENCION).toString()));
        assertThat(texto(respuesta)).doesNotContain(NUMERO_B);

        var fila = jdbc.queryForMap("SELECT * FROM cuenta_bancaria");
        assertThat(filasDeCuenta()).isEqualTo(1);
        assertThat(instante(fila.get("retener_hasta"))).isEqualTo(cambioEn.plus(RETENCION));
        assertThat(cifrador.descifrar(fila.get("numero").toString())).isEqualTo(NUMERO_B);
        assertThat(fila.get("titular")).isEqualTo("Inversiones Ocultas SAC");
        assertThat(notificador.cambiosCuenta()).hasSize(1);
        var aviso = notificador.cambiosCuenta().getFirst();
        assertThat(aviso.correo()).isEqualTo(CORREO_ARRENDADOR);
        assertThat(aviso.datos().banco()).isEqualTo("Otro Banco");
        assertThat(aviso.datos().numeroEnmascarado()).isEqualTo("****2222");
        assertThat(aviso.datos().retenidaHasta()).isEqualTo(cambioEn.plus(RETENCION));
        assertThat(aviso.datos().toString()).doesNotContain(NUMERO_B);
        var cambios = auditorias("CUENTA_BANCARIA_CAMBIADA");
        assertThat(cambios).hasSize(1);
        assertThat(cambios.getFirst().get("usuario_id")).isEqualTo(arrendadorId);
        assertThat(cambios.getFirst().get("ip")).isEqualTo(ip);
        assertThat(cambios.getFirst().get("detalle")).isEqualTo("anterior=" + BANCO + "/****1111;nueva=Otro Banco/"
                + "****2222;retenerHasta=" + cambioEn.plus(RETENCION));
        assertThat(cambios.getFirst().get("detalle").toString()).doesNotContain(NUMERO_A).doesNotContain(NUMERO_B)
                .doesNotContain(CONTRASENA);
    }

    @Test
    @DisplayName("HU-18 R18: la consulta responde RETENIDA y, pasadas 72 h, DISPONIBLE sin cambiar la fila")
    void retenidaYLuegoDisponible() throws Exception {
        verificarIdentidad();
        var sesion = token(CORREO_ARRENDADOR);
        guardar(sesion, BANCO, NUMERO_A).andExpect(status().isOk());
        guardar(sesion, "Otro Banco", NUMERO_B).andExpect(status().isOk());
        var antes = jdbc.queryForMap("SELECT * FROM cuenta_bancaria");

        consultar(sesion).andExpect(jsonPath("$.cuenta.estado").value("RETENIDA"))
                .andExpect(jsonPath("$.cuenta.retenidaHasta").exists());
        reloj.avanzar(RETENCION.plusMinutes(1));
        var nueva = token(CORREO_ARRENDADOR);

        consultar(nueva).andExpect(status().isOk()).andExpect(jsonPath("$.cuenta.estado").value("DISPONIBLE"))
                .andExpect(jsonPath("$.cuenta.retenidaHasta").doesNotExist());
        assertThat(jdbc.queryForMap("SELECT * FROM cuenta_bancaria")).isEqualTo(antes);
    }

    @Test
    @DisplayName("HU-18 R16: enviar la misma cuenta responde 400 y no cambia retención, auditoría ni correo")
    void mismaCuenta() throws Exception {
        verificarIdentidad();
        var sesion = token(CORREO_ARRENDADOR);
        guardar(sesion, BANCO, NUMERO_A).andExpect(status().isOk());
        var antes = jdbc.queryForMap("SELECT * FROM cuenta_bancaria");

        guardar(sesion, BANCO.toUpperCase(), NUMERO_A).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(CuentaBancariaService.MENSAJE_YA_REGISTRADA));

        assertThat(jdbc.queryForMap("SELECT * FROM cuenta_bancaria")).isEqualTo(antes);
        assertThat(auditorias("CUENTA_BANCARIA_CAMBIADA")).isEmpty();
        assertThat(notificador.cambiosCuenta()).isEmpty();
    }

    @Test
    @DisplayName("HU-18 R15: si el correo falla, el cambio sigue confirmado y la respuesta es 200")
    void falloDelCorreoNoRevierte() throws Exception {
        verificarIdentidad();
        var sesion = token(CORREO_ARRENDADOR);
        guardar(sesion, BANCO, NUMERO_A).andExpect(status().isOk());
        notificador.fallar();

        guardar(sesion, "Otro Banco", NUMERO_B).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RETENIDA"));

        assertThat(jdbc.queryForObject("SELECT banco FROM cuenta_bancaria", String.class)).isEqualTo("Otro Banco");
        assertThat(notificador.cambiosCuenta()).isEmpty();
        assertThat(auditorias("CUENTA_BANCARIA_CAMBIADA")).hasSize(1);
    }

    // ── CA3 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-18 R3: CA3 sin identidad verificada responde 400 sin escribir, sin auditar y sin contar intentos")
    void ca3SinIdentidad() throws Exception {
        var sesion = token(CORREO_ARRENDADOR);

        consultar(sesion).andExpect(status().isOk()).andExpect(jsonPath("$.identidadVerificada").value(false))
                .andExpect(jsonPath("$.cuenta").doesNotExist());
        for (var i = 0; i < CuentaBancariaService.MAXIMO_FALLOS + 1; i++) {
            guardar(sesion, cuerpo(BANCO, NUMERO_A, true, "contraseña equivocada")).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje").value(IDENTIDAD_NO_VERIFICADA))
                    .andExpect(jsonPath("$.errores.length()").value(0));
        }

        assertThat(filasDeCuenta()).isZero();
        assertThat(auditorias("CUENTA_BANCARIA_CONTRASENA_FALLIDA")).isEmpty();
        assertThat(auditorias("CUENTA_BANCARIA_REGISTRADA")).isEmpty();
        verificarIdentidad();
        guardar(sesion, BANCO, NUMERO_A).andExpect(status().isOk());
    }

    // ── Contraseña (R6-R9) ───────────────────────────────────────────────

    @Test
    @DisplayName("HU-18 R7, R8, R9: 5 contraseñas incorrectas dan 400 en contrasena y auditoría; la 6.ª da 429; sin "
            + "escribir")
    void contrasenaIncorrectaYBloqueo() throws Exception {
        verificarIdentidad();
        var sesion = token(CORREO_ARRENDADOR);

        for (var i = 0; i < CuentaBancariaService.MAXIMO_FALLOS; i++) {
            guardar(sesion, cuerpo(BANCO, NUMERO_A, true, "mala")).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores[0].campo").value("contrasena"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value(
                            CuentaBancariaService.MENSAJE_CONTRASENA_INCORRECTA));
        }
        guardar(sesion, BANCO, NUMERO_A).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.mensaje").value(DEMASIADOS_INTENTOS));

        assertThat(filasDeCuenta()).isZero();
        var fallidas = auditorias("CUENTA_BANCARIA_CONTRASENA_FALLIDA");
        assertThat(fallidas).hasSize(CuentaBancariaService.MAXIMO_FALLOS);
        assertThat(fallidas.getFirst().get("usuario_id")).isEqualTo(arrendadorId);
        assertThat(fallidas.getFirst().get("ip")).isEqualTo(ip);
    }

    @Test
    @DisplayName("HU-18 R9: una contraseña correcta reinicia el contador de fallos")
    void aciertoReinicia() throws Exception {
        verificarIdentidad();
        var sesion = token(CORREO_ARRENDADOR);
        for (var i = 0; i < CuentaBancariaService.MAXIMO_FALLOS - 1; i++) {
            guardar(sesion, cuerpo(BANCO, NUMERO_A, true, "mala")).andExpect(status().isBadRequest());
        }
        guardar(sesion, BANCO, NUMERO_A).andExpect(status().isOk());

        for (var i = 0; i < CuentaBancariaService.MAXIMO_FALLOS - 1; i++) {
            guardar(sesion, cuerpo("Otro Banco", NUMERO_B, true, "mala")).andExpect(status().isBadRequest());
        }
        guardar(sesion, "Otro Banco", NUMERO_B).andExpect(status().isOk());
    }

    // ── Validación (R6, R10) ─────────────────────────────────────────────

    @Test
    @DisplayName("HU-18 R6: sin contrasena o en blanco responde 400 en el campo contrasena, sin contar fallos")
    void sinContrasena() throws Exception {
        verificarIdentidad();
        var sesion = token(CORREO_ARRENDADOR);

        for (var cuerpo : List.of(cuerpo(BANCO, NUMERO_A, true, null), cuerpo(BANCO, NUMERO_A, true, "  "))) {
            guardar(sesion, cuerpo).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores[0].campo").value("contrasena"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value(GuardarCuentaBancariaDTO.MENSAJE_CONTRASENA));
        }

        assertThat(auditorias("CUENTA_BANCARIA_CONTRASENA_FALLIDA")).isEmpty();
        assertThat(filasDeCuenta()).isZero();
    }

    @Test
    @DisplayName("HU-18 R10: banco, número y declaración inválidos dan un error por campo y no guardan nada")
    void camposInvalidos() throws Exception {
        verificarIdentidad();
        var sesion = token(CORREO_ARRENDADOR);

        var respuesta = guardar(sesion, cuerpo("", "123", false, CONTRASENA)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.length()").value(3));

        var cuerpoRespuesta = texto(respuesta);
        assertThat(cuerpoRespuesta).contains("\"banco\"").contains("\"numero\"").contains("\"declaraTitularidad\"")
                .contains(GuardarCuentaBancariaDTO.MENSAJE_DECLARACION);
        assertThat(filasDeCuenta()).isZero();
        assertThat(auditorias("CUENTA_BANCARIA_CONTRASENA_FALLIDA")).isEmpty();
    }

    // ── Acceso (R1, R2, R4) ──────────────────────────────────────────────

    @Test
    @DisplayName("HU-18 R1: sin sesión o con un token inventado las dos rutas responden 401 sin tocar la tabla")
    void sinSesion() throws Exception {
        consultar(null).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.mensaje").value(SESION_INVALIDA));
        guardar(null, BANCO, NUMERO_A).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(SESION_INVALIDA));
        guardar("token-inventado", BANCO, NUMERO_A).andExpect(status().isUnauthorized());

        assertThat(filasDeCuenta()).isZero();
    }

    @Test
    @DisplayName("HU-18 R2: un USUARIO recibe 403 en las dos rutas, sin comprobar la contraseña ni escribir")
    void otroRol() throws Exception {
        var sesion = token(CORREO_USUARIO);

        consultar(sesion).andExpect(status().isForbidden()).andExpect(jsonPath("$.mensaje").value(ACCESO_DENEGADO));
        guardar(sesion, BANCO, NUMERO_A).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(ACCESO_DENEGADO));
        guardar(sesion, cuerpo(BANCO, NUMERO_A, true, "mala")).andExpect(status().isForbidden());

        assertThat(filasDeCuenta()).isZero();
        assertThat(auditorias("CUENTA_BANCARIA_CONTRASENA_FALLIDA")).isEmpty();
    }

    @Test
    @DisplayName("HU-18 R4: el arrendador solo lee y modifica su propia cuenta; el cuerpo no acepta otro id")
    void soloLaPropiaCuenta() throws Exception {
        verificarIdentidad();
        var otro = crearOtroArrendador();
        var sesion = token(CORREO_ARRENDADOR);

        guardar(sesion, "{\"banco\":\"" + BANCO + "\",\"numero\":\"" + NUMERO_A + "\",\"declaraTitularidad\":true,"
                + "\"contrasena\":\"" + CONTRASENA + "\",\"arrendadorId\":" + otro + "}").andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT arrendador_id FROM cuenta_bancaria", Long.class))
                .isEqualTo(arrendadorId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cuenta_bancaria WHERE arrendador_id = ?", Integer.class,
                otro)).isZero();
    }

    private long crearOtroArrendador() {
        var usuario = usuarios.save(pe.edu.ulima.espacioya.cuentas.domain.Usuario.registrarArrendador(
                "otro.arrendador@correo.pe", encoder.encode(CONTRASENA), "Otro Arrendador", "999000111", "2026-10",
                reloj.instant()));
        perfiles.save(pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador.crear(usuario.getId(),
                pe.edu.ulima.espacioya.cuentas.domain.TipoDocumento.RUC, "20100070971", "f".repeat(64), "Otra SAC",
                "Av. Otra 1"));
        return usuario.getId();
    }

    // ── Concurrencia (R25) ───────────────────────────────────────────────

    @Test
    @DisplayName("HU-18 R25: dos PUT simultáneos del mismo arrendador terminan sin error de integridad y con una sola fila")
    void dosPutSimultaneos() throws Exception {
        verificarIdentidad();
        var sesion = token(CORREO_ARRENDADOR);
        var salida = new CountDownLatch(1);
        List<Callable<MvcResult>> solicitudes = List.of(
                () -> guardar(sesion, "Banco Uno", NUMERO_A).andReturn(),
                () -> guardar(sesion, "Banco Dos", NUMERO_B).andReturn());

        List<Integer> estados;
        try (var hilos = Executors.newFixedThreadPool(solicitudes.size())) {
            var pendientes = solicitudes.stream().map(s -> hilos.submit(() -> {
                salida.await();
                return s.call();
            })).toList();
            salida.countDown();
            var resultado = new java.util.ArrayList<Integer>();
            for (var pendiente : pendientes) {
                resultado.add(pendiente.get(30, TimeUnit.SECONDS).getResponse().getStatus());
            }
            estados = resultado;
        }

        assertThat(estados).containsExactly(200, 200);
        assertThat(filasDeCuenta()).isEqualTo(1);
        assertThat(auditorias("CUENTA_BANCARIA_REGISTRADA")).hasSize(1);
        assertThat(auditorias("CUENTA_BANCARIA_CAMBIADA")).hasSize(1);
        assertThat(notificador.cambiosCuenta()).hasSize(1);
        var banco = jdbc.queryForObject("SELECT banco FROM cuenta_bancaria", String.class);
        var ultimo = auditorias("CUENTA_BANCARIA_CAMBIADA").getFirst().get("detalle").toString();
        assertThat(ultimo).contains("nueva=" + banco + "/");
        assertThat(jdbc.queryForObject("SELECT retener_hasta FROM cuenta_bancaria", Timestamp.class).toInstant())
                .isAfter(Instant.parse("2026-10-28T15:00:00Z"));
    }
}
