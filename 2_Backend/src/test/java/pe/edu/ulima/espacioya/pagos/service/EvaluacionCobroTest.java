package pe.edu.ulima.espacioya.pagos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.DemasiadosIntentosException;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.pagos.domain.EstadoPago;
import pe.edu.ulima.espacioya.pagos.repository.PagoRepository;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservaPorCobrar;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservasPorCobrar;

@ExtendWith(MockitoExtension.class)
class EvaluacionCobroTest {

    private static final Instant AHORA = Instant.parse("2026-10-28T15:00:00Z");
    private static final Instant EXPIRA = AHORA.plus(Duration.ofMinutes(10));
    private static final Instant EN_ULTIMO_TRAMO = AHORA.plusSeconds(30);
    private static final long RESERVA_ID = 90L;
    private static final long USUARIO_ID = 7L;
    private static final String ID = "90";
    private static final UsuarioAutenticado USUARIO = new UsuarioAutenticado(USUARIO_ID, 31L, "USUARIO");

    @Mock
    private ReservasPorCobrar reservas;
    @Mock
    private PagoRepository pagos;

    private EvaluacionCobro evaluacion;

    @BeforeEach
    void preparar() {
        evaluacion = new EvaluacionCobro(reservas, pagos, Clock.fixed(AHORA, ZoneOffset.UTC), Duration.ofMinutes(1));
    }

    private void reserva(boolean vigente, Instant expiraEn, boolean visible, long rechazos) {
        when(reservas.deUsuario(RESERVA_ID, USUARIO_ID)).thenReturn(Optional.of(
                new ReservaPorCobrar(RESERVA_ID, new BigDecimal("241.50"), expiraEn, vigente, visible)));
        lenient().when(pagos.countByReservaIdAndEstadoAndCreadoEnGreaterThanEqual(RESERVA_ID, EstadoPago.RECHAZADO,
                AHORA.minus(Duration.ofMinutes(30)))).thenReturn(rechazos);
    }

    private void esNoDisponible() {
        assertThatThrownBy(() -> evaluacion.exigirDisponible(USUARIO, ID))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Esta reserva ya no se puede pagar. Busca otro horario para reservar de nuevo.")
                .extracting("campo").isNull();
    }

    @Test
    @DisplayName("HU-13 R1: una reserva con pago disponible se devuelve, con menos de 3 rechazos recientes")
    void disponible() {
        reserva(true, EXPIRA, true, 2);

        assertThat(evaluacion.exigirDisponible(USUARIO, ID).id()).isEqualTo(RESERVA_ID);
    }

    @Test
    @DisplayName("HU-13 R10: una reserva no vigente es 400 sin campo")
    void noVigente() {
        reserva(false, EXPIRA, true, 0);

        esNoDisponible();
    }

    @Test
    @DisplayName("HU-13 R11: en el último tramo es 400 sin campo")
    void ultimoTramo() {
        reserva(true, EN_ULTIMO_TRAMO, true, 0);

        esNoDisponible();
    }

    @Test
    @DisplayName("HU-13 R12: con el espacio no visible es 400 sin campo")
    void espacioNoVisible() {
        reserva(true, EXPIRA, false, 0);

        esNoDisponible();
    }

