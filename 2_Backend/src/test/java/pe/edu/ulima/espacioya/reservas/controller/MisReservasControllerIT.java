package pe.edu.ulima.espacioya.reservas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;
import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.TipoDocumento;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;

/**
 * "Mis reservas" y "Reservas de mis espacios" contra PostgreSQL real (HU-14). Reloj en el miércoles 2026-10-28 a las
 * 10:00 de Lima. Las pasadas, las fechas distintas y los estados raros se siembran con {@code filaReserva} y
 * {@code filaBloque}; un caso de punta a punta llega a CONFIRMADA por la API con la pasarela simulada (D9). Cada test
 * usa su propia IP (10.16.x.x).
 */
class MisReservasControllerIT extends ReservasControllerSoporte {

    private static final String DE_MIS_ESPACIOS = "/api/arrendadores/actual/reservas";
    private static final String MENSAJE_VALIDACION = "Revisa los datos del formulario.";
    private static final String SESION_INVALIDA = "Tu sesión no es válida o expiró. Inicia sesión de nuevo.";
    private static final String ACCESO_DENEGADO = "No tienes permiso para realizar esta acción.";
    private static final String M_GRUPO = "Elige próximas o pasadas.";
    private static final String M_PAGINA = "La página no es válida.";
    private static final String POR_DEFECTO = "Usuario de EspacioYa";
    private static final String CORREO_OTRO_ARRENDADOR = "luis.otro@correo.pe";
    private static final String TELEFONO_OTRO_ARRENDADOR = "988777666";
    private static final String CORREO_ADMIN = "admin.hu14@espacioya.pe";

    private static final AtomicInteger SIGUIENTE_IP = new AtomicInteger(1);
    private static final AtomicInteger SIGUIENTE_HUELLA = new AtomicInteger(1);
    private static String hash;

    private LocalDate hoy;

    @BeforeEach
    void prepararHu14() {
        // Corre después del @BeforeEach del soporte: reemplaza su IP 10.14 por una de HU-14.
        var n = SIGUIENTE_IP.getAndIncrement();
        ip = "10.16." + (n / 250) + "." + (n % 250 + 1);
        hoy = ZonaHoraria.hoy(reloj);
        if (hash == null) {
            hash = encoder.encode(CONTRASENA);
        }
    }

    // pago no tiene cascada desde reserva y el soporte no lo borra; este @AfterEach corre antes que su limpiar().
    @AfterEach
    void borrarPagos() {
        jdbc.update("DELETE FROM reembolso");
        jdbc.update("DELETE FROM pago");
    }

    // ── Datos ────────────────────────────────────────────────────────────

    private long otroArrendador(String correo) {
        var cuenta = usuarios.save(Usuario.registrarArrendador(correo, hash, "Luis Otro", TELEFONO_OTRO_ARRENDADOR,
                "2026-10", reloj.instant()));
        perfiles.save(PerfilArrendador.crear(cuenta.getId(), TipoDocumento.RUC, "2010007" + (1000 + cuenta.getId()),
                String.format("f%063d", SIGUIENTE_HUELLA.getAndIncrement()), "Otra Empresa SAC", "Jr. Otro 123"));
        return cuenta.getId();
    }

    private long espacioDe(long arrendador, String nombre, String estado) {
        return jdbc.queryForObject("INSERT INTO espacio (arrendador_id, nombre, tipo, direccion, zona, capacidad, "
                        + "precio_hora, descripcion, estado, creado_en, actualizado_en) VALUES (?, ?, 'CANCHA', "
                        + "'Av. Universitaria 1234, Los Olivos', 'LOS_OLIVOS', 22, 80.50, "
                        + "'Cancha de grass sintético con iluminación.', ?, now(), now()) RETURNING id", Long.class,
                arrendador, nombre, estado);
    }

    private void nombreDe(long usuario, String nombre) {
        jdbc.update("UPDATE usuario SET nombre = ? WHERE id = ?", nombre, usuario);
    }

    /** Una reserva sembrada con una fila de reserva_bloque por hora en {@code dia}. */
    private long sembrar(long usuario, long espacio, String estado, Instant expiraEn, LocalDate dia, boolean activo,
                         int... horas) {
        var reserva = filaReserva(usuario, espacio, estado, expiraEn);
        for (var hora : horas) {
            filaBloque(reserva, espacio, dia, hora, activo);
        }
        return reserva;
    }

