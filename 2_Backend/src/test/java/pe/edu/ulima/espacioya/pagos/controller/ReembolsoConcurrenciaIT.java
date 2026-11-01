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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
}
