package pe.edu.ulima.espacioya.cuentas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.seguridad.TokenAleatorio;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.dto.SolicitudCambioCorreoDTO;
import pe.edu.ulima.espacioya.cuentas.service.CambioCorreoService;
import pe.edu.ulima.espacioya.cuentas.service.ReautenticacionPerfil;

/** HU-20 CA2 por la API contra PostgreSQL real: solicitud, confirmación, reautenticación, límites y concurrencia. */
@SpringBootTest
// Sin volcar las solicitudes de MockMvc: R46 revisa que la salida no tenga contraseñas ni datos personales.
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import({TestcontainersConfiguration.class, PerfilSoporte.Dobles.class})
@ExtendWith(OutputCaptureExtension.class)
class CambioCorreoControllerIT extends PerfilSoporte {

    private static final String SOLICITUDES = "/api/usuarios/actual/cambios-correo";
    private static final String CONFIRMACIONES = "/api/cambios-correo/confirmaciones";
    private static final String FACTURACION = "/api/arrendadores/actual/facturacion";
    private static final String NUEVO = "juana.nueva@correo.pe";
    private static final String NUEVO_ENMASCARADO = "j***@correo.pe";
    private static final String INCORRECTA = "Incorrecta99";
    private static final String PREFIJO_ENLACE = "http://localhost:5173/confirmar-correo#token=";

    @Autowired
    private CambioCorreoService servicio;

