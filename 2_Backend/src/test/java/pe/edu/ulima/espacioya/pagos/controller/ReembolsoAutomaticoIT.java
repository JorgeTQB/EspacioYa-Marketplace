package pe.edu.ulima.espacioya.pagos.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;
import pe.edu.ulima.espacioya.pagos.service.PagoService;
import pe.edu.ulima.espacioya.pagos.service.ReembolsoPorCancelacion;
import pe.edu.ulima.espacioya.pagos.service.pasarela.PasarelaPago;
import pe.edu.ulima.espacioya.pagos.service.pasarela.SolicitudReembolsoPasarela;

/**
 * CA1 de HU-16 de punta a punta, contra PostgreSQL real y la pasarela simulada sin fallos: pagar y cancelar por la API
 * deja el reembolso y el pago REEMBOLSADOS. La pasarela va espiada para ver qué se le pidió y cuándo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PagosSoporte.Dobles.class})
class ReembolsoAutomaticoIT extends ReembolsosSoporte {

    @MockitoSpyBean
    private PasarelaPago pasarela;
    @Autowired
    private PagoService pagoService;
    @Autowired
    private TransactionTemplate transacciones;

    @Test
    @DisplayName("HU-16 R1, R5, R10, R11, R14: cancelar una reserva pagada con >= 48 h deja el reembolso y el pago "
            + "REEMBOLSADOS en un intento")
    void cancelarReembolsa() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var reserva = pagadaPorLaApi(sesion);
        var pago = pagoDe(reserva);
        var ahora = reloj.instant();

        cancelar(sesion, reserva, MONTO_PAGADO).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"))
                .andExpect(jsonPath("$.montoReembolso").value(241.5));

        var reembolsos = reembolsosDe(reserva);
        assertThat(reembolsos).hasSize(1);
        var fila = reembolsos.getFirst();
        var id = numero(fila.get("id"));
        assertThat(fila.get("pago_id")).isEqualTo(pago.get("id"));
        assertThat(decimal(fila.get("monto"))).isEqualByComparingTo(MONTO_PAGADO);
        assertThat(fila.get("origen")).isEqualTo("CANCELACION");
        assertThat(fila.get("estado")).isEqualTo("REEMBOLSADO");
        assertThat(fila.get("intentos")).isEqualTo(1);
        assertThat(instante(fila.get("creado_en"))).isEqualTo(ahora);
        assertThat(instante(fila.get("ultimo_intento_en"))).isEqualTo(ahora);
        assertThat(fila.get("id_reembolso_pasarela")).isEqualTo("sim-reembolso-espacioya-reembolso-" + id);
        assertThat(pagoDe(reserva).get("estado")).isEqualTo("REEMBOLSADO");
    }

    @Test
    @DisplayName("HU-16 R2, R36, R38: audita REEMBOLSO_SOLICITADO y REEMBOLSO_REALIZADO con quien canceló, su IP, la "
            + "fecha del reloj y el detalle")
    void auditaLaSolicitudYElResultado() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var reserva = pagadaPorLaApi(sesion);
        var pago = numero(pagoDe(reserva).get("id"));
        var ahora = reloj.instant();

        cancelar(sesion, reserva, MONTO_PAGADO).andExpect(status().isOk());

        var solicitados = auditorias("REEMBOLSO_SOLICITADO");
        assertThat(solicitados).hasSize(1);
        assertThat(solicitados.getFirst().get("detalle"))
                .isEqualTo("origen=CANCELACION;reservaId=" + reserva + ";monto=" + MONTO_PAGADO);
        var id = numero(reembolsosDe(reserva).getFirst().get("id"));
        var realizados = auditorias("REEMBOLSO_REALIZADO");
        assertThat(realizados).hasSize(1);
        var registro = realizados.getFirst();
        assertThat(registro.get("usuario_id")).isEqualTo(usuarioId);
        assertThat(registro.get("ip")).isEqualTo(ip);
        assertThat(registro.get("entidad")).isEqualTo("reembolso");
        assertThat(numero(registro.get("entidad_id"))).isEqualTo(id);
        assertThat(instante(registro.get("fecha"))).isEqualTo(ahora);
        assertThat(registro.get("detalle")).isEqualTo("pagoId=" + pago + ";reservaId=" + reserva
                + ";intento=1;disparo=AUTOMATICO;monto=241.50;idReembolso=sim-reembolso-espacioya-reembolso-" + id);
        assertThat(auditorias("REEMBOLSO_FALLIDO")).isEmpty();
    }

    @Test
    @DisplayName("HU-16 R8, R9: pide a la pasarela la transacción del pago original, su monto y la clave del reembolso")
    void reembolsaContraElPagoOriginal() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var reserva = pagadaPorLaApi(sesion);
        var transaccion = pagoDe(reserva).get("id_transaccion_pasarela");

        cancelar(sesion, reserva, MONTO_PAGADO).andExpect(status().isOk());

        var pedido = ArgumentCaptor.forClass(SolicitudReembolsoPasarela.class);
        verify(pasarela).reembolsar(pedido.capture());
        var id = numero(reembolsosDe(reserva).getFirst().get("id"));
        assertThat(pedido.getValue().idTransaccion()).isEqualTo(transaccion).startsWith("sim-" + reserva);
        assertThat(pedido.getValue().monto()).isEqualByComparingTo(MONTO_PAGADO);
        assertThat(pedido.getValue().claveIdempotencia()).isEqualTo("espacioya-reembolso-" + id);
    }

    @Test
    @DisplayName("HU-16 R3, R4: una cancelación rechazada (monto visto distinto) no crea reembolso ni llama a la "
            + "pasarela")
    void cancelacionRechazadaNoReembolsa() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var reserva = pagadaPorLaApi(sesion);

        cancelar(sesion, reserva, "1.00").andExpect(status().isBadRequest());

        assertThat(filas("reembolso")).isZero();
        assertThat(pagoDe(reserva).get("estado")).isEqualTo("PAGADO");
        verify(pasarela, never()).reembolsar(any());
    }

    @Test
    @DisplayName("HU-16 R3, R4: si la transacción del llamador se revierte, no queda reembolso ni se llama a la "
            + "pasarela; dentro de ella tampoco se llamó")
    void rollbackDelLlamador() throws Exception {
        var reserva = pagadaPorLaApi(token(CORREO_USUARIO));

        transacciones.executeWithoutResult(estado -> {
            pagoService.reembolsarCancelacion(new ReembolsoPorCancelacion(reserva, new BigDecimal(MONTO_PAGADO),
                    usuarioId, ip));
            verify(pasarela, never()).reembolsar(any());
            estado.setRollbackOnly();
        });

        assertThat(filas("reembolso")).isZero();
        assertThat(auditorias("REEMBOLSO_SOLICITADO")).isEmpty();
        assertThat(pagoDe(reserva).get("estado")).isEqualTo("PAGADO");
        verify(pasarela, never()).reembolsar(any());
    }

    @Test
    @DisplayName("HU-16 R3, R5: dentro de la transacción del llamador no se llama a la pasarela; al confirmarse, sí")
    void soloTrasElCommit() throws Exception {
        var reserva = pagadaPorLaApi(token(CORREO_USUARIO));

        transacciones.executeWithoutResult(estado -> {
            pagoService.reembolsarCancelacion(new ReembolsoPorCancelacion(reserva, new BigDecimal(MONTO_PAGADO),
                    usuarioId, ip));
            verify(pasarela, never()).reembolsar(any());
        });

        verify(pasarela).reembolsar(any());
        assertThat(reembolsosDe(reserva).getFirst().get("estado")).isEqualTo("REEMBOLSADO");
    }

    @Test
    @DisplayName("HU-16 R1: con 0 % (menos de 48 h) la cancelación no crea reembolso ni llama a la pasarela")
    void ceroPorCientoSinReembolso() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var reserva = vigenteEl(ZonaHoraria.hoy(reloj).plusDays(1), 21);
        pagarPorLaApi(sesion, reserva);

        cancelar(sesion, reserva, "0.00").andExpect(status().isOk()).andExpect(jsonPath("$.porcentaje").value(0));

        assertThat(filas("reembolso")).isZero();
        assertThat(pagoDe(reserva).get("estado")).isEqualTo("PAGADO");
        verify(pasarela, never()).reembolsar(any());
    }
}
