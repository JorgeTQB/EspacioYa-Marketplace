package pe.edu.ulima.espacioya.reservas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.ErrorCampoDTO;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ValidacionException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.service.CuentaService;
import pe.edu.ulima.espacioya.espacios.service.CatalogoEspaciosService;
import pe.edu.ulima.espacioya.reservas.domain.EstadoReserva;
import pe.edu.ulima.espacioya.reservas.domain.Reserva;
import pe.edu.ulima.espacioya.reservas.domain.ReservaConHorario;
import pe.edu.ulima.espacioya.reservas.dto.ReservaDTO;
import pe.edu.ulima.espacioya.reservas.dto.ReservaDeMiEspacioDTO;
import pe.edu.ulima.espacioya.reservas.repository.ReservaDAO;

/** Miércoles 2026-10-28 a las 10:00 de Lima (15:00 UTC). */
@ExtendWith(MockitoExtension.class)
class ConsultaReservasServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-28T15:00:00Z");
    private static final LocalDate HOY = LocalDate.parse("2026-10-28");
    private static final long USUARIO_ID = 7L;
    private static final long ARRENDADOR_ID = 3L;
    private static final UsuarioAutenticado USUARIO = new UsuarioAutenticado(USUARIO_ID, 31L, "USUARIO");
    private static final UsuarioAutenticado ARRENDADOR = new UsuarioAutenticado(ARRENDADOR_ID, 32L, "ARRENDADOR");
    private static final UsuarioAutenticado ADMIN = new UsuarioAutenticado(1L, 33L, "ADMIN");

    @Mock
    private ReservaDAO dao;
    @Mock
    private CatalogoEspaciosService catalogo;
    @Mock
    private CuentaService cuentas;

    private ConsultaReservasService servicio;

    @BeforeEach
    void preparar() {
        servicio = new ConsultaReservasService(dao, catalogo, cuentas, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private static ReservaConHorario reserva(long id, long usuarioId, long espacioId, EstadoReserva estado,
                                             Instant creadaEn, LocalDate fecha, int horaInicio, int horaFin) {
        var reserva = Reserva.pendiente(usuarioId, espacioId, new BigDecimal("80.50"), horaFin - horaInicio,
                creadaEn);
        ReflectionTestUtils.setField(reserva, "id", id);
        ReflectionTestUtils.setField(reserva, "estado", estado);
        return new ReservaConHorario(reserva, fecha, horaInicio, horaFin);
    }

    private static ReservaConHorario confirmada(long id, long usuarioId, long espacioId, LocalDate fecha, int hora) {
        return reserva(id, usuarioId, espacioId, EstadoReserva.CONFIRMADA, AHORA.minus(Duration.ofDays(2)), fecha,
                hora, hora + 1);
    }

    private static List<ErrorCampoDTO> errores(Throwable error) {
        return ((ValidacionException) error).getErrores();
    }

    // ── Rol (R2, R3) ─────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"ARRENDADOR", "ADMIN"})
    @DisplayName("HU-14 R2: misReservas con un rol distinto de USUARIO da 403 sin validar ni consultar nada")
    void misReservasConOtroRol(String rol) {
        var cuenta = new UsuarioAutenticado(9L, 1L, rol);

        assertThatThrownBy(() -> servicio.misReservas(cuenta, "proximas", "1"))
                .isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> servicio.misReservas(cuenta, "todas", "0"))
                .isInstanceOf(AccesoDenegadoException.class);
        verifyNoInteractions(dao, catalogo, cuentas);
    }

    @ParameterizedTest
    @ValueSource(strings = {"USUARIO", "ADMIN"})
    @DisplayName("HU-14 R3: reservasDeMisEspacios con un rol distinto de ARRENDADOR da 403 sin validar ni consultar")
    void reservasDeMisEspaciosConOtroRol(String rol) {
        var cuenta = new UsuarioAutenticado(9L, 1L, rol);

        assertThatThrownBy(() -> servicio.reservasDeMisEspacios(cuenta, "pasadas", null))
                .isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> servicio.reservasDeMisEspacios(cuenta, null, "abc"))
                .isInstanceOf(AccesoDenegadoException.class);
        verifyNoInteractions(dao, catalogo, cuentas);
    }

    // ── Validación (R28–R31) ─────────────────────────────────────────────

    @Test
    @DisplayName("HU-14 R31: grupo y página inválidos a la vez dan una ValidacionException con dos errores, sin DAO")
    void grupoYPaginaInvalidos() {
        assertThatThrownBy(() -> servicio.misReservas(USUARIO, "todas", "0"))
                .isInstanceOf(ValidacionException.class)
                .satisfies(error -> assertThat(errores(error)).containsExactly(
                        new ErrorCampoDTO("grupo", "Elige próximas o pasadas."),
                        new ErrorCampoDTO("pagina", "La página no es válida.")));
        assertThatThrownBy(() -> servicio.reservasDeMisEspacios(ARRENDADOR, null, "2147483648"))
                .isInstanceOf(ValidacionException.class)
                .satisfies(error -> assertThat(errores(error)).hasSize(2));
        verifyNoInteractions(dao, catalogo, cuentas);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "1.5", "2147483648", "", " 1"})
    @DisplayName("HU-14 R29: una página que no es de 1 a 2 147 483 647 en dígitos da un error en pagina")
    void paginaInvalida(String pagina) {
        assertThatThrownBy(() -> servicio.misReservas(USUARIO, "proximas", pagina))
                .isInstanceOf(ValidacionException.class)
                .satisfies(error -> assertThat(errores(error))
                        .containsExactly(new ErrorCampoDTO("pagina", "La página no es válida.")));
        verifyNoInteractions(dao);
    }

    @Test
    @DisplayName("HU-14 R30: sin grupo da un error en grupo")
    void sinGrupo() {
        assertThatThrownBy(() -> servicio.reservasDeMisEspacios(ARRENDADOR, null, null))
                .isInstanceOf(ValidacionException.class)
                .satisfies(error -> assertThat(errores(error))
                        .containsExactly(new ErrorCampoDTO("grupo", "Elige próximas o pasadas.")));
        verifyNoInteractions(dao, catalogo);
    }

    @Test
    @DisplayName("HU-14 R28: sin página responde la página 1")
    void sinPaginaEsLaUno() {
        when(dao.delUsuario(USUARIO_ID)).thenReturn(List.of());

        assertThat(servicio.misReservas(USUARIO, "proximas", null).pagina()).isEqualTo(1);
    }

    // ── Mis reservas (R4–R6, R9–R11) ─────────────────────────────────────

    @Test
    @DisplayName("HU-14 R4, R6, R9, R11: próximas del usuario de la sesión, con el nombre del espacio y sus segundos")
    void misReservasProximas() {
        var vigente = reserva(1L, USUARIO_ID, 40L, EstadoReserva.PENDIENTE_PAGO, AHORA, HOY.plusDays(7), 14, 16);
        var pasada = confirmada(2L, USUARIO_ID, 41L, HOY.minusDays(1), 8);
        when(dao.delUsuario(USUARIO_ID)).thenReturn(List.of(pasada, vigente));
        when(catalogo.nombres(List.of(40L))).thenReturn(Map.of(40L, "Cancha Los Olivos"));

        var pagina = servicio.misReservas(USUARIO, "proximas", "1");

        assertThat(pagina.pagina()).isEqualTo(1);
        assertThat(pagina.hayMas()).isFalse();
        assertThat(pagina.reservas()).containsExactly(new ReservaDTO(1L, 40L, "Cancha Los Olivos", "PENDIENTE_PAGO",
                HOY.plusDays(7), 14, 16, new BigDecimal("80.50"), new BigDecimal("161.00"),
                AHORA.plus(Duration.ofMinutes(15)), 900));
        verify(dao).delUsuario(USUARIO_ID);
        verifyNoInteractions(cuentas);
    }

    @Test
    @DisplayName("HU-14 R5, R10, R11: una vencida sin procesar va a pasadas como EXPIRADA con 0 segundos")
    void misReservasPasadasConVencida() {
        var vencida = reserva(1L, USUARIO_ID, 40L, EstadoReserva.PENDIENTE_PAGO, AHORA.minus(Duration.ofMinutes(20)),
                HOY.plusDays(7), 14, 15);
        when(dao.delUsuario(USUARIO_ID)).thenReturn(List.of(vencida));
        when(catalogo.nombres(List.of(40L))).thenReturn(Map.of(40L, "Cancha Los Olivos"));

        var pagina = servicio.misReservas(USUARIO, "pasadas", null);

        assertThat(pagina.reservas()).singleElement().satisfies(dto -> {
            assertThat(dto.estado()).isEqualTo("EXPIRADA");
            assertThat(dto.segundosRestantes()).isZero();
        });
        assertThat(vencida.reserva().getEstado()).isEqualTo(EstadoReserva.PENDIENTE_PAGO);
    }

    @Test
    @DisplayName("HU-14 R32: sin reservas en la página responde vacía, hayMas = false y no pide nombres")
    void paginaVaciaNoPideNombres() {
        when(dao.delUsuario(USUARIO_ID)).thenReturn(List.of(confirmada(1L, USUARIO_ID, 40L, HOY.plusDays(1), 8)));

        var pagina = servicio.misReservas(USUARIO, "proximas", "2");

        assertThat(pagina.pagina()).isEqualTo(2);
        assertThat(pagina.reservas()).isEmpty();
        assertThat(pagina.hayMas()).isFalse();
        verifyNoInteractions(catalogo);
    }

    @Test
    @DisplayName("HU-14 R22, R10: la clasificación y el estado del DTO usan un mismo instante por solicitud")
    void unSoloInstante() {
        var vence = AHORA.plus(Duration.ofMinutes(15));
        // Un reloj que avanza 1 hora en cada lectura: si el servicio leyera dos veces, el estado no coincidiría.
        var reloj = new Clock() {
            private Instant siguiente = vence.minusSeconds(1);

            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zona) {
                return this;
            }

            @Override
            public Instant instant() {
                var actual = siguiente;
                siguiente = siguiente.plus(Duration.ofHours(1));
                return actual;
            }
        };
        servicio = new ConsultaReservasService(dao, catalogo, cuentas, reloj);
        var vigente = reserva(1L, USUARIO_ID, 40L, EstadoReserva.PENDIENTE_PAGO, AHORA, HOY.plusDays(7), 14, 15);
        when(dao.delUsuario(USUARIO_ID)).thenReturn(List.of(vigente));
        when(catalogo.nombres(any())).thenReturn(Map.of(40L, "Cancha Los Olivos"));

        var pagina = servicio.misReservas(USUARIO, "proximas", "1");

        assertThat(pagina.reservas()).singleElement().satisfies(dto -> {
            assertThat(dto.estado()).isEqualTo("PENDIENTE_PAGO");
            assertThat(dto.segundosRestantes()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("HU-14 R26, R27: con 11 próximas, la página 1 trae 10 con hayMas y pide nombres solo de esos 10")
    void paginaDeDiez() {
        var reservas = LongStream.rangeClosed(1, 11)
                .mapToObj(id -> confirmada(id, USUARIO_ID, 100 + id, HOY.plusDays(id), 8))
                .toList();
        when(dao.delUsuario(USUARIO_ID)).thenReturn(reservas);
        when(catalogo.nombres(any())).thenReturn(Map.of());

        var pagina = servicio.misReservas(USUARIO, "proximas", "1");

        assertThat(pagina.reservas()).extracting(ReservaDTO::id)
                .containsExactlyElementsOf(LongStream.rangeClosed(1, 10).boxed().toList());
        assertThat(pagina.hayMas()).isTrue();
        verify(catalogo).nombres(LongStream.rangeClosed(101, 110).boxed().toList());
    }

    // ── Reservas de mis espacios (R13–R21) ───────────────────────────────

    @Test
    @DisplayName("HU-14 R21: un arrendador sin espacios recibe una página vacía sin invocar el DAO")
    void arrendadorSinEspacios() {
        when(catalogo.nombresDelArrendador(ARRENDADOR_ID)).thenReturn(Map.of());

        var pagina = servicio.reservasDeMisEspacios(ARRENDADOR, "proximas", "3");

        assertThat(pagina.pagina()).isEqualTo(3);
        assertThat(pagina.reservas()).isEmpty();
        assertThat(pagina.hayMas()).isFalse();
        verifyNoInteractions(dao, cuentas);
    }

    @Test
    @DisplayName("HU-14 R13, R16, R18, R19: confirmadas de sus espacios, con el nombre del usuario o el texto fijo")
    void reservasDeMisEspaciosProximas() {
        when(catalogo.nombresDelArrendador(ARRENDADOR_ID)).thenReturn(Map.of(40L, "Cancha A", 41L, "Cancha B"));
        var conNombre = confirmada(1L, 20L, 40L, HOY.plusDays(1), 18);
        var sinNombre = confirmada(2L, 21L, 41L, HOY.plusDays(2), 9);
        when(dao.confirmadasDeEspacios(Set.of(40L, 41L))).thenReturn(List.of(sinNombre, conNombre));
        when(cuentas.nombres(List.of(20L, 21L))).thenReturn(Map.of(20L, "Carla Ríos"));

        var pagina = servicio.reservasDeMisEspacios(ARRENDADOR, "proximas", null);

        assertThat(pagina.reservas()).containsExactly(
                new ReservaDeMiEspacioDTO(1L, 40L, "Cancha A", "Carla Ríos", "CONFIRMADA", HOY.plusDays(1), 18, 19,
                        new BigDecimal("80.50")),
                new ReservaDeMiEspacioDTO(2L, 41L, "Cancha B", "Usuario de EspacioYa", "CONFIRMADA", HOY.plusDays(2),
                        9, 10, new BigDecimal("80.50")));
        verify(catalogo, never()).nombres(any());
    }

    @Test
    @DisplayName("HU-14 R14, R25: las pasadas del arrendador van de la más reciente a la más antigua")
    void reservasDeMisEspaciosPasadas() {
        when(catalogo.nombresDelArrendador(ARRENDADOR_ID)).thenReturn(Map.of(40L, "Cancha A"));
        when(dao.confirmadasDeEspacios(Set.of(40L))).thenReturn(List.of(confirmada(1L, 20L, 40L, HOY.minusDays(9), 8),
                confirmada(2L, 20L, 40L, HOY.minusDays(1), 8), confirmada(3L, 20L, 40L, HOY.plusDays(1), 8)));
        when(cuentas.nombres(any())).thenReturn(Map.of());

        assertThat(servicio.reservasDeMisEspacios(ARRENDADOR, "pasadas", "1").reservas())
                .extracting(ReservaDeMiEspacioDTO::id).containsExactly(2L, 1L);
    }

    @Test
    @DisplayName("HU-14 R20: cuentas.nombres se pide solo con los usuarios de la página (minimización)")
    void nombresSoloDeLaPagina() {
        when(catalogo.nombresDelArrendador(ARRENDADOR_ID)).thenReturn(Map.of(40L, "Cancha A"));
        var reservas = LongStream.rangeClosed(1, 12)
                .mapToObj(id -> confirmada(id, 200 + id, 40L, HOY.plusDays(id), 8))
                .toList();
        when(dao.confirmadasDeEspacios(Set.of(40L))).thenReturn(reservas);
        var pedidos = ArgumentCaptor.forClass(List.class);
        when(cuentas.nombres(pedidos.capture())).thenReturn(Map.of());

        var pagina = servicio.reservasDeMisEspacios(ARRENDADOR, "proximas", "2");

        assertThat(pagina.reservas()).extracting(ReservaDeMiEspacioDTO::id).containsExactly(11L, 12L);
        assertThat(pagina.hayMas()).isFalse();
        assertThat(pedidos.getAllValues()).containsExactly(List.of(211L, 212L));
    }

    // ── Filtro por espacio (HU-21 R19–R22) ───────────────────────────────

    @Test
    @DisplayName("HU-21 R19: con espacioId de un espacio propio devuelve solo las confirmadas de ese espacio")
    void filtraPorEspacioPropio() {
        when(catalogo.nombresDelArrendador(ARRENDADOR_ID)).thenReturn(Map.of(40L, "Cancha A", 41L, "Cancha B"));
        when(dao.confirmadasDeEspacios(Set.of(41L))).thenReturn(List.of(confirmada(2L, 21L, 41L, HOY.plusDays(2), 9)));
        when(cuentas.nombres(List.of(21L))).thenReturn(Map.of(21L, "Carla Ríos"));

        var pagina = servicio.reservasDeMisEspacios(ARRENDADOR, "proximas", "1", "41");

        assertThat(pagina.reservas()).containsExactly(new ReservaDeMiEspacioDTO(2L, 41L, "Cancha B", "Carla Ríos",
                "CONFIRMADA", HOY.plusDays(2), 9, 10, new BigDecimal("80.50")));
        assertThat(pagina.pagina()).isEqualTo(1);
        assertThat(pagina.hayMas()).isFalse();
        verify(dao, never()).confirmadasDeEspacios(Set.of(40L, 41L));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-1", "9223372036854775808", "", "1.5", " 4"})
    @DisplayName("HU-21 R20: un espacioId que no es un entero positivo da 400 en espacioId junto a grupo, sin DAO")
    void espacioIdInvalido(String espacioId) {
        assertThatThrownBy(() -> servicio.reservasDeMisEspacios(ARRENDADOR, "todas", "1", espacioId))
                .isInstanceOf(ValidacionException.class)
                .satisfies(error -> assertThat(errores(error)).containsExactly(
                        new ErrorCampoDTO("grupo", "Elige próximas o pasadas."),
                        new ErrorCampoDTO("espacioId", "El espacio no es válido.")));
        assertThatThrownBy(() -> servicio.reservasDeMisEspacios(ARRENDADOR, "proximas", "0", espacioId))
                .isInstanceOf(ValidacionException.class)
                .satisfies(error -> assertThat(errores(error)).containsExactly(
                        new ErrorCampoDTO("pagina", "La página no es válida."),
                        new ErrorCampoDTO("espacioId", "El espacio no es válido.")));
        assertThatThrownBy(() -> servicio.reservasDeMisEspacios(ARRENDADOR, "proximas", "1", espacioId))
                .isInstanceOf(ValidacionException.class)
                .satisfies(error -> assertThat(errores(error)).containsExactly(
                        new ErrorCampoDTO("espacioId", "El espacio no es válido.")));
        verifyNoInteractions(dao, catalogo, cuentas);
    }

    @Test
    @DisplayName("HU-21 R20: el mayor espacioId válido es Long.MAX_VALUE")
    void espacioIdMaximo() {
        when(catalogo.nombresDelArrendador(ARRENDADOR_ID)).thenReturn(Map.of(Long.MAX_VALUE, "Cancha A"));
        when(dao.confirmadasDeEspacios(Set.of(Long.MAX_VALUE))).thenReturn(List.of());

        var pagina = servicio.reservasDeMisEspacios(ARRENDADOR, "proximas", null, "9223372036854775807");

        assertThat(pagina.reservas()).isEmpty();
    }

    @Test
    @DisplayName("HU-21 R21: un espacioId ajeno o inexistente da 404 sin consultar reservas")
    void espacioIdAjenoDa404() {
        when(catalogo.nombresDelArrendador(ARRENDADOR_ID)).thenReturn(Map.of(40L, "Cancha A"));

        assertThatThrownBy(() -> servicio.reservasDeMisEspacios(ARRENDADOR, "proximas", "1", "99"))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(dao, cuentas);
    }

    @Test
    @DisplayName("HU-21 R21: con espacioId y un arrendador sin espacios da 404, no una página vacía")
    void espacioIdSinEspaciosDa404() {
        when(catalogo.nombresDelArrendador(ARRENDADOR_ID)).thenReturn(Map.of());

        assertThatThrownBy(() -> servicio.reservasDeMisEspacios(ARRENDADOR, "proximas", "1", "40"))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(dao, cuentas);
    }

    @Test
    @DisplayName("HU-21 R22: sin espacioId responde como HU-14, con todas las confirmadas de sus espacios")
    void sinEspacioIdIgualQueHu14() {
        when(catalogo.nombresDelArrendador(ARRENDADOR_ID)).thenReturn(Map.of(40L, "Cancha A", 41L, "Cancha B"));
        when(dao.confirmadasDeEspacios(Set.of(40L, 41L))).thenReturn(List.of(
                confirmada(1L, 20L, 40L, HOY.plusDays(1), 18), confirmada(2L, 21L, 41L, HOY.plusDays(2), 9)));
        when(cuentas.nombres(any())).thenReturn(Map.of());

        var conNull = servicio.reservasDeMisEspacios(ARRENDADOR, "proximas", "1", null);
        var deTres = servicio.reservasDeMisEspacios(ARRENDADOR, "proximas", "1");

        assertThat(conNull.reservas()).extracting(ReservaDeMiEspacioDTO::id).containsExactly(1L, 2L);
        assertThat(deTres).isEqualTo(conNull);
    }

    @Test
    @DisplayName("HU-21 R20: con espacioId y otro rol da 403 antes de validar")
    void espacioIdConOtroRol() {
        assertThatThrownBy(() -> servicio.reservasDeMisEspacios(USUARIO, "proximas", "1", "abc"))
                .isInstanceOf(AccesoDenegadoException.class);
        verifyNoInteractions(dao, catalogo, cuentas);
    }
}
