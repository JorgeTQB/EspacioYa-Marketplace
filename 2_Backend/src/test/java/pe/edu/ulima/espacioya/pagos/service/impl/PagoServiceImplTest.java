package pe.edu.ulima.espacioya.pagos.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.pagos.domain.EstadoPago;
import pe.edu.ulima.espacioya.pagos.domain.Pago;
import pe.edu.ulima.espacioya.pagos.repository.PagoRepository;
import pe.edu.ulima.espacioya.pagos.service.ReembolsoPorCancelacion;
import pe.edu.ulima.espacioya.pagos.service.pasarela.PasarelaPago;

@ExtendWith(MockitoExtension.class)
class PagoServiceImplTest {

    private static final long RESERVA_ID = 7L;
    private static final long PAGO_ID = 31L;
    private static final long USUARIO_ID = 12L;
    private static final String IP = "10.17.0.1";

    @Mock
    private PagoRepository pagos;
    @Mock
    private AuditoriaService auditoria;

    private PagoServiceImpl servicio;

    @BeforeEach
    void preparar() {
        servicio = new PagoServiceImpl(pagos, auditoria);
    }

    private static Pago pagado() {
        var pago = Pago.de(RESERVA_ID, "sim-123", new BigDecimal("161.00"), EstadoPago.PAGADO, "1234",
                Instant.parse("2026-10-28T15:00:00Z"));
        ReflectionTestUtils.setField(pago, "id", PAGO_ID);
        return pago;
    }

    private static ReembolsoPorCancelacion solicitud(String monto) {
        return new ReembolsoPorCancelacion(RESERVA_ID, new BigDecimal(monto), USUARIO_ID, IP);
    }

    @Test
    @DisplayName("HU-15 R24: audita REEMBOLSO_SOLICITADO sobre el pago PAGADO con origen, reserva y monto")
    void auditaLaSolicitud() {
        when(pagos.findByReservaIdAndEstado(RESERVA_ID, EstadoPago.PAGADO)).thenReturn(Optional.of(pagado()));

        servicio.reembolsarCancelacion(solicitud("161.00"));

        verify(auditoria).registrar(new EventoAuditoria(USUARIO_ID, "REEMBOLSO_SOLICITADO", "pago", PAGO_ID, IP,
                "origen=CANCELACION;reservaId=7;monto=161.00"));
    }

    @Test
    @DisplayName("HU-15 R24: no cambia el estado del pago ni lo guarda")
    void noTocaElPago() {
        var pago = pagado();
        when(pagos.findByReservaIdAndEstado(RESERVA_ID, EstadoPago.PAGADO)).thenReturn(Optional.of(pago));

        servicio.reembolsarCancelacion(solicitud("161.00"));

        assertThat(pago.getEstado()).isEqualTo(EstadoPago.PAGADO);
        verify(pagos, never()).save(any());
    }

    @Test
    @DisplayName("HU-15 R24: no depende de PasarelaPago (HU-16 hará la llamada)")
    void sinPasarela() {
        var constructores = PagoServiceImpl.class.getDeclaredConstructors();

        assertThat(constructores).allSatisfy(c -> assertThat(c.getParameterTypes()).doesNotContain(PasarelaPago.class));
        assertThat(Arrays.stream(PagoServiceImpl.class.getDeclaredFields()).map(Field::getType).toList())
                .allSatisfy(tipo -> assertThat(tipo).isNotEqualTo(PasarelaPago.class));
    }

    @Test
    @DisplayName("HU-15 R24: se une a la transacción del llamador (MANDATORY)")
    void exigeTransaccion() throws Exception {
        var metodo = PagoServiceImpl.class.getMethod("reembolsarCancelacion", ReembolsoPorCancelacion.class);

        assertThat(metodo.getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.MANDATORY);
    }

    @Test
    @DisplayName("HU-15 R25: sin pago PAGADO lanza IllegalStateException y no audita")
    void sinPagoPagado() {
        when(pagos.findByReservaIdAndEstado(RESERVA_ID, EstadoPago.PAGADO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.reembolsarCancelacion(solicitud("161.00")))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("HU-15 R24: un monto de 0 lanza IllegalArgumentException y no audita")
    void montoCero() {
        when(pagos.findByReservaIdAndEstado(RESERVA_ID, EstadoPago.PAGADO)).thenReturn(Optional.of(pagado()));

        assertThatThrownBy(() -> servicio.reembolsarCancelacion(solicitud("0.00")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("HU-15 R24: un monto mayor que el pago lanza IllegalArgumentException y no audita")
    void montoMayorQueElPago() {
        when(pagos.findByReservaIdAndEstado(RESERVA_ID, EstadoPago.PAGADO)).thenReturn(Optional.of(pagado()));

        assertThatThrownBy(() -> servicio.reembolsarCancelacion(solicitud("161.01")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(auditoria);
    }
}
