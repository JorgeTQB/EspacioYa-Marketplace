package pe.edu.ulima.espacioya.pagos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.DemasiadosIntentosException;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.pagos.domain.EstadoPago;
import pe.edu.ulima.espacioya.pagos.dto.SimulacionDTO;
import pe.edu.ulima.espacioya.pagos.dto.SimularPagoDTO;
import pe.edu.ulima.espacioya.pagos.repository.PagoRepository;
import pe.edu.ulima.espacioya.pagos.service.pasarela.NotificacionRecibida;
import pe.edu.ulima.espacioya.pagos.service.pasarela.PasarelaSimulada;
import pe.edu.ulima.espacioya.pagos.service.pasarela.ResultadoPasarela;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservaPorCobrar;

@ExtendWith(MockitoExtension.class)
class SimulacionPagoTest {

    private static final long RESERVA_ID = 90L;
    private static final long USUARIO_ID = 7L;
    private static final String ID = "90";
    private static final String IP = "10.15.0.1";
    private static final BigDecimal MONTO = new BigDecimal("241.50");
    private static final Instant EXPIRA = Instant.parse("2026-10-28T15:15:00Z");
    private static final UsuarioAutenticado USUARIO = new UsuarioAutenticado(USUARIO_ID, 31L, "USUARIO");
    private static final ReservaPorCobrar RESERVA = new ReservaPorCobrar(RESERVA_ID, MONTO, EXPIRA, true, true);

    @Mock
    private EvaluacionCobro evaluacion;
    @Mock
    private SesionesCheckout sesiones;
    @Mock
    private PasarelaSimulada pasarela;
    @Mock
    private WebhookPagoService webhook;
    @Mock
    private PagoRepository pagos;
    @Mock
    private AuditoriaService auditoria;

    private SimulacionPago simulacion;

    @BeforeEach
    void preparar() {
        simulacion = new SimulacionPago(evaluacion, sesiones, pasarela, webhook, pagos, auditoria);
    }

    private static NotificacionRecibida notificacion(String id) {
        return new NotificacionRecibida(Map.of("type", "payment", "data.id", id), Map.of("x-signature", "token"));
    }

    private void listaParaSimular() {
        when(evaluacion.exigirCheckoutNoAgotado(USUARIO, ID)).thenReturn(RESERVA);
        when(sesiones.existe(RESERVA_ID)).thenReturn(true);
    }

