package pe.edu.ulima.espacioya.pagos.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.espacios.service.DisponibilidadService;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservasPorCobrar;
import pe.edu.ulima.espacioya.reservas.service.ExpiracionReservas;

/** Reglas de concurrencia con hilos reales, cada uno en su conexión (docs/verification.md, Nivel 2). */
class PagoConcurrenciaIT extends PagosConMercadoPagoSoporte {

    private static final int RONDAS = 5;
    private static final int SEGUNDOS_MAXIMOS = 30;
    private static final int NOTIFICACIONES_IGUALES = 5;

    @Autowired
    private ExpiracionReservas expiracion;
    @Autowired
    private DisponibilidadService disponibilidad;
    @Autowired
    private ReservasPorCobrar reservas;
    @Autowired
    private TransactionTemplate transacciones;

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

    private static List<Integer> estados(List<MvcResult> respuestas) {
        return respuestas.stream().map(respuesta -> respuesta.getResponse().getStatus()).toList();
    }

    private void borrarReservas() {
        jdbc.update("DELETE FROM reembolso");
        jdbc.update("DELETE FROM pago");
        jdbc.update("DELETE FROM reserva");
    }

    @Test
    @DisplayName("HU-13 R7: dos Pagar a la vez dejan una fila en checkout_pago y responden la misma URL")
    void dosPagarALaVez() throws Exception {
        var sesion = token(CORREO_USUARIO);

        for (var ronda = 0; ronda < RONDAS; ronda++) {
            var reserva = vigente();

            var respuestas = aLaVez(List.of(
                    () -> pagar(sesion, reserva).andReturn(),
                    () -> pagar(sesion, reserva).andReturn()));

            assertThat(estados(respuestas)).containsExactly(200, 200);
            assertThat(respuestas.get(0).getResponse().getContentAsString())
                    .isEqualTo(respuestas.get(1).getResponse().getContentAsString());
            assertThat(filas("checkout_pago")).isEqualTo(1);
            borrarReservas();
        }
    }

    @Test
    @DisplayName("HU-13 R43: dos notificaciones iguales a la vez dejan una fila y un correo de cada tipo")
    void dosWebhooksIguales() throws Exception {
        for (var ronda = 0; ronda < RONDAS; ronda++) {
            notificador.reiniciar();
            var reserva = vigente();
            var id = pagoEnLaPasarela("approved", reserva);

            var respuestas = aLaVez(List.of(
                    () -> notificar(id).andReturn(),
                    () -> notificar(id).andReturn()));

            assertThat(estados(respuestas)).containsExactly(200, 200);
            assertThat(pagosDe(reserva)).hasSize(1);
            assertThat(estadoDeReserva(reserva)).isEqualTo("CONFIRMADA");
            assertThat(notificador.comprobantes()).hasSize(1);
            assertThat(notificador.avisos()).hasSize(1);
            borrarReservas();
        }
    }

    @Test
    @DisplayName("HU-13 R43 (AC003): cinco notificaciones iguales a la vez dejan una fila, una confirmación y un correo "
            + "de cada tipo")
    void cincoWebhooksIguales() throws Exception {
        for (var ronda = 0; ronda < RONDAS; ronda++) {
            notificador.reiniciar();
            var reserva = vigente();
            var id = pagoEnLaPasarela("approved", reserva);
            var solicitudes = new ArrayList<Callable<MvcResult>>();
            for (var vez = 0; vez < NOTIFICACIONES_IGUALES; vez++) {
                solicitudes.add(() -> notificar(id).andReturn());
            }

            var respuestas = aLaVez(solicitudes);

            assertThat(estados(respuestas)).containsOnly(200).hasSize(NOTIFICACIONES_IGUALES);
            assertThat(filasConTransaccion(id)).isEqualTo(1);
            assertThat(pagosDe(reserva)).hasSize(1);
            assertThat(estadoDeReserva(reserva)).isEqualTo("CONFIRMADA");
            assertThat(confirmacionesDe(reserva)).isEqualTo(1);
            assertThat(notificador.comprobantes()).hasSize(1);
            assertThat(notificador.avisos()).hasSize(1);
            borrarReservas();
        }
    }

    private int filasConTransaccion(String idTransaccion) {
        return jdbc.queryForObject("SELECT count(*) FROM pago WHERE id_transaccion_pasarela = ?", Integer.class,
                idTransaccion);
    }