    private long confirmada(long usuario, long espacio, LocalDate dia, int... horas) {
        return sembrar(usuario, espacio, "CONFIRMADA", haceMinutos(60), dia, true, horas);
    }

    // ── Llamadas ─────────────────────────────────────────────────────────

    private ResultActions misReservas(String sesion, String consulta) throws Exception {
        return mvc.perform(con(sesion, get(RESERVAS + consulta)));
    }

    private ResultActions deMisEspacios(String sesion, String consulta) throws Exception {
        return mvc.perform(con(sesion, get(DE_MIS_ESPACIOS + consulta)));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> reservas(ResultActions respuesta) throws Exception {
        return (List<Map<String, Object>>) mapa(texto(respuesta.andExpect(status().isOk()))).get("reservas");
    }

    private List<Long> ids(ResultActions respuesta) throws Exception {
        return reservas(respuesta).stream().map(reserva -> ((Number) reserva.get("id")).longValue()).toList();
    }

    private static Map<String, Object> conId(List<Map<String, Object>> reservas, long id) {
        return reservas.stream().filter(reserva -> ((Number) reserva.get("id")).longValue() == id).findFirst()
                .orElseThrow();
    }

    private String tokenAdmin() throws Exception {
        crearAdmin(CORREO_ADMIN);
        return token(CORREO_ADMIN, IP_ADMIN);
    }

    private void esErrorDeCampo(ResultActions respuesta, String campo, String mensaje) throws Exception {
        respuesta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_VALIDACION))
                .andExpect(jsonPath("$.errores", hasSize(1)))
                .andExpect(jsonPath("$.errores[0].campo").value(campo))
                .andExpect(jsonPath("$.errores[0].mensaje").value(mensaje));
    }

    private void esPrimeraPaginaVacia(ResultActions respuesta) throws Exception {
        respuesta.andExpect(status().isOk())
                .andExpect(jsonPath("$.pagina").value(1))
                .andExpect(jsonPath("$.reservas", hasSize(0)))
                .andExpect(jsonPath("$.hayMas").value(false));
    }

    // ── Sesión y rol (R1–R3) ─────────────────────────────────────────────

    @Test
    @DisplayName("HU-14 R1: sin sesión, las dos rutas responden 401 con el mensaje de sesión inválida")
    void sinSesion() throws Exception {
        misReservas(null, "?grupo=proximas").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(SESION_INVALIDA));
        deMisEspacios(null, "?grupo=proximas").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(SESION_INVALIDA));
        misReservas("token-inventado", "?grupo=pasadas").andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ARRENDADOR", "ADMIN"})
    @DisplayName("HU-14 R2: ARRENDADOR y ADMIN reciben 403 en /api/reservas, también con grupo y página inválidos")
    void misReservasConOtroRol(String rol) throws Exception {
        confirmada(usuarioId, espacioId, fecha, 14);
        var sesion = "ADMIN".equals(rol) ? tokenAdmin() : token(CORREO_ARRENDADOR);

        for (var consulta : List.of("?grupo=proximas", "?grupo=todas&pagina=0", "")) {
            misReservas(sesion, consulta).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.mensaje").value(ACCESO_DENEGADO))
                    .andExpect(jsonPath("$.errores", hasSize(0)));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"USUARIO", "ADMIN"})
    @DisplayName("HU-14 R3: USUARIO y ADMIN reciben 403 en la ruta del arrendador, también con grupo inválido")
    void deMisEspaciosConOtroRol(String rol) throws Exception {
        confirmada(usuarioId, espacioId, fecha, 14);
        var sesion = "ADMIN".equals(rol) ? tokenAdmin() : token(CORREO_USUARIO);

        for (var consulta : List.of("?grupo=proximas", "?grupo=todas&pagina=abc", "")) {
            deMisEspacios(sesion, consulta).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.mensaje").value(ACCESO_DENEGADO))
                    .andExpect(jsonPath("$.errores", hasSize(0)));
        }
    }

    // ── Mis reservas (R4–R12) ────────────────────────────────────────────

    @Test
    @DisplayName("HU-14 R4, R11, R12: próximas del usuario con exactamente las claves del ReservaDTO y 900 segundos")
    void misReservasProximas() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var vigente = idDe(reservar(sesion, espacioId, fecha, List.of(14, 15)).andExpect(status().isCreated()));

        var respuesta = misReservas(sesion, "?grupo=proximas&pagina=1").andExpect(status().isOk())
                .andExpect(jsonPath("$.pagina").value(1))
                .andExpect(jsonPath("$.hayMas").value(false))
                .andExpect(jsonPath("$.reservas", hasSize(1)))
                .andExpect(jsonPath("$.reservas[0].id").value(vigente))
                .andExpect(jsonPath("$.reservas[0].espacioId").value(espacioId))
                .andExpect(jsonPath("$.reservas[0].espacioNombre").value("Cancha Los Olivos"))
                .andExpect(jsonPath("$.reservas[0].estado").value("PENDIENTE_PAGO"))
                .andExpect(jsonPath("$.reservas[0].fecha").value(fecha.toString()))
                .andExpect(jsonPath("$.reservas[0].horaInicio").value(14))
                .andExpect(jsonPath("$.reservas[0].horaFin").value(16))
                .andExpect(jsonPath("$.reservas[0].precioHora").value(80.50))
                .andExpect(jsonPath("$.reservas[0].montoTotal").value(161.00))
                .andExpect(jsonPath("$.reservas[0].segundosRestantes").value(900));

        assertThat(mapa(texto(respuesta)).keySet()).containsExactlyInAnyOrder("pagina", "reservas", "hayMas");
        assertThat(reservas(respuesta).getFirst().keySet()).containsExactlyInAnyOrder("id", "espacioId",
                "espacioNombre", "estado", "fecha", "horaInicio", "horaFin", "precioHora", "montoTotal", "expiraEn",
                "segundosRestantes");
        assertThat(texto(respuesta)).doesNotContain("usuarioId", "arrendador", CORREO_ARRENDADOR, "Ana Reservada",
                "ultimos4", "pago");
    }

    @Test
    @DisplayName("HU-14 R5, R8, R11: pasadas con el horario de una EXPIRADA cuyas filas están inactivas y 0 segundos")
    void misReservasPasadasConHorarioInactivo() throws Exception {
        var expirada = sembrar(usuarioId, espacioId, "EXPIRADA", haceMinutos(60 * 24 * 3), hoy.minusDays(3), false,
                14, 15, 16);

        var respuesta = misReservas(token(CORREO_USUARIO), "?grupo=pasadas");

        assertThat(reservas(respuesta)).singleElement().satisfies(reserva -> {
            assertThat(((Number) reserva.get("id")).longValue()).isEqualTo(expirada);
            assertThat(reserva.get("estado")).isEqualTo("EXPIRADA");
            assertThat(reserva.get("fecha")).isEqualTo(hoy.minusDays(3).toString());
            assertThat(reserva.get("horaInicio")).isEqualTo(14);
            assertThat(reserva.get("horaFin")).isEqualTo(17);
            assertThat(reserva.get("segundosRestantes")).isEqualTo(0);
            assertThat(reserva.keySet()).hasSize(11);
        });
    }

    @Test
    @DisplayName("HU-14 R10, R23: una vencida sin procesar con fecha futura va a pasadas como EXPIRADA y su fila sigue "
            + "PENDIENTE_PAGO")
    void vencidaSinProcesar() throws Exception {
        var vencida = sembrar(usuarioId, espacioId, "PENDIENTE_PAGO", haceMinutos(1), fecha, true, 14);
        var sesion = token(CORREO_USUARIO);

        assertThat(ids(misReservas(sesion, "?grupo=proximas"))).isEmpty();
        assertThat(reservas(misReservas(sesion, "?grupo=pasadas"))).singleElement().satisfies(reserva -> {
            assertThat(reserva.get("estado")).isEqualTo("EXPIRADA");
            assertThat(reserva.get("segundosRestantes")).isEqualTo(0);
        });
        assertThat(estadoGuardado(vencida)).isEqualTo("PENDIENTE_PAGO");
        assertThat(bloquesActivos(espacioId)).isEqualTo(1);
    }

    @Test
    @DisplayName("HU-14 R23: EXPIRADA y CANCELADA con fecha futura van a pasadas, con su estado")
    void expiradaYCanceladaFuturasSonPasadas() throws Exception {
        var expirada = sembrar(usuarioId, espacioId, "EXPIRADA", haceMinutos(20), fecha, false, 14);
        var cancelada = sembrar(usuarioId, espacioId, "CANCELADA", haceMinutos(600), fecha.plusDays(1), false, 9);
        var sesion = token(CORREO_USUARIO);

        assertThat(ids(misReservas(sesion, "?grupo=proximas"))).isEmpty();
        var pasadas = reservas(misReservas(sesion, "?grupo=pasadas"));
        assertThat(pasadas).extracting(reserva -> ((Number) reserva.get("id")).longValue())
                .containsExactly(cancelada, expirada);
        assertThat(conId(pasadas, cancelada).get("estado")).isEqualTo("CANCELADA");
        assertThat(conId(pasadas, expirada).get("estado")).isEqualTo("EXPIRADA");
    }

    @Test
    @DisplayName("HU-14 R9: el nombre del espacio se conserva con el espacio PAUSADO o el arrendador suspendido")
    void nombreDeUnEspacioNoVisible() throws Exception {
        confirmada(usuarioId, espacioId, fecha, 14);
        var usuario = token(CORREO_USUARIO);
        var arrendador = token(CORREO_ARRENDADOR);
        jdbc.update("UPDATE espacio SET estado = 'PAUSADO' WHERE id = ?", espacioId);

        misReservas(usuario, "?grupo=proximas")
                .andExpect(jsonPath("$.reservas[0].espacioNombre").value("Cancha Los Olivos"));
        deMisEspacios(arrendador, "?grupo=proximas")
                .andExpect(jsonPath("$.reservas[0].espacioNombre").value("Cancha Los Olivos"));

        jdbc.update("UPDATE espacio SET estado = 'PUBLICADO' WHERE id = ?", espacioId);
        jdbc.update("UPDATE usuario SET estado = 'SUSPENDIDA' WHERE id = ?", arrendadorId);
        misReservas(usuario, "?grupo=proximas")
                .andExpect(jsonPath("$.reservas[0].espacioNombre").value("Cancha Los Olivos"));
    }

    @Test
    @DisplayName("HU-14 R6, R7: el usuario solo ve sus reservas y ?usuarioId de otro se ignora")
    void soloLasDelUsuarioDeLaSesion() throws Exception {
        var otro = crearUsuario(OTRO_CORREO);
        var propia = confirmada(usuarioId, espacioId, fecha, 14);
        var ajena = confirmada(otro, espacioId, fecha, 16);
        sembrar(otro, espacioId, "EXPIRADA", haceMinutos(60), hoy.minusDays(2), false, 9);
        var sesion = token(CORREO_USUARIO);

        assertThat(ids(misReservas(sesion, "?grupo=proximas"))).containsExactly(propia);
        assertThat(ids(misReservas(sesion, "?grupo=proximas&usuarioId=" + otro + "&espacioId=" + espacioId
                + "&arrendadorId=" + arrendadorId))).containsExactly(propia);
        assertThat(ids(misReservas(sesion, "?grupo=pasadas&usuarioId=" + otro))).isEmpty();
        assertThat(ids(misReservas(token(OTRO_CORREO), "?grupo=proximas"))).containsExactly(ajena);
    }

    // ── CA2 de punta a punta con la pasarela simulada (D9) ───────────────

    @Test
    @DisplayName("HU-14 R4, R13, R15: reservar, pagar y aprobar el pago simulado la muestra CONFIRMADA al usuario y "
            + "al arrendador")
    void confirmadaDePuntaAPunta() throws Exception {
        var usuario = token(CORREO_USUARIO);
        var arrendador = token(CORREO_ARRENDADOR);
        var reserva = idDe(reservar(usuario, espacioId, fecha, List.of(18, 19)).andExpect(status().isCreated()));
        assertThat(ids(deMisEspacios(arrendador, "?grupo=proximas"))).isEmpty();

        mvc.perform(con(usuario, post(RESERVAS + "/" + reserva + "/pago"))).andExpect(status().isOk());
        mvc.perform(con(usuario, post(RESERVAS + "/" + reserva + "/pago/simulacion"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"resultado\":\"APROBADO\"}"))
                .andExpect(status().isNoContent());

        deMisEspacios(arrendador, "?grupo=proximas").andExpect(status().isOk())
                .andExpect(jsonPath("$.reservas", hasSize(1)))
                .andExpect(jsonPath("$.reservas[0].id").value(reserva))
                .andExpect(jsonPath("$.reservas[0].estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.reservas[0].usuarioNombre").value(POR_DEFECTO))
                .andExpect(jsonPath("$.reservas[0].fecha").value(fecha.toString()))
                .andExpect(jsonPath("$.reservas[0].horaInicio").value(18))
                .andExpect(jsonPath("$.reservas[0].horaFin").value(20))
                .andExpect(jsonPath("$.reservas[0].montoTotal").value(161.00));
        misReservas(usuario, "?grupo=proximas").andExpect(status().isOk())
                .andExpect(jsonPath("$.reservas", hasSize(1)))
                .andExpect(jsonPath("$.reservas[0].id").value(reserva))
                .andExpect(jsonPath("$.reservas[0].estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.reservas[0].segundosRestantes").value(0));
        assertThat(estadoGuardado(reserva)).isEqualTo("CONFIRMADA");
    }

    // ── Reservas de mis espacios (R13–R21) ───────────────────────────────

    @Test
    @DisplayName("HU-14 R13, R15: solo las CONFIRMADA; pendiente vigente, vencida, EXPIRADA y CANCELADA no aparecen")
    void soloConfirmadas() throws Exception {
        var confirmadaId = confirmada(usuarioId, espacioId, fecha, 8);
        sembrar(usuarioId, espacioId, "PENDIENTE_PAGO", dentroDe(10), fecha, true, 10);
        sembrar(usuarioId, espacioId, "PENDIENTE_PAGO", haceMinutos(1), fecha, true, 12);
        sembrar(usuarioId, espacioId, "EXPIRADA", haceMinutos(30), fecha, false, 14);
        sembrar(usuarioId, espacioId, "CANCELADA", haceMinutos(300), fecha, false, 16);
        sembrar(usuarioId, espacioId, "EXPIRADA", haceMinutos(60 * 48), hoy.minusDays(2), false, 9);
        var sesion = token(CORREO_ARRENDADOR);

        assertThat(ids(deMisEspacios(sesion, "?grupo=proximas"))).containsExactly(confirmadaId);
        assertThat(ids(deMisEspacios(sesion, "?grupo=pasadas"))).isEmpty();
    }

    @Test
    @DisplayName("HU-14 R14, R16, R17: pasadas y próximas de sus espacios en cualquier estado, nunca de otro "
            + "arrendador; ?arrendadorId se ignora")
    void deSusEspaciosEnCualquierEstado() throws Exception {
        var pausado = espacioDe(arrendadorId, "Auditorio Pausado", "PAUSADO");
        var deBaja = espacioDe(arrendadorId, "Estadio de Baja", "BAJA");
        var oculto = espacioDe(arrendadorId, "Cancha Oculta", "OCULTO");
        var otro = otroArrendador(CORREO_OTRO_ARRENDADOR);
        var ajeno = espacioDe(otro, "Cancha Ajena", "PUBLICADO");
        var enPublicado = confirmada(usuarioId, espacioId, fecha, 8);
        var enPausado = confirmada(usuarioId, pausado, fecha.plusDays(1), 8);
        var enOculto = confirmada(usuarioId, oculto, fecha.plusDays(2), 8);
        var pasadaEnBaja = confirmada(usuarioId, deBaja, hoy.minusDays(5), 20);
        var ajenaProxima = confirmada(usuarioId, ajeno, fecha, 8);
        var ajenaPasada = confirmada(usuarioId, ajeno, hoy.minusDays(1), 8);
        var sesion = token(CORREO_ARRENDADOR);

        var proximas = reservas(deMisEspacios(sesion, "?grupo=proximas"));
        assertThat(proximas).extracting(reserva -> ((Number) reserva.get("id")).longValue())
                .containsExactly(enPublicado, enPausado, enOculto);
        assertThat(conId(proximas, enPausado).get("espacioNombre")).isEqualTo("Auditorio Pausado");
        assertThat(ids(deMisEspacios(sesion, "?grupo=pasadas"))).containsExactly(pasadaEnBaja);
        assertThat(ids(deMisEspacios(sesion, "?grupo=proximas&arrendadorId=" + otro)))
                .containsExactly(enPublicado, enPausado, enOculto);

        var delOtro = token(CORREO_OTRO_ARRENDADOR);
        assertThat(ids(deMisEspacios(delOtro, "?grupo=proximas"))).containsExactly(ajenaProxima);
        assertThat(ids(deMisEspacios(delOtro, "?grupo=pasadas&arrendadorId=" + arrendadorId)))
                .containsExactly(ajenaPasada);
    }

    @Test
    @DisplayName("HU-14 R18, R19, R36: el nombre del usuario si lo tiene; sin nombre o en blanco, Usuario de EspacioYa")
    void nombreDelUsuario() throws Exception {
        var conNombre = crearUsuario("dora@correo.pe");
        nombreDe(conNombre, "Dora Quispe");
        var enBlanco = crearUsuario("eli@correo.pe");
        nombreDe(enBlanco, "   ");
        var conNombreId = confirmada(conNombre, espacioId, fecha, 8);
        var sinNombreId = confirmada(usuarioId, espacioId, fecha, 10);
        var enBlancoId = confirmada(enBlanco, espacioId, fecha, 12);

        var lista = reservas(deMisEspacios(token(CORREO_ARRENDADOR), "?grupo=proximas"));

        assertThat(conId(lista, conNombreId).get("usuarioNombre")).isEqualTo("Dora Quispe");
        assertThat(conId(lista, sinNombreId).get("usuarioNombre")).isEqualTo(POR_DEFECTO);
        assertThat(conId(lista, enBlancoId).get("usuarioNombre")).isEqualTo(POR_DEFECTO);
    }

    @Test
    @DisplayName("HU-14 R20: exactamente las claves del ReservaDeMiEspacioDTO, sin usuarioId, correo, teléfono ni pago")
    void minimizacionDelArrendador() throws Exception {
        var conTelefono = crearUsuario("fabi@correo.pe");
        jdbc.update("UPDATE usuario SET nombre = 'Fabiola Ríos', telefono = '977666555' WHERE id = ?", conTelefono);
        confirmada(conTelefono, espacioId, fecha, 8);
        confirmada(usuarioId, espacioId, fecha, 10);

        var respuesta = deMisEspacios(token(CORREO_ARRENDADOR), "?grupo=proximas").andExpect(status().isOk());

        assertThat(mapa(texto(respuesta)).keySet()).containsExactlyInAnyOrder("pagina", "reservas", "hayMas");
        assertThat(reservas(respuesta)).hasSize(2).allSatisfy(reserva -> assertThat(reserva.keySet())
                .containsExactlyInAnyOrder("id", "espacioId", "espacioNombre", "usuarioNombre", "estado", "fecha",
                        "horaInicio", "horaFin", "montoTotal"));
        assertThat(texto(respuesta)).doesNotContain("usuarioId", "correo", "telefono", "977666555", "fabi@correo.pe",
                CORREO_USUARIO, "ultimos4", "pago", "precioHora", "expiraEn");
    }

    @Test
    @DisplayName("HU-14 R21: un arrendador sin espacios recibe una página vacía")
    void arrendadorSinEspacios() throws Exception {
        otroArrendador(CORREO_OTRO_ARRENDADOR);
        confirmada(usuarioId, espacioId, fecha, 8);
        var sesion = token(CORREO_OTRO_ARRENDADOR);

        esPrimeraPaginaVacia(deMisEspacios(sesion, "?grupo=proximas"));
        esPrimeraPaginaVacia(deMisEspacios(sesion, "?grupo=pasadas&pagina=1"));
    }

    // ── Corte, orden y páginas (R22–R32) ─────────────────────────────────

    @Test
    @DisplayName("HU-14 R22: a las 10:00 de Lima, una reserva que termina a las 10:00 es pasada y una que termina a "
            + "las 11:00 es próxima")
    void limiteDeLasDiez() throws Exception {
        var terminada = confirmada(usuarioId, espacioId, hoy, 8, 9);
        var enCurso = confirmada(usuarioId, espacioId, hoy, 10);
        var usuario = token(CORREO_USUARIO);
        var arrendador = token(CORREO_ARRENDADOR);

        assertThat(ids(misReservas(usuario, "?grupo=proximas"))).containsExactly(enCurso);
        assertThat(ids(misReservas(usuario, "?grupo=pasadas"))).containsExactly(terminada);
        assertThat(ids(deMisEspacios(arrendador, "?grupo=proximas"))).containsExactly(enCurso);
        assertThat(ids(deMisEspacios(arrendador, "?grupo=pasadas"))).containsExactly(terminada);

        // Una hora sin actividad vence la sesión (30 min): se inicia otra.
        reloj.avanzar(Duration.ofHours(1));
        var despues = token(CORREO_USUARIO);
        assertThat(ids(misReservas(despues, "?grupo=proximas"))).isEmpty();
        assertThat(ids(misReservas(despues, "?grupo=pasadas"))).containsExactly(enCurso, terminada);
    }

    @Test
    @DisplayName("HU-14 R24, R25: próximas ascendentes y pasadas descendentes por fecha, hora de inicio e id")
    void orden() throws Exception {
        var manana18 = confirmada(usuarioId, espacioId, hoy.plusDays(1), 18);
        var manana08 = confirmada(usuarioId, espacioId, hoy.plusDays(1), 8);
        var en3dias = confirmada(usuarioId, espacioId, hoy.plusDays(3), 7);
        // Mismo día y hora en otro espacio del arrendador: desempata el id.
        var manana08Bis = confirmada(usuarioId, espacioDe(arrendadorId, "Cancha Gemela", "PUBLICADO"),
                hoy.plusDays(1), 8);
        var ayer20 = confirmada(usuarioId, espacioId, hoy.minusDays(1), 20);
        var semanaPasada = confirmada(usuarioId, espacioId, hoy.minusDays(7), 22);
        var ayer06 = confirmada(usuarioId, espacioId, hoy.minusDays(1), 6);
        var sesion = token(CORREO_USUARIO);

        assertThat(ids(misReservas(sesion, "?grupo=proximas")))
                .containsExactly(manana08, manana08Bis, manana18, en3dias);
        assertThat(ids(misReservas(sesion, "?grupo=pasadas"))).containsExactly(ayer20, ayer06, semanaPasada);
        assertThat(ids(deMisEspacios(token(CORREO_ARRENDADOR), "?grupo=pasadas")))
                .containsExactly(ayer20, ayer06, semanaPasada);
    }

    @Test
    @DisplayName("HU-14 R26, R27, R32: 11 próximas: página 1 con 10 y hayMas, página 2 con 1, página 5 vacía")
    void paginas() throws Exception {
        var ids = new ArrayList<Long>();
        for (var dia = 1; dia <= 11; dia++) {
            ids.add(confirmada(usuarioId, espacioId, hoy.plusDays(dia), 8));
        }
        var usuario = token(CORREO_USUARIO);
        var arrendador = token(CORREO_ARRENDADOR);

        for (var ruta : List.of(RESERVAS, DE_MIS_ESPACIOS)) {
            var sesion = ruta.equals(RESERVAS) ? usuario : arrendador;
            var primera = mvc.perform(con(sesion, get(ruta + "?grupo=proximas&pagina=1")))
                    .andExpect(jsonPath("$.pagina").value(1))
                    .andExpect(jsonPath("$.hayMas").value(true));
            assertThat(ids(primera)).containsExactlyElementsOf(ids.subList(0, 10));
            var segunda = mvc.perform(con(sesion, get(ruta + "?grupo=proximas&pagina=2")))
                    .andExpect(jsonPath("$.pagina").value(2))
                    .andExpect(jsonPath("$.hayMas").value(false));
            assertThat(ids(segunda)).containsExactly(ids.get(10));
            mvc.perform(con(sesion, get(ruta + "?grupo=proximas&pagina=5"))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.pagina").value(5))
                    .andExpect(jsonPath("$.reservas", hasSize(0)))
                    .andExpect(jsonPath("$.hayMas").value(false));
            mvc.perform(con(sesion, get(ruta + "?grupo=pasadas&pagina=2147483647"))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.reservas", hasSize(0)))
                    .andExpect(jsonPath("$.hayMas").value(false));
        }
    }

    @Test
    @DisplayName("HU-14 R28, R32: sin pagina responde la página 1; sin reservas, vacía y hayMas = false")
    void sinPagina() throws Exception {
        esPrimeraPaginaVacia(misReservas(token(CORREO_USUARIO), "?grupo=proximas"));
        esPrimeraPaginaVacia(deMisEspacios(token(CORREO_ARRENDADOR), "?grupo=pasadas"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "1.5", "2147483648", ""})
    @DisplayName("HU-14 R29: una página no válida responde 400 en el campo pagina, en las dos rutas")
    void paginaInvalida(String pagina) throws Exception {
        esErrorDeCampo(misReservas(token(CORREO_USUARIO), "?grupo=proximas&pagina=" + pagina), "pagina", M_PAGINA);
        esErrorDeCampo(deMisEspacios(token(CORREO_ARRENDADOR), "?grupo=pasadas&pagina=" + pagina), "pagina",
                M_PAGINA);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "?grupo=PROXIMAS", "?grupo=todas", "?grupo=", "?grupo=pasadas%20"})
    @DisplayName("HU-14 R30: sin grupo o con un grupo distinto de proximas y pasadas responde 400 en el campo grupo")
    void grupoInvalido(String consulta) throws Exception {
        esErrorDeCampo(misReservas(token(CORREO_USUARIO), consulta), "grupo", M_GRUPO);
        esErrorDeCampo(deMisEspacios(token(CORREO_ARRENDADOR), consulta), "grupo", M_GRUPO);
    }

    @Test
    @DisplayName("HU-14 R31: grupo y página inválidos a la vez responden un solo 400 con los dos errores")
    void grupoYPaginaInvalidos() throws Exception {
        for (var ruta : List.of(RESERVAS, DE_MIS_ESPACIOS)) {
            var sesion = token(ruta.equals(RESERVAS) ? CORREO_USUARIO : CORREO_ARRENDADOR);
            mvc.perform(con(sesion, get(ruta + "?grupo=todas&pagina=0"))).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje").value(MENSAJE_VALIDACION))
                    .andExpect(jsonPath("$.errores", hasSize(2)))
                    .andExpect(jsonPath("$.errores[0].campo").value("grupo"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value(M_GRUPO))
                    .andExpect(jsonPath("$.errores[1].campo").value("pagina"))
                    .andExpect(jsonPath("$.errores[1].mensaje").value(M_PAGINA));
        }
    }

    // ── Solo lectura (R38) ───────────────────────────────────────────────

    @Test
    @DisplayName("HU-14 R38: las dos rutas no escriben en reserva, reserva_bloque ni auditoria")
    void soloLectura() throws Exception {
        sembrar(usuarioId, espacioId, "PENDIENTE_PAGO", haceMinutos(1), fecha, true, 14);
        sembrar(usuarioId, espacioId, "PENDIENTE_PAGO", dentroDe(5), fecha, true, 16);
        confirmada(usuarioId, espacioId, fecha, 8);
        sembrar(usuarioId, espacioId, "EXPIRADA", haceMinutos(60), hoy.minusDays(1), false, 9);
        var usuario = token(CORREO_USUARIO);
        var arrendador = token(CORREO_ARRENDADOR);
        var reservasAntes = jdbc.queryForList("SELECT * FROM reserva ORDER BY id");
        var bloquesAntes = jdbc.queryForList("SELECT * FROM reserva_bloque ORDER BY id");
        var auditoriaAntes = filas("auditoria");

        for (var grupo : List.of("proximas", "pasadas")) {
            misReservas(usuario, "?grupo=" + grupo).andExpect(status().isOk());
            deMisEspacios(arrendador, "?grupo=" + grupo).andExpect(status().isOk());
        }

        assertThat(jdbc.queryForList("SELECT * FROM reserva ORDER BY id")).isEqualTo(reservasAntes);
        assertThat(jdbc.queryForList("SELECT * FROM reserva_bloque ORDER BY id")).isEqualTo(bloquesAntes);
        assertThat(filas("auditoria")).isEqualTo(auditoriaAntes);
    }
}
