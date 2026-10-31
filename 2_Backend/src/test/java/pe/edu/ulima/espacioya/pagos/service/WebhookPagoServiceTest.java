package pe.edu.ulima.espacioya.pagos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.ServicioExternoException;
import pe.edu.ulima.espacioya.pagos.domain.EstadoPago;
import pe.edu.ulima.espacioya.pagos.domain.Pago;
import pe.edu.ulima.espacioya.pagos.repository.PagoRepository;
import pe.edu.ulima.espacioya.pagos.service.pasarela.FirmaInvalidaException;
import pe.edu.ulima.espacioya.pagos.service.pasarela.NotificacionRecibida;
import pe.edu.ulima.espacioya.pagos.service.pasarela.PagoConsultado;
import pe.edu.ulima.espacioya.pagos.service.pasarela.PasarelaPago;
import pe.edu.ulima.espacioya.pagos.service.pasarela.ResultadoPasarela;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservaBloqueada;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservasPorCobrar;

@ExtendWith(MockitoExtension.class)
class WebhookPagoServiceTest {

    private static final long RESERVA_ID = 90L;
    private static final long PAGO_ID = 500L;
    private static final String ID_PAGO = "555";
    private static final String IP = "10.15.0.1";
    private static final BigDecimal MONTO = new BigDecimal("241.50");
    private static final Instant EVALUADA = Instant.parse("2026-10-28T15:05:00Z");
    private static final NotificacionRecibida NOTIFICACION = new NotificacionRecibida(Map.of(), Map.of());

    @Mock
    private PasarelaPago pasarela;
    @Mock
    private ReservasPorCobrar reservas;
    @Mock
    private PagoRepository pagos;
    @Mock
    private SesionesCheckout sesiones;
    @Mock
    private AvisosPago avisos;
    @Mock
    private AuditoriaService auditoria;

    private WebhookPagoService servicio;

