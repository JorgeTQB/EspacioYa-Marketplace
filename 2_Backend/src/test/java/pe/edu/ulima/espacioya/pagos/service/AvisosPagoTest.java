package pe.edu.ulima.espacioya.pagos.service;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import pe.edu.ulima.espacioya.cuentas.service.AvisoReservaPagada;
import pe.edu.ulima.espacioya.cuentas.service.ComprobantePago;
import pe.edu.ulima.espacioya.cuentas.service.CuentaService;
import pe.edu.ulima.espacioya.cuentas.service.NotificadorCorreo;
import pe.edu.ulima.espacioya.pagos.service.pasarela.PagoConsultado;
import pe.edu.ulima.espacioya.pagos.service.pasarela.ResultadoPasarela;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservaBloqueada;

@ExtendWith(MockitoExtension.class)
class AvisosPagoTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 11, 2);
    private static final BigDecimal MONTO = new BigDecimal("241.50");
    private static final String CORREO_USUARIO = "carla@correo.pe";
    private static final String CORREO_ARRENDADOR = "ana@correo.pe";
    private static final ReservaBloqueada RESERVA = new ReservaBloqueada(90L, 7L, 3L, "Cancha Los Olivos", FECHA, 14,
            17, MONTO, true, Instant.parse("2026-10-28T15:05:00Z"));
    private static final PagoConsultado PAGO =
            new PagoConsultado("555", ResultadoPasarela.APROBADO, MONTO, "PEN", "1234", "90");
    private static final ComprobantePago COMPROBANTE =
            new ComprobantePago("Cancha Los Olivos", FECHA, 14, 17, MONTO, "1234", "555");
    private static final AvisoReservaPagada AVISO = new AvisoReservaPagada("Cancha Los Olivos", FECHA, 14, 17);

    @Mock
    private CuentaService cuentas;
    @Mock
    private NotificadorCorreo notificador;

    private AvisosPago avisos;

    @BeforeEach
    void preparar() {
        avisos = new AvisosPago(cuentas, notificador);
        TransactionSynchronizationManager.initSynchronization();
        when(cuentas.correoDe(7L)).thenReturn(CORREO_USUARIO);
        when(cuentas.correoDe(3L)).thenReturn(CORREO_ARRENDADOR);
    }

    @AfterEach
    void limpiar() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private static void confirmarTransaccion() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    @Test
    @DisplayName("HU-13 R48, R50: lee los correos dentro de la transacción y no envía nada antes del commit")
    void nadaAntesDelCommit() {
        avisos.programar(RESERVA, PAGO);

        verify(cuentas).correoDe(7L);
        verify(cuentas).correoDe(3L);
        verify(notificador, never()).notificarPagoConfirmado(CORREO_USUARIO, COMPROBANTE);
        verify(notificador, never()).notificarReservaPagada(CORREO_ARRENDADOR, AVISO);
    }

    @Test
    @DisplayName("HU-13 R48: tras el commit envía al usuario el comprobante con espacio, fecha, horario y monto")
    void enviaElComprobante() {
        avisos.programar(RESERVA, PAGO);

        confirmarTransaccion();

        verify(notificador).notificarPagoConfirmado(CORREO_USUARIO, COMPROBANTE);
    }

    @Test
    @DisplayName("HU-13 R49: tras el commit envía al arrendador espacio, fecha y horario")
    void enviaElAviso() {
        avisos.programar(RESERVA, PAGO);

        confirmarTransaccion();

        verify(notificador).notificarReservaPagada(CORREO_ARRENDADOR, AVISO);
    }

    @Test
    @DisplayName("HU-13 R51: si falla el comprobante, el aviso al arrendador sale igual")
    void unFalloNoImpideElOtro() {
        doThrow(new IllegalStateException("el proveedor de correo falló"))
                .when(notificador).notificarPagoConfirmado(CORREO_USUARIO, COMPROBANTE);
        avisos.programar(RESERVA, PAGO);

        confirmarTransaccion();

        verify(notificador).notificarReservaPagada(CORREO_ARRENDADOR, AVISO);
    }
}