    // La limpieza entre rondas no borra la auditoría: se cuenta solo la de esta reserva.
    private int confirmacionesDe(long reserva) {
        return jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE accion = 'RESERVA_CONFIRMADA' "
                + "AND entidad = 'reserva' AND entidad_id = ?", Integer.class, reserva);
    }

    @Test
    @DisplayName("HU-13 R37: un webhook frente a la expiración deja CONFIRMADA con PAGADO o EXPIRADA con PAGADO_TARDIO")
    void webhookFrenteAExpiracion() throws Exception {
        for (var ronda = 0; ronda < RONDAS * 3; ronda++) {
            var reserva = reserva(usuarioId, "PENDIENTE_PAGO", reloj.instant().plusSeconds(60));
            var id = pagoEnLaPasarela("approved", reserva);
            // La espera varía por ronda: el webhook hace una llamada HTTP antes de bloquear y, sin ella, siempre
            // llegaría tarde y la carrera nunca probaría la salida CONFIRMADA.
            var espera = (ronda % 6) * 8L;

            aLaVez(List.of(
                    () -> notificar(id).andReturn(),
                    () -> {
                        Thread.sleep(espera);
                        reloj.avanzar(Duration.ofSeconds(61));
                        expiracion.expirarVencidas();
                        return null;
                    }));

            var resultado = estadoDeReserva(reserva) + "+" + pagosDe(reserva).getFirst().get("estado");
            assertThat(resultado).isIn("CONFIRMADA+PAGADO", "EXPIRADA+PAGADO_TARDIO");
            assertThat(pagosDe(reserva)).hasSize(1);
            borrarReservas();
        }
    }

    @Test
    @DisplayName("HU-13 R37: si el webhook ya tiene la fila, la expiración espera y no toca la reserva CONFIRMADA")
    void laExpiracionEsperaAlWebhook() throws Exception {
        var reserva = reserva(usuarioId, "PENDIENTE_PAGO", reloj.instant().plusSeconds(60));
        Callable<Integer> proceso = expiracion::expirarVencidas;

        try (var hilos = Executors.newSingleThreadExecutor()) {
            var expiradas = transacciones.execute(estado -> {
                var bloqueada = reservas.bloquear(reserva).orElseThrow();
                assertThat(bloqueada.vigente()).isTrue();
                reloj.avanzar(Duration.ofSeconds(61));
                var enCurso = hilos.submit(proceso);
                esperarUnBloqueo();
                reservas.confirmar(reserva, bloqueada.evaluadaEn());
                return enCurso;
            });
            assertThat(expiradas.get(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS)).isZero();
        }

        assertThat(estadoDeReserva(reserva)).isEqualTo("CONFIRMADA");
        assertThat(bloquesActivosDe(reserva)).isEqualTo(3);
        assertThat(auditorias("RESERVA_EXPIRADA")).isEmpty();
    }

    @Test
    @DisplayName("HU-13 R36: el webhook evalúa la vigencia después de esperar el bloqueo del espacio, no antes")
    void evaluaLaVigenciaTrasElBloqueo() throws Exception {
        var reserva = reserva(usuarioId, "PENDIENTE_PAGO", reloj.instant().plusSeconds(60));
        var id = pagoEnLaPasarela("approved", reserva);

        try (var hilos = Executors.newSingleThreadExecutor()) {
            var notificacion = transacciones.execute(estado -> {
                disponibilidad.bloquearEspacio(espacioId);
                var enCurso = hilos.submit(() -> notificar(id).andReturn());
                esperarUnBloqueo();
                reloj.avanzar(Duration.ofSeconds(61));
                return enCurso;
            });
            assertThat(notificacion.get(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
        }

        assertThat(pagosDe(reserva).getFirst().get("estado")).isEqualTo("PAGADO_TARDIO");
        assertThat(estadoDeReserva(reserva)).isEqualTo("PENDIENTE_PAGO");
    }

    // Hasta que otra conexión esté de verdad esperando un bloqueo de fila: así el orden no depende de esperas fijas.
    private void esperarUnBloqueo() {
        var limite = Instant.now().plusSeconds(SEGUNDOS_MAXIMOS);
        while (jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock'",
                Integer.class) == 0) {
            assertThat(Instant.now()).isBefore(limite);
            Thread.onSpinWait();
        }
    }

    @Test
    @DisplayName("HU-13 R38: bloquearEspacio, bloquear y confirmar sin transacción lanzan la excepción de Spring")
    void exigenUnaTransaccion() {
        var reserva = vigente();

        assertThatThrownBy(() -> disponibilidad.bloquearEspacio(espacioId))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> reservas.bloquear(reserva)).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> reservas.confirmar(reserva, reloj.instant()))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(estadoDeReserva(reserva)).isEqualTo("PENDIENTE_PAGO");
    }
}
