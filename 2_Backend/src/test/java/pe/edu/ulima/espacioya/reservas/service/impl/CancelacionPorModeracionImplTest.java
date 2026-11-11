package pe.edu.ulima.espacioya.reservas.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.espacios.service.DisponibilidadService;
import pe.edu.ulima.espacioya.espacios.service.EspacioBloqueado;
import pe.edu.ulima.espacioya.pagos.service.PagoService;
import pe.edu.ulima.espacioya.pagos.service.ReembolsoPorCancelacion;
import pe.edu.ulima.espacioya.reservas.domain.EstadoReserva;
import pe.edu.ulima.espacioya.reservas.domain.QuienCancela;
import pe.edu.ulima.espacioya.reservas.domain.ReembolsoCalculado;
import pe.edu.ulima.espacioya.reservas.domain.Reserva;
import pe.edu.ulima.espacioya.reservas.domain.ReservaBloque;
import pe.edu.ulima.espacioya.reservas.domain.ReservaConHorario;
import pe.edu.ulima.espacioya.reservas.repository.ReservaBloqueRepository;
import pe.edu.ulima.espacioya.reservas.repository.ReservaRepository;
import pe.edu.ulima.espacioya.reservas.service.AvisosCancelacion;

@ExtendWith(MockitoExtension.class)
class CancelacionPorModeracionImplTest {

    // Miércoles 2026-11-04 a las 09:59 de Lima: la reserva de las 10:00 empieza en 1 minuto.
    private static final Instant AHORA = Instant.parse("2026-11-04T14:59:00Z");
    private static final LocalDate HOY = LocalDate.of(2026, 11, 4);
    private static final long ESPACIO_ID = 40L;
    private static final long ARRENDADOR_ID = 3L;
    private static final long ADMIN_ID = 99L;
    private static final String NOMBRE = "Cancha Los Olivos";
    private static final String IP = "127.0.0.1";
    private static final UsuarioAutenticado ADMIN = new UsuarioAutenticado(ADMIN_ID, 5L, "ADMIN");

    @Mock
    private ReservaRepository reservas;
    @Mock
    private ReservaBloqueRepository bloques;
    @Mock
    private DisponibilidadService disponibilidad;
    @Mock
    private PagoService pagos;
    @Mock
    private AvisosCancelacion avisos;
    @Mock
    private AuditoriaService auditoria;

    private CancelacionPorModeracionImpl cancelacion;

