package pe.edu.ulima.espacioya.reservas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;
import pe.edu.ulima.espacioya.reservas.service.ExpiracionReservas;

/** POST y GET de reservas, la expiración y las restricciones de la base contra PostgreSQL real (HU-12). */
class ReservaControllerIT extends ReservasControllerSoporte {

    private static final String MENSAJE_VALIDACION = "Revisa los datos del formulario.";
    private static final String SESION_INVALIDA = "Tu sesión no es válida o expiró. Inicia sesión de nuevo.";
    private static final String ACCESO_DENEGADO = "No tienes permiso para realizar esta acción.";
    private static final String NO_ENCONTRADO = "No encontramos lo que buscas o el enlace expiró.";
    private static final String M_ESPACIO = "Elige un espacio válido.";
    private static final String M_FECHA = "Elige una fecha válida.";
    private static final String M_FUERA_DE_VENTANA = "Elige una fecha entre hoy y las próximas 8 semanas.";
    private static final String M_HORAS = "Elige uno o más bloques consecutivos.";

    @Autowired
    private ExpiracionReservas expiracion;

    private void esErrorDeCampo(ResultActions respuesta, String campo, String mensaje) throws Exception {
        respuesta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_VALIDACION))
                .andExpect(jsonPath("$.errores", hasSize(1)))
                .andExpect(jsonPath("$.errores[0].campo").value(campo))
                .andExpect(jsonPath("$.errores[0].mensaje").value(mensaje));
    }

    private void esErrorSinCampo(ResultActions respuesta, String mensaje) throws Exception {
        respuesta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(mensaje))
                .andExpect(jsonPath("$.errores").isEmpty());
    }

    private void esNoEncontrado(ResultActions respuesta) throws Exception {
        respuesta.andExpect(status().isNotFound()).andExpect(jsonPath("$.mensaje").value(NO_ENCONTRADO));
    }

    private void sinFilasDeReserva() {
        assertThat(filas("reserva")).isZero();
        assertThat(filas("reserva_bloque")).isZero();
    }

    private long vencidaDe(long usuario, int... horas) {
        return reservaEnBd(usuario, espacioId, "PENDIENTE_PAGO", haceMinutos(1), true, horas);
    }

    // ── Crear (R1–R5) ────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-12 R1: 201 con un ReservaDTO PENDIENTE_PAGO, con exactamente sus claves y las horas en desorden")
    void creaLaReservaConSuDto() throws Exception {
        var respuesta = reservar(token(CORREO_USUARIO), espacioId, fecha, List.of(16, 14, 15))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.espacioId").value(espacioId))
                .andExpect(jsonPath("$.espacioNombre").value("Cancha Los Olivos"))
                .andExpect(jsonPath("$.estado").value("PENDIENTE_PAGO"))
                .andExpect(jsonPath("$.fecha").value(fecha.toString()))
                .andExpect(jsonPath("$.horaInicio").value(14))
                .andExpect(jsonPath("$.horaFin").value(17))
                .andExpect(jsonPath("$.precioHora").value(80.50))
                .andExpect(jsonPath("$.montoTotal").value(241.50))
                .andExpect(jsonPath("$.segundosRestantes").value(900));

        assertThat(mapa(texto(respuesta)).keySet()).containsExactlyInAnyOrder("id", "espacioId", "espacioNombre",
                "estado", "fecha", "horaInicio", "horaFin", "precioHora", "montoTotal", "expiraEn",
                "segundosRestantes");
    }

    @Test
    @DisplayName("HU-12 R2: guarda la reserva a nombre del usuario de la sesión, con creada_en = ahora y 15 min")
    void guardaLaFilaDeReserva() throws Exception {
        var ahora = reloj.instant();

        var id = idDe(reservar(token(CORREO_USUARIO), espacioId, fecha, List.of(14)).andExpect(status().isCreated()));

        var fila = reservaGuardada(id);
        assertThat(fila.get("estado")).isEqualTo("PENDIENTE_PAGO");
        assertThat(fila.get("usuario_id")).isEqualTo(usuarioId);
        assertThat(fila.get("espacio_id")).isEqualTo(espacioId);
        assertThat(instante(fila.get("creada_en"))).isEqualTo(ahora);
        assertThat(instante(fila.get("expira_en"))).isEqualTo(ahora.plus(Duration.ofMinutes(15)));
    }

    @Test
    @DisplayName("HU-12 R3: copia el precio del espacio y calcula el monto: 3 bloques a S/ 80.50 son S/ 241.50")
    void guardaPrecioYMonto() throws Exception {
        var id = idDe(reservar(token(CORREO_USUARIO), espacioId, fecha, List.of(14, 15, 16))
                .andExpect(status().isCreated()));

        var fila = reservaGuardada(id);
        assertThat(fila.get("precio_hora_aplicado").toString()).isEqualTo("80.50");
        assertThat(fila.get("monto_total").toString()).isEqualTo("241.50");
    }

    @Test
    @DisplayName("HU-12 R3: el precio aplicado es el del momento: editarlo después no cambia la reserva")
    void elPrecioQuedaCopiado() throws Exception {
        var id = idDe(reservar(token(CORREO_USUARIO), espacioId, fecha, List.of(14)).andExpect(status().isCreated()));

        jdbc.update("UPDATE espacio SET precio_hora = 999.00 WHERE id = ?", espacioId);

        assertThat(reservaGuardada(id).get("precio_hora_aplicado").toString()).isEqualTo("80.50");
        consultar(token(CORREO_USUARIO), id).andExpect(jsonPath("$.precioHora").value(80.50));
    }

    @Test
    @DisplayName("HU-12 R4: guarda una fila activa de reserva_bloque por cada hora pedida")
    void guardaLosBloques() throws Exception {
        var id = idDe(reservar(token(CORREO_USUARIO), espacioId, fecha, List.of(15, 14))
                .andExpect(status().isCreated()));

        var bloques = bloquesGuardados(id);
        assertThat(bloques).extracting(fila -> ((Number) fila.get("hora_inicio")).intValue()).containsExactly(14, 15);
        assertThat(bloques).allSatisfy(fila -> {
            assertThat(fila.get("espacio_id")).isEqualTo(espacioId);
            assertThat(((Date) fila.get("fecha")).toLocalDate()).isEqualTo(fecha);
            assertThat(fila.get("activo")).isEqualTo(true);
        });
    }

    @Test
    @DisplayName("HU-12 R5: ignora usuarioId, id, estado, montoTotal, precioHora y expiraEn del cuerpo")
    void ignoraLoQueDecideElServidor() throws Exception {
        var otro = crearUsuario(OTRO_CORREO);
        var cuerpo = "{\"espacioId\":" + espacioId + ",\"fecha\":\"" + fecha + "\",\"horas\":[14],"
                + "\"usuarioId\":" + otro + ",\"id\":123456,\"estado\":\"CONFIRMADA\",\"montoTotal\":0.01,"
                + "\"precioHora\":0.01,\"expiraEn\":\"2030-01-01T00:00:00Z\"}";

        var respuesta = enviar(token(CORREO_USUARIO), cuerpo).andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE_PAGO"))
                .andExpect(jsonPath("$.montoTotal").value(80.50));

        var fila = reservaGuardada(idDe(respuesta));
        assertThat(fila.get("id")).isNotEqualTo(123456L);
        assertThat(fila.get("usuario_id")).isEqualTo(usuarioId);
        assertThat(fila.get("estado")).isEqualTo("PENDIENTE_PAGO");
        assertThat(fila.get("monto_total").toString()).isEqualTo("80.50");
        assertThat(instante(fila.get("expira_en"))).isEqualTo(dentroDe(15));
    }

    // ── Retención (R6, R8) ───────────────────────────────────────────────

    @Test
    @DisplayName("HU-12 R6, R8: las horas de una reserva recién creada salen de la disponibilidad pública")
    void laRetencionSaleDeLaRutaPublica() throws Exception {
        reservar(token(CORREO_USUARIO), espacioId, fecha, List.of(14, 15)).andExpect(status().isCreated());

        assertThat(bloquesLibres(espacioId, fecha)).doesNotContain(14, 15).contains(13, 16);
    }

    @Test
    @DisplayName("HU-12 R8: las horas de una CONFIRMADA tampoco se ofrecen")
    void unaConfirmadaNoSeOfrece() throws Exception {
        reservaEnBd(usuarioId, espacioId, "CONFIRMADA", haceMinutos(60), true, 14);

        assertThat(bloquesLibres(espacioId, fecha)).doesNotContain(14);
    }

    // ── Expiración (R9–R12, R17) ─────────────────────────────────────────

    @Test
    @DisplayName("HU-12 R9, R10, R11: a los 14:59 sigue vigente; a los 15:00 queda EXPIRADA y el bloque vuelve a estar "
            + "libre")
    void expiraALosQuinceMinutos() throws Exception {
        var id = idDe(reservar(token(CORREO_USUARIO), espacioId, fecha, List.of(14, 15))
                .andExpect(status().isCreated()));

        reloj.avanzar(Duration.ofMinutes(15).minusSeconds(1));
        assertThat(expiracion.expirarVencidas()).isZero();
        assertThat(estadoGuardado(id)).isEqualTo("PENDIENTE_PAGO");
        assertThat(bloquesLibres(espacioId, fecha)).doesNotContain(14, 15);

        reloj.avanzar(Duration.ofSeconds(1));
        assertThat(expiracion.expirarVencidas()).isEqualTo(1);

        assertThat(estadoGuardado(id)).isEqualTo("EXPIRADA");
        assertThat(bloquesGuardados(id)).hasSize(2).allSatisfy(fila -> assertThat(fila.get("activo")).isEqualTo(false));
        assertThat(bloquesLibres(espacioId, fecha)).contains(14, 15);
    }

    @Test
    @DisplayName("HU-12 R11: el proceso no toca una reserva vigente ni una CONFIRMADA")
    void noTocaVigentesNiConfirmadas() {
        var vigente = reservaEnBd(usuarioId, espacioId, "PENDIENTE_PAGO", dentroDe(5), true, 8);
        var confirmada = reservaEnBd(usuarioId, espacioId, "CONFIRMADA", haceMinutos(60), true, 9);

        assertThat(expiracion.expirarVencidas()).isZero();

        assertThat(estadoGuardado(vigente)).isEqualTo("PENDIENTE_PAGO");
        assertThat(estadoGuardado(confirmada)).isEqualTo("CONFIRMADA");
        assertThat(bloquesActivos(espacioId)).isEqualTo(2);
    }

    @Test
    @DisplayName("HU-12 R12: una vencida con activo = true ya no ocupa en la ruta pública aunque nadie la expirara")
    void vencidaSinProcesarNoOcupa() throws Exception {
        var vencida = vencidaDe(usuarioId, 14);

        assertThat(bloquesLibres(espacioId, fecha)).contains(14);
        assertThat(estadoGuardado(vencida)).isEqualTo("PENDIENTE_PAGO");
        assertThat(bloquesActivos(espacioId)).isEqualTo(1);
    }

    @Test
    @DisplayName("HU-12 R17, R43: trampa del flush: otro usuario reserva un bloque vencido sin procesar, 201, la vieja "
            + "queda EXPIRADA y hay una sola fila activa")
    void reservaUnBloqueVencidoSinProcesar() throws Exception {
        crearUsuario(OTRO_CORREO);
        var vieja = reservaEnBd(usuarioId, espacioId, "PENDIENTE_PAGO", haceMinutos(1), true, 14, 15);

        var nueva = idDe(reservar(token(OTRO_CORREO), espacioId, fecha, List.of(14)).andExpect(status().isCreated()));

        assertThat(estadoGuardado(vieja)).isEqualTo("EXPIRADA");
        assertThat(bloquesGuardados(vieja)).allSatisfy(fila -> assertThat(fila.get("activo")).isEqualTo(false));
        assertThat(bloquesActivos(espacioId)).isEqualTo(1);
        assertThat(bloquesGuardados(nueva)).singleElement()
                .satisfies(fila -> assertThat(fila.get("activo")).isEqualTo(true));
        var eventos = auditorias("RESERVA_EXPIRADA");
        assertThat(eventos).singleElement().satisfies(evento -> {
            assertThat(evento.get("usuario_id")).isNull();
            assertThat(evento.get("ip")).isNull();
            assertThat(evento.get("entidad")).isEqualTo("reserva");
            assertThat(evento.get("entidad_id")).isEqualTo(vieja);
            assertThat(evento.get("detalle")).isEqualTo("origen=PROCESO;espacioId=" + espacioId);
        });
    }

    @Test
    @DisplayName("HU-12 R43: el proceso audita RESERVA_EXPIRADA sin usuario ni IP, con la fecha y el detalle exactos")
    void elProcesoAuditaLaExpiracion() {
        var vencida = vencidaDe(usuarioId, 14);
        var ahora = reloj.instant();

        expiracion.expirarVencidas();

        assertThat(auditorias("RESERVA_EXPIRADA")).singleElement().satisfies(evento -> {
            assertThat(evento.get("usuario_id")).isNull();
            assertThat(evento.get("ip")).isNull();
            assertThat(evento.get("entidad")).isEqualTo("reserva");
            assertThat(evento.get("entidad_id")).isEqualTo(vencida);
            assertThat(instante(evento.get("fecha"))).isEqualTo(ahora);
            assertThat(evento.get("detalle")).isEqualTo("origen=PROCESO;espacioId=" + espacioId);
        });
    }

    // ── Conflicto (R18) y restricciones (R20, R21, R45) ──────────────────

    @Test
    @DisplayName("HU-12 R18: un bloque retenido por otra reserva es 400 sin campo, sin filas nuevas")
    void bloqueRetenido() throws Exception {
        reservaEnBd(crearUsuario(OTRO_CORREO), espacioId, "PENDIENTE_PAGO", dentroDe(10), true, 15);

        esErrorSinCampo(reservar(token(CORREO_USUARIO), espacioId, fecha, List.of(14, 15)), HORARIO_NO_DISPONIBLE);

        assertThat(filas("reserva")).isEqualTo(1);
        assertThat(filas("reserva_bloque")).isEqualTo(1);
    }

    @Test
    @DisplayName("HU-12 R18: un bloque de una CONFIRMADA es 400 sin campo")
    void bloqueConfirmado() throws Exception {
        reservaEnBd(crearUsuario(OTRO_CORREO), espacioId, "CONFIRMADA", haceMinutos(60), true, 14);

        esErrorSinCampo(reservar(token(CORREO_USUARIO), espacioId, fecha, List.of(14)), HORARIO_NO_DISPONIBLE);

        assertThat(filas("reserva")).isEqualTo(1);
    }

    @Test
    @DisplayName("HU-12 R18: en una fecha bloqueada por el dueño es 400 sin campo y sin filas")
    void fechaBloqueada() throws Exception {
        jdbc.update("INSERT INTO bloqueo_fecha (espacio_id, fecha) VALUES (?, ?)", espacioId, Date.valueOf(fecha));

        esErrorSinCampo(reservar(token(CORREO_USUARIO), espacioId, fecha, List.of(14)), HORARIO_NO_DISPONIBLE);

        sinFilasDeReserva();
    }

    @Test
    @DisplayName("HU-12 R18: una hora ya empezada de hoy es 400 sin campo y sin filas")
    void horaYaEmpezada() throws Exception {
        var hoy = ZonaHoraria.hoy(reloj);
        var hora = ZonaHoraria.ahora(reloj).getHour();
        franja(espacioId, hoy, hora, hora + 1);

        esErrorSinCampo(reservar(token(CORREO_USUARIO), espacioId, hoy, List.of(hora)), HORARIO_NO_DISPONIBLE);

        sinFilasDeReserva();
    }

    @Test
    @DisplayName("HU-12 R18: una hora fuera del horario es 400 sin campo y sin filas")
    void horaFueraDeHorario() throws Exception {
        esErrorSinCampo(reservar(token(CORREO_USUARIO), espacioId, fecha, List.of(22)), HORARIO_NO_DISPONIBLE);

        sinFilasDeReserva();
    }

    @Test
    @DisplayName("HU-12 R20: la base impide dos filas activas del mismo espacio, fecha y hora")
    void indiceImpideDosActivas() {
        var reserva = filaReserva(usuarioId, espacioId, "PENDIENTE_PAGO", dentroDe(10));
        var otra = filaReserva(usuarioId, espacioId, "PENDIENTE_PAGO", dentroDe(10));
        filaBloque(reserva, espacioId, fecha, 14, true);

        assertThatThrownBy(() -> filaBloque(otra, espacioId, fecha, 14, true))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_reserva_bloque_activo");
    }

    @Test
    @DisplayName("HU-12 R21: la base acepta una fila activa junto a inactivas, y dos inactivas juntas")
    void indiceIgnoraLasInactivas() {
        var reserva = filaReserva(usuarioId, espacioId, "EXPIRADA", haceMinutos(60));
        var otra = filaReserva(usuarioId, espacioId, "EXPIRADA", haceMinutos(60));
        var activa = filaReserva(usuarioId, espacioId, "PENDIENTE_PAGO", dentroDe(10));

        filaBloque(reserva, espacioId, fecha, 14, false);
        filaBloque(otra, espacioId, fecha, 14, false);
        filaBloque(activa, espacioId, fecha, 14, true);

        assertThat(filas("reserva_bloque")).isEqualTo(3);
        assertThat(bloquesActivos(espacioId)).isEqualTo(1);
    }

    @Test
    @DisplayName("HU-12 R22, R44: con PostgreSQL real, si el INSERT viola el índice responde 400 horario no "
            + "disponible, no 500, y no queda nada de la reserva")
    void violacionRealDelIndice() throws Exception {
        // Una CANCELADA con su bloque aún activo no ocupa para el puerto ni la libera liberarVencidas: la comprobación
        // bajo el bloqueo la deja pasar y solo el índice la detiene.
        var cancelada = reservaEnBd(crearUsuario(OTRO_CORREO), espacioId, "CANCELADA", haceMinutos(60), true, 14);
        assertThat(bloquesLibres(espacioId, fecha)).contains(13, 14);

        esErrorSinCampo(reservar(token(CORREO_USUARIO), espacioId, fecha, List.of(13, 14)), HORARIO_NO_DISPONIBLE);

        assertThat(filas("reserva")).isEqualTo(1);
        assertThat(filas("reserva_bloque")).isEqualTo(1);
        assertThat(estadoGuardado(cancelada)).isEqualTo("CANCELADA");
        assertThat(auditorias("RESERVA_CREADA")).isEmpty();
    }

    @Test
    @DisplayName("HU-12 R45: la base rechaza un estado desconocido")
    void checkDeEstado() {
        assertThatThrownBy(() -> filaReserva(usuarioId, espacioId, "OTRO", dentroDe(10)))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_reserva_estado");
    }

    @ParameterizedTest
    @ValueSource(strings = {"precio_hora_aplicado", "monto_total"})
    @DisplayName("HU-12 R45: la base rechaza montos en cero o negativos")
    void checkDeMontos(String columna) {
        var id = filaReserva(usuarioId, espacioId, "PENDIENTE_PAGO", dentroDe(10));

        for (var valor : List.of("0", "-1.00")) {
            assertThatThrownBy(() -> jdbc.update("UPDATE reserva SET " + columna + " = " + valor + " WHERE id = ?", id))
                    .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_reserva_montos");
        }
    }

    @Test
    @DisplayName("HU-12 R45: la base rechaza expira_en no posterior a creada_en")
    void checkDeVencimiento() {
        var ahora = Timestamp.from(reloj.instant());

        assertThatThrownBy(() -> jdbc.update("INSERT INTO reserva (usuario_id, espacio_id, estado, "
                        + "precio_hora_aplicado, monto_total, creada_en, expira_en) "
                        + "VALUES (?, ?, 'PENDIENTE_PAGO', 80.50, 80.50, ?, ?)", usuarioId, espacioId, ahora, ahora))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_reserva_expira");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 24})
    @DisplayName("HU-12 R45: la base rechaza una hora_inicio fuera de 0 a 23")
    void checkDeHora(int hora) {
        var reserva = filaReserva(usuarioId, espacioId, "PENDIENTE_PAGO", dentroDe(10));

        assertThatThrownBy(() -> filaBloque(reserva, espacioId, fecha, hora, true))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_reserva_bloque_hora");
    }

    // ── Cupo (R26, R27) ──────────────────────────────────────────────────

    @Test
    @DisplayName("HU-12 R26: con 3 vigentes responde 400 cupo sin filas nuevas")
    void cupoSuperado() throws Exception {
        reservaEnBd(usuarioId, espacioId, "PENDIENTE_PAGO", dentroDe(10), true, 8);
        reservaEnBd(usuarioId, espacioId, "PENDIENTE_PAGO", dentroDe(10), true, 9);
        reservaEnBd(usuarioId, espacioId, "PENDIENTE_PAGO", dentroDe(10), true, 10);

        esErrorSinCampo(reservar(token(CORREO_USUARIO), espacioId, fecha, List.of(14)), CUPO);

        assertThat(filas("reserva")).isEqualTo(3);
        assertThat(filas("reserva_bloque")).isEqualTo(3);
    }

    @Test
    @DisplayName("HU-12 R27: no cuentan una vencida, una CONFIRMADA, una EXPIRADA ni las pendientes de otro usuario")
    void soloCuentanLasVigentesPropias() throws Exception {
        var otro = crearUsuario(OTRO_CORREO);
        reservaEnBd(usuarioId, espacioId, "PENDIENTE_PAGO", dentroDe(10), true, 8);
        reservaEnBd(usuarioId, espacioId, "PENDIENTE_PAGO", dentroDe(10), true, 9);
        reservaEnBd(usuarioId, espacioId, "PENDIENTE_PAGO", haceMinutos(1), true, 10);
        reservaEnBd(usuarioId, espacioId, "CONFIRMADA", haceMinutos(60), true, 11);
        reservaEnBd(usuarioId, espacioId, "EXPIRADA", haceMinutos(60), false, 12);
        reservaEnBd(otro, espacioId, "PENDIENTE_PAGO", dentroDe(10), true, 13);

        reservar(token(CORREO_USUARIO), espacioId, fecha, List.of(14)).andExpect(status().isCreated());
    }

    // ── Validación (R29–R34) ─────────────────────────────────────────────

    static Stream<String> espaciosInvalidos() {
        return Stream.of("\"fecha\":\"%s\",\"horas\":[14]", "\"espacioId\":0,\"fecha\":\"%s\",\"horas\":[14]",
                "\"espacioId\":5.5,\"fecha\":\"%s\",\"horas\":[14]",
                "\"espacioId\":\"5\",\"fecha\":\"%s\",\"horas\":[14]");
    }

    @ParameterizedTest
    @MethodSource("espaciosInvalidos")
    @DisplayName("HU-12 R29: espacioId ausente, 0, decimal o texto es 400 en ese campo")
    void espacioInvalido(String plantilla) throws Exception {
        esErrorDeCampo(enviar(token(CORREO_USUARIO), "{" + plantilla.formatted(fecha) + "}"), "espacioId", M_ESPACIO);

        sinFilasDeReserva();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ",\"fecha\":\"2026-02-30\"", ",\"fecha\":\"07/11/2026\"", ",\"fecha\":20261107"})
    @DisplayName("HU-12 R30: fecha ausente, inexistente, mal escrita o de otro tipo es 400 en ese campo")
    void fechaInvalida(String campo) throws Exception {
        var cuerpo = "{\"espacioId\":" + espacioId + ",\"horas\":[14]" + campo + "}";

        esErrorDeCampo(enviar(token(CORREO_USUARIO), cuerpo), "fecha", M_FECHA);

        sinFilasDeReserva();
    }

    @Test
    @DisplayName("HU-12 R31: ayer y hoy + 56 son 400 en fecha con el mensaje de la ventana")
    void fechaFueraDeVentana() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var hoy = ZonaHoraria.hoy(reloj);

        esErrorDeCampo(reservar(sesion, espacioId, hoy.minusDays(1), List.of(14)), "fecha", M_FUERA_DE_VENTANA);
        esErrorDeCampo(reservar(sesion, espacioId, hoy.plusDays(56), List.of(14)), "fecha", M_FUERA_DE_VENTANA);

        sinFilasDeReserva();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ",\"horas\":[]", ",\"horas\":[14,16]", ",\"horas\":[14,14]", ",\"horas\":[24]",
        ",\"horas\":[14.5]", ",\"horas\":14", ",\"horas\":[\"14\"]", ",\"horas\":[null]"})
    @DisplayName("HU-12 R32: horas ausente, vacía, con huecos, repetida, fuera de rango o de otro tipo es 400 en ese "
            + "campo")
    void horasInvalidas(String campo) throws Exception {
        var cuerpo = "{\"espacioId\":" + espacioId + ",\"fecha\":\"" + fecha + "\"" + campo + "}";

        esErrorDeCampo(enviar(token(CORREO_USUARIO), cuerpo), "horas", M_HORAS);

        sinFilasDeReserva();
    }

    @Test
    @DisplayName("HU-12 R33: un espacio inexistente es 404")
    void espacioInexistente() throws Exception {
        esNoEncontrado(reservar(token(CORREO_USUARIO), 999_999L, fecha, List.of(14)));

        sinFilasDeReserva();
    }

    @ParameterizedTest
    @ValueSource(strings = {"BORRADOR", "PAUSADO", "BAJA", "OCULTO"})
    @DisplayName("HU-12 R33: un espacio no PUBLICADO es 404")
    void espacioNoPublicado(String estado) throws Exception {
        jdbc.update("UPDATE espacio SET estado = ? WHERE id = ?", estado, espacioId);

        esNoEncontrado(reservar(token(CORREO_USUARIO), espacioId, fecha, List.of(14)));

        sinFilasDeReserva();
    }

    @Test
    @DisplayName("HU-12 R33: un espacio de un arrendador suspendido es 404")
    void arrendadorSuspendido() throws Exception {
        jdbc.update("UPDATE usuario SET estado = 'SUSPENDIDA' WHERE id = ?", arrendadorId);

        esNoEncontrado(reservar(token(CORREO_USUARIO), espacioId, fecha, List.of(14)));

        sinFilasDeReserva();
    }

    @Test
    @DisplayName("HU-12 R34: la forma del cuerpo va antes que el rol, y el rol antes que el calendario")
    void formaAntesQueRol() throws Exception {
        var arrendador = token(CORREO_ARRENDADOR);

        esErrorDeCampo(reservar(arrendador, 0L, fecha, List.of(14)), "espacioId", M_ESPACIO);
        esErrorDeCampo(enviar(arrendador, "{\"espacioId\":" + espacioId + ",\"fecha\":\"" + fecha + "\"}"), "horas",
                M_HORAS);
        enviar(arrendador, "{\"espacioId\":" + espacioId + ",\"fecha\":\"2026-02-30\",\"horas\":[14,16]}")
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("HU-12 R34: la fecha va antes que la selección, la selección antes que el cupo y el cupo antes que el "
            + "espacio")
    void ordenDeLasComprobaciones() throws Exception {
        var sesion = token(CORREO_USUARIO);
        reservaEnBd(usuarioId, espacioId, "PENDIENTE_PAGO", dentroDe(10), true, 8);
        reservaEnBd(usuarioId, espacioId, "PENDIENTE_PAGO", dentroDe(10), true, 9);
        reservaEnBd(usuarioId, espacioId, "PENDIENTE_PAGO", dentroDe(10), true, 10);

        esErrorDeCampo(enviar(sesion, "{\"espacioId\":999999,\"fecha\":\"2026-02-30\",\"horas\":[14,16]}"), "fecha",
                M_FECHA);
        esErrorDeCampo(reservar(sesion, 999_999L, fecha, List.of(14, 16)), "horas", M_HORAS);
        esErrorSinCampo(reservar(sesion, 999_999L, fecha, List.of(14)), CUPO);
    }

    // ── Sesión y rol (R35, R36) ──────────────────────────────────────────

    @Test
    @DisplayName("HU-12 R35: sin sesión, POST y GET responden 401 sin filas")
    void sinSesion() throws Exception {
        reservar(null, espacioId, fecha, List.of(14)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(SESION_INVALIDA));
        consultar(null, 1).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(SESION_INVALIDA));

        sinFilasDeReserva();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ARRENDADOR", "ADMIN"})
    @DisplayName("HU-12 R36: ARRENDADOR y ADMIN reciben 403 sin filas ni auditoría de creación")
    void rolSinPermiso(String rol) throws Exception {
        String sesion;
        if ("ADMIN".equals(rol)) {
            crearAdmin("admin@espacioya.pe");
            sesion = token("admin@espacioya.pe", IP_ADMIN);
        } else {
            sesion = token(CORREO_ARRENDADOR);
        }

        reservar(sesion, espacioId, fecha, List.of(14)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(ACCESO_DENEGADO));

        sinFilasDeReserva();
        assertThat(auditorias("RESERVA_CREADA")).isEmpty();
    }

    // ── Consulta (R37–R41) ───────────────────────────────────────────────

    @Test
    @DisplayName("HU-12 R37: su dueño recibe 200 con exactamente las claves del DTO y su horario")
    void consultaSuReserva() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var id = idDe(reservar(sesion, espacioId, fecha, List.of(14, 15)).andExpect(status().isCreated()));

        var respuesta = consultar(sesion, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.espacioNombre").value("Cancha Los Olivos"))
                .andExpect(jsonPath("$.estado").value("PENDIENTE_PAGO"))
                .andExpect(jsonPath("$.fecha").value(fecha.toString()))
                .andExpect(jsonPath("$.horaInicio").value(14))
                .andExpect(jsonPath("$.horaFin").value(16))
                .andExpect(jsonPath("$.montoTotal").value(161.00));

        assertThat(mapa(texto(respuesta)).keySet()).containsExactlyInAnyOrder("id", "espacioId", "espacioNombre",
                "estado", "fecha", "horaInicio", "horaFin", "precioHora", "montoTotal", "expiraEn",
                "segundosRestantes");
    }

    @Test
    @DisplayName("HU-12 R37: conserva el horario con los bloques ya liberados")
    void horarioDeUnaExpirada() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var id = idDe(reservar(sesion, espacioId, fecha, List.of(14, 15)).andExpect(status().isCreated()));
        reloj.avanzar(Duration.ofMinutes(15));
        expiracion.expirarVencidas();

        consultar(sesion, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EXPIRADA"))
                .andExpect(jsonPath("$.horaInicio").value(14))
                .andExpect(jsonPath("$.horaFin").value(16));
    }

    @Test
    @DisplayName("HU-12 R37: sigue mostrando el nombre del espacio pausado o de un arrendador suspendido")
    void nombreDeUnEspacioNoVisible() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var id = idDe(reservar(sesion, espacioId, fecha, List.of(14)).andExpect(status().isCreated()));

        jdbc.update("UPDATE espacio SET estado = 'PAUSADO' WHERE id = ?", espacioId);
        consultar(sesion, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.espacioNombre").value("Cancha Los Olivos"));

        jdbc.update("UPDATE espacio SET estado = 'PUBLICADO' WHERE id = ?", espacioId);
        jdbc.update("UPDATE usuario SET estado = 'SUSPENDIDA' WHERE id = ?", arrendadorId);
        consultar(sesion, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.espacioNombre").value("Cancha Los Olivos"));
    }

    @Test
    @DisplayName("HU-12 R38: ajena, inexistente y mal escrita responden el mismo 404")
    void ajenaInexistenteYMalEscrita() throws Exception {
        crearUsuario(OTRO_CORREO);
        var id = idDe(reservar(token(CORREO_USUARIO), espacioId, fecha, List.of(14)).andExpect(status().isCreated()));
        var otro = token(OTRO_CORREO);

        var ajena = texto(consultar(otro, id).andExpect(status().isNotFound()));

        assertThat(texto(consultar(otro, id + 1000).andExpect(status().isNotFound()))).isEqualTo(ajena);
        assertThat(texto(consultar(otro, "abc").andExpect(status().isNotFound()))).isEqualTo(ajena);
        assertThat(mapa(ajena).get("mensaje")).isEqualTo(NO_ENCONTRADO);
    }

    @Test
    @DisplayName("HU-12 R39: una vencida sin procesar se responde EXPIRADA y la fila sigue PENDIENTE_PAGO, sin "
            + "auditoría")
    void vencidaSeLeeExpiradaSinEscribir() throws Exception {
        var vencida = vencidaDe(usuarioId, 14);

        consultar(token(CORREO_USUARIO), vencida).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EXPIRADA"))
                .andExpect(jsonPath("$.segundosRestantes").value(0));

        assertThat(estadoGuardado(vencida)).isEqualTo("PENDIENTE_PAGO");
        assertThat(bloquesActivos(espacioId)).isEqualTo(1);
        assertThat(auditorias("RESERVA_EXPIRADA")).isEmpty();
    }

    @Test
    @DisplayName("HU-12 R40: segundosRestantes cuenta los que faltan y es 0 al vencer")
    void segundosRestantes() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var id = idDe(reservar(sesion, espacioId, fecha, List.of(14)).andExpect(status().isCreated()));

        reloj.avanzar(Duration.ofSeconds(61));
        consultar(sesion, id).andExpect(jsonPath("$.segundosRestantes").value(839));

        reloj.avanzar(Duration.ofSeconds(839));
        consultar(sesion, id).andExpect(jsonPath("$.segundosRestantes").value(0));
    }

    @Test
    @DisplayName("HU-12 R41: la respuesta no lleva usuario_id ni datos del arrendador")
    void noExponeDatosDePersonas() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var creada = reservar(sesion, espacioId, fecha, List.of(14)).andExpect(status().isCreated());
        var consultada = consultar(sesion, idDe(creada)).andExpect(status().isOk());

        for (var cuerpo : List.of(texto(creada), texto(consultada))) {
            assertThat(cuerpo).doesNotContain("usuarioId", "usuario_id", "arrendador", CORREO_ARRENDADOR,
                    CORREO_USUARIO, DOCUMENTO_ARRENDADOR, "Ana Reservada", "Inversiones Ocultas");
        }
    }

    // ── Auditoría (R42, R44) ─────────────────────────────────────────────

    @Test
    @DisplayName("HU-12 R42: registra RESERVA_CREADA con usuario, entidad, IP, fecha y detalle exactos")
    void auditaLaCreacion() throws Exception {
        var ahora = reloj.instant();

        var id = idDe(reservar(token(CORREO_USUARIO), espacioId, fecha, List.of(15, 14))
                .andExpect(status().isCreated()));

        assertThat(auditorias("RESERVA_CREADA")).singleElement().satisfies(evento -> {
            assertThat(evento.get("usuario_id")).isEqualTo(usuarioId);
            assertThat(evento.get("entidad")).isEqualTo("reserva");
            assertThat(evento.get("entidad_id")).isEqualTo(id);
            assertThat(evento.get("ip")).isEqualTo(ip);
            assertThat(instante(evento.get("fecha"))).isEqualTo(ahora);
            assertThat(evento.get("detalle")).isEqualTo("espacioId=" + espacioId + ";fecha=" + fecha
                    + ";horas=14,15;monto=161.00");
        });
    }

    @Test
    @DisplayName("HU-12 R44: ningún rechazo registra RESERVA_CREADA")
    void losRechazosNoSeAuditan() throws Exception {
        var sesion = token(CORREO_USUARIO);
        reservaEnBd(crearUsuario(OTRO_CORREO), espacioId, "PENDIENTE_PAGO", dentroDe(10), true, 14);

        reservar(sesion, espacioId, fecha, List.of(14)).andExpect(status().isBadRequest());
        reservar(sesion, 0L, fecha, List.of(15)).andExpect(status().isBadRequest());
        reservar(sesion, espacioId, fecha.minusDays(30), List.of(15)).andExpect(status().isBadRequest());
        reservar(sesion, espacioId, fecha, List.of(15, 17)).andExpect(status().isBadRequest());
        reservar(sesion, 999_999L, fecha, List.of(15)).andExpect(status().isNotFound());
        reservaEnBd(usuarioId, espacioId, "PENDIENTE_PAGO", dentroDe(10), true, 8);
        reservaEnBd(usuarioId, espacioId, "PENDIENTE_PAGO", dentroDe(10), true, 9);
        reservaEnBd(usuarioId, espacioId, "PENDIENTE_PAGO", dentroDe(10), true, 10);
        reservar(sesion, espacioId, fecha, List.of(15)).andExpect(status().isBadRequest());

        assertThat(auditorias("RESERVA_CREADA")).isEmpty();
    }

    @Test
    @DisplayName("HU-12 R1: se puede reservar de una vez todo el horario de un día")
    void reservaTodoElHorario() throws Exception {
        var horas = IntStream.range(8, 22).boxed().toList();

        reservar(token(CORREO_USUARIO), espacioId, fecha, horas).andExpect(status().isCreated())
                .andExpect(jsonPath("$.horaInicio").value(8))
                .andExpect(jsonPath("$.horaFin").value(22));

        assertThat(bloquesLibres(espacioId, fecha)).isEmpty();
    }

    @Test
    @DisplayName("HU-12 R35: una sesión inválida no crea reservas por una ruta con el contenido correcto")
    void tokenInvalido() throws Exception {
        mvc.perform(con("token-falso", post(RESERVAS)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"espacioId\":" + espacioId + ",\"fecha\":\"" + fecha + "\",\"horas\":[14]}"))
                .andExpect(status().isUnauthorized());

        sinFilasDeReserva();
    }
}
