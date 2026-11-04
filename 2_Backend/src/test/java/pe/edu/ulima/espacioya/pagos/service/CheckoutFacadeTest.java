package pe.edu.ulima.espacioya.pagos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.pagos.domain.EstadoPago;
import pe.edu.ulima.espacioya.pagos.domain.Pago;
import pe.edu.ulima.espacioya.pagos.dto.CheckoutDTO;
import pe.edu.ulima.espacioya.pagos.dto.EstadoPagoDTO;
import pe.edu.ulima.espacioya.pagos.repository.PagoRepository;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservaPorCobrar;

@ExtendWith(MockitoExtension.class)
class CheckoutFacadeTest {

    private static final long RESERVA_ID = 90L;
    private static final long USUARIO_ID = 7L;
    private static final Instant AHORA = Instant.parse("2026-10-28T15:00:00Z");
    private static final String IP = "10.15.0.1";
    private static final String URL = "https://pago.invalid/pref-1";
    private static final UsuarioAutenticado USUARIO = new UsuarioAutenticado(USUARIO_ID, 31L, "USUARIO");
    private static final ReservaPorCobrar RESERVA =
            new ReservaPorCobrar(RESERVA_ID, new BigDecimal("241.50"), AHORA.plusSeconds(600), true, true);

    @Mock
    private EvaluacionCobro evaluacion;
    @Mock
    private SesionesCheckout sesiones;
    @Mock
    private PagoRepository pagos;
    @Mock
    private AuditoriaService auditoria;

    private CheckoutFacade facade;

    @BeforeEach
    void preparar() {
        facade = new CheckoutFacade(evaluacion, sesiones, pagos, auditoria);
    }

    @Test
    @DisplayName("HU-13 R1: coordina evaluar, obtener la URL y auditar, en ese orden, y responde el CheckoutDTO")
    void iniciaEnOrden() {
        when(evaluacion.exigirDisponible(USUARIO, "90")).thenReturn(RESERVA);
        when(sesiones.urlPara(RESERVA)).thenReturn(URL);

        assertThat(facade.iniciar(USUARIO, "90", IP)).isEqualTo(new CheckoutDTO(URL));

        var orden = inOrder(evaluacion, sesiones, auditoria);
        orden.verify(evaluacion).exigirDisponible(USUARIO, "90");
        orden.verify(sesiones).urlPara(RESERVA);
        orden.verify(auditoria).registrar(any());
    }

    @Test
    @DisplayName("HU-13 R9: audita CHECKOUT_INICIADO con el usuario, la reserva, la IP y el monto")
    void auditaElInicio() {
        when(evaluacion.exigirDisponible(USUARIO, "90")).thenReturn(RESERVA);
        when(sesiones.urlPara(RESERVA)).thenReturn(URL);

        facade.iniciar(USUARIO, "90", IP);

        verify(auditoria).registrar(new EventoAuditoria(USUARIO_ID, "CHECKOUT_INICIADO", "reserva", RESERVA_ID, IP,
                "monto=241.50"));
    }

    @Test
    @DisplayName("HU-13 R8: iniciar no escribe en pago ni usa nada que cambie la reserva")
    void iniciarNoCambiaNada() {
        when(evaluacion.exigirDisponible(USUARIO, "90")).thenReturn(RESERVA);
        when(sesiones.urlPara(RESERVA)).thenReturn(URL);

        facade.iniciar(USUARIO, "90", IP);

        verifyNoInteractions(pagos);
        verifyNoMoreInteractions(evaluacion);
    }

    @Test
    @DisplayName("HU-13 R10: si la reserva no está disponible no crea checkout ni audita")
    void noDisponible() {
        when(evaluacion.exigirDisponible(USUARIO, "90")).thenThrow(new ReglaNegocioException("no disponible"));

        assertThatThrownBy(() -> facade.iniciar(USUARIO, "90", IP)).isInstanceOf(ReglaNegocioException.class);

        verifyNoInteractions(sesiones, auditoria);
    }

    @Test
    @DisplayName("HU-13 R19: el estado lleva el resultado del último pago, intentos y disponibilidad")
    void estadoConPago() {
        when(evaluacion.resumen(USUARIO, "90")).thenReturn(new EvaluacionCobro.ResumenCobro(RESERVA_ID, false, true));
        when(pagos.findFirstByReservaIdOrderByCreadoEnDescIdDesc(RESERVA_ID)).thenReturn(Optional.of(
                Pago.de(RESERVA_ID, "555", new BigDecimal("241.50"), EstadoPago.RECHAZADO, "1234", AHORA)));

        assertThat(facade.estado(USUARIO, "90")).isEqualTo(new EstadoPagoDTO("RECHAZADO", false, true));
    }

    @Test
    @DisplayName("HU-13 R19: sin pagos, ultimoResultado es null")
    void estadoSinPagos() {
        when(evaluacion.resumen(USUARIO, "90")).thenReturn(new EvaluacionCobro.ResumenCobro(RESERVA_ID, true, false));
        when(pagos.findFirstByReservaIdOrderByCreadoEnDescIdDesc(RESERVA_ID)).thenReturn(Optional.empty());

        assertThat(facade.estado(USUARIO, "90")).isEqualTo(new EstadoPagoDTO(null, true, false));
    }

    @Test
    @DisplayName("HU-13 R21: leer el estado no audita, no abre checkout y no guarda")
    void estadoNoEscribe() {
        when(evaluacion.resumen(USUARIO, "90")).thenReturn(new EvaluacionCobro.ResumenCobro(RESERVA_ID, false, true));
        when(pagos.findFirstByReservaIdOrderByCreadoEnDescIdDesc(RESERVA_ID)).thenReturn(Optional.empty());

        facade.estado(USUARIO, "90");

        verifyNoInteractions(sesiones, auditoria);
        verifyNoMoreInteractions(pagos);
    }

    @Test
    @DisplayName("HU-13 R20: los DTO solo tienen sus campos: sin transacción, últimos 4 ni id del checkout")
    void losDtoNoExponenDatosDelPago() {
        assertThat(CheckoutDTO.class.getRecordComponents()).extracting("name").containsExactly("urlPago");
        assertThat(EstadoPagoDTO.class.getRecordComponents()).extracting("name")
                .containsExactly("ultimoResultado", "intentosAgotados", "pagoDisponible");
    }
}
