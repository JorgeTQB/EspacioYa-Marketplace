package pe.edu.ulima.espacioya.pagos.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;

import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.pagos.service.ReembolsoAdminService;
import pe.edu.ulima.espacioya.pagos.service.pasarela.PasarelaPago;

/** Reintentos a la vez del mismo reembolso, con hilos reales y cada uno en su conexión (docs/verification.md, Nivel 2). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PagosSoporte.Dobles.class})
class ReembolsoConcurrenciaIT extends ReembolsosSoporte {

    private static final int RONDAS = 5;
    private static final int SEGUNDOS_MAXIMOS = 30;
    private static final int DIEZ = 10;
    private static final Logger LOG = LoggerFactory.getLogger(ReembolsoConcurrenciaIT.class);

    @MockitoSpyBean
    private PasarelaPago pasarela;

    private List<MvcResult> aLaVez(List<Callable<MvcResult>> solicitudes, CountDownLatch alResponder)
            throws Exception {
        var salida = new CountDownLatch(1);
        var respuestas = new ArrayList<MvcResult>();
        try (var hilos = Executors.newFixedThreadPool(solicitudes.size())) {
            var pendientes = solicitudes.stream().map(solicitud -> hilos.submit(() -> {
                salida.await();
                try {
                    return solicitud.call();
                } finally {
                    alResponder.countDown();
                }
            })).toList();
            salida.countDown();
            for (var pendiente : pendientes) {
                respuestas.add(pendiente.get(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS));
            }
        }
        return respuestas;
    }

    @Test
    @DisplayName("HU-16 R22, R23: dos reintentos a la vez de un FALLIDO llaman una vez a la pasarela, responden 200 y "
            + "400 en proceso, y suben intentos en 1")
    void dosReintentosALaVez() throws Exception {
        var admin = tokenAdmin();

        for (var ronda = 0; ronda < RONDAS; ronda++) {
            clearInvocations(pasarela);
            var id = fallidoSembrado();
            var unaRespuesta = new CountDownLatch(1);
            // Quien llega a la pasarela espera a que el otro ya haya respondido: así el otro lo ve en proceso y no ya
            // reembolsado, sea cual sea el orden en que los hilos toman el bloqueo.
            doAnswer(invocacion -> {
                unaRespuesta.await(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS);
                return invocacion.callRealMethod();
            }).when(pasarela).reembolsar(any());

            var respuestas = aLaVez(List.of(
                    () -> mvc.perform(comoAdmin(admin, post(rutaReintento(id)))).andReturn(),
                    () -> mvc.perform(comoAdmin(admin, post(rutaReintento(id)))).andReturn()), unaRespuesta);

            var estados = respuestas.stream().map(respuesta -> respuesta.getResponse().getStatus()).toList();
            assertThat(estados).containsExactlyInAnyOrder(200, 400);
            var rechazada = respuestas.get(estados.indexOf(400)).getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(mapa(rechazada).get("mensaje")).isEqualTo(ReembolsoAdminService.MENSAJE_EN_PROCESO);
            verify(pasarela, times(1)).reembolsar(any());
            assertThat(reembolso(id).get("intentos")).isEqualTo(2);
            assertThat(reembolso(id).get("estado")).isEqualTo("REEMBOLSADO");
            assertThat(auditorias("REEMBOLSO_REALIZADO")).hasSize(ronda + 1);
        }
    }

    @Test
    @DisplayName("HU-16 R22, R23 (AC005): diez reintentos a la vez de un FALLIDO llaman una vez a la pasarela, responden "
            + "un 200 y nueve 400, y dejan un solo reembolso")
    void diezReintentosALaVez() throws Exception {
        var admin = tokenAdmin();

        for (var ronda = 0; ronda < RONDAS; ronda++) {
            clearInvocations(pasarela);
            var id = fallidoSembrado();
            var sembrado = reembolso(id);
            var auditoriasAntes = totalAuditorias();
            var unaRespuesta = new CountDownLatch(1);
            // Igual que con dos: quien llega a la pasarela espera la primera respuesta, que por eso es "en proceso". Las
            // que responden después de que el ganador termina pueden ver el reembolso ya hecho.
            doAnswer(invocacion -> {
                unaRespuesta.await(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS);
                return invocacion.callRealMethod();
            }).when(pasarela).reembolsar(any());

            var solicitudes = new ArrayList<Callable<MvcResult>>();
            for (var i = 0; i < DIEZ; i++) {
                solicitudes.add(() -> mvc.perform(comoAdmin(admin, post(rutaReintento(id)))).andReturn());
            }
            var respuestas = aLaVez(solicitudes, unaRespuesta);

            verify(pasarela, times(1)).reembolsar(any());
            var estados = respuestas.stream().map(respuesta -> respuesta.getResponse().getStatus()).toList();
            assertThat(estados).filteredOn(estado -> estado == 200).hasSize(1);
            assertThat(estados).filteredOn(estado -> estado == 400).hasSize(DIEZ - 1);
            var mensajes = mensajesDeLosRechazos(respuestas);
            assertThat(mensajes).hasSize(DIEZ - 1).allMatch(mensaje -> ReembolsoAdminService.MENSAJE_EN_PROCESO
                    .equals(mensaje) || ReembolsoAdminService.MENSAJE_YA_REEMBOLSADO.equals(mensaje));
            assertThat(mensajes).contains(ReembolsoAdminService.MENSAJE_EN_PROCESO);
            // Evidencia del escenario AC005: cuántos rechazos de cada tipo dio el orden de la ronda.
            LOG.info("AC005 ronda {}: en proceso = {}, ya reembolsado = {}", ronda + 1,
                    mensajes.stream().filter(ReembolsoAdminService.MENSAJE_EN_PROCESO::equals).count(),
                    mensajes.stream().filter(ReembolsoAdminService.MENSAJE_YA_REEMBOLSADO::equals).count());
            assertThat(reembolsosDelPagoYOrigen(sembrado)).isEqualTo(1);
            assertThat(reembolso(id).get("intentos")).isEqualTo(2);
            assertThat(reembolso(id).get("estado")).isEqualTo("REEMBOLSADO");
            assertThat(auditorias("REEMBOLSO_REALIZADO")).hasSize(ronda + 1);
            assertThat(totalAuditorias()).isEqualTo(auditoriasAntes + 1);
        }
    }

    private List<String> mensajesDeLosRechazos(List<MvcResult> respuestas) throws Exception {
        var mensajes = new ArrayList<String>();
        for (var respuesta : respuestas) {
            if (respuesta.getResponse().getStatus() == 400) {
                var cuerpo = respuesta.getResponse().getContentAsString(StandardCharsets.UTF_8);
                mensajes.add(String.valueOf(mapa(cuerpo).get("mensaje")));
            }
        }
        return mensajes;
    }

    private long reembolsosDelPagoYOrigen(Map<String, Object> sembrado) {
        return jdbc.queryForObject("SELECT count(*) FROM reembolso WHERE pago_id = ? AND origen = ?", Long.class,
                numero(sembrado.get("pago_id")), sembrado.get("origen"));
    }

    private long totalAuditorias() {
        return jdbc.queryForObject("SELECT count(*) FROM auditoria", Long.class);
    }
}