    @Test
    @DisplayName("HU-13 R13: con 3 rechazos recientes lanza demasiados intentos")
    void intentosAgotados() {
        reserva(true, EXPIRA, true, 3);

        assertThatThrownBy(() -> evaluacion.exigirDisponible(USUARIO, ID))
                .isInstanceOf(DemasiadosIntentosException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ARRENDADOR", "ADMIN"})
    @DisplayName("HU-13 R14: un rol distinto de USUARIO es 403, sin leer la reserva")
    void otroRol(String rol) {
        assertThatThrownBy(() -> evaluacion.exigirDisponible(new UsuarioAutenticado(USUARIO_ID, 31L, rol), ID))
                .isInstanceOf(AccesoDenegadoException.class);
        verifyNoInteractions(reservas, pagos);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abc", "0", "-1", "99999999999999999999"})
    @DisplayName("HU-13 R15: un id mal escrito es 404, sin leer la reserva")
    void idMalEscrito(String id) {
        assertThatThrownBy(() -> evaluacion.exigirDisponible(USUARIO, id))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> evaluacion.resumen(USUARIO, id)).isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(reservas);
    }

    @Test
    @DisplayName("HU-13 R15: una reserva ajena o inexistente es 404")
    void ajena() {
        when(reservas.deUsuario(RESERVA_ID, USUARIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> evaluacion.exigirDisponible(USUARIO, ID))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(pagos, never()).countByReservaIdAndEstadoAndCreadoEnGreaterThanEqual(anyLong(), any(), any());
    }

    @Test
    @DisplayName("HU-13 R18: el rol va antes que la reserva, aunque esta sea ajena")
    void ordenRolAntesQueReserva() {
        assertThatThrownBy(() -> evaluacion.exigirDisponible(new UsuarioAutenticado(USUARIO_ID, 31L, "ADMIN"), "abc"))
                .isInstanceOf(AccesoDenegadoException.class);
    }

    @Test
    @DisplayName("HU-13 R18: vigencia, último tramo y espacio van antes que los rechazos")
    void ordenConTresRechazos() {
        reserva(false, EXPIRA, true, 3);
        esNoDisponible();

        reserva(true, EN_ULTIMO_TRAMO, true, 3);
        esNoDisponible();

        reserva(true, EXPIRA, false, 3);
        esNoDisponible();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ARRENDADOR", "ADMIN"})
    @DisplayName("HU-13 R74: exigirRol y exigirCheckoutNoAgotado rechazan otro rol, sin leer la reserva")
    void simulacionConOtroRol(String rol) {
        var otro = new UsuarioAutenticado(USUARIO_ID, 31L, rol);

        assertThatThrownBy(() -> evaluacion.exigirRol(otro)).isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> evaluacion.exigirCheckoutNoAgotado(otro, ID))
                .isInstanceOf(AccesoDenegadoException.class);
        verifyNoInteractions(reservas, pagos);
    }

    @Test
    @DisplayName("HU-13 R74: exigirRol deja pasar el rol USUARIO")
    void exigirRolConUsuario() {
        assertThatCode(() -> evaluacion.exigirRol(USUARIO)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abc", "0", "-1", "99999999999999999999"})
    @DisplayName("HU-13 R75: en la simulación, un id mal escrito es 404, sin leer la reserva")
    void simulacionConIdMalEscrito(String id) {
        assertThatThrownBy(() -> evaluacion.exigirCheckoutNoAgotado(USUARIO, id))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(reservas);
    }

    @Test
    @DisplayName("HU-13 R75: en la simulación, una reserva ajena o inexistente es 404, sin contar rechazos")
    void simulacionConReservaAjena() {
        when(reservas.deUsuario(RESERVA_ID, USUARIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> evaluacion.exigirCheckoutNoAgotado(USUARIO, ID))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(pagos);
    }

    @Test
    @DisplayName("HU-13 R77: con 2 rechazos recientes devuelve la reserva y con 3 lanza demasiados intentos")
    void simulacionConRechazos() {
        reserva(true, EXPIRA, true, 2);
        assertThat(evaluacion.exigirCheckoutNoAgotado(USUARIO, ID).id()).isEqualTo(RESERVA_ID);

        reserva(true, EXPIRA, true, 3);
        assertThatThrownBy(() -> evaluacion.exigirCheckoutNoAgotado(USUARIO, ID))
                .isInstanceOf(DemasiadosIntentosException.class);
    }

    @Test
    @DisplayName("HU-13 R79: no mira vigencia, último tramo ni espacio visible: eso lo decide el webhook")
    void simulacionNoMiraVigencia() {
        reserva(false, EN_ULTIMO_TRAMO, false, 0);

        assertThat(evaluacion.exigirCheckoutNoAgotado(USUARIO, ID).id()).isEqualTo(RESERVA_ID);
    }

    @Test
    @DisplayName("HU-13 R19: el resumen informa disponible con 2 rechazos")
    void resumenDisponible() {
        reserva(true, EXPIRA, true, 2);

        assertThat(evaluacion.resumen(USUARIO, ID))
                .isEqualTo(new EvaluacionCobro.ResumenCobro(RESERVA_ID, false, true));
    }

    @Test
    @DisplayName("HU-13 R19: el resumen informa intentos agotados aunque la reserva ya no sea vigente")
    void resumenAgotado() {
        reserva(false, EXPIRA, true, 3);

        assertThat(evaluacion.resumen(USUARIO, ID))
                .isEqualTo(new EvaluacionCobro.ResumenCobro(RESERVA_ID, true, false));
    }

    @Test
    @DisplayName("HU-13 R19: el resumen de una reserva propia no depende del rol")
    void resumenSinRol() {
        reserva(true, EXPIRA, false, 0);

        assertThat(evaluacion.resumen(new UsuarioAutenticado(USUARIO_ID, 31L, "ARRENDADOR"), ID))
                .isEqualTo(new EvaluacionCobro.ResumenCobro(RESERVA_ID, false, false));
    }
}
