package pe.edu.ulima.espacioya.pagos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.comun.error.ServicioExternoException;
import pe.edu.ulima.espacioya.pagos.domain.CheckoutPago;
import pe.edu.ulima.espacioya.pagos.repository.CheckoutPagoRepository;
import pe.edu.ulima.espacioya.pagos.service.pasarela.CheckoutCreado;
import pe.edu.ulima.espacioya.pagos.service.pasarela.PasarelaPago;
import pe.edu.ulima.espacioya.pagos.service.pasarela.SolicitudCheckout;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservaPorCobrar;

@ExtendWith(MockitoExtension.class)
class SesionesCheckoutTest {

    private static final Instant AHORA = Instant.parse("2026-10-28T15:00:00Z");
    private static final Instant EXPIRA = AHORA.plus(Duration.ofMinutes(15));
    private static final long RESERVA_ID = 90L;
    private static final ReservaPorCobrar RESERVA =
            new ReservaPorCobrar(RESERVA_ID, new BigDecimal("241.50"), EXPIRA, true, true);
    private static final CheckoutCreado CREADO = new CheckoutCreado("pref-1", "https://pago.invalid/pref-1");
    private static final CheckoutPago EXISTENTE =
            new CheckoutPago(RESERVA_ID, "pref-0", "https://pago.invalid/pref-0", AHORA);

    @Mock
    private PasarelaPago pasarela;
    @Mock
    private CheckoutPagoRepository checkouts;

    private SesionesCheckout sesiones;

    @BeforeEach
    void preparar() {
        sesiones = new SesionesCheckout(pasarela, checkouts,
                new TransactionTemplate(mock(PlatformTransactionManager.class)), Clock.fixed(AHORA, ZoneOffset.UTC),
                Duration.ofMinutes(1), "http://localhost:5173");
    }

    private static DataIntegrityViolationException violacion(String restriccion) {
        return new DataIntegrityViolationException("no se pudo guardar",
                new SQLException("ERROR: duplicate key value violates unique constraint \"" + restriccion + "\""));
    }

    @Test
    @DisplayName("HU-13 R4: crea el checkout con venceEn = expira_en − margen y la URL de retorno de la reserva")
    void creaElCheckout() {
        when(checkouts.findByReservaId(RESERVA_ID)).thenReturn(Optional.empty());
        when(pasarela.crearCheckout(any())).thenReturn(CREADO);

        assertThat(sesiones.urlPara(RESERVA)).isEqualTo(CREADO.urlPago());

        verify(pasarela).crearCheckout(new SolicitudCheckout(RESERVA_ID, new BigDecimal("241.50"),
                EXPIRA.minus(Duration.ofMinutes(1)), "http://localhost:5173/reservas/90"));
    }

    @Test
    @DisplayName("HU-13 R2: guarda la fila con el id y la URL del checkout")
    void guardaLaFila() {
        when(checkouts.findByReservaId(RESERVA_ID)).thenReturn(Optional.empty());
        when(pasarela.crearCheckout(any())).thenReturn(CREADO);

        sesiones.urlPara(RESERVA);

        var guardada = ArgumentCaptor.forClass(CheckoutPago.class);
        verify(checkouts).saveAndFlush(guardada.capture());
        assertThat(guardada.getValue().getReservaId()).isEqualTo(RESERVA_ID);
        assertThat(guardada.getValue().getIdCheckout()).isEqualTo("pref-1");
        assertThat(guardada.getValue().getUrlPago()).isEqualTo(CREADO.urlPago());
        assertThat(guardada.getValue().getCreadoEn()).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("HU-13 R6: con una fila ya guardada responde su URL sin llamar a la pasarela")
    void reutilizaElCheckout() {
        when(checkouts.findByReservaId(RESERVA_ID)).thenReturn(Optional.of(EXISTENTE));

        assertThat(sesiones.urlPara(RESERVA)).isEqualTo(EXISTENTE.getUrlPago());

        verifyNoInteractions(pasarela);
        verify(checkouts, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("HU-13 R7: quien pierde la carrera relee la fila ganadora y responde su URL")
    void releeAlPerderLaCarrera() {
        when(checkouts.findByReservaId(RESERVA_ID)).thenReturn(Optional.empty(), Optional.of(EXISTENTE));
        when(pasarela.crearCheckout(any())).thenReturn(CREADO);
        when(checkouts.saveAndFlush(any())).thenThrow(violacion("uq_checkout_pago_reserva"));

        assertThat(sesiones.urlPara(RESERVA)).isEqualTo(EXISTENTE.getUrlPago());
    }

    @Test
    @DisplayName("HU-13 R7: otra violación de integridad se relanza")
    void otraViolacionSeRelanza() {
        when(checkouts.findByReservaId(RESERVA_ID)).thenReturn(Optional.empty());
        when(pasarela.crearCheckout(any())).thenReturn(CREADO);
        when(checkouts.saveAndFlush(any())).thenThrow(violacion("checkout_pago_reserva_id_fkey"));

        assertThatThrownBy(() -> sesiones.urlPara(RESERVA)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("HU-13 R17: si la pasarela falla, no guarda ninguna fila")
    void pasarelaCaida() {
        when(checkouts.findByReservaId(RESERVA_ID)).thenReturn(Optional.empty());
        when(pasarela.crearCheckout(any())).thenThrow(new ServicioExternoException());

        assertThatThrownBy(() -> sesiones.urlPara(RESERVA)).isInstanceOf(ServicioExternoException.class);

        verify(checkouts, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("HU-13 R78: existe dice si la reserva tiene fila en checkout_pago, sin llamar a la pasarela")
    void existe() {
        when(checkouts.existsByReservaId(RESERVA_ID)).thenReturn(true);
        when(checkouts.existsByReservaId(91L)).thenReturn(false);

        assertThat(sesiones.existe(RESERVA_ID)).isTrue();
        assertThat(sesiones.existe(91L)).isFalse();
        verifyNoInteractions(pasarela);
    }

    @Test
    @DisplayName("HU-13 R40: cerrar pide a la pasarela cerrar el checkout de esa reserva")
    void cierra() {
        when(checkouts.findByReservaId(RESERVA_ID)).thenReturn(Optional.of(EXISTENTE));

        sesiones.cerrar(RESERVA_ID);

        verify(pasarela).cerrarCheckout("pref-0");
    }

    @Test
    @DisplayName("HU-13 R40: sin checkout guardado no hay nada que cerrar")
    void cerrarSinCheckout() {
        when(checkouts.findByReservaId(RESERVA_ID)).thenReturn(Optional.empty());

        sesiones.cerrar(RESERVA_ID);

        verifyNoInteractions(pasarela);
    }
}
