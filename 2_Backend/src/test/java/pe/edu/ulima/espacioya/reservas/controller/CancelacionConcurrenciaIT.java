package pe.edu.ulima.espacioya.reservas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Dos confirmaciones de la misma cancelación con hilos reales, cada una en su conexión (docs/verification.md, Nivel 2;
 * HU-15 R33, R34). Usa el notificador simulado del soporte. IP 10.17.200.x.
 */
class CancelacionConcurrenciaIT extends ReservasControllerSoporte {

    private static final int RONDAS = 5;
    private static final int SEGUNDOS_MAXIMOS = 30;
    private static final String YA_CANCELADA = "Esta reserva ya está cancelada.";
    private static final String CUERPO = "{\"montoReembolsoVisto\":161.00}";

    private static final AtomicInteger SIGUIENTE_IP = new AtomicInteger(1);

    @BeforeEach
    void prepararHu15() {
        ip = "10.17.200." + SIGUIENTE_IP.getAndIncrement();
    }

    @AfterEach
    void borrarPagos() {
        jdbc.update("DELETE FROM reembolso");
        jdbc.update("DELETE FROM pago");
    }

    /** CONFIRMADA del usuario a más de 48 h, de 18:00 a 20:00 (S/ 161.00), con su pago PAGADO. */
    private long pagada() {
        var expiraEn = haceMinutos(45);
        var reserva = jdbc.queryForObject("INSERT INTO reserva (usuario_id, espacio_id, estado, precio_hora_aplicado, "
                        + "monto_total, creada_en, expira_en) VALUES (?, ?, 'CONFIRMADA', 80.50, 161.00, ?, ?) "
                        + "RETURNING id", Long.class, usuarioId, espacioId,
                Timestamp.from(expiraEn.minus(Duration.ofMinutes(15))), Timestamp.from(expiraEn));
        filaBloque(reserva, espacioId, fecha, 18, true);
        filaBloque(reserva, espacioId, fecha, 19, true);
        jdbc.update("INSERT INTO pago (reserva_id, id_transaccion_pasarela, monto, estado, creado_en) "
                + "VALUES (?, ?, 161.00, 'PAGADO', ?)", reserva, "sim-hu15-c" + reserva, Timestamp.from(expiraEn));
        return reserva;
    }

    private MvcResult cancelar(String sesion, String ruta) throws Exception {
        return mvc.perform(con(sesion, post(ruta)).contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andReturn();
    }

    private List<MvcResult> aLaVez(List<Callable<MvcResult>> solicitudes) throws Exception {
        var salida = new CountDownLatch(1);
        var respuestas = new ArrayList<MvcResult>();
        try (var hilos = Executors.newFixedThreadPool(solicitudes.size())) {
            var pendientes = solicitudes.stream().map(solicitud -> hilos.submit(() -> {
                salida.await();
                return solicitud.call();
            })).toList();
            salida.countDown();
            for (var pendiente : pendientes) {
                respuestas.add(pendiente.get(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS));
            }
        }
        return respuestas;
    }

    @Test
    @DisplayName("HU-15 R33, R34: usuario y arrendador confirman la misma cancelación a la vez: un 200, un 400 ya "
            + "cancelada, un RESERVA_CANCELADA y un REEMBOLSO_SOLICITADO")
    void dosConfirmacionesALaVez() throws Exception {
        var usuario = token(CORREO_USUARIO);
        var arrendador = token(CORREO_ARRENDADOR);

        for (var ronda = 0; ronda < RONDAS; ronda++) {
            var reserva = pagada();

            var respuestas = aLaVez(List.of(
                    () -> cancelar(usuario, RESERVAS + "/" + reserva + "/cancelacion"),
                    () -> cancelar(arrendador, "/api/arrendadores/actual/reservas/" + reserva + "/cancelacion")));

            assertThat(respuestas.stream().map(respuesta -> respuesta.getResponse().getStatus()))
                    .containsExactlyInAnyOrder(200, 400);
            var rechazo = respuestas.stream().filter(respuesta -> respuesta.getResponse().getStatus() == 400)
                    .findFirst().orElseThrow();
            assertThat(mapa(rechazo.getResponse().getContentAsString()).get("mensaje")).isEqualTo(YA_CANCELADA);
            assertThat(estadoGuardado(reserva)).isEqualTo("CANCELADA");
            assertThat(auditorias("RESERVA_CANCELADA")).hasSize(1);
            assertThat(auditorias("REEMBOLSO_SOLICITADO")).hasSize(1);

            jdbc.update("DELETE FROM reembolso");
            jdbc.update("DELETE FROM pago");
            jdbc.update("DELETE FROM reserva");
            jdbc.update("DELETE FROM auditoria WHERE accion IN ('RESERVA_CANCELADA', 'REEMBOLSO_SOLICITADO')");
        }
    }
}
