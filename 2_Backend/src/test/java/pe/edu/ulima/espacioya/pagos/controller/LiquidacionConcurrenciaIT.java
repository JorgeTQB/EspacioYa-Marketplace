package pe.edu.ulima.espacioya.pagos.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.pagos.dto.MarcarLiquidacionPagadaDTO;
import pe.edu.ulima.espacioya.pagos.service.LiquidacionAdminService;

/** Reglas de concurrencia de HU-19 con hilos reales, cada uno en su conexión (docs/verification.md, Nivel 2). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PagosSoporte.Dobles.class})
class LiquidacionConcurrenciaIT extends LiquidacionesSoporte {

    private static final int RONDAS = 3;
    private static final int SEGUNDOS_MAXIMOS = 30;

    @Autowired
    private LiquidacionAdminService servicio;

    private <T> List<T> aLaVez(List<Callable<T>> tareas) throws Exception {
        var salida = new CountDownLatch(1);
        var resultados = new ArrayList<T>();
        try (var hilos = Executors.newFixedThreadPool(tareas.size())) {
            var pendientes = tareas.stream().map(tarea -> hilos.submit(() -> {
                salida.await();
                return tarea.call();
            })).toList();
            salida.countDown();
            for (var pendiente : pendientes) {
                resultados.add(pendiente.get(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS));
            }
        }
        return resultados;
    }

    private UsuarioAutenticado admin() {
        crearAdmin(CORREO_ADMIN);
        return new UsuarioAutenticado(idAdmin(), null, "ADMIN");
    }

    @Test
    @DisplayName("HU-19 R22: dos generaciones a la vez responden las dos y cada reserva queda en una sola línea")
    void dosGeneracionesALaVez() throws Exception {
        var admin = admin();
        var otro = otroArrendador();
        var espacioDelOtro = espacioDe(otro);
        var pasado = ZonaHoraria.hoy(reloj).minusDays(5);
        var reservas = new ArrayList<Long>();
        for (var hora : List.of(6, 10, 14)) {
            reservas.add(usadaSembrada(espacioId, pasado, hora, transaccionSimulada()));
            reservas.add(usadaSembrada(espacioDelOtro, pasado, hora, transaccionSimulada()));
        }

        for (var ronda = 0; ronda < RONDAS; ronda++) {
            jdbc.update("DELETE FROM liquidacion_detalle");
            jdbc.update("DELETE FROM liquidacion");

            var respuestas = aLaVez(List.<Callable<Integer>>of(
                    () -> servicio.generar(admin, IP_ADMIN).liquidaciones().size(),
                    () -> servicio.generar(admin, IP_ADMIN).liquidaciones().size()));

            assertThat(respuestas.stream().mapToInt(Integer::intValue).sum()).isEqualTo(2);
            assertThat(jdbc.queryForList("SELECT reserva_id FROM liquidacion_detalle ORDER BY reserva_id", Long.class))
                    .containsExactlyElementsOf(reservas.stream().sorted().toList());
            assertThat(filas("liquidacion")).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("HU-19 R32: dos marcados a la vez de la misma liquidación: uno gana y el otro recibe ya pagada, y "
            + "queda la operación del ganador")
    void dosMarcadosALaVez() throws Exception {
        var admin = admin();
        cuentaDe(arrendadorId, NUMERO_CUENTA, null);

        for (var ronda = 0; ronda < RONDAS; ronda++) {
            var id = liquidacionSembrada(arrendadorId, "GENERADA", "241.50", reloj.instant(), admin.usuarioId());
            var n = ronda;

            var resultados = aLaVez(List.<Callable<String>>of(
                    () -> marcar(admin, id, "OP-A-" + n),
                    () -> marcar(admin, id, "OP-B-" + n)));

            assertThat(resultados).filteredOn(r -> r.startsWith("OP-")).hasSize(1);
            assertThat(resultados).filteredOn(LiquidacionAdminService.MENSAJE_YA_PAGADA::equals).hasSize(1);
            var ganador = resultados.stream().filter(r -> r.startsWith("OP-")).findFirst().orElseThrow();
            assertThat(liquidacion(id).get("numero_operacion")).isEqualTo(ganador);
            assertThat(liquidacion(id).get("estado")).isEqualTo("PAGADA");
        }
        assertThat(auditorias(LiquidacionAdminService.ACCION_PAGADA)).hasSize(RONDAS);
    }

    /** La operación con la que quedó pagada si ganó, o el mensaje del rechazo. */
    private String marcar(UsuarioAutenticado admin, long id, String operacion) {
        try {
            return servicio.marcarPagada(admin, String.valueOf(id), new MarcarLiquidacionPagadaDTO(operacion),
                    IP_ADMIN).pago().numeroOperacion();
        } catch (ReglaNegocioException rechazo) {
            return rechazo.getMessage();
        }
    }
}
