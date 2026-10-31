package pe.edu.ulima.espacioya.pagos.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.ResultActions;

import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.cuentas.service.AvisoReservaPagada;
import pe.edu.ulima.espacioya.cuentas.service.ComprobantePago;
import pe.edu.ulima.espacioya.espacios.service.puertos.BloqueReservado;
import pe.edu.ulima.espacioya.espacios.service.puertos.ReservasDelEspacio;
import pe.edu.ulima.espacioya.pagos.service.WebhookPagoService;
import pe.edu.ulima.espacioya.pagos.service.pasarela.NotificacionRecibida;
import pe.edu.ulima.espacioya.pagos.service.pasarela.PasarelaPago;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservasPorCobrar;

/**
 * Pantalla de pago simulado contra PostgreSQL real (HU-13 D9): comprueba que el pago simulado pasa por el camino real.
 * {@link WebhookPagoService} va espiado para demostrar que es el único que decide, y que no se le llama cuando no toca.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PagosSoporte.Dobles.class})
class PagoSimuladoIT extends PagosSoporte {

    private static final int RONDAS = 5;
    private static final int SEGUNDOS_MAXIMOS = 30;
    private static final String APROBADO = "{\"resultado\":\"APROBADO\"}";
    private static final String RECHAZADO = "{\"resultado\":\"RECHAZADO\"}";

    @MockitoSpyBean
    private WebhookPagoService webhook;
    @MockitoSpyBean
    private PasarelaPago pasarela;
    @MockitoSpyBean
    private ReservasPorCobrar puerto;
    @Autowired
    private ReservasDelEspacio reservasDelEspacio;

    private static String ruta(Object reserva) {
        return "/api/reservas/" + reserva + "/pago/simulacion";
    }

    private ResultActions simular(String sesion, Object reserva, String cuerpo) throws Exception {
        return mvc.perform(con(sesion, post(ruta(reserva))).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ResultActions consultarSimulacion(String sesion, Object reserva) throws Exception {
        return mvc.perform(con(sesion, get(ruta(reserva))));
    }

    /** Una reserva vigente con su checkout abierto, como tras pulsar Pagar. */
    private long conCheckout(String sesion) throws Exception {
        var reserva = vigente();
        pagar(sesion, reserva).andExpect(status().isOk());
        return reserva;
    }

    private void filaCheckout(long reserva) {
        jdbc.update("INSERT INTO checkout_pago (reserva_id, id_checkout, url_pago, creado_en) VALUES (?, ?, ?, now())",
                reserva, "sim-checkout-" + reserva, "http://localhost:5173/reservas/" + reserva + "/pago-simulado");
    }

    private void rechazos(long reserva, int cantidad) {
        for (var n = 0; n < cantidad; n++) {
            filaPago(reserva, "rechazo-" + reserva + "-" + n, "RECHAZADO", haceMinutos(1));
        }
    }

    private void sinLlamarAlWebhook() {
        verify(webhook, never()).recibir(any(), any());
    }

    private void sinCorreos() {
        assertThat(notificador.comprobantes()).isEmpty();
        assertThat(notificador.avisos()).isEmpty();
    }

    private void disponible(String sesion, long reserva, boolean esperado) throws Exception {
        consultarSimulacion(sesion, reserva)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(esperado));
    }

    // ── La pantalla ──────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-13 R59: Pagar lleva a la pantalla simulada y un segundo Pagar responde la misma URL")
    void pagarLlevaALaPantalla() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var reserva = vigente();
        var url = "http://localhost:5173/reservas/" + reserva + "/pago-simulado";

        pagar(sesion, reserva).andExpect(jsonPath("$.urlPago").value(url));
        pagar(sesion, reserva).andExpect(jsonPath("$.urlPago").value(url));

        assertThat(filas("checkout_pago")).isEqualTo(1);
    }

    @Test
    @DisplayName("HU-13 R60, R80: con pago disponible y checkout responde true, con exactamente su clave")
    void disponibleConCheckout() throws Exception {
        var sesion = token(CORREO_USUARIO);

        var respuesta = consultarSimulacion(sesion, conCheckout(sesion)).andExpect(status().isOk());

        assertThat(mapa(texto(respuesta))).containsOnlyKeys("disponible").containsEntry("disponible", true);
    }

    @Test
    @DisplayName("HU-13 R60: sin fila en checkout_pago responde false")
    void noDisponibleSinCheckout() throws Exception {
        disponible(token(CORREO_USUARIO), vigente(), false);
    }

    @Test
    @DisplayName("HU-13 R60: una reserva vencida, en el último tramo o confirmada responde false")
    void noDisponibleSinPagoDisponible() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var vencimientos = List.of(haceMinutos(1), reloj.instant().plusSeconds(30));
        for (var expiraEn : vencimientos) {
            var reserva = reserva(usuarioId, "PENDIENTE_PAGO", expiraEn);
            filaCheckout(reserva);
            disponible(sesion, reserva, false);
            jdbc.update("DELETE FROM reserva");
        }

        var confirmada = reserva(usuarioId, "CONFIRMADA", dentroDe(10));
        filaCheckout(confirmada);
        disponible(sesion, confirmada, false);
    }

    @Test
    @DisplayName("HU-13 R60: con el espacio pausado o 3 rechazos responde false")
    void noDisponibleConEspacioPausadoOTresRechazos() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var conRechazos = conCheckout(sesion);
        rechazos(conRechazos, 3);
        disponible(sesion, conRechazos, false);
        jdbc.update("DELETE FROM pago");
        jdbc.update("DELETE FROM reserva");

        var pausada = conCheckout(sesion);
        jdbc.update("UPDATE espacio SET estado = 'PAUSADO' WHERE id = ?", espacioId);
        disponible(sesion, pausada, false);
    }

    @Test
    @DisplayName("HU-13 R61: consultar no escribe en la base ni llama al webhook")
    void consultarNoEscribe() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var reserva = conCheckout(sesion);
        var auditoriasAntes = filas("auditoria");

        consultarSimulacion(sesion, reserva).andExpect(status().isOk());

        assertThat(filas("auditoria")).isEqualTo(auditoriasAntes);
        assertThat(filas("pago")).isZero();
        assertThat(filas("checkout_pago")).isEqualTo(1);
        assertThat(estadoDeReserva(reserva)).isEqualTo("PENDIENTE_PAGO");
        sinLlamarAlWebhook();
    }

    // ── El camino real ───────────────────────────────────────────────────

    @Test
    @DisplayName("HU-13 R62: responde 204 sin cuerpo tras una sola llamada a recibir, con el id simulado")
    void pasaPorElWebhook() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var reserva = conCheckout(sesion);

        simular(sesion, reserva, APROBADO).andExpect(status().isNoContent()).andExpect(content().string(""));

        var notificacion = ArgumentCaptor.forClass(NotificacionRecibida.class);
        verify(webhook, times(1)).recibir(notificacion.capture(), any());
        assertThat(notificacion.getValue().parametros())
                .containsEntry("type", "payment")
                .containsEntry("data.id", "sim-" + reserva + "-APROBADO-1");
    }

    @Test
    @DisplayName("HU-13 R63: un APROBADO de una reserva vigente la confirma, con su fila PAGADO y su bloque pagado")
    void aprobadoConfirma() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var reserva = conCheckout(sesion);

        simular(sesion, reserva, APROBADO).andExpect(status().isNoContent());

        assertThat(estadoDeReserva(reserva)).isEqualTo("CONFIRMADA");
        assertThat(pagosDe(reserva)).singleElement().satisfies(fila -> {
            assertThat(fila.get("estado")).isEqualTo("PAGADO");
            assertThat(fila.get("monto").toString()).isEqualTo(MONTO);
            assertThat(fila.get("ultimos4")).isEqualTo("4242");
            assertThat(((Timestamp) fila.get("creado_en")).toInstant()).isEqualTo(reloj.instant());
        });
        assertThat(reservasDelEspacio.bloquesReservados(espacioId, fecha, fecha))
                .extracting(BloqueReservado::pagado).containsExactly(true, true, true);
        assertThat(auditorias("RESERVA_CONFIRMADA")).hasSize(1);
        assertThat(auditorias("PAGO_APROBADO")).hasSize(1);
    }

    @Test
    @DisplayName("HU-13 R64: tras confirmar envía el comprobante y el aviso, una vez cada uno")
    void aprobadoEnviaLosCorreos() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var reserva = conCheckout(sesion);

        simular(sesion, reserva, APROBADO).andExpect(status().isNoContent());

        assertThat(notificador.comprobantes()).containsExactly(new Comprobante(CORREO_USUARIO,
                new ComprobantePago(NOMBRE_ESPACIO, fecha, 14, 17, new BigDecimal(MONTO), "4242",
                        "sim-" + reserva + "-APROBADO-1")));
        assertThat(notificador.avisos()).containsExactly(new AvisoArrendador(CORREO_ARRENDADOR,
                new AvisoReservaPagada(NOMBRE_ESPACIO, fecha, 14, 17)));
    }

    @Test
    @DisplayName("HU-13 R64: si la transacción no se confirma, no hay correos ni pago, pero queda quién lo intentó")
    void sinCommitNoHayCorreos() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var reserva = conCheckout(sesion);
        ReservasPorCobrar sinProxy = AopTestUtils.getUltimateTargetObject(puerto);
        doThrow(new IllegalStateException("falla al confirmar")).when(sinProxy).confirmar(any(), any());

        simular(sesion, reserva, APROBADO).andExpect(status().isInternalServerError());

        assertThat(filas("pago")).isZero();
        assertThat(estadoDeReserva(reserva)).isEqualTo("PENDIENTE_PAGO");
        sinCorreos();
        assertThat(auditorias("PAGO_SIMULADO")).hasSize(1);
    }

    @Test
    @DisplayName("HU-13 R65: con la pantalla abierta hasta vencer, APROBADO guarda PAGADO_TARDIO, sin confirmar")
    void tardioConReservaVencida() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var reserva = conCheckout(sesion);
        reloj.avanzar(Duration.ofMinutes(11));

        simular(sesion, reserva, APROBADO).andExpect(status().isNoContent());

        assertThat(pagosDe(reserva)).singleElement()
                .satisfies(fila -> assertThat(fila.get("estado")).isEqualTo("PAGADO_TARDIO"));
        assertThat(estadoDeReserva(reserva)).isEqualTo("PENDIENTE_PAGO");
        assertThat(bloquesActivosDe(reserva)).isEqualTo(3);
        assertThat(auditorias("PAGO_TARDIO")).hasSize(1);
        assertThat(auditorias("RESERVA_CONFIRMADA")).isEmpty();
        sinCorreos();
    }

    @Test
    @DisplayName("HU-13 R65: con la reserva ya EXPIRADA, APROBADO también guarda PAGADO_TARDIO")
    void tardioConReservaExpirada() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var reserva = reserva(usuarioId, "EXPIRADA", haceMinutos(1));
        filaCheckout(reserva);

        simular(sesion, reserva, APROBADO).andExpect(status().isNoContent());

        assertThat(pagosDe(reserva).getFirst().get("estado")).isEqualTo("PAGADO_TARDIO");
        assertThat(estadoDeReserva(reserva)).isEqualTo("EXPIRADA");
        sinCorreos();
    }

    @Test
    @DisplayName("HU-13 R63: con el espacio pausado y la reserva vigente, APROBADO confirma, como D6")
    void aprobadoConEspacioPausado() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var reserva = conCheckout(sesion);
        jdbc.update("UPDATE espacio SET estado = 'PAUSADO' WHERE id = ?", espacioId);

        simular(sesion, reserva, APROBADO).andExpect(status().isNoContent());

        assertThat(estadoDeReserva(reserva)).isEqualTo("CONFIRMADA");
    }

    @Test
    @DisplayName("HU-13 R66: un RECHAZADO guarda su fila y deja la reserva igual, y se puede reintentar")
    void rechazado() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var reserva = conCheckout(sesion);

        simular(sesion, reserva, RECHAZADO).andExpect(status().isNoContent());

        assertThat(pagosDe(reserva)).singleElement().satisfies(fila -> {
            assertThat(fila.get("estado")).isEqualTo("RECHAZADO");
            assertThat(fila.get("monto").toString()).isEqualTo(MONTO);
        });
        assertThat(estadoDeReserva(reserva)).isEqualTo("PENDIENTE_PAGO");
        sinCorreos();
        disponible(sesion, reserva, true);
    }

    @Test
    @DisplayName("HU-13 R67: solo el tercer rechazo cierra el checkout, y el cuarto intento responde 429")
    void tercerRechazoCierra() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var reserva = conCheckout(sesion);

        simular(sesion, reserva, RECHAZADO).andExpect(status().isNoContent());
        simular(sesion, reserva, RECHAZADO).andExpect(status().isNoContent());
        verify(pasarela, never()).cerrarCheckout(any());

        simular(sesion, reserva, RECHAZADO).andExpect(status().isNoContent());

        verify(pasarela, times(1)).cerrarCheckout("sim-checkout-" + reserva);
        simular(sesion, reserva, RECHAZADO).andExpect(status().isTooManyRequests());
        assertThat(pagosDe(reserva)).hasSize(3);
    }

    @Test
    @DisplayName("HU-13 R68: un segundo APROBADO responde 204, sin otra fila, sin correos y sigue CONFIRMADA")
    void aprobadoRepetido() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var reserva = conCheckout(sesion);
        simular(sesion, reserva, APROBADO).andExpect(status().isNoContent());

        simular(sesion, reserva, APROBADO).andExpect(status().isNoContent());

        assertThat(pagosDe(reserva)).singleElement()
                .satisfies(fila -> assertThat(fila.get("estado")).isEqualTo("PAGADO"));
        assertThat(estadoDeReserva(reserva)).isEqualTo("CONFIRMADA");
        assertThat(notificador.comprobantes()).hasSize(1);
        assertThat(notificador.avisos()).hasSize(1);
        assertThat(auditorias("PAGO_APROBADO")).hasSize(1);
    }

    @Test
    @DisplayName("HU-13 R69: dos APROBADO a la vez dejan una fila PAGADO, ningún tardío y un correo de cada tipo")
    void dosAprobadosALaVez() throws Exception {
        var sesion = token(CORREO_USUARIO);

        for (var ronda = 0; ronda < RONDAS; ronda++) {
            notificador.reiniciar();
            var reserva = conCheckout(sesion);
            var salida = new CountDownLatch(1);
            var respuestas = new ArrayList<Integer>();

            try (var hilos = Executors.newFixedThreadPool(2)) {
                var uno = hilos.submit(() -> {
                    salida.await();
                    return simular(sesion, reserva, APROBADO).andReturn().getResponse().getStatus();
                });
                var dos = hilos.submit(() -> {
                    salida.await();
                    return simular(sesion, reserva, APROBADO).andReturn().getResponse().getStatus();
                });
                salida.countDown();
                respuestas.add(uno.get(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS));
                respuestas.add(dos.get(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS));
            }

            assertThat(respuestas).containsExactly(204, 204);
            assertThat(pagosDe(reserva)).singleElement()
                    .satisfies(fila -> assertThat(fila.get("estado")).isEqualTo("PAGADO"));
            assertThat(estadoDeReserva(reserva)).isEqualTo("CONFIRMADA");
            assertThat(notificador.comprobantes()).hasSize(1);
            assertThat(notificador.avisos()).hasSize(1);
            jdbc.update("DELETE FROM pago");
            jdbc.update("DELETE FROM reserva");
        }
    }

    @Test
    @DisplayName("HU-13 R70: dos rechazos seguidos guardan dos filas con ids distintos, salidos de la base")
    void dosRechazos() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var reserva = conCheckout(sesion);

        simular(sesion, reserva, RECHAZADO).andExpect(status().isNoContent());
        simular(sesion, reserva, RECHAZADO).andExpect(status().isNoContent());

        assertThat(pagosDe(reserva)).extracting(fila -> fila.get("id_transaccion_pasarela"))
                .containsExactly("sim-" + reserva + "-RECHAZADO-1", "sim-" + reserva + "-RECHAZADO-2");
    }

    @Test
    @DisplayName("HU-13 R71: el monto sale de monto_total e ignora lo que el cuerpo traiga además de resultado")
    void ignoraLoDemasDelCuerpo() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var reserva = conCheckout(sesion);
        var cuerpo = "{\"resultado\":\"APROBADO\",\"monto\":1.00,\"estado\":\"RECHAZADO\",\"usuarioId\":999}";

        simular(sesion, reserva, cuerpo).andExpect(status().isNoContent());

        assertThat(pagosDe(reserva)).singleElement().satisfies(fila -> {
            assertThat(fila.get("estado")).isEqualTo("PAGADO");
            assertThat(fila.get("monto").toString()).isEqualTo(MONTO);
        });
        assertThat(auditorias("PAGO_SIMULADO").getFirst().get("usuario_id")).isEqualTo(usuarioId);
    }

    @Test
    @DisplayName("HU-13 R72: con la fila de checkout creada sin el bean, como tras reiniciar, APROBADO confirma")
    void trasReiniciar() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var reserva = vigente();
        filaCheckout(reserva);

        simular(sesion, reserva, APROBADO).andExpect(status().isNoContent());

        assertThat(estadoDeReserva(reserva)).isEqualTo("CONFIRMADA");
        assertThat(pagosDe(reserva).getFirst().get("monto").toString()).isEqualTo(MONTO);
    }

    // ── Quién puede simular ──────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"resultado\":null}", "{\"resultado\":\"SIN_RESULTADO\"}",
        "{\"resultado\":\"PAGADO\"}", "{\"resultado\":\"aprobado\"}", "esto no es JSON", ""})
    @DisplayName("HU-13 R73: un resultado ausente o distinto de APROBADO y RECHAZADO es 400, sin llamar al webhook")
    void resultadoInvalido(String cuerpo) throws Exception {
        var sesion = token(CORREO_USUARIO);
        var reserva = conCheckout(sesion);

        simular(sesion, reserva, cuerpo).andExpect(status().isBadRequest());

        sinLlamarAlWebhook();
        assertThat(filas("pago")).isZero();
        assertThat(auditorias("PAGO_SIMULADO")).isEmpty();
    }

    @Test
    @DisplayName("HU-13 R74: un ARRENDADOR o un ADMIN reciben 403 en GET y POST, sin llamar al webhook")
    void otrosRoles() throws Exception {
        crearAdmin("admin.pagos@correo.pe");
        var reserva = conCheckout(token(CORREO_USUARIO));
        var arrendador = token(CORREO_ARRENDADOR);
        var admin = token("admin.pagos@correo.pe", IP_ADMIN);

        for (var sesion : List.of(arrendador, admin)) {
            consultarSimulacion(sesion, reserva).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.mensaje").value(ACCESO_DENEGADO));
            simular(sesion, reserva, APROBADO).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.mensaje").value(ACCESO_DENEGADO));
        }

        sinLlamarAlWebhook();
        assertThat(estadoDeReserva(reserva)).isEqualTo("PENDIENTE_PAGO");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-1", "1.5", "99999999999999999999", "999999"})
    @DisplayName("HU-13 R75: un id mal escrito o inexistente es 404 en GET y POST, sin llamar al webhook")
    void idInvalido(String id) throws Exception {
        var sesion = token(CORREO_USUARIO);

        consultarSimulacion(sesion, id).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value(NO_ENCONTRADO));
        simular(sesion, id, APROBADO).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value(NO_ENCONTRADO));

        sinLlamarAlWebhook();
    }

    @Test
    @DisplayName("HU-13 R75: la reserva de otro usuario es el mismo 404, y no se aprueba")
    void reservaAjena() throws Exception {
        crearUsuario(OTRO_CORREO);
        var reserva = conCheckout(token(CORREO_USUARIO));
        var ajena = token(OTRO_CORREO);

        consultarSimulacion(ajena, reserva).andExpect(status().isNotFound());
        simular(ajena, reserva, APROBADO).andExpect(status().isNotFound());

        sinLlamarAlWebhook();
        assertThat(estadoDeReserva(reserva)).isEqualTo("PENDIENTE_PAGO");
        assertThat(filas("pago")).isZero();
    }

    @Test
    @DisplayName("HU-13 R76: sin sesión es 401 en GET y POST")
    void sinSesion() throws Exception {
        var reserva = vigente();

        consultarSimulacion(null, reserva).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(SESION_INVALIDA));
        simular(null, reserva, APROBADO).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(SESION_INVALIDA));

        sinLlamarAlWebhook();
    }

    @Test
    @DisplayName("HU-13 R77: con 3 rechazos recientes es 429, sin llamar al webhook ni auditar")
    void tresRechazos() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var reserva = conCheckout(sesion);
        rechazos(reserva, 3);

        simular(sesion, reserva, APROBADO)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.mensaje").value(DEMASIADOS_INTENTOS));

        sinLlamarAlWebhook();
        assertThat(auditorias("PAGO_SIMULADO")).isEmpty();
    }

    @Test
    @DisplayName("HU-13 R78: sin fila en checkout_pago es 400 pago no disponible, sin llamar al webhook")
    void sinCheckout() throws Exception {
        var reserva = vigente();

        simular(token(CORREO_USUARIO), reserva, APROBADO)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(NO_DISPONIBLE))
                .andExpect(jsonPath("$.errores").isEmpty());

        sinLlamarAlWebhook();
        assertThat(filas("pago")).isZero();
    }

    @Test
    @DisplayName("HU-13 R79: el rol va antes que la reserva: un ADMIN recibe 403 aunque el id no exista")
    void rolAntesQueReserva() throws Exception {
        crearAdmin("admin.pagos@correo.pe");

        simular(token("admin.pagos@correo.pe", IP_ADMIN), "abc", APROBADO).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("HU-13 R79: la reserva va antes que los rechazos: una ajena con 3 rechazos es 404")
    void reservaAntesQueRechazos() throws Exception {
        crearUsuario(OTRO_CORREO);
        var reserva = conCheckout(token(CORREO_USUARIO));
        rechazos(reserva, 3);

        simular(token(OTRO_CORREO), reserva, APROBADO).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("HU-13 R79: los rechazos van antes que el checkout: sin fila y con 3 rechazos es 429")
    void rechazosAntesQueCheckout() throws Exception {
        var reserva = vigente();
        rechazos(reserva, 3);

        simular(token(CORREO_USUARIO), reserva, APROBADO).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("HU-13 R79: no repite vigencia ni espacio: una vencida con checkout llega al webhook")
    void noRepiteLaVigencia() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var reserva = conCheckout(sesion);
        reloj.avanzar(Duration.ofMinutes(11));
        jdbc.update("UPDATE espacio SET estado = 'OCULTO' WHERE id = ?", espacioId);

        simular(sesion, reserva, APROBADO).andExpect(status().isNoContent());

        verify(webhook, times(1)).recibir(any(), any());
    }

    @Test
    @DisplayName("HU-13 R80: ninguna respuesta lleva tarjeta, monto, transacción ni checkout")
    void noExponeDatosDelPago() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var reserva = conCheckout(sesion);

        var consulta = texto(consultarSimulacion(sesion, reserva).andExpect(status().isOk()));
        var simulacion = texto(simular(sesion, reserva, APROBADO).andExpect(status().isNoContent()));

        assertThat(consulta).doesNotContain("4242", MONTO, "sim-");
        assertThat(simulacion).isEmpty();
    }

    // ── Token y registro ─────────────────────────────────────────────────

    @Test
    @DisplayName("HU-13 R85: el token interno firma la notificación y no aparece en respuestas, auditoría ni pago")
    void elTokenNoSale() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var reserva = conCheckout(sesion);
        var respuesta = texto(consultarSimulacion(sesion, reserva));

        simular(sesion, reserva, APROBADO).andExpect(status().isNoContent());

        var notificacion = ArgumentCaptor.forClass(NotificacionRecibida.class);
        verify(webhook).recibir(notificacion.capture(), any());
        var tokenInterno = notificacion.getValue().encabezados().get("x-signature");
        assertThat(tokenInterno).hasSize(64).isNotEqualTo("secreto-solo-pruebas");
        assertThat(respuesta).doesNotContain(tokenInterno);
        assertThat(jdbc.queryForList("SELECT detalle FROM auditoria WHERE detalle IS NOT NULL", String.class))
                .isNotEmpty().allSatisfy(detalle -> assertThat(detalle).doesNotContain(tokenInterno));
        assertThat(pagosDe(reserva).toString()).doesNotContain(tokenInterno);
    }

    @Test
    @DisplayName("HU-13 R86: audita PAGO_SIMULADO con el usuario, la reserva, la IP y el detalle exacto")
    void auditaQuienDecidio() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var reserva = conCheckout(sesion);

        simular(sesion, reserva, APROBADO).andExpect(status().isNoContent());

        assertThat(auditorias("PAGO_SIMULADO")).singleElement().satisfies(fila -> {
            assertThat(fila.get("usuario_id")).isEqualTo(usuarioId);
            assertThat(fila.get("entidad")).isEqualTo("reserva");
            assertThat(fila.get("entidad_id")).isEqualTo(reserva);
            assertThat(fila.get("ip")).isEqualTo(ip);
            assertThat(fila.get("detalle"))
                    .isEqualTo("resultado=APROBADO;transaccion=sim-" + reserva + "-APROBADO-1");
        });
    }

    @Test
    @DisplayName("HU-13 R86: también audita un segundo APROBADO, aunque el webhook no escriba nada")
    void auditaCadaIntento() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var reserva = conCheckout(sesion);

        simular(sesion, reserva, APROBADO).andExpect(status().isNoContent());
        simular(sesion, reserva, APROBADO).andExpect(status().isNoContent());

        assertThat(auditorias("PAGO_SIMULADO")).hasSize(2);
    }

    @Test
    @DisplayName("HU-13 R52, R87: el id de transacción lleva el prefijo sim- y la auditoría del webhook sigue igual")
    void idConPrefijo() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var reserva = conCheckout(sesion);

        simular(sesion, reserva, RECHAZADO).andExpect(status().isNoContent());

        var id = "sim-" + reserva + "-RECHAZADO-1";
        assertThat(pagosDe(reserva).getFirst().get("id_transaccion_pasarela")).isEqualTo(id);
        assertThat(auditorias("PAGO_RECHAZADO")).singleElement().satisfies(fila -> {
            assertThat(fila.get("usuario_id")).isNull();
            assertThat(fila.get("detalle"))
                    .isEqualTo("origen=WEBHOOK;reservaId=" + reserva + ";transaccion=" + id + ";monto=" + MONTO);
        });
    }
}
