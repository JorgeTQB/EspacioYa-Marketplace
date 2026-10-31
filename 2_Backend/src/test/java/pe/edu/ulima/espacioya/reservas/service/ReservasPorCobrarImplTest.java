package pe.edu.ulima.espacioya.reservas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.espacios.service.CatalogoEspaciosService;
import pe.edu.ulima.espacioya.espacios.service.DisponibilidadService;
import pe.edu.ulima.espacioya.espacios.service.EspacioBloqueado;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservaBloqueada;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservaPorCobrar;
import pe.edu.ulima.espacioya.reservas.domain.EstadoReserva;
import pe.edu.ulima.espacioya.reservas.domain.Reserva;
import pe.edu.ulima.espacioya.reservas.domain.ReservaBloque;
import pe.edu.ulima.espacioya.reservas.repository.ReservaBloqueRepository;
import pe.edu.ulima.espacioya.reservas.repository.ReservaRepository;

@ExtendWith(MockitoExtension.class)
class ReservasPorCobrarImplTest {

    private static final Instant CREADA = Instant.parse("2026-10-28T15:00:00Z");
    private static final Instant ANTES_DE_VENCER = CREADA.plus(Duration.ofMinutes(5));
    private static final Instant VENCE = CREADA.plus(Duration.ofMinutes(15));
    private static final LocalDate FECHA = LocalDate.of(2026, 11, 2);
    private static final long RESERVA_ID = 90L;
    private static final long USUARIO_ID = 7L;
    private static final long ESPACIO_ID = 40L;
    private static final long ARRENDADOR_ID = 3L;

    @Mock
    private ReservaRepository reservas;
    @Mock
    private ReservaBloqueRepository bloques;
    @Mock
    private DisponibilidadService disponibilidad;
    @Mock
    private CatalogoEspaciosService catalogo;
    @Mock
    private AuditoriaService auditoria;
    @Mock
    private Clock reloj;

    private ReservasPorCobrarImpl puerto;

    @BeforeEach
    void preparar() {
        puerto = new ReservasPorCobrarImpl(reservas, bloques, disponibilidad, catalogo, auditoria, reloj);
    }

    private static Reserva reserva() {
        var reserva = Reserva.pendiente(USUARIO_ID, ESPACIO_ID, new BigDecimal("80.50"), 3, CREADA);
        ReflectionTestUtils.setField(reserva, "id", RESERVA_ID);
        return reserva;
    }

    private void paraBloquear(Instant ahora) {
        when(reservas.findEspacioIdById(RESERVA_ID)).thenReturn(Optional.of(ESPACIO_ID));
        when(disponibilidad.bloquearEspacio(ESPACIO_ID))
                .thenReturn(new EspacioBloqueado(ARRENDADOR_ID, "Cancha Los Olivos"));
        when(reservas.findParaActualizar(RESERVA_ID)).thenReturn(Optional.of(reserva()));
        when(bloques.findByReservaIdOrderByHoraInicioAsc(RESERVA_ID)).thenReturn(List.of(
                new ReservaBloque(RESERVA_ID, ESPACIO_ID, FECHA, 14),
                new ReservaBloque(RESERVA_ID, ESPACIO_ID, FECHA, 15),
                new ReservaBloque(RESERVA_ID, ESPACIO_ID, FECHA, 16)));
        when(reloj.instant()).thenReturn(ahora);
    }

    @Test
    @DisplayName("HU-13 R36: bloquea por proyección, luego el espacio, luego la reserva, y solo entonces lee el reloj")
    void ordenDeLosBloqueos() {
        paraBloquear(ANTES_DE_VENCER);

        puerto.bloquear(RESERVA_ID);

        var orden = inOrder(reservas, disponibilidad, reloj);
        orden.verify(reservas).findEspacioIdById(RESERVA_ID);
        orden.verify(disponibilidad).bloquearEspacio(ESPACIO_ID);
        orden.verify(reservas).findParaActualizar(RESERVA_ID);
        orden.verify(reloj).instant();
        verify(reservas, never()).findById(any());
    }