    private ResultActions solicitar(String sesion, String correo, String contrasena) throws Exception {
        var cuerpo = "{\"correo\":\"" + correo + "\""
                + (contrasena == null ? "" : ",\"contrasena\":\"" + contrasena + "\"") + "}";
        return mvc.perform(con(sesion, post(SOLICITUDES)).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ResultActions confirmar(String token) throws Exception {
        return mvc.perform(desde(post(CONFIRMACIONES), ip).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"));
    }

    private String tokenDelEnlace(int indice) {
        var enlace = notificador.cambios().get(indice).enlace();
        assertThat(enlace).startsWith(PREFIJO_ENLACE);
        return enlace.substring(PREFIJO_ENLACE.length());
    }

    private String ultimoToken() {
        return tokenDelEnlace(notificador.cambios().size() - 1);
    }

    private int tokens() {
        return jdbc.queryForObject("SELECT count(*) FROM token", Integer.class);
    }

    private void rechazoGenerico(ResultActions respuesta) throws Exception {
        respuesta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(CambioCorreoService.MENSAJE_ENLACE_INVALIDO))
                .andExpect(jsonPath("$.errores.length()").value(0));
    }

    private List<String> motivosDeRechazo() {
        return auditorias("CORREO_CAMBIO_RECHAZADO").stream().map(fila -> (String) fila.get("detalle")).toList();
    }

    // ── CA2: flujo completo ──────────────────────────────────────────────

    @Test
    @DisplayName("HU-20 R25, R26, R27, R30, R31, R49, R50, R7: CA2 el correo nuevo solo se activa al confirmar el "
            + "enlace; el anterior recibe un aviso")
    void ca2FlujoCompleto() throws Exception {
        var id = crearUsuario(CORREO_USUARIO);
        var sesion = token(CORREO_USUARIO);

        solicitar(sesion, "Juana.Nueva@Correo.PE", CONTRASENA).andExpect(status().isAccepted())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.mensaje").value(CambioCorreoService.MENSAJE_SOLICITUD_RECIBIDA));

        var fila = filaUsuario(id);
        assertThat(fila.get("correo")).isEqualTo(CORREO_USUARIO);
        assertThat(fila.get("correo_pendiente")).isEqualTo(NUEVO);
        var token = jdbc.queryForMap("SELECT * FROM token");
        assertThat(token.get("tipo")).isEqualTo("CAMBIO_CORREO");
        assertThat(token.get("usuario_id")).isEqualTo(id);
        assertThat(notificador.cambios()).hasSize(1);
        assertThat(notificador.cambios().getFirst().correo()).isEqualTo(NUEVO);
        var enClaro = tokenDelEnlace(0);
        assertThat(token.get("hash_token")).isEqualTo(TokenAleatorio.hash(enClaro));
        assertThat(token.toString()).doesNotContain(enClaro);
        assertThat(notificador.avisos()).containsExactly(new AvisoCorreo(CORREO_USUARIO, NUEVO_ENMASCARADO));
        var solicitado = auditorias("CORREO_CAMBIO_SOLICITADO");
        assertThat(solicitado).hasSize(1);
        assertThat(solicitado.getFirst().get("usuario_id")).isEqualTo(id);
        assertThat(solicitado.getFirst().get("ip")).isEqualTo(ip);
        assertThat(solicitado.getFirst().get("detalle")).isEqualTo("nuevo=" + NUEVO_ENMASCARADO);

        // R30: mientras esté pendiente, la cuenta sigue usando el correo actual.
        iniciarSesion(NUEVO, CONTRASENA).andExpect(status().isUnauthorized());
        iniciarSesion(CORREO_USUARIO, CONTRASENA).andExpect(status().isCreated());

        confirmar(enClaro).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.mensaje").value(CambioCorreoService.MENSAJE_CORREO_CONFIRMADO));

        fila = filaUsuario(id);
        assertThat(fila.get("correo")).isEqualTo(NUEVO);
        assertThat(fila.get("correo_pendiente")).isNull();
        assertThat(jdbc.queryForObject("SELECT usado_en FROM token", Object.class)).isNotNull();
        iniciarSesion(NUEVO, CONTRASENA).andExpect(status().isCreated());
        iniciarSesion(CORREO_USUARIO, CONTRASENA).andExpect(status().isUnauthorized());
        var cambiado = auditorias("CORREO_CAMBIADO");
        assertThat(cambiado).hasSize(1);
        assertThat(cambiado.getFirst().get("usuario_id")).isEqualTo(id);
        assertThat(cambiado.getFirst().get("detalle")).isEqualTo("anterior=c***@correo.pe;nuevo=" + NUEVO_ENMASCARADO);
        // D4: la sesión con la que se pidió el cambio sigue vigente.
        mvc.perform(con(sesion, get("/api/usuarios/actual/perfil"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value(NUEVO));
    }

    @Test
    @DisplayName("HU-20 R25: un ARRENDADOR también pide el cambio y lo confirma")
    void arrendadorCambiaCorreo() throws Exception {
        var id = crearArrendador();
        var sesion = token(CORREO_ARRENDADOR);

        solicitar(sesion, NUEVO, CONTRASENA).andExpect(status().isAccepted());
        confirmar(ultimoToken()).andExpect(status().isOk());

        assertThat(filaUsuario(id).get("correo")).isEqualTo(NUEVO);
    }

    // ── Correo de otra cuenta (RNF-SEG-13) ───────────────────────────────

    @Test
    @DisplayName("HU-20 R29, R49: un correo de otra cuenta responde lo mismo, guarda el pendiente y avisa, sin token "
            + "ni enlace")
    void correoDeOtraCuenta() throws Exception {
        var id = crearUsuario(CORREO_USUARIO);
        crearUsuario(NUEVO);
        var sesion = token(CORREO_USUARIO);

        var libre = texto(solicitar(sesion, "libre.perfil@correo.pe", CONTRASENA).andExpect(status().isAccepted()));
        notificador.reiniciar();
        var ocupado = texto(solicitar(sesion, NUEVO, CONTRASENA).andExpect(status().isAccepted())
                .andExpect(header().string("Cache-Control", "no-store")));

        assertThat(ocupado).isEqualTo(libre);
        assertThat(filaUsuario(id).get("correo_pendiente")).isEqualTo(NUEVO);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM token WHERE usado_en IS NULL", Integer.class)).isZero();
        assertThat(tokens()).isEqualTo(1);
        assertThat(notificador.cambios()).isEmpty();
        assertThat(notificador.avisos()).containsExactly(new AvisoCorreo(CORREO_USUARIO, NUEVO_ENMASCARADO));
        var registros = auditorias("CORREO_CAMBIO_NO_EMITIDO");
        assertThat(registros).hasSize(1);
        assertThat(registros.getFirst().get("usuario_id")).isEqualTo(id);
        assertThat(registros.getFirst().get("ip")).isEqualTo(ip);
        assertThat(registros.getFirst().get("detalle")).isEqualTo("motivo=CORREO_EN_USO;nuevo=" + NUEVO_ENMASCARADO);
    }

    // ── Validación y reautenticación (STRIDE S) ──────────────────────────

    @Test
    @DisplayName("HU-20 R18: sin contraseña o en blanco responde 400 en contrasena sin contar un fallo")
    void sinContrasena() throws Exception {
        crearUsuario(CORREO_USUARIO);
        var sesion = token(CORREO_USUARIO);

        solicitar(sesion, NUEVO, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("contrasena"))
                .andExpect(jsonPath("$.errores[0].mensaje").value(ReautenticacionPerfil.MENSAJE_CONTRASENA));
        for (int i = 0; i < 6; i++) {
            solicitar(sesion, NUEVO, "  ").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores[0].campo").value("contrasena"));
        }

        solicitar(sesion, NUEVO, CONTRASENA).andExpect(status().isAccepted());
        assertThat(auditorias("PERFIL_CONTRASENA_FALLIDA")).isEmpty();
    }

    @Test
    @DisplayName("HU-20 R22: un correo inválido responde 400 en correo sin comprobar la contraseña")
    void correoInvalido() throws Exception {
        crearUsuario(CORREO_USUARIO);
        var sesion = token(CORREO_USUARIO);

        solicitar(sesion, "no-es-correo", INCORRECTA).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("correo"))
                .andExpect(jsonPath("$.errores[0].mensaje").value(SolicitudCambioCorreoDTO.MENSAJE_CORREO));
        solicitar(sesion, "a".repeat(250) + "@x.pe", INCORRECTA).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("correo"));
        solicitar(sesion, " ", INCORRECTA).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("correo"));

        assertThat(auditorias("PERFIL_CONTRASENA_FALLIDA")).isEmpty();
        assertThat(tokens()).isZero();
    }

    @Test
    @DisplayName("HU-20 R19, R20: cada contraseña incorrecta da 400 y se audita; al 5.º fallo responde 429 aun con la "
            + "correcta")
    void contrasenaIncorrectaYBloqueo() throws Exception {
        var id = crearUsuario(CORREO_USUARIO);
        var sesion = token(CORREO_USUARIO);

        for (int i = 0; i < ReautenticacionPerfil.MAXIMO_FALLOS; i++) {
            solicitar(sesion, NUEVO, INCORRECTA).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores[0].campo").value("contrasena"))
                    .andExpect(jsonPath("$.errores[0].mensaje")
                            .value(ReautenticacionPerfil.MENSAJE_CONTRASENA_INCORRECTA));
        }
        solicitar(sesion, NUEVO, CONTRASENA).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.mensaje").value(DEMASIADOS_INTENTOS));

        assertThat(filaUsuario(id).get("correo_pendiente")).isNull();
        assertThat(tokens()).isZero();
        assertThat(notificador.avisos()).isEmpty();
        var fallos = auditorias("PERFIL_CONTRASENA_FALLIDA");
        assertThat(fallos).hasSize(ReautenticacionPerfil.MAXIMO_FALLOS);
        assertThat(fallos).allSatisfy(fila -> {
            assertThat(fila.get("usuario_id")).isEqualTo(id);
            assertThat(fila.get("ip")).isEqualTo(ip);
            assertThat(fila.get("detalle")).isEqualTo("operacion=CORREO");
        });

        reloj.avanzar(ReautenticacionPerfil.VENTANA_FALLOS.plusSeconds(1));
        var nueva = token(CORREO_USUARIO);
        solicitar(nueva, NUEVO, CONTRASENA).andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("HU-20 R20: el cupo de fallos es uno solo para el correo y la facturación")
    void cupoCompartido() throws Exception {
        crearArrendador();
        var sesion = token(CORREO_ARRENDADOR);
        var facturacion = "{\"tipoDocumento\":\"RUC\",\"razonSocial\":\"" + RAZON + "\",\"direccionFiscal\":\""
                + DIRECCION + "\",\"contrasena\":\"" + INCORRECTA + "\"}";

        for (int i = 0; i < 3; i++) {
            solicitar(sesion, NUEVO, INCORRECTA).andExpect(status().isBadRequest());
        }
        for (int i = 0; i < 2; i++) {
            mvc.perform(con(sesion, put(FACTURACION)).contentType(MediaType.APPLICATION_JSON).content(facturacion))
                    .andExpect(status().isBadRequest());
        }

        solicitar(sesion, NUEVO, CONTRASENA).andExpect(status().isTooManyRequests());
        mvc.perform(con(sesion, put(FACTURACION)).contentType(MediaType.APPLICATION_JSON)
                .content(facturacion.replace(INCORRECTA, CONTRASENA))).andExpect(status().isTooManyRequests());
        assertThat(auditorias("PERFIL_CONTRASENA_FALLIDA")).extracting(fila -> fila.get("detalle"))
                .containsExactly("operacion=CORREO", "operacion=CORREO", "operacion=CORREO",
                        "operacion=FACTURACION", "operacion=FACTURACION");
    }

    @Test
    @DisplayName("HU-20 R21: la contraseña correcta reinicia el contador de fallos")
    void aciertoReiniciaContador() throws Exception {
        crearUsuario(CORREO_USUARIO);
        var sesion = token(CORREO_USUARIO);

        for (int i = 0; i < 4; i++) {
            solicitar(sesion, NUEVO, INCORRECTA).andExpect(status().isBadRequest());
        }
        solicitar(sesion, NUEVO, CONTRASENA).andExpect(status().isAccepted());
        for (int i = 0; i < 4; i++) {
            solicitar(sesion, NUEVO, INCORRECTA).andExpect(status().isBadRequest());
        }

        solicitar(sesion, "otro.nuevo@correo.pe", CONTRASENA).andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("HU-20 R23: pedir el mismo correo responde 400 en correo sin token ni correos")
    void mismoCorreo() throws Exception {
        var id = crearUsuario(CORREO_USUARIO);
        var sesion = token(CORREO_USUARIO);

        solicitar(sesion, "CARLA.Perfil@correo.pe", CONTRASENA).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("correo"))
                .andExpect(jsonPath("$.errores[0].mensaje").value(CambioCorreoService.MENSAJE_MISMO_CORREO));

        assertThat(filaUsuario(id).get("correo_pendiente")).isNull();
        assertThat(tokens()).isZero();
        assertThat(notificador.cambios()).isEmpty();
        assertThat(notificador.avisos()).isEmpty();
    }

    @Test
    @DisplayName("HU-20 R24: la 4.ª solicitud en la hora responde 429 sin tocar el pendiente, sin token ni correos")
    void cuartaSolicitud429() throws Exception {
        var id = crearUsuario(CORREO_USUARIO);
        var sesion = token(CORREO_USUARIO);
        for (int i = 1; i <= CambioCorreoService.MAXIMO_SOLICITUDES; i++) {
            solicitar(sesion, "nuevo" + i + "@correo.pe", CONTRASENA).andExpect(status().isAccepted());
        }
        notificador.reiniciar();

        solicitar(sesion, "nuevo4@correo.pe", CONTRASENA).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.mensaje").value(DEMASIADOS_INTENTOS));

        assertThat(filaUsuario(id).get("correo_pendiente")).isEqualTo("nuevo3@correo.pe");
        assertThat(tokens()).isEqualTo(3);
        assertThat(notificador.cambios()).isEmpty();
        assertThat(notificador.avisos()).isEmpty();

        reloj.avanzar(CambioCorreoService.VENTANA_SOLICITUDES.plusSeconds(1));
        solicitar(token(CORREO_USUARIO), "nuevo4@correo.pe", CONTRASENA).andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("HU-20 R28: si el envío de correos falla, la solicitud queda confirmada y responde 202")
    void falloDelCorreo() throws Exception {
        var id = crearUsuario(CORREO_USUARIO);
        var sesion = token(CORREO_USUARIO);
        notificador.fallar();

        solicitar(sesion, NUEVO, CONTRASENA).andExpect(status().isAccepted());

        assertThat(filaUsuario(id).get("correo_pendiente")).isEqualTo(NUEVO);
        assertThat(tokens()).isEqualTo(1);
        assertThat(auditorias("CORREO_CAMBIO_SOLICITADO")).hasSize(1);
    }

    @Test
    @DisplayName("HU-20 R28: si la transacción se revierte no queda el pendiente ni el token y no se envía ningún correo")
    void rollbackNoEnvia() {
        var id = crearUsuario(CORREO_USUARIO);
        var usuario = new UsuarioAutenticado(id, 1L, "USUARIO");
        // Una IP de más de 45 caracteres hace fallar el INSERT de la auditoría dentro de la transacción.
        var ipInvalida = "x".repeat(60);

        assertThatThrownBy(() -> servicio.solicitar(usuario, new SolicitudCambioCorreoDTO(NUEVO, CONTRASENA),
                ipInvalida)).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(filaUsuario(id).get("correo_pendiente")).isNull();
        assertThat(tokens()).isZero();
        assertThat(notificador.cambios()).isEmpty();
        assertThat(notificador.avisos()).isEmpty();
    }

    // ── Confirmación inválida (R32, R33) ─────────────────────────────────

    @Test
    @DisplayName("HU-20 R32, R50: un enlace usado, vencido, anulado o desconocido responde 400 genérico y se audita")
    void enlacesInvalidos() throws Exception {
        var id = crearUsuario(CORREO_USUARIO);
        var sesion = token(CORREO_USUARIO);

        solicitar(sesion, "primero@correo.pe", CONTRASENA).andExpect(status().isAccepted());
        var anulado = ultimoToken();
        solicitar(sesion, NUEVO, CONTRASENA).andExpect(status().isAccepted());
        var vigente = ultimoToken();

        rechazoGenerico(confirmar(anulado));
        rechazoGenerico(confirmar(TokenAleatorio.generar()));
        rechazoGenerico(confirmar("corto"));
        assertThat(filaUsuario(id).get("correo")).isEqualTo(CORREO_USUARIO);

        confirmar(vigente).andExpect(status().isOk());
        rechazoGenerico(confirmar(vigente));

        assertThat(filaUsuario(id).get("correo")).isEqualTo(NUEVO);
        assertThat(motivosDeRechazo()).containsExactly("motivo=TOKEN_USADO", "motivo=TOKEN_DESCONOCIDO",
                "motivo=TOKEN_DESCONOCIDO", "motivo=TOKEN_USADO");
        assertThat(auditorias("CORREO_CAMBIO_RECHAZADO").getFirst().get("usuario_id")).isEqualTo(id);
        assertThat(auditorias("CORREO_CAMBIO_RECHAZADO").get(1).get("usuario_id")).isNull();
    }

    @Test
    @DisplayName("HU-20 R32: un enlace vencido (más de 30 min) responde 400 genérico sin cambiar el correo")
    void enlaceVencido() throws Exception {
        var id = crearUsuario(CORREO_USUARIO);
        solicitar(token(CORREO_USUARIO), NUEVO, CONTRASENA).andExpect(status().isAccepted());
        var token = ultimoToken();

        reloj.avanzar(Duration.ofMinutes(30));

        rechazoGenerico(confirmar(token));
        assertThat(filaUsuario(id).get("correo")).isEqualTo(CORREO_USUARIO);
        assertThat(motivosDeRechazo()).containsExactly("motivo=TOKEN_EXPIRADO");
    }

    @Test
    @DisplayName("HU-20 R32: la confirmación de una cuenta suspendida responde 400 genérico")
    void cuentaSuspendida() throws Exception {
        var id = crearUsuario(CORREO_USUARIO);
        solicitar(token(CORREO_USUARIO), NUEVO, CONTRASENA).andExpect(status().isAccepted());
        jdbc.update("UPDATE usuario SET estado = 'SUSPENDIDA' WHERE id = ?", id);

        rechazoGenerico(confirmar(ultimoToken()));

        assertThat(filaUsuario(id).get("correo")).isEqualTo(CORREO_USUARIO);
        assertThat(motivosDeRechazo()).containsExactly("motivo=CUENTA_NO_HABILITADA");
    }

    @Test
    @DisplayName("HU-20 R33: si otra cuenta registra el correo pendiente antes de confirmar, 400 genérico y sin 500")
    void correoTomadoAntesDeConfirmar() throws Exception {
        var id = crearUsuario(CORREO_USUARIO);
        solicitar(token(CORREO_USUARIO), NUEVO, CONTRASENA).andExpect(status().isAccepted());
        crearUsuario(NUEVO);

        rechazoGenerico(confirmar(ultimoToken()));

        assertThat(filaUsuario(id).get("correo")).isEqualTo(CORREO_USUARIO);
        assertThat(motivosDeRechazo()).containsExactly("motivo=CORREO_EN_USO");
    }

    // Repetida: la carrera entre las dos confirmaciones no siempre se da en una sola corrida (review vuelta 1).
    @RepeatedTest(10)
    @DisplayName("HU-20 R33, R50: dos confirmaciones simultáneas del mismo enlace cambian el correo una sola vez")
    void confirmacionesSimultaneas() throws Exception {
        var id = crearUsuario(CORREO_USUARIO);
        solicitar(token(CORREO_USUARIO), NUEVO, CONTRASENA).andExpect(status().isAccepted());
        var token = ultimoToken();
        var listos = new CountDownLatch(1);
        Callable<Boolean> confirmarUno = () -> {
            listos.await();
            try {
                servicio.confirmar(token, ip);
                return true;
            } catch (ReglaNegocioException e) {
                return false;
            }
        };

        try (var hilos = Executors.newFixedThreadPool(2)) {
            var a = hilos.submit(confirmarUno);
            var b = hilos.submit(confirmarUno);
            listos.countDown();
            assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(true, false);
        }

        assertThat(filaUsuario(id).get("correo")).isEqualTo(NUEVO);
        assertThat(auditorias("CORREO_CAMBIADO")).hasSize(1);
        assertThat(motivosDeRechazo()).containsExactly("motivo=TOKEN_USADO");
    }

    // ── Datos sensibles (R46) ────────────────────────────────────────────

    @Test
    @DisplayName("HU-20 R46: ni los logs ni la auditoría contienen la contraseña, el correo nuevo completo ni el token")
    void sinDatosSensibles(CapturedOutput salida) throws Exception {
        crearUsuario(CORREO_USUARIO);
        var sesion = token(CORREO_USUARIO);

        solicitar(sesion, NUEVO, INCORRECTA).andExpect(status().isBadRequest());
        solicitar(sesion, NUEVO, CONTRASENA).andExpect(status().isAccepted());
        var token = ultimoToken();
        confirmar(token).andExpect(status().isOk());
        rechazoGenerico(confirmar(token));

        var registros = String.join("\n", detallesDeAuditoria());
        assertThat(registros).doesNotContain(NUEVO).doesNotContain(CONTRASENA).doesNotContain(INCORRECTA)
                .doesNotContain(token).doesNotContain(TokenAleatorio.hash(token));
        assertThat(salida.getAll()).doesNotContain(NUEVO).doesNotContain(CONTRASENA).doesNotContain(INCORRECTA)
                .doesNotContain(token);
    }
}