    @BeforeEach
    void preparar() {
        servicio = new WebhookPagoService(pasarela, reservas, pagos, sesiones, avisos, auditoria,
                new TransactionTemplate(mock(PlatformTransactionManager.class)));
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void limpiar() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private static ReservaBloqueada reserva(boolean vigente) {
        return new ReservaBloqueada(RESERVA_ID, 7L, 3L, "Cancha Los Olivos", LocalDate.of(2026, 11, 2), 14, 17, MONTO,
                vigente, EVALUADA);
    }

    private static PagoConsultado consultado(ResultadoPasarela resultado, String monto, String moneda) {
        return new PagoConsultado(ID_PAGO, resultado, new BigDecimal(monto), moneda, "1234", "90");
    }

    private void notifica(PagoConsultado pago) {
        when(pasarela.idNotificado(NOTIFICACION)).thenReturn(Optional.of(ID_PAGO));
        when(pasarela.consultarPago(ID_PAGO)).thenReturn(pago);
    }

    private void bloquea(ReservaBloqueada reserva) {
        when(reservas.bloquear(RESERVA_ID)).thenReturn(Optional.of(reserva));
        when(pagos.existsByIdTransaccionPasarela(ID_PAGO)).thenReturn(false);
    }

    private void guardaConId() {
        when(pagos.saveAndFlush(any(Pago.class))).thenAnswer(llamada -> {
            Pago pago = llamada.getArgument(0);
            ReflectionTestUtils.setField(pago, "id", PAGO_ID);
            return pago;
        });
    }

    private Pago pagoGuardado() {
        var guardado = ArgumentCaptor.forClass(Pago.class);
        verify(pagos).saveAndFlush(guardado.capture());
        return guardado.getValue();
    }

    private void rechazosRecientes(long cantidad) {
        when(pagos.countByReservaIdAndEstadoAndCreadoEnGreaterThanEqual(RESERVA_ID, EstadoPago.RECHAZADO,
                EVALUADA.minusSeconds(1800))).thenReturn(cantidad);
    }

    private static void confirmarTransaccion() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    private static DataIntegrityViolationException violacion(String restriccion) {
        return new DataIntegrityViolationException("no se pudo guardar",
                new SQLException("ERROR: duplicate key value violates unique constraint \"" + restriccion + "\""));
    }

    private void sinConfirmar() {
        verify(reservas, never()).confirmar(any(), any());
        verifyNoInteractions(avisos);
    }

    @Test
    @DisplayName("HU-13 R23: sin id notificado termina sin consultar, sin bloquear y sin escribir")
    void otroFormato() {
        when(pasarela.idNotificado(NOTIFICACION)).thenReturn(Optional.empty());

        servicio.recibir(NOTIFICACION, IP);

        verify(pasarela, never()).consultarPago(any());
        verifyNoInteractions(reservas, pagos, auditoria);
    }

    @Test
    @DisplayName("HU-13 R24: una firma inválida no consulta la pasarela ni escribe")
    void firmaInvalida() {
        when(pasarela.idNotificado(NOTIFICACION)).thenThrow(new FirmaInvalidaException());

        assertThatThrownBy(() -> servicio.recibir(NOTIFICACION, IP)).isInstanceOf(FirmaInvalidaException.class);

        verify(pasarela, never()).consultarPago(any());
        verifyNoInteractions(reservas, pagos, auditoria);
    }

    @Test
    @DisplayName("HU-13 R25: consulta el pago antes de bloquear la reserva")
    void consultaAntesDeBloquear() {
        notifica(consultado(ResultadoPasarela.APROBADO, "241.50", "PEN"));
        bloquea(reserva(true));
        guardaConId();

        servicio.recibir(NOTIFICACION, IP);

        var orden = inOrder(pasarela, reservas);
        orden.verify(pasarela).consultarPago(ID_PAGO);
        orden.verify(reservas).bloquear(RESERVA_ID);
    }

    @Test
    @DisplayName("HU-13 R27: si la consulta falla, no bloquea ni escribe y la falla sube para responder 503")
    void consultaFallida() {
        when(pasarela.idNotificado(NOTIFICACION)).thenReturn(Optional.of(ID_PAGO));
        when(pasarela.consultarPago(ID_PAGO)).thenThrow(new ServicioExternoException());

        assertThatThrownBy(() -> servicio.recibir(NOTIFICACION, IP)).isInstanceOf(ServicioExternoException.class);

        verifyNoInteractions(reservas, pagos, auditoria);
    }

    @Test
    @DisplayName("HU-13 R28: un pago sin resultado final no bloquea ni escribe")
    void sinResultado() {
        notifica(consultado(ResultadoPasarela.SIN_RESULTADO, "241.50", "PEN"));

        servicio.recibir(NOTIFICACION, IP);

        verifyNoInteractions(reservas, pagos, auditoria);
    }

    @Test
    @DisplayName("HU-13 R29: sin reserva del pago audita PAGO_SIN_RESERVA y no escribe en pago")
    void sinReserva() {
        notifica(consultado(ResultadoPasarela.APROBADO, "241.50", "PEN"));
        when(reservas.bloquear(RESERVA_ID)).thenReturn(Optional.empty());

        servicio.recibir(NOTIFICACION, IP);

        verify(auditoria).registrar(new EventoAuditoria(null, "PAGO_SIN_RESERVA", "pago", null, IP,
                "origen=WEBHOOK;transaccion=555"));
        verify(pagos, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("HU-13 R29: una referencia que no es un id no llega a bloquear")
    void referenciaInvalida() {
        notifica(new PagoConsultado(ID_PAGO, ResultadoPasarela.APROBADO, MONTO, "PEN", "1234", "no-es-un-id"));

        servicio.recibir(NOTIFICACION, IP);

        verifyNoInteractions(reservas);
        verify(auditoria).registrar(any());
    }

    @Test
    @DisplayName("HU-13 R30: un aprobado coincidente y vigente guarda PAGADO con el monto de la reserva y evaluadaEn")
    void aprobadoGuardaPagado() {
        notifica(consultado(ResultadoPasarela.APROBADO, "241.5", "PEN"));
        bloquea(reserva(true));
        guardaConId();

        servicio.recibir(NOTIFICACION, IP);

        var pago = pagoGuardado();
        assertThat(pago.getEstado()).isEqualTo(EstadoPago.PAGADO);
        assertThat(pago.getReservaId()).isEqualTo(RESERVA_ID);
        assertThat(pago.getIdTransaccionPasarela()).isEqualTo(ID_PAGO);
        assertThat(pago.getMonto()).isEqualTo(MONTO);
        assertThat(pago.getUltimos4()).isEqualTo("1234");
        assertThat(pago.getCreadoEn()).isEqualTo(EVALUADA);
    }

    @Test
    @DisplayName("HU-13 R31: confirma con el instante evaluado después de guardar el pago, y programa los correos")
    void aprobadoConfirma() {
        var pagoConsultado = consultado(ResultadoPasarela.APROBADO, "241.50", "PEN");
        var reserva = reserva(true);
        notifica(pagoConsultado);
        bloquea(reserva);
        guardaConId();

        servicio.recibir(NOTIFICACION, IP);

        var orden = inOrder(pagos, reservas, avisos);
        orden.verify(pagos).saveAndFlush(any());
        orden.verify(reservas).confirmar(RESERVA_ID, EVALUADA);
        orden.verify(avisos).programar(reserva, pagoConsultado);
    }

    @Test
    @DisplayName("HU-13 R33: un aprobado de otro monto guarda MONTO_DISTINTO con el monto consultado, sin confirmar")
    void montoDistinto() {
        notifica(consultado(ResultadoPasarela.APROBADO, "1.00", "PEN"));
        bloquea(reserva(true));
        guardaConId();

        servicio.recibir(NOTIFICACION, IP);

        assertThat(pagoGuardado().getEstado()).isEqualTo(EstadoPago.MONTO_DISTINTO);
        assertThat(pagoGuardado().getMonto()).isEqualByComparingTo("1.00");
        sinConfirmar();
    }

    @Test
    @DisplayName("HU-13 R34: un aprobado coincidente con la reserva no vigente guarda PAGADO_TARDIO, sin confirmar")
    void tardio() {
        notifica(consultado(ResultadoPasarela.APROBADO, "241.50", "PEN"));
        bloquea(reserva(false));
        guardaConId();

        servicio.recibir(NOTIFICACION, IP);

        assertThat(pagoGuardado().getEstado()).isEqualTo(EstadoPago.PAGADO_TARDIO);
        sinConfirmar();
    }

    @Test
    @DisplayName("HU-13 R39: un rechazado guarda RECHAZADO con el monto consultado, sin confirmar")
    void rechazado() {
        notifica(consultado(ResultadoPasarela.RECHAZADO, "241.50", "PEN"));
        bloquea(reserva(true));
        guardaConId();
        rechazosRecientes(1);

        servicio.recibir(NOTIFICACION, IP);

        assertThat(pagoGuardado().getEstado()).isEqualTo(EstadoPago.RECHAZADO);
        sinConfirmar();
    }

    @Test
    @DisplayName("HU-13 R39: un rechazo sobre una reserva ya CONFIRMADA solo agrega su fila")
    void rechazadoFueraDeOrden() {
        notifica(consultado(ResultadoPasarela.RECHAZADO, "241.50", "PEN"));
        bloquea(reserva(false));
        guardaConId();
        rechazosRecientes(1);

        servicio.recibir(NOTIFICACION, IP);

        assertThat(pagoGuardado().getEstado()).isEqualTo(EstadoPago.RECHAZADO);
        verify(reservas, never()).confirmar(any(), any());
    }

    @Test
    @DisplayName("HU-13 R40: con 2 rechazos no cierra el checkout")
    void dosRechazosNoCierran() {
        notifica(consultado(ResultadoPasarela.RECHAZADO, "241.50", "PEN"));
        bloquea(reserva(true));
        guardaConId();
        rechazosRecientes(2);

        servicio.recibir(NOTIFICACION, IP);
        confirmarTransaccion();

        verifyNoInteractions(sesiones);
    }

    @Test
    @DisplayName("HU-13 R40: con 3 rechazos cierra el checkout solo después del commit")
    void tresRechazosCierranTrasElCommit() {
        notifica(consultado(ResultadoPasarela.RECHAZADO, "241.50", "PEN"));
        bloquea(reserva(true));
        guardaConId();
        rechazosRecientes(3);

        servicio.recibir(NOTIFICACION, IP);
        verifyNoInteractions(sesiones);

        confirmarTransaccion();
        verify(sesiones).cerrar(RESERVA_ID);
    }

    @Test
    @DisplayName("HU-13 R42: un id de transacción ya guardado termina sin escribir, confirmar ni avisar")
    void repetido() {
        notifica(consultado(ResultadoPasarela.APROBADO, "241.50", "PEN"));
        when(reservas.bloquear(RESERVA_ID)).thenReturn(Optional.of(reserva(true)));
        when(pagos.existsByIdTransaccionPasarela(ID_PAGO)).thenReturn(true);

        servicio.recibir(NOTIFICACION, IP);

        verify(pagos, never()).saveAndFlush(any());
        verifyNoInteractions(auditoria, sesiones);
        sinConfirmar();
    }

    @Test
    @DisplayName("HU-13 R44: la violación de uq_pago_id_transaccion se absorbe")
    void violacionDeLaUnicidad() {
        notifica(consultado(ResultadoPasarela.APROBADO, "241.50", "PEN"));
        bloquea(reserva(true));
        when(pagos.saveAndFlush(any())).thenThrow(violacion("uq_pago_id_transaccion"));

        assertThatCode(() -> servicio.recibir(NOTIFICACION, IP)).doesNotThrowAnyException();

        sinConfirmar();
    }

    @Test
    @DisplayName("HU-13 R44: otra violación de integridad se relanza")
    void otraViolacion() {
        notifica(consultado(ResultadoPasarela.APROBADO, "241.50", "PEN"));
        bloquea(reserva(true));
        when(pagos.saveAndFlush(any())).thenThrow(violacion("uq_pago_reserva_pagado"));

        assertThatThrownBy(() -> servicio.recibir(NOTIFICACION, IP))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("HU-13 R52: audita PAGO_APROBADO sin usuario, con la IP y el detalle exacto")
    void auditaAprobado() {
        notifica(consultado(ResultadoPasarela.APROBADO, "241.5", "PEN"));
        bloquea(reserva(true));
        guardaConId();

        servicio.recibir(NOTIFICACION, IP);

        verify(auditoria).registrar(new EventoAuditoria(null, "PAGO_APROBADO", "pago", PAGO_ID, IP,
                "origen=WEBHOOK;reservaId=90;transaccion=555;monto=241.50"));
    }

    @Test
    @DisplayName("HU-13 R52: audita PAGO_RECHAZADO, PAGO_TARDIO y PAGO_MONTO_DISTINTO según el estado")
    void auditaLosDemas() {
        notifica(consultado(ResultadoPasarela.RECHAZADO, "241.50", "PEN"));
        bloquea(reserva(true));
        guardaConId();
        rechazosRecientes(1);
        servicio.recibir(NOTIFICACION, IP);

        when(pasarela.consultarPago(ID_PAGO)).thenReturn(consultado(ResultadoPasarela.APROBADO, "241.50", "PEN"));
        when(reservas.bloquear(RESERVA_ID)).thenReturn(Optional.of(reserva(false)));
        servicio.recibir(NOTIFICACION, IP);

        when(pasarela.consultarPago(ID_PAGO)).thenReturn(consultado(ResultadoPasarela.APROBADO, "1.00", "PEN"));
        servicio.recibir(NOTIFICACION, IP);

        var evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria, times(3)).registrar(evento.capture());
        assertThat(evento.getAllValues()).extracting(EventoAuditoria::accion)
                .containsExactly("PAGO_RECHAZADO", "PAGO_TARDIO", "PAGO_MONTO_DISTINTO");
    }
}
