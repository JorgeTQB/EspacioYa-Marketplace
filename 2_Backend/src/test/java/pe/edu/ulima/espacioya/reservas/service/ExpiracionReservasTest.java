package pe.edu.ulima.espacioya.reservas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.reservas.domain.EstadoReserva;
import pe.edu.ulima.espacioya.reservas.domain.Reserva;
import pe.edu.ulima.espacioya.reservas.repository.ReservaBloqueRepository;
import pe.edu.ulima.espacioya.reservas.repository.ReservaRepository;

@ExtendWith(MockitoExtension.class)
class ExpiracionReservasTest {

    private static final Instant AHORA = Instant.parse("2026-10-28T15:00:00Z");
    private static final Instant HACE_20_MIN = AHORA.minus(Duration.ofMinutes(20));
    private static final LocalDate FECHA = LocalDate.of(2026, 11, 2);
    private static final long ESPACIO_ID = 40L;

    @Mock
    private ReservaRepository reservas;
    @Mock
    private ReservaBloqueRepository bloques;
    @Mock
    private AuditoriaService auditoria;

    private ExpiracionReservas expiracion;

    @BeforeEach
    void preparar() {
        expiracion = new ExpiracionReservas(reservas, bloques, auditoria,
                new TransactionTemplate(mock(PlatformTransactionManager.class)), Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private static Reserva reserva(long id, Instant creadaEn) {
        var reserva = Reserva.pendiente(7L, ESPACIO_ID, new BigDecimal("80.00"), 1, creadaEn);
        ReflectionTestUtils.setField(reserva, "id", id);
        return reserva;
    }

    private static Reserva vencida(long id) {
        return reserva(id, HACE_20_MIN);
    }

    private static Reserva conEstado(long id, EstadoReserva estado) {
        var reserva = vencida(id);
        ReflectionTestUtils.setField(reserva, "estado", estado);
        return reserva;
    }

    @Test
    @DisplayName("HU-12 R9, R10: expira cada vencida y desactiva sus bloques")
    void expiraYDesactiva() {
        var primera = vencida(1L);
        var segunda = vencida(2L);
        when(reservas.findIdsVencidas(AHORA)).thenReturn(List.of(1L, 2L));
        when(reservas.findParaActualizar(1L)).thenReturn(Optional.of(primera));
        when(reservas.findParaActualizar(2L)).thenReturn(Optional.of(segunda));

        assertThat(expiracion.expirarVencidas()).isEqualTo(2);

        assertThat(primera.getEstado()).isEqualTo(EstadoReserva.EXPIRADA);
        assertThat(segunda.getEstado()).isEqualTo(EstadoReserva.EXPIRADA);
        verify(bloques).desactivarDeReserva(1L);
        verify(bloques).desactivarDeReserva(2L);
    }

    @Test
    @DisplayName("HU-12 R43: audita RESERVA_EXPIRADA sin usuario ni IP y con el detalle exacto")
    void auditaLaExpiracion() {
        when(reservas.findIdsVencidas(AHORA)).thenReturn(List.of(1L));
        when(reservas.findParaActualizar(1L)).thenReturn(Optional.of(vencida(1L)));

        expiracion.expirarVencidas();

        var evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        assertThat(evento.getValue()).isEqualTo(new EventoAuditoria(null, "RESERVA_EXPIRADA", "reserva", 1L, null,
                "origen=PROCESO;espacioId=40"));
    }

    @Test
    @DisplayName("HU-12 R15: si falla la primera, la segunda se expira igual")
    void unFalloNoDetieneLasDemas() {
        var segunda = vencida(2L);
        when(reservas.findIdsVencidas(AHORA)).thenReturn(List.of(1L, 2L));
        when(reservas.findParaActualizar(1L)).thenThrow(new IllegalStateException("fallo simulado"));
        when(reservas.findParaActualizar(2L)).thenReturn(Optional.of(segunda));

        assertThat(expiracion.expirarVencidas()).isEqualTo(1);

        assertThat(segunda.getEstado()).isEqualTo(EstadoReserva.EXPIRADA);
        verify(bloques, never()).desactivarDeReserva(1L);
        verify(bloques).desactivarDeReserva(2L);
    }

    @Test
    @DisplayName("HU-12 R16: si tras el bloqueo ya no está vencida (vigente, CONFIRMADA o EXPIRADA) no la toca ni "
            + "audita")
    void yaNoVencidaTrasElBloqueo() {
        var vigente = reserva(1L, AHORA);
        var confirmada = conEstado(2L, EstadoReserva.CONFIRMADA);
        var expirada = conEstado(3L, EstadoReserva.EXPIRADA);
        when(reservas.findIdsVencidas(AHORA)).thenReturn(List.of(1L, 2L, 3L, 4L));
        when(reservas.findParaActualizar(1L)).thenReturn(Optional.of(vigente));
        when(reservas.findParaActualizar(2L)).thenReturn(Optional.of(confirmada));
        when(reservas.findParaActualizar(3L)).thenReturn(Optional.of(expirada));
        when(reservas.findParaActualizar(4L)).thenReturn(Optional.empty());

        assertThat(expiracion.expirarVencidas()).isZero();

        assertThat(vigente.getEstado()).isEqualTo(EstadoReserva.PENDIENTE_PAGO);
        assertThat(confirmada.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        verify(bloques, never()).desactivarDeReserva(anyLong());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("HU-12 R11: sin vencidas no abre transacciones ni cambia nada")
    void sinVencidas() {
        when(reservas.findIdsVencidas(AHORA)).thenReturn(List.of());

        assertThat(expiracion.expirarVencidas()).isZero();

        verify(reservas, never()).findParaActualizar(any());
        verifyNoInteractions(bloques, auditoria);
    }

    @Test
    @DisplayName("HU-12 R17: liberarVencidas expira las que retienen esas horas, las libera y audita")
    void liberaLasVencidasQueChocan() {
        var vieja = vencida(1L);
        when(reservas.findVencidasQueRetienen(ESPACIO_ID, FECHA, List.of((short) 14, (short) 15), AHORA))
                .thenReturn(List.of(vieja));

        expiracion.liberarVencidas(ESPACIO_ID, FECHA, List.of(14, 15), AHORA);

        assertThat(vieja.getEstado()).isEqualTo(EstadoReserva.EXPIRADA);
        verify(bloques, times(1)).desactivarDeReserva(1L);
        verify(auditoria, times(1)).registrar(new EventoAuditoria(null, "RESERVA_EXPIRADA", "reserva", 1L, null,
                "origen=PROCESO;espacioId=40"));
    }
}