    private void sinSimular() {
        verifyNoInteractions(webhook, auditoria);
        verify(pasarela, never()).notificacionDe(any(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("HU-13 R62: orden: comprobaciones, notificación, auditoría y recibir")
    void ordenDelCaminoFeliz() {
        listaParaSimular();
        var notificacion = notificacion("sim-90-APROBADO-1");
        when(pasarela.notificacionDe(RESERVA_ID, MONTO, ResultadoPasarela.APROBADO, 1)).thenReturn(notificacion);

        simulacion.simular(USUARIO, ID, ResultadoPasarela.APROBADO, IP);

        var orden = inOrder(evaluacion, sesiones, pasarela, auditoria, webhook);
        orden.verify(evaluacion).exigirCheckoutNoAgotado(USUARIO, ID);
        orden.verify(sesiones).existe(RESERVA_ID);
        orden.verify(pasarela).notificacionDe(RESERVA_ID, MONTO, ResultadoPasarela.APROBADO, 1);
        orden.verify(auditoria).registrar(any());
        orden.verify(webhook).recibir(notificacion, IP);
        orden.verifyNoMoreInteractions();
    }

    @Test
    @DisplayName("HU-13 R62, R68: un APROBADO siempre es el intento 1, sin consultar rechazos")
    void aprobarEsSiempreElIntentoUno() {
        listaParaSimular();
        when(pasarela.notificacionDe(RESERVA_ID, MONTO, ResultadoPasarela.APROBADO, 1))
                .thenReturn(notificacion("sim-90-APROBADO-1"));

        simulacion.simular(USUARIO, ID, ResultadoPasarela.APROBADO, IP);

        verifyNoInteractions(pagos);
    }

    @Test
    @DisplayName("HU-13 R70: un RECHAZADO usa el número de filas RECHAZADO más 1")
    void rechazarSaleDelConteo() {
        listaParaSimular();
        when(pagos.countByReservaIdAndEstado(RESERVA_ID, EstadoPago.RECHAZADO)).thenReturn(2L);
        when(pasarela.notificacionDe(RESERVA_ID, MONTO, ResultadoPasarela.RECHAZADO, 3))
                .thenReturn(notificacion("sim-90-RECHAZADO-3"));

        simulacion.simular(USUARIO, ID, ResultadoPasarela.RECHAZADO, IP);

        verify(webhook).recibir(any(), eq(IP));
    }

    @Test
    @DisplayName("HU-13 R71: el monto es el de la reserva")
    void elMontoSaleDeLaReserva() {
        listaParaSimular();
        var monto = ArgumentCaptor.forClass(BigDecimal.class);
        when(pasarela.notificacionDe(eq(RESERVA_ID), monto.capture(),
                eq(ResultadoPasarela.APROBADO), eq(1L)))
                .thenReturn(notificacion("sim-90-APROBADO-1"));

        simulacion.simular(USUARIO, ID, ResultadoPasarela.APROBADO, IP);

        assertThat(monto.getValue()).isSameAs(MONTO);
    }

    @Test
    @DisplayName("HU-13 R86: audita PAGO_SIMULADO con el usuario de la sesión, la reserva, la IP y el detalle")
    void auditaQuienDecidio() {
        listaParaSimular();
        when(pagos.countByReservaIdAndEstado(RESERVA_ID, EstadoPago.RECHAZADO)).thenReturn(0L);
        when(pasarela.notificacionDe(RESERVA_ID, MONTO, ResultadoPasarela.RECHAZADO, 1))
                .thenReturn(notificacion("sim-90-RECHAZADO-1"));

        simulacion.simular(USUARIO, ID, ResultadoPasarela.RECHAZADO, IP);

        verify(auditoria).registrar(new EventoAuditoria(USUARIO_ID, "PAGO_SIMULADO", "reserva", RESERVA_ID, IP,
                "resultado=RECHAZADO;transaccion=sim-90-RECHAZADO-1"));
    }

    @Test
    @DisplayName("HU-13 R78: sin fila en checkout_pago responde pago no disponible y no llama a recibir")
    void sinCheckout() {
        when(evaluacion.exigirCheckoutNoAgotado(USUARIO, ID)).thenReturn(RESERVA);
        when(sesiones.existe(RESERVA_ID)).thenReturn(false);

        assertThatThrownBy(() -> simulacion.simular(USUARIO, ID, ResultadoPasarela.APROBADO, IP))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage(EvaluacionCobro.MENSAJE_PAGO_NO_DISPONIBLE);

        sinSimular();
    }

    @Test
    @DisplayName("HU-13 R74: si el rol no vale, no llama a recibir")
    void otroRol() {
        when(evaluacion.exigirCheckoutNoAgotado(USUARIO, ID)).thenThrow(new AccesoDenegadoException());

        assertThatThrownBy(() -> simulacion.simular(USUARIO, ID, ResultadoPasarela.APROBADO, IP))
                .isInstanceOf(AccesoDenegadoException.class);

        sinSimular();
    }

    @Test
    @DisplayName("HU-13 R75: si la reserva no es propia, no llama a recibir")
    void reservaAjena() {
        when(evaluacion.exigirCheckoutNoAgotado(USUARIO, ID)).thenThrow(new RecursoNoEncontradoException());

        assertThatThrownBy(() -> simulacion.simular(USUARIO, ID, ResultadoPasarela.APROBADO, IP))
                .isInstanceOf(RecursoNoEncontradoException.class);

        sinSimular();
    }

    @Test
    @DisplayName("HU-13 R77: con los intentos agotados no llama a recibir")
    void intentosAgotados() {
        when(evaluacion.exigirCheckoutNoAgotado(USUARIO, ID)).thenThrow(new DemasiadosIntentosException());

        assertThatThrownBy(() -> simulacion.simular(USUARIO, ID, ResultadoPasarela.RECHAZADO, IP))
                .isInstanceOf(DemasiadosIntentosException.class);

        sinSimular();
    }

    @Test
    @DisplayName("HU-13 R60: consultar es true con pago disponible y fila en checkout_pago")
    void consultaDisponible() {
        when(evaluacion.resumen(USUARIO, ID)).thenReturn(new EvaluacionCobro.ResumenCobro(RESERVA_ID, false, true));
        when(sesiones.existe(RESERVA_ID)).thenReturn(true);

        assertThat(simulacion.consultar(USUARIO, ID)).isEqualTo(new SimulacionDTO(true));
    }

    @Test
    @DisplayName("HU-13 R60: consultar es false sin fila en checkout_pago")
    void consultaSinCheckout() {
        when(evaluacion.resumen(USUARIO, ID)).thenReturn(new EvaluacionCobro.ResumenCobro(RESERVA_ID, false, true));
        when(sesiones.existe(RESERVA_ID)).thenReturn(false);

        assertThat(simulacion.consultar(USUARIO, ID)).isEqualTo(new SimulacionDTO(false));
    }

    @Test
    @DisplayName("HU-13 R60: consultar es false sin pago disponible")
    void consultaSinPagoDisponible() {
        when(evaluacion.resumen(USUARIO, ID)).thenReturn(new EvaluacionCobro.ResumenCobro(RESERVA_ID, false, false));

        assertThat(simulacion.consultar(USUARIO, ID)).isEqualTo(new SimulacionDTO(false));
    }

    @Test
    @DisplayName("HU-13 R61: consultar no audita, no notifica y no llama a recibir")
    void consultarSoloLee() {
        when(evaluacion.resumen(USUARIO, ID)).thenReturn(new EvaluacionCobro.ResumenCobro(RESERVA_ID, false, true));
        when(sesiones.existe(RESERVA_ID)).thenReturn(true);

        simulacion.consultar(USUARIO, ID);

        sinSimular();
        verifyNoInteractions(pagos);
    }

    @Test
    @DisplayName("HU-13 R74: consultar exige el rol antes de leer la reserva")
    void consultarExigeElRol() {
        doThrow(new AccesoDenegadoException()).when(evaluacion).exigirRol(USUARIO);

        assertThatThrownBy(() -> simulacion.consultar(USUARIO, ID)).isInstanceOf(AccesoDenegadoException.class);

        verify(evaluacion, never()).resumen(any(), any());
    }

    @Test
    @DisplayName("HU-13 R80: los DTO no tienen tarjeta, monto, transacción ni checkout")
    void losDtoSoloTienenSuCampo() {
        assertThat(SimulacionDTO.class.getRecordComponents()).extracting("name").containsExactly("disponible");
        assertThat(SimularPagoDTO.class.getRecordComponents()).extracting("name").containsExactly("resultado");
    }
}
