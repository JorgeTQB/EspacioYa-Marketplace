package pe.edu.ulima.espacioya.busqueda.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.net.URI;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import pe.edu.ulima.espacioya.RelojControlable;
import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.TipoDocumento;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;
import pe.edu.ulima.espacioya.espacios.service.puertos.BloqueReservado;
import pe.edu.ulima.espacioya.espacios.service.puertos.ReservasDelEspacio;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Ficha pública y bloques libres contra PostgreSQL real. El reloj está fijo en el miércoles 2026-10-28 a las 10:00
 * de Lima y el puerto de reservas es un doble. Cada test usa su propia IP (10.12.x.x).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FichaControllerIT.Dobles.class})
class FichaControllerIT {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 28);
    private static final LocalDate LUNES = LocalDate.of(2026, 11, 2);
    private static final String NO_ENCONTRADO = "No encontramos lo que buscas o el enlace expiró.";
    private static final String MENSAJE_VALIDACION = "Revisa los datos del formulario.";
    private static final String FECHA_INVALIDA = "Elige una fecha válida.";
    private static final String FUERA_DE_VENTANA = "Elige una fecha entre hoy y las próximas 8 semanas.";

    // Datos del arrendador que nunca deben salir en la ficha (R7).
    private static final String CORREO_SECRETO = "arrendador.secreto@correo.pe";
    private static final String TELEFONO_SECRETO = "999111222";
    private static final String NOMBRE_SECRETO = "Ana Reservada Quispe";
    private static final String DOCUMENTO_SECRETO = "20100070970";
    private static final String RAZON_SOCIAL_SECRETA = "Inversiones Ocultas SAC";
    private static final String DIRECCION_FISCAL_SECRETA = "Jr. Fiscal Escondido 777";

    private static final AtomicInteger SIGUIENTE_IP = new AtomicInteger(1);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RelojControlable reloj;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private PerfilArrendadorRepository perfiles;
    @Autowired
    private ReservasFalsas reservas;
    @Autowired
    private JsonMapper json;

    private String ip;
    private long arrendador;

    @TestConfiguration(proxyBeanMethods = false)
    static class Dobles {

        @Bean
        @Primary
        RelojControlable relojControlable() {
            return new RelojControlable(Instant.parse("2026-10-28T15:00:00Z"));
        }

        @Bean
        @Primary
        ReservasFalsas reservasFalsas() {
            return new ReservasFalsas();
        }
    }

    /** Devuelve las reservas que el test cargue por espacio; ninguna por defecto. */
    static class ReservasFalsas implements ReservasDelEspacio {

        private final Map<Long, List<BloqueReservado>> porEspacio = new ConcurrentHashMap<>();

        void cargar(long espacioId, BloqueReservado... bloques) {
            porEspacio.computeIfAbsent(espacioId, id -> new ArrayList<>()).addAll(List.of(bloques));
        }

        void limpiar() {
            porEspacio.clear();
        }

        @Override
        public List<BloqueReservado> bloquesReservados(Long espacioId, LocalDate desde, LocalDate hasta) {
            return porEspacio.getOrDefault(espacioId, List.of()).stream()
                    .filter(bloque -> !bloque.fecha().isBefore(desde) && !bloque.fecha().isAfter(hasta))
                    .toList();
        }
    }

    @BeforeEach
    void prepararBase() {
        jdbc.update("DELETE FROM suspension_cuenta");
        jdbc.update("DELETE FROM foto_espacio");
        jdbc.update("DELETE FROM espacio");
        jdbc.update("DELETE FROM documento_identidad");
        jdbc.update("DELETE FROM sesion");
        jdbc.update("DELETE FROM auditoria");
        jdbc.update("DELETE FROM perfil_arrendador");
        jdbc.update("DELETE FROM usuario");
        reservas.limpiar();
        ip = siguienteIp();
        arrendador = crearArrendador();
    }

    // HU-01…HU-09 usan 10.1.x.x a 10.9.x.x; HU-10, 10.10 y 10.11; LimiteFichaIT, 10.13.
    private static String siguienteIp() {
        var n = SIGUIENTE_IP.getAndIncrement();
        return "10.12." + (n / 250) + "." + (n % 250 + 1);
    }

    // ── Datos ────────────────────────────────────────────────────────────

    private long crearArrendador() {
        var usuario = usuarios.save(Usuario.registrarArrendador(CORREO_SECRETO, "x", NOMBRE_SECRETO,
                TELEFONO_SECRETO, "2026-10", reloj.instant()));
        perfiles.save(PerfilArrendador.crear(usuario.getId(), TipoDocumento.RUC, DOCUMENTO_SECRETO,
                String.format("%064d", 1), RAZON_SOCIAL_SECRETA, DIRECCION_FISCAL_SECRETA));
        return usuario.getId();
    }

    private long espacio(String estado) {
        return jdbc.queryForObject("INSERT INTO espacio (arrendador_id, nombre, tipo, direccion, zona, capacidad, "
                        + "precio_hora, descripcion, estado, creado_en, actualizado_en) VALUES (?, ?, ?, ?, ?, ?, ?, "
                        + "?, ?, now(), now()) RETURNING id", Long.class, arrendador, "Cancha Los Olivos", "CANCHA",
                "Av. Universitaria 1234, Los Olivos", "LOS_OLIVOS", 22, new BigDecimal("80.00"),
                "Cancha de grass sintético con iluminación.", estado);
    }

    private long publicado() {
        return espacio("PUBLICADO");
    }

    private void franja(long espacioId, LocalDate fecha, int inicio, int fin) {
        jdbc.update("INSERT INTO horario_semanal (espacio_id, dia_semana, hora_inicio, hora_fin) VALUES (?, ?, ?, ?)",
                espacioId, fecha.getDayOfWeek().getValue(), inicio, fin);
    }

    private String foto(long espacioId, int orden) {
        var uuid = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO foto_espacio (espacio_id, ruta_archivo, tipo_mime, orden) VALUES (?, ?, ?, ?)",
                espacioId, "espacios/" + uuid, "image/jpeg", orden);
        return uuid;
    }

    private int auditorias() {
        return jdbc.queryForObject("SELECT count(*) FROM auditoria", Integer.class);
    }

    // ── Llamadas ─────────────────────────────────────────────────────────

    private ResultActions pedir(MockHttpServletRequestBuilder solicitud) throws Exception {
        var origen = ip;
        return mvc.perform(solicitud.with(peticion -> {
            peticion.setRemoteAddr(origen);
            return peticion;
        }));
    }

    private ResultActions ficha(Object id) throws Exception {
        return pedir(get(URI.create("/api/espacios/" + id)));
    }

    private ResultActions disponibilidad(Object id, String fecha) throws Exception {
        var url = "/api/espacios/" + id + "/disponibilidad" + (fecha == null ? "" : "?fecha=" + fecha);
        return pedir(get(URI.create(url)));
    }

    private Map<String, Object> cuerpo(ResultActions respuesta) throws Exception {
        return json.readValue(respuesta.andReturn().getResponse().getContentAsString(),
                new TypeReference<Map<String, Object>>() { });
    }

    private String texto(ResultActions respuesta) throws Exception {
        return respuesta.andReturn().getResponse().getContentAsString();
    }

    private List<Integer> horasLibres(ResultActions respuesta) throws Exception {
        respuesta.andExpect(status().isOk());
        return bloques(respuesta).stream().map(bloque -> (Integer) bloque.get("horaInicio")).toList();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> bloques(ResultActions respuesta) throws Exception {
        return (List<Map<String, Object>>) cuerpo(respuesta).get("bloquesLibres");
    }

    private void esNoEncontrado(ResultActions respuesta) throws Exception {
        respuesta.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value(NO_ENCONTRADO))
                .andExpect(jsonPath("$.errores").isEmpty());
    }

    private void esErrorDeFecha(ResultActions respuesta, String mensaje) throws Exception {
        respuesta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_VALIDACION))
                .andExpect(jsonPath("$.errores.length()").value(1))
                .andExpect(jsonPath("$.errores[0].campo").value("fecha"))
                .andExpect(jsonPath("$.errores[0].mensaje").value(mensaje))
                .andExpect(jsonPath("$.bloquesLibres").doesNotExist());
    }

    // ── Ficha ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-11 R1, R3: sin sesión, la ficha de un espacio visible responde 200 con sus datos guardados")
    void fichaDeUnEspacioVisible() throws Exception {
        var id = publicado();

        ficha(id).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.nombre").value("Cancha Los Olivos"))
                .andExpect(jsonPath("$.tipo").value("CANCHA"))
                .andExpect(jsonPath("$.direccion").value("Av. Universitaria 1234, Los Olivos"))
                .andExpect(jsonPath("$.zona").value("LOS_OLIVOS"))
                .andExpect(jsonPath("$.capacidad").value(22))
                .andExpect(jsonPath("$.precioHora").value(80.00))
                .andExpect(jsonPath("$.descripcion").value("Cancha de grass sintético con iluminación."));
    }

    @Test
    @DisplayName("HU-11 R2: la ficha trae exactamente los diez campos del contrato y los dos del resumen de HU-24")
    void fichaConExactamenteSusCampos() throws Exception {
        var id = publicado();
        foto(id, 1);

        assertThat(cuerpo(ficha(id).andExpect(status().isOk()))).containsOnlyKeys("id", "nombre", "tipo",
                "direccion", "zona", "zonaNombre", "capacidad", "precioHora", "descripcion", "fotos",
                "calificacionPromedio", "totalResenas");
    }

    @Test
    @DisplayName("HU-11 R4: zonaNombre es el nombre de la zona del espacio")
    void fichaConNombreDeZona() throws Exception {
        var id = publicado();

        ficha(id).andExpect(status().isOk()).andExpect(jsonPath("$.zonaNombre").value("Los Olivos"));
    }

    @Test
    @DisplayName("HU-11 R5: las fotos salen como /api/fotos/<UUID> en orden ascendente aunque se guardaran en otro orden")
    void fotosEnOrden() throws Exception {
        var id = publicado();
        var tercera = foto(id, 3);
        var primera = foto(id, 1);
        var segunda = foto(id, 2);

        ficha(id).andExpect(status().isOk())
                .andExpect(jsonPath("$.fotos.length()").value(3))
                .andExpect(jsonPath("$.fotos[0]").value("/api/fotos/" + primera))
                .andExpect(jsonPath("$.fotos[1]").value("/api/fotos/" + segunda))
                .andExpect(jsonPath("$.fotos[2]").value("/api/fotos/" + tercera));
    }

    @Test
    @DisplayName("HU-11 R6: un espacio visible sin fotos responde 200 con fotos vacía")
    void sinFotos() throws Exception {
        var id = publicado();

        ficha(id).andExpect(status().isOk()).andExpect(jsonPath("$.fotos").isArray())
                .andExpect(jsonPath("$.fotos").isEmpty());
    }

    @Test
    @DisplayName("HU-11 R7: la ficha no expone arrendadorId, estado, fechas, rutas de fotos ni datos del arrendador")
    void minimizacion() throws Exception {
        var id = publicado();
        foto(id, 1);

        var cuerpo = texto(ficha(id).andExpect(status().isOk()));

        assertThat(cuerpo).doesNotContain(CORREO_SECRETO, TELEFONO_SECRETO, NOMBRE_SECRETO, DOCUMENTO_SECRETO,
                RAZON_SOCIAL_SECRETA, DIRECCION_FISCAL_SECRETA, "espacios/", "arrendadorId", "arrendador",
                "estado", "PUBLICADO", "creadoEn", "actualizadoEn", "correo", "telefono");
    }

    @Test
    @DisplayName("HU-11 R8: con un Bearer inexistente la ficha y la disponibilidad responden 200 como sin sesión")
    void bearerInvalidoComoAnonimo() throws Exception {
        var id = publicado();
        franja(id, LUNES, 8, 10);

        pedir(get("/api/espacios/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer token-que-no-existe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        pedir(get("/api/espacios/" + id + "/disponibilidad?fecha=" + LUNES)
                .header(HttpHeaders.AUTHORIZATION, "Bearer token-que-no-existe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bloquesLibres.length()").value(2));
    }

    @Test
    @DisplayName("HU-11 R9: un id inexistente o un espacio BORRADOR, PAUSADO, BAJA u OCULTO responde el 404 genérico")
    void noPublicadosResponden404() throws Exception {
        var visible = publicado();
        esNoEncontrado(ficha(visible + 1000));
        for (var estado : List.of("BORRADOR", "PAUSADO", "BAJA", "OCULTO")) {
            var id = espacio(estado);
            foto(id, 1);
            esNoEncontrado(ficha(id));
        }
    }

    @Test
    @DisplayName("HU-11 R10: un PUBLICADO de arrendador SUSPENDIDA o ELIMINADA responde el 404 genérico")
    void arrendadorNoActivoResponde404() throws Exception {
        var id = publicado();
        for (var estado : List.of("SUSPENDIDA", "ELIMINADA")) {
            jdbc.update("UPDATE usuario SET estado = ? WHERE id = ?", estado, arrendador);
            esNoEncontrado(ficha(id));
        }
    }

    @Test
    @DisplayName("HU-11 R11: un id mal escrito responde el 404 genérico, no 400 ni 500")
    void idMalEscritoResponde404() throws Exception {
        for (var id : List.of("abc", "0", "-1", "1.5", "007a", "99999999999999999999")) {
            esNoEncontrado(ficha(id));
        }
    }

    @Test
    @DisplayName("HU-11 R12: todos los 404 de la ficha y la disponibilidad tienen el mismo cuerpo")
    void cuerposIguales() throws Exception {
        var borrador = espacio("BORRADOR");
        var suspendido = publicado();
        var cuerpos = new ArrayList<String>();
        cuerpos.add(texto(ficha(borrador)));
        cuerpos.add(texto(ficha(borrador + 1000)));
        cuerpos.add(texto(ficha("abc")));
        cuerpos.add(texto(disponibilidad(borrador, LUNES.toString())));
        cuerpos.add(texto(disponibilidad("abc", LUNES.toString())));
        jdbc.update("UPDATE usuario SET estado = 'SUSPENDIDA' WHERE id = ?", arrendador);
        cuerpos.add(texto(ficha(suspendido)));
        cuerpos.add(texto(disponibilidad(suspendido, LUNES.toString())));

        assertThat(cuerpos).allSatisfy(cuerpo -> assertThat(cuerpo).isEqualTo(cuerpos.getFirst()));
        assertThat(cuerpos.getFirst()).contains(NO_ENCONTRADO);
    }

    // ── Disponibilidad ───────────────────────────────────────────────────

    @Test
    @DisplayName("HU-11 R13, R14: sin sesión, los bloques libres del día salen en orden con horaFin = horaInicio + 1")
    void bloquesLibresDelDia() throws Exception {
        var id = publicado();
        franja(id, LUNES, 14, 16);
        franja(id, LUNES, 8, 10);

        var respuesta = disponibilidad(id, LUNES.toString()).andExpect(status().isOk())
                .andExpect(jsonPath("$.fecha").value(LUNES.toString()));

        assertThat(bloques(respuesta)).containsExactly(
                Map.of("horaInicio", 8, "horaFin", 9), Map.of("horaInicio", 9, "horaFin", 10),
                Map.of("horaInicio", 14, "horaFin", 15), Map.of("horaInicio", 15, "horaFin", 16));
    }

    @Test
    @DisplayName("HU-11 R15: una fecha bloqueada responde bloquesLibres vacía")
    void fechaBloqueada() throws Exception {
        var id = publicado();
        franja(id, LUNES, 8, 10);
        jdbc.update("INSERT INTO bloqueo_fecha (espacio_id, fecha) VALUES (?, ?)", id, Date.valueOf(LUNES));

        assertThat(horasLibres(disponibilidad(id, LUNES.toString()))).isEmpty();
    }

    @Test
    @DisplayName("HU-11 R16: excluye las horas que el puerto ReservasDelEspacio informa como reservadas")
    void excluyeReservadas() throws Exception {
        var id = publicado();
        franja(id, LUNES, 8, 11);
        reservas.cargar(id, new BloqueReservado(LUNES, 9, true));

        assertThat(horasLibres(disponibilidad(id, LUNES.toString()))).containsExactly(8, 10);
    }

    @Test
    @DisplayName("HU-11 R17: hoy, a las 10:00 de Lima, con franja 8–12 solo queda libre el bloque de las 11")
    void hoyExcluyeLasHorasEmpezadas() throws Exception {
        var id = publicado();
        franja(id, HOY, 8, 12);

        assertThat(horasLibres(disponibilidad(id, HOY.toString()))).containsExactly(11);
    }

    @Test
    @DisplayName("HU-11 R18: un día de la semana sin franjas responde 200 con bloquesLibres vacía")
    void diaSinHorario() throws Exception {
        var id = publicado();
        franja(id, LUNES.plusDays(1), 8, 10);

        disponibilidad(id, LUNES.toString()).andExpect(status().isOk())
                .andExpect(jsonPath("$.fecha").value(LUNES.toString()))
                .andExpect(jsonPath("$.bloquesLibres").isArray())
                .andExpect(jsonPath("$.bloquesLibres").isEmpty());
    }

    @Test
    @DisplayName("HU-11 R19: la disponibilidad solo trae fecha y bloquesLibres, y cada bloque solo sus dos horas")
    void disponibilidadMinima() throws Exception {
        var id = publicado();
        franja(id, LUNES, 8, 11);
        reservas.cargar(id, new BloqueReservado(LUNES, 9, true));

        var respuesta = disponibilidad(id, LUNES.toString()).andExpect(status().isOk());

        assertThat(cuerpo(respuesta)).containsOnlyKeys("fecha", "bloquesLibres");
        assertThat(bloques(respuesta)).isNotEmpty()
                .allSatisfy(bloque -> assertThat(bloque).containsOnlyKeys("horaInicio", "horaFin"));
        assertThat(texto(respuesta)).doesNotContain("RESERVADO", "BLOQUEADO", "PASADO", "estado");
    }

    @Test
    @DisplayName("HU-11 R20: la disponibilidad de un id mal escrito, inexistente o no visible responde el 404 genérico")
    void disponibilidadNoVisibleResponde404() throws Exception {
        var borrador = espacio("BORRADOR");
        franja(borrador, LUNES, 8, 10);
        esNoEncontrado(disponibilidad(borrador, LUNES.toString()));
        esNoEncontrado(disponibilidad(borrador + 1000, LUNES.toString()));
        esNoEncontrado(disponibilidad("abc", LUNES.toString()));
        esNoEncontrado(disponibilidad("0", LUNES.toString()));
        var suspendido = publicado();
        franja(suspendido, LUNES, 8, 10);
        jdbc.update("UPDATE usuario SET estado = 'SUSPENDIDA' WHERE id = ?", arrendador);
        esNoEncontrado(disponibilidad(suspendido, LUNES.toString()));
    }

    @Test
    @DisplayName("HU-11 R21: una fecha ausente, vacía o mal escrita responde 400 con un error en fecha")
    void fechaInvalida() throws Exception {
        var id = publicado();
        esErrorDeFecha(disponibilidad(id, null), FECHA_INVALIDA);
        esErrorDeFecha(disponibilidad(id, ""), FECHA_INVALIDA);
        esErrorDeFecha(disponibilidad(id, "2026-02-30"), FECHA_INVALIDA);
        esErrorDeFecha(disponibilidad(id, "02/11/2026"), FECHA_INVALIDA);
        esErrorDeFecha(disponibilidad(id, "manana"), FECHA_INVALIDA);
    }

    @Test
    @DisplayName("HU-11 R22: una fecha anterior a hoy o posterior a hoy + 55 responde 400 'fuera de ventana'")
    void fechaFueraDeVentana() throws Exception {
        var id = publicado();
        esErrorDeFecha(disponibilidad(id, HOY.minusDays(1).toString()), FUERA_DE_VENTANA);
        esErrorDeFecha(disponibilidad(id, HOY.plusDays(56).toString()), FUERA_DE_VENTANA);
        disponibilidad(id, HOY.plusDays(55).toString()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("HU-11 R23: un espacio no visible con una fecha inválida responde 404, no 400")
    void noVisibleYFechaInvalidaResponde404() throws Exception {
        var borrador = espacio("BORRADOR");
        esNoEncontrado(disponibilidad(borrador, "2026-02-30"));
        esNoEncontrado(disponibilidad(borrador, null));
        esNoEncontrado(disponibilidad("abc", HOY.plusDays(90).toString()));
    }

    // ── Caché y auditoría ────────────────────────────────────────────────

    @Test
    @DisplayName("HU-11 R24: la ficha y la disponibilidad salen con Cache-Control no-store")
    void sinCache() throws Exception {
        var id = publicado();

        ficha(id).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
        disponibilidad(id, LUNES.toString()).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
    }

    @Test
    @DisplayName("HU-11 R25: ninguna respuesta de la ficha ni de la disponibilidad registra eventos de auditoría")
    void sinAuditoria() throws Exception {
        var id = publicado();
        var borrador = espacio("BORRADOR");
        var antes = auditorias();

        ficha(id).andExpect(status().isOk());
        ficha(borrador).andExpect(status().isNotFound());
        ficha("abc").andExpect(status().isNotFound());
        disponibilidad(id, LUNES.toString()).andExpect(status().isOk());
        disponibilidad(id, "2026-02-30").andExpect(status().isBadRequest());
        disponibilidad(borrador, LUNES.toString()).andExpect(status().isNotFound());

        assertThat(auditorias()).isEqualTo(antes);
    }
}