    @Test
    @DisplayName("HU-13 R36: devuelve lo necesario para los correos, con la vigencia evaluada tras los bloqueos")
    void devuelveLaReservaBloqueada() {
        paraBloquear(ANTES_DE_VENCER);

        assertThat(puerto.bloquear(RESERVA_ID)).contains(new ReservaBloqueada(RESERVA_ID, USUARIO_ID, ARRENDADOR_ID,
                "Cancha Los Olivos", FECHA, 14, 17, new BigDecimal("241.50"), true, ANTES_DE_VENCER));
    }

    @Test
    @DisplayName("HU-13 R36: si vence mientras espera el bloqueo, se declara no vigente")
    void venceMientrasEspera() {
        paraBloquear(VENCE);

        assertThat(puerto.bloquear(RESERVA_ID)).get().extracting(ReservaBloqueada::vigente).isEqualTo(false);
    }

    @Test
    @DisplayName("HU-13 R29: una reserva inexistente da vacío sin tomar bloqueos")
    void reservaInexistente() {
        when(reservas.findEspacioIdById(RESERVA_ID)).thenReturn(Optional.empty());

        assertThat(puerto.bloquear(RESERVA_ID)).isEmpty();
        verifyNoInteractions(disponibilidad);
    }

    @Test
    @DisplayName("HU-13 R31, R53: confirmar deja la reserva CONFIRMADA y audita sin usuario ni IP, con su detalle")
    void confirmaYAudita() {
        var reserva = reserva();
        when(reservas.findParaActualizar(RESERVA_ID)).thenReturn(Optional.of(reserva));

        puerto.confirmar(RESERVA_ID, ANTES_DE_VENCER);

        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        verify(auditoria).registrar(new EventoAuditoria(null, "RESERVA_CONFIRMADA", "reserva", RESERVA_ID, null,
                "origen=WEBHOOK;espacioId=" + ESPACIO_ID));
    }

    @Test
    @DisplayName("HU-13 R32: confirmar no toca reserva_bloque")
    void confirmarNoTocaLosBloques() {
        when(reservas.findParaActualizar(RESERVA_ID)).thenReturn(Optional.of(reserva()));

        puerto.confirmar(RESERVA_ID, ANTES_DE_VENCER);

        verifyNoInteractions(bloques);
    }

    @Test
    @DisplayName("HU-13 R34: confirmar una reserva no vigente lanza y no audita")
    void confirmarNoVigente() {
        when(reservas.findParaActualizar(RESERVA_ID)).thenReturn(Optional.of(reserva()));

        assertThatThrownBy(() -> puerto.confirmar(RESERVA_ID, VENCE)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("HU-13 R12: deUsuario informa vigencia y visibilidad del espacio")
    void deUsuarioConEspacioNoVisible() {
        when(reloj.instant()).thenReturn(ANTES_DE_VENCER);
        when(reservas.findByIdAndUsuarioId(RESERVA_ID, USUARIO_ID)).thenReturn(Optional.of(reserva()));
        when(catalogo.esVisible(ESPACIO_ID)).thenReturn(false);

        assertThat(puerto.deUsuario(RESERVA_ID, USUARIO_ID)).contains(new ReservaPorCobrar(RESERVA_ID,
                new BigDecimal("241.50"), VENCE, true, false));
    }

    @Test
    @DisplayName("HU-13 R15: deUsuario de una reserva ajena o inexistente da vacío")
    void deUsuarioAjena() {
        when(reloj.instant()).thenReturn(ANTES_DE_VENCER);
        when(reservas.findByIdAndUsuarioId(RESERVA_ID, 8L)).thenReturn(Optional.empty());

        assertThat(puerto.deUsuario(RESERVA_ID, 8L)).isEmpty();
    }
}