    @BeforeEach
    void preparar() {
        cancelacion = new CancelacionPorModeracionImpl(reservas, bloques, disponibilidad, pagos, avisos, auditoria,
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private Reserva reserva(long id, long usuario, EstadoReserva estado, LocalDate dia, int hora, int horas) {
        var reserva = Reserva.pendiente(usuario, ESPACIO_ID, new BigDecimal("80.50"), horas, AHORA.minusSeconds(60));
        ReflectionTestUtils.setField(reserva, "id", id);
        ReflectionTestUtils.setField(reserva, "estado", estado);
        var filas = new ArrayList<ReservaBloque>();
        for (var h = hora; h < hora + horas; h++) {
            filas.add(new ReservaBloque(id, ESPACIO_ID, dia, h));
        }
        when(bloques.findByReservaIdOrderByHoraInicioAsc(id)).thenReturn(filas);
        return reserva;
    }

    private void espacioBloqueado(List<Reserva> confirmadas) {
        when(disponibilidad.bloquearEspacio(ESPACIO_ID)).thenReturn(new EspacioBloqueado(ARRENDADOR_ID, NOMBRE));
        when(reservas.findConfirmadasParaActualizar(ESPACIO_ID)).thenReturn(confirmadas);
    }

    @Test
    @DisplayName("HU-27 R64, R65: cancela solo las por iniciar; iniciada, terminada, pendiente, expirada y cancelada no")
    void cancelaSoloLasPorIniciar() {
        var enUnMinuto = reserva(1L, 7L, EstadoReserva.CONFIRMADA, HOY, 10, 1);
        var manana = reserva(2L, 8L, EstadoReserva.CONFIRMADA, HOY.plusDays(1), 8, 3);
        var iniciada = reserva(3L, 7L, EstadoReserva.CONFIRMADA, HOY, 9, 2);
        var terminada = reserva(4L, 7L, EstadoReserva.CONFIRMADA, HOY.minusDays(1), 8, 1);
        var pendiente = reserva(5L, 7L, EstadoReserva.PENDIENTE_PAGO, HOY.plusDays(2), 8, 1);
        var expirada = reserva(6L, 7L, EstadoReserva.EXPIRADA, HOY.plusDays(2), 9, 1);
        var cancelada = reserva(7L, 7L, EstadoReserva.CANCELADA, HOY.plusDays(2), 10, 1);
        espacioBloqueado(List.of(enUnMinuto, manana, iniciada, terminada, pendiente, expirada, cancelada));

        var resultado = cancelacion.cancelarPorFraude(ADMIN, ESPACIO_ID, IP);

        assertThat(resultado.cantidad()).isEqualTo(2);
        for (var hecha : List.of(enUnMinuto, manana)) {
            assertThat(hecha.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
            assertThat(hecha.getCanceladaPor()).isEqualTo(QuienCancela.ADMIN);
            assertThat(hecha.getCanceladaEn()).isEqualTo(AHORA);
            assertThat(hecha.getMontoReembolso()).isEqualTo(hecha.getMontoTotal());
            verify(bloques).desactivarDeReserva(hecha.getId());
        }
        assertThat(manana.getMontoReembolso()).isEqualTo(new BigDecimal("241.50"));
        for (var intacta : List.of(iniciada, terminada)) {
            assertThat(intacta.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
            assertThat(intacta.getCanceladaPor()).isNull();
        }
        assertThat(pendiente.getEstado()).isEqualTo(EstadoReserva.PENDIENTE_PAGO);
        assertThat(expirada.getEstado()).isEqualTo(EstadoReserva.EXPIRADA);
        assertThat(cancelada.getCanceladaPor()).isNull();
        verify(bloques, times(2)).desactivarDeReserva(anyLong());
    }

    @Test
    @DisplayName("HU-27 R66: solicita el reembolso de cada cancelada por su monto_total con el admin y la IP")
    void reembolsaElMontoTotal() {
        var manana = reserva(2L, 8L, EstadoReserva.CONFIRMADA, HOY.plusDays(1), 8, 3);
        espacioBloqueado(List.of(manana));

        cancelacion.cancelarPorFraude(ADMIN, ESPACIO_ID, IP);

        verify(pagos).reembolsarCancelacion(new ReembolsoPorCancelacion(2L, new BigDecimal("241.50"), ADMIN_ID, IP));
    }

    @Test
    @DisplayName("HU-27 R71: audita RESERVA_CANCELADA con canceladaPor=ADMIN, MODERACION, 100 % y el monto")
    void auditaLaCancelacion() {
        var manana = reserva(2L, 8L, EstadoReserva.CONFIRMADA, HOY.plusDays(1), 8, 3);
        espacioBloqueado(List.of(manana));

        cancelacion.cancelarPorFraude(ADMIN, ESPACIO_ID, IP);

        verify(auditoria).registrar(new EventoAuditoria(ADMIN_ID, "RESERVA_CANCELADA", "reserva", 2L, IP,
                "canceladaPor=ADMIN;espacioId=40;politica=MODERACION;porcentaje=100;monto=241.50"));
    }

    @Test
    @DisplayName("HU-27 R69, R70: programa el aviso a cada usuario con ADMIN y 100 %, nunca el del arrendador")
    void avisaSoloALosUsuarios() {
        var enUnMinuto = reserva(1L, 7L, EstadoReserva.CONFIRMADA, HOY, 10, 1);
        var manana = reserva(2L, 8L, EstadoReserva.CONFIRMADA, HOY.plusDays(1), 8, 3);
        espacioBloqueado(List.of(enUnMinuto, manana));

        cancelacion.cancelarPorFraude(ADMIN, ESPACIO_ID, IP);

        var horario = ArgumentCaptor.forClass(ReservaConHorario.class);
        verify(avisos).programarAlUsuario(eq(8L), eq(NOMBRE), horario.capture(), eq(QuienCancela.ADMIN),
                eq(new ReembolsoCalculado("MODERACION", 100, new BigDecimal("241.50"))));
        assertThat(horario.getValue().fecha()).isEqualTo(HOY.plusDays(1));
        assertThat(horario.getValue().horaInicio()).isEqualTo(8);
        assertThat(horario.getValue().horaFin()).isEqualTo(11);
        verify(avisos).programarAlUsuario(eq(7L), eq(NOMBRE), any(), eq(QuienCancela.ADMIN),
                eq(new ReembolsoCalculado("MODERACION", 100, new BigDecimal("80.50"))));
        verify(avisos, never()).programar(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("HU-27 R73: bloquea el espacio antes que sus reservas confirmadas")
    void bloqueaEspacioAntesQueReservas() {
        espacioBloqueado(List.of());

        var resultado = cancelacion.cancelarPorFraude(ADMIN, ESPACIO_ID, IP);

        assertThat(resultado.cantidad()).isZero();
        var orden = inOrder(disponibilidad, reservas);
        orden.verify(disponibilidad).bloquearEspacio(ESPACIO_ID);
        orden.verify(reservas).findConfirmadasParaActualizar(ESPACIO_ID);
        verifyNoInteractions(pagos, auditoria, avisos);
    }

    @Test
    @DisplayName("HU-27 R76: con una sesión que no es ADMIN da 403 sin leer ni bloquear reservas")
    void sinRolAdmin() {
        for (var sesion : new UsuarioAutenticado[] {new UsuarioAutenticado(7L, 1L, "USUARIO"),
                new UsuarioAutenticado(3L, 2L, "ARRENDADOR"), null}) {
            assertThatThrownBy(() -> cancelacion.cancelarPorFraude(sesion, ESPACIO_ID, IP))
                    .isInstanceOf(AccesoDenegadoException.class);
        }
        verifyNoInteractions(reservas, bloques, disponibilidad, pagos, avisos, auditoria);
    }

    @Test
    @DisplayName("HU-27 R72: si la solicitud de reembolso falla, la excepción se propaga para revertir todo")
    void falloDelReembolsoSePropaga() {
        var manana = reserva(2L, 8L, EstadoReserva.CONFIRMADA, HOY.plusDays(1), 8, 3);
        espacioBloqueado(List.of(manana));
        doThrow(new IllegalStateException("sin pago PAGADO")).when(pagos).reembolsarCancelacion(any());

        assertThatThrownBy(() -> cancelacion.cancelarPorFraude(ADMIN, ESPACIO_ID, IP))
                .isInstanceOf(IllegalStateException.class).hasMessage("sin pago PAGADO");
        verifyNoInteractions(auditoria, avisos);
    }
}
