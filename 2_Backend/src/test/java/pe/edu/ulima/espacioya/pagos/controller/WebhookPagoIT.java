package pe.edu.ulima.espacioya.pagos.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;
import pe.edu.ulima.espacioya.cuentas.service.AvisoReservaPagada;
import pe.edu.ulima.espacioya.cuentas.service.ComprobantePago;
import pe.edu.ulima.espacioya.espacios.service.puertos.BloqueReservado;
import pe.edu.ulima.espacioya.espacios.service.puertos.ReservasDelEspacio;
import pe.edu.ulima.espacioya.pagos.service.SimulacionPago;
import pe.edu.ulima.espacioya.pagos.service.pasarela.MercadoPagoAdapter;
import pe.edu.ulima.espacioya.pagos.service.pasarela.PasarelaPago;
import pe.edu.ulima.espacioya.pagos.service.pasarela.PasarelaSimulada;
import pe.edu.ulima.espacioya.pagos.service.pasarela.StubMercadoPago;

/** {@code POST /api/pagos/webhook} de punta a punta, con el adaptador real contra el doble HTTP (HU-13). */
class WebhookPagoIT extends PagosConMercadoPagoSoporte {

    private static final String INSERTAR_PAGO = "INSERT INTO pago (reserva_id, id_transaccion_pasarela, monto, estado, "
            + "ultimos4, creado_en) VALUES (?, ?, ?, ?, ?, now())";
    private static final String BLOQUEOS = "/api/arrendadores/actual/espacios/%d/bloqueos";
    private static final DateTimeFormatter FORMATO_LIMA =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").withZone(ZonaHoraria.LIMA);

    @Autowired
    private PasarelaPago pasarela;
    @Autowired
    private ReservasDelEspacio reservasDelEspacio;
    @Autowired
    private ApplicationContext contexto;

    private void sinEfectos(long reserva, String estadoInicial) {
        assertThat(filas("pago")).isZero();
        assertThat(estadoDeReserva(reserva)).isEqualTo(estadoInicial);
        sinCorreos();
    }

    private void sinCorreos() {
        assertThat(notificador.comprobantes()).isEmpty();
        assertThat(notificador.avisos()).isEmpty();
    }

    private ResultActions notificarConFirma(String id, String firma, String idSolicitud) throws Exception {
        return mvc.perform(desde(post(WEBHOOK).queryParam("type", "payment").queryParam("data.id", id)
                .header("x-signature", firma).header("x-request-id", idSolicitud), ip));
    }

    // ── Iniciar el pago con el adaptador real ────────────────────────────

    @Test
    @DisplayName("HU-13 R55: con mercadopago el backend usa el adaptador de Mercado Pago")
    void usaElAdaptador() {
        assertThat(pasarela).isInstanceOf(MercadoPagoAdapter.class);
    }

    @Test
    @DisplayName("HU-13 R1, R2: responde el init_point y envía a la pasarela el monto de la reserva")
    void iniciaContraLaPasarela() throws Exception {
        var reserva = vigente();

        pagar(token(CORREO_USUARIO), reserva)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urlPago").value(StubMercadoPago.URL_PAGO + "pref-1"));

        var enviado = mapa(DOBLE.solicitudes("POST").getLast().cuerpo());
        assertThat(enviado.get("external_reference")).isEqualTo(String.valueOf(reserva));
        assertThat(DOBLE.solicitudes("POST").getLast().cuerpo()).contains("\"unit_price\":241.50");
    }

    @Test
    @DisplayName("HU-13 R4: el enlace vence un minuto antes que la reserva, en hora de Lima")
    void elEnlaceVenceAntes() throws Exception {
        var reserva = vigente();

        pagar(token(CORREO_USUARIO), reserva).andExpect(status().isOk());

        var enviado = mapa(DOBLE.solicitudes("POST").getLast().cuerpo());
        assertThat(enviado.get("expiration_date_to")).isEqualTo(FORMATO_LIMA.format(dentroDe(9)));
        assertThat((String) enviado.get("expiration_date_to")).endsWith("-05:00");
    }

    @Test
    @DisplayName("HU-13 R17: si la pasarela no crea el checkout responde 503 sin guardar filas ni auditar")
    void pasarelaCaidaAlIniciar() throws Exception {
        DOBLE.estadoPreferencia(500);

        pagar(token(CORREO_USUARIO), vigente())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensaje").value(PASARELA_NO_DISPONIBLE));

        assertThat(filas("checkout_pago")).isZero();
        assertThat(auditorias("CHECKOUT_INICIADO")).isEmpty();
    }

    @Test
    @DisplayName("HU-13 R17: si la pasarela vuelve, el reintento funciona")
    void reintentoTrasLaCaida() throws Exception {
        var reserva = vigente();
        var sesion = token(CORREO_USUARIO);
        DOBLE.estadoPreferencia(500);
        pagar(sesion, reserva).andExpect(status().isServiceUnavailable());

        DOBLE.estadoPreferencia(200);

        pagar(sesion, reserva).andExpect(status().isOk());
    }

    // ── Notificación ─────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-13 R22: el webhook no exige sesión, y otras rutas de pagos sí")
    void sinSesion() throws Exception {
        var id = pagoEnLaPasarela("approved", vigente());

        notificar(id).andExpect(status().isOk());

        mvc.perform(desde(get(WEBHOOK), ip)).andExpect(status().isUnauthorized());
        mvc.perform(desde(post(WEBHOOK + "/otra"), ip)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"topic=payment&id=1", "topic=merchant_order&id=1", "type=merchant_order&data.id=1", ""})
    @DisplayName("HU-13 R23: sin type=payment responde 200, sin firma, sin consultar y sin escribir")
    void otrosFormatos(String parametros) throws Exception {
        var reserva = vigente();

        mvc.perform(desde(post(WEBHOOK + "?" + parametros), ip))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        assertThat(DOBLE.solicitudes()).isEmpty();
        sinEfectos(reserva, "PENDIENTE_PAGO");
    }

    @Test
    @DisplayName("HU-13 R24: una firma inválida responde 401 sin cuerpo, sin consultar y sin escribir")
    void firmaMala() throws Exception {
        var reserva = vigente();
        var id = pagoEnLaPasarela("approved", reserva);

        notificarConFirma(id, firma(id, "req-1", TS, "otro-secreto"), "req-1")
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));

        assertThat(DOBLE.solicitudes()).isEmpty();
        sinEfectos(reserva, "PENDIENTE_PAGO");
    }

    @Test
    @DisplayName("HU-13 R24: un ts o un id alterados invalidan la firma")
    void firmaAlterada() throws Exception {
        var reserva = vigente();
        var id = pagoEnLaPasarela("approved", reserva);
        var otroId = pagoEnLaPasarela("approved", reserva);
        var buena = firma(id, "req-1", TS, SECRETO);

        notificarConFirma(id, buena.replace("ts=" + TS, "ts=1704908011"), "req-1")
                .andExpect(status().isUnauthorized());
        notificarConFirma(otroId, buena, "req-1").andExpect(status().isUnauthorized());

        assertThat(DOBLE.solicitudes()).isEmpty();
        sinEfectos(reserva, "PENDIENTE_PAGO");
    }

    @Test
    @DisplayName("HU-13 R24: una notificación de pago sin id o sin encabezados responde 401")
    void sinIdOSinFirma() throws Exception {
        var reserva = vigente();

        mvc.perform(desde(post(WEBHOOK).queryParam("type", "payment"), ip)).andExpect(status().isUnauthorized());
        mvc.perform(desde(post(WEBHOOK).queryParam("type", "payment").queryParam("data.id", "555"), ip))
                .andExpect(status().isUnauthorized());

        sinEfectos(reserva, "PENDIENTE_PAGO");
    }

    @Test
    @DisplayName("HU-13 R24: acepta el id en data_id, que también entra a la firma")
    void aceptaDataId() throws Exception {
        var reserva = vigente();
        var id = pagoEnLaPasarela("approved", reserva);

        mvc.perform(desde(post(WEBHOOK).queryParam("type", "payment").queryParam("data_id", id)
                        .header("x-signature", firma(id, "req-1", TS, SECRETO)).header("x-request-id", "req-1"), ip))
                .andExpect(status().isOk());

        assertThat(estadoDeReserva(reserva)).isEqualTo("CONFIRMADA");
    }

    @Test
    @DisplayName("HU-13 R25: con firma válida consulta el pago en la pasarela")
    void consultaElPago() throws Exception {
        var id = pagoEnLaPasarela("approved", vigente());

        notificar(id).andExpect(status().isOk());

        assertThat(DOBLE.solicitudes("GET")).singleElement().satisfies(solicitud -> {
            assertThat(solicitud.ruta()).isEqualTo("/v1/payments/" + id);
            assertThat(solicitud.autorizacion()).isEqualTo("Bearer llave-solo-pruebas");
        });
    }

    @Test
    @DisplayName("HU-13 R26: el cuerpo de la notificación no cuenta: dice approved, la consulta dice rejected")
    void elCuerpoMiente() throws Exception {
        var reserva = vigente();
        var id = pagoEnLaPasarela("rejected", reserva);

        mvc.perform(desde(post(WEBHOOK).queryParam("type", "payment").queryParam("data.id", id)
                        .header("x-signature", firma(id, "req-1", TS, SECRETO)).header("x-request-id", "req-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"approved\",\"transaction_amount\":1.00,\"live_mode\":false}"), ip))
                .andExpect(status().isOk());

        assertThat(pagosDe(reserva)).singleElement().satisfies(fila -> {
            assertThat(fila.get("estado")).isEqualTo("RECHAZADO");
            assertThat(fila.get("monto").toString()).isEqualTo(MONTO);
        });
        assertThat(estadoDeReserva(reserva)).isEqualTo("PENDIENTE_PAGO");
    }

    @Test
    @DisplayName("HU-13 R26: live_mode no se usa para decidir")
    void liveModeNoImporta() throws Exception {
        var reserva = vigente();
        var id = idDePago();
        DOBLE.pago(id, """
                {"id":%s,"status":"approved","transaction_amount":241.50,"currency_id":"PEN",
                 "external_reference":"%d","live_mode":false}""".formatted(id, reserva));

        notificar(id).andExpect(status().isOk());

        assertThat(estadoDeReserva(reserva)).isEqualTo("CONFIRMADA");
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 404})
    @DisplayName("HU-13 R27: si la consulta falla responde 503, sin escribir, para que la pasarela reintente")
    void consultaFallida(int estado) throws Exception {
        var reserva = vigente();
        var id = pagoEnLaPasarela("approved", reserva);
        DOBLE.estadoDeConsulta(id, estado);

        notificar(id).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensaje").value(PASARELA_NO_DISPONIBLE));

        sinEfectos(reserva, "PENDIENTE_PAGO");
    }

    @ParameterizedTest
    @ValueSource(strings = {"pending", "in_process", "refunded", "cancelled"})
    @DisplayName("HU-13 R28: un pago sin resultado final responde 200 sin escribir")
    void sinResultado(String estado) throws Exception {
        var reserva = vigente();

        notificar(pagoEnLaPasarela(estado, reserva)).andExpect(status().isOk());

        sinEfectos(reserva, "PENDIENTE_PAGO");
    }

    @Test
    @DisplayName("HU-13 R29: una referencia que no existe responde 200, no guarda el pago y audita PAGO_SIN_RESERVA")
    void referenciaInexistente() throws Exception {
        var id = idDePago();
        DOBLE.pago(id, "approved", MONTO, "PEN", "999999", "1234");

        notificar(id).andExpect(status().isOk());

        assertThat(filas("pago")).isZero();
        assertThat(auditorias("PAGO_SIN_RESERVA")).singleElement().satisfies(fila -> {
            assertThat(fila.get("usuario_id")).isNull();
            assertThat(fila.get("detalle")).isEqualTo("origen=WEBHOOK;transaccion=" + id);
        });
    }

    @Test
    @DisplayName("HU-13 R29: una referencia nula o que no es un id se trata igual")
    void referenciaInvalida() throws Exception {
        var nula = idDePago();
        var texto = idDePago();
        DOBLE.pago(nula, "approved", MONTO, "PEN", null, "1234");
        DOBLE.pago(texto, "approved", MONTO, "PEN", "reserva-7", "1234");

        notificar(nula).andExpect(status().isOk());
        notificar(texto).andExpect(status().isOk());

        assertThat(filas("pago")).isZero();
        assertThat(auditorias("PAGO_SIN_RESERVA")).hasSize(2);
    }

    // ── Pago aprobado ────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-13 R30: guarda la fila PAGADO exacta y responde 200 sin cuerpo")
    void guardaElPago() throws Exception {
        var reserva = vigente();
        var id = pagoEnLaPasarela("approved", reserva);

        notificar(id).andExpect(status().isOk()).andExpect(content().string(""));

        assertThat(pagosDe(reserva)).singleElement().satisfies(fila -> {
            assertThat(fila.get("estado")).isEqualTo("PAGADO");
            assertThat(fila.get("id_transaccion_pasarela")).isEqualTo(id);
            assertThat(fila.get("monto").toString()).isEqualTo(MONTO);
            assertThat(fila.get("ultimos4")).isEqualTo("1234");
            assertThat(((Timestamp) fila.get("creado_en")).toInstant()).isEqualTo(reloj.instant());
        });
    }

    @Test
    @DisplayName("HU-13 R30: 241.5 de la pasarela coincide con 241.50 de la reserva")
    void montoSinCeroFinal() throws Exception {
        var reserva = vigente();
        var id = idDePago();
        DOBLE.pago(id, "approved", "241.5", "PEN", String.valueOf(reserva), "1234");

        notificar(id).andExpect(status().isOk());

        assertThat(estadoDeReserva(reserva)).isEqualTo("CONFIRMADA");
        assertThat(pagosDe(reserva).getFirst().get("monto").toString()).isEqualTo(MONTO);
    }

    @Test
    @DisplayName("HU-13 R30: un pago aprobado sin tarjeta confirma igual y ultimos4 queda nulo")
    void sinUltimos4() throws Exception {
        var reserva = vigente();
        var id = idDePago();
        DOBLE.pago(id, "approved", MONTO, "PEN", String.valueOf(reserva), null);

        notificar(id).andExpect(status().isOk());

        assertThat(pagosDe(reserva).getFirst().get("ultimos4")).isNull();
        assertThat(estadoDeReserva(reserva)).isEqualTo("CONFIRMADA");
    }

    @Test
    @DisplayName("HU-13 R31: la reserva pasa a CONFIRMADA y la API la lee así, incluso después de expira_en")
    void confirmaLaReserva() throws Exception {
        var reserva = vigente();
        var sesion = token(CORREO_USUARIO);

        notificar(pagoEnLaPasarela("approved", reserva)).andExpect(status().isOk());

        assertThat(estadoDeReserva(reserva)).isEqualTo("CONFIRMADA");
        mvc.perform(con(sesion, get("/api/reservas/" + reserva))).andExpect(jsonPath("$.estado").value("CONFIRMADA"));
        estadoDePago(sesion, reserva)
                .andExpect(jsonPath("$.ultimoResultado").value("PAGADO"))
                .andExpect(jsonPath("$.pagoDisponible").value(false));
    }

    @Test
    @DisplayName("HU-13 R32: sus bloques siguen activos y el puerto los informa pagados")
    void bloquesPagados() throws Exception {
        var reserva = vigente();

        notificar(pagoEnLaPasarela("approved", reserva)).andExpect(status().isOk());

        assertThat(bloquesActivosDe(reserva)).isEqualTo(3);
        assertThat(reservasDelEspacio.bloquesReservados(espacioId, fecha, fecha))
                .extracting(BloqueReservado::pagado).containsExactly(true, true, true);
    }

    @Test
    @DisplayName("HU-13 R32: el arrendador ya no puede bloquear esa fecha: tiene reservas pagadas")
    void noSePuedeBloquearLaFecha() throws Exception {
        notificar(pagoEnLaPasarela("approved", vigente())).andExpect(status().isOk());

        mvc.perform(con(token(CORREO_ARRENDADOR), post(BLOQUEOS.formatted(espacioId)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fecha\":\"" + fecha + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("No puedes bloquear esta fecha porque tiene reservas pagadas."));
    }

    @Test
    @DisplayName("HU-13 R33: otro monto guarda MONTO_DISTINTO con el monto consultado, sin confirmar ni avisar")
    void otroMonto() throws Exception {
        var reserva = vigente();
        var id = idDePago();
        DOBLE.pago(id, "approved", "10.00", "PEN", String.valueOf(reserva), "1234");

        notificar(id).andExpect(status().isOk());

        assertThat(pagosDe(reserva)).singleElement().satisfies(fila -> {
            assertThat(fila.get("estado")).isEqualTo("MONTO_DISTINTO");
            assertThat(fila.get("monto").toString()).isEqualTo("10.00");
        });
        assertThat(estadoDeReserva(reserva)).isEqualTo("PENDIENTE_PAGO");
        sinCorreos();
    }

    @Test
    @DisplayName("HU-13 R33: otra moneda con el mismo número guarda MONTO_DISTINTO")
    void otraMoneda() throws Exception {
        var reserva = vigente();
        var id = idDePago();
        DOBLE.pago(id, "approved", MONTO, "USD", String.valueOf(reserva), "1234");

        notificar(id).andExpect(status().isOk());

        assertThat(pagosDe(reserva).getFirst().get("estado")).isEqualTo("MONTO_DISTINTO");
        assertThat(estadoDeReserva(reserva)).isEqualTo("PENDIENTE_PAGO");
    }

    @Test
    @DisplayName("HU-13 R34: con la reserva EXPIRADA guarda PAGADO_TARDIO sin tocar la reserva ni sus bloques")
    void tardioConExpirada() throws Exception {
        var reserva = reserva(usuarioId, "EXPIRADA", haceMinutos(1));

        notificar(pagoEnLaPasarela("approved", reserva)).andExpect(status().isOk());

        assertThat(pagosDe(reserva).getFirst().get("estado")).isEqualTo("PAGADO_TARDIO");
        assertThat(estadoDeReserva(reserva)).isEqualTo("EXPIRADA");
        assertThat(bloquesActivosDe(reserva)).isZero();
        sinCorreos();
    }

    @Test
    @DisplayName("HU-13 R34: una vencida aún sin procesar también es PAGADO_TARDIO y sigue sin confirmarse")
    void tardioConVencidaSinProcesar() throws Exception {
        var reserva = reserva(usuarioId, "PENDIENTE_PAGO", haceMinutos(1));

        notificar(pagoEnLaPasarela("approved", reserva)).andExpect(status().isOk());

        assertThat(pagosDe(reserva).getFirst().get("estado")).isEqualTo("PAGADO_TARDIO");
        assertThat(estadoDeReserva(reserva)).isEqualTo("PENDIENTE_PAGO");
        assertThat(bloquesActivosDe(reserva)).isEqualTo(3);
        sinCorreos();
    }

    @Test
    @DisplayName("HU-13 R34: un segundo pago aprobado sobre una CONFIRMADA es PAGADO_TARDIO, sin otro correo")
    void segundoPagoSobreConfirmada() throws Exception {
        var reserva = vigente();
        notificar(pagoEnLaPasarela("approved", reserva)).andExpect(status().isOk());

        notificar(pagoEnLaPasarela("approved", reserva)).andExpect(status().isOk());

        assertThat(pagosDe(reserva)).extracting(fila -> fila.get("estado")).containsExactly("PAGADO", "PAGADO_TARDIO");
        assertThat(estadoDeReserva(reserva)).isEqualTo("CONFIRMADA");
        assertThat(notificador.comprobantes()).hasSize(1);
    }

    @Test
    @DisplayName("HU-13 R35: con el espacio pausado u oculto, la reserva vigente se confirma igual")
    void espacioNoVisible() throws Exception {
        var reserva = vigente();
        jdbc.update("UPDATE espacio SET estado = 'PAUSADO' WHERE id = ?", espacioId);

        notificar(pagoEnLaPasarela("approved", reserva)).andExpect(status().isOk());

        assertThat(estadoDeReserva(reserva)).isEqualTo("CONFIRMADA");
        assertThat(notificador.comprobantes()).hasSize(1);
    }

    @Test
    @DisplayName("HU-13 R35: con el arrendador o el usuario suspendidos, la reserva vigente se confirma igual")
    void cuentasSuspendidas() throws Exception {
        var reserva = vigente();
        jdbc.update("UPDATE usuario SET estado = 'SUSPENDIDA' WHERE id IN (?, ?)", arrendadorId, usuarioId);

        notificar(pagoEnLaPasarela("approved", reserva)).andExpect(status().isOk());

        assertThat(estadoDeReserva(reserva)).isEqualTo("CONFIRMADA");
    }

    // ── Pago rechazado ───────────────────────────────────────────────────

    @Test
    @DisplayName("HU-13 R39: un rechazo guarda RECHAZADO, deja la reserva igual y se puede reintentar")
    void rechazado() throws Exception {
        var reserva = vigente();
        var sesion = token(CORREO_USUARIO);

        notificar(pagoEnLaPasarela("rejected", reserva)).andExpect(status().isOk());

        assertThat(pagosDe(reserva)).singleElement().satisfies(fila -> {
            assertThat(fila.get("estado")).isEqualTo("RECHAZADO");
            assertThat(fila.get("monto").toString()).isEqualTo(MONTO);
        });
        assertThat(estadoDeReserva(reserva)).isEqualTo("PENDIENTE_PAGO");
        sinCorreos();
        estadoDePago(sesion, reserva)
                .andExpect(jsonPath("$.ultimoResultado").value("RECHAZADO"))
                .andExpect(jsonPath("$.pagoDisponible").value(true));
    }

    @Test
    @DisplayName("HU-13 R39: un rechazo que llega después del aprobado no toca la CONFIRMADA")
    void rechazadoFueraDeOrden() throws Exception {
        var reserva = vigente();
        notificar(pagoEnLaPasarela("approved", reserva)).andExpect(status().isOk());

        notificar(pagoEnLaPasarela("rejected", reserva)).andExpect(status().isOk());

        assertThat(pagosDe(reserva)).extracting(fila -> fila.get("estado")).containsExactly("PAGADO", "RECHAZADO");
        assertThat(estadoDeReserva(reserva)).isEqualTo("CONFIRMADA");
    }

    @Test
    @DisplayName("HU-13 R40: solo el tercer rechazo pide cerrar el checkout")
    void tercerRechazoCierra() throws Exception {
        var reserva = vigente();
        pagar(token(CORREO_USUARIO), reserva).andExpect(status().isOk());

        notificar(pagoEnLaPasarela("rejected", reserva)).andExpect(status().isOk());
        notificar(pagoEnLaPasarela("rejected", reserva)).andExpect(status().isOk());
        assertThat(DOBLE.solicitudes("PUT")).isEmpty();

        notificar(pagoEnLaPasarela("rejected", reserva)).andExpect(status().isOk());

        assertThat(DOBLE.solicitudes("PUT")).singleElement()
                .satisfies(solicitud -> assertThat(solicitud.ruta()).isEqualTo("/checkout/preferences/pref-1"));
        assertThat(pagosDe(reserva)).hasSize(3);
    }

    @Test
    @DisplayName("HU-13 R41: si falla el cierre responde 200 y conserva la fila de pago")
    void cierreFallido() throws Exception {
        var reserva = vigente();
        pagar(token(CORREO_USUARIO), reserva).andExpect(status().isOk());
        filaPago(reserva, "antes-1", "RECHAZADO", haceMinutos(2));
        filaPago(reserva, "antes-2", "RECHAZADO", haceMinutos(1));
        DOBLE.estadoCierre(500);
        var id = pagoEnLaPasarela("rejected", reserva);

        notificar(id).andExpect(status().isOk());

        assertThat(pagosDe(reserva)).extracting(fila -> fila.get("id_transaccion_pasarela")).contains(id);
        assertThat(DOBLE.solicitudes("PUT")).hasSize(1);
    }

    // ── Idempotencia y restricciones ─────────────────────────────────────

    @Test
    @DisplayName("HU-13 R42: el mismo id notificado dos veces deja una fila, una auditoría y un correo de cada uno")
    void aprobadoRepetido() throws Exception {
        var reserva = vigente();
        var id = pagoEnLaPasarela("approved", reserva);

        notificar(id).andExpect(status().isOk());
        notificar(id).andExpect(status().isOk());

        assertThat(pagosDe(reserva)).hasSize(1);
        assertThat(auditorias("PAGO_APROBADO")).hasSize(1);
        assertThat(auditorias("RESERVA_CONFIRMADA")).hasSize(1);
        assertThat(notificador.comprobantes()).hasSize(1);
        assertThat(notificador.avisos()).hasSize(1);
    }

    @Test
    @DisplayName("HU-13 R42: un rechazo repetido no cuenta dos veces ni cierra el checkout")
    void rechazoRepetido() throws Exception {
        var reserva = vigente();
        var sesion = token(CORREO_USUARIO);
        pagar(sesion, reserva).andExpect(status().isOk());
        var id = pagoEnLaPasarela("rejected", reserva);

        notificar(id).andExpect(status().isOk());
        notificar(id).andExpect(status().isOk());
        notificar(id).andExpect(status().isOk());

        assertThat(pagosDe(reserva)).hasSize(1);
        assertThat(auditorias("PAGO_RECHAZADO")).hasSize(1);
        assertThat(DOBLE.solicitudes("PUT")).isEmpty();
        estadoDePago(sesion, reserva).andExpect(jsonPath("$.intentosAgotados").value(false));
    }

    @Test
    @DisplayName("HU-13 R45: la base impide dos pagos con el mismo id de transacción y uno con el id nulo")
    void unicidadDeLaTransaccion() {
        var reserva = vigente();
        jdbc.update(INSERTAR_PAGO, reserva, "t-1", new BigDecimal("1.00"), "RECHAZADO", null);

        assertThatThrownBy(() -> jdbc.update(INSERTAR_PAGO, reserva, "t-1", new BigDecimal("1.00"), "RECHAZADO", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_pago_id_transaccion");
        assertThatThrownBy(() -> jdbc.update(INSERTAR_PAGO, reserva, null, new BigDecimal("1.00"), "RECHAZADO", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("HU-13 R46: la base impide dos pagos PAGADO de la misma reserva, pero admite varios rechazos")
    void unPagadoPorReserva() {
        var reserva = vigente();
        jdbc.update(INSERTAR_PAGO, reserva, "t-1", new BigDecimal("1.00"), "PAGADO", null);
        jdbc.update(INSERTAR_PAGO, reserva, "t-2", new BigDecimal("1.00"), "RECHAZADO", null);
        jdbc.update(INSERTAR_PAGO, reserva, "t-3", new BigDecimal("1.00"), "RECHAZADO", null);

        assertThatThrownBy(() -> jdbc.update(INSERTAR_PAGO, reserva, "t-4", new BigDecimal("1.00"), "PAGADO", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_pago_reserva_pagado");
    }

    @Test
    @DisplayName("HU-13 R47: la base impide un estado desconocido, un monto no positivo y ultimos4 sin 4 dígitos")
    void checksDePago() {
        var reserva = vigente();

        assertThatThrownBy(() -> jdbc.update(INSERTAR_PAGO, reserva, "t-1", new BigDecimal("1.00"), "INICIADO", null))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_pago_estado");
        assertThatThrownBy(() -> jdbc.update(INSERTAR_PAGO, reserva, "t-2", BigDecimal.ZERO, "RECHAZADO", null))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_pago_monto");
        assertThatThrownBy(() -> jdbc.update(INSERTAR_PAGO, reserva, "t-3", BigDecimal.ONE, "RECHAZADO", "12a4"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_pago_ultimos4");
    }

    @Test
    @DisplayName("HU-13 R47: la base admite los seis estados de pago")
    void estadosDePago() {
        var reserva = vigente();

        for (var estado : new String[] {"RECHAZADO", "PAGADO_TARDIO", "MONTO_DISTINTO", "REEMBOLSADO",
            "REEMBOLSO_FALLIDO", "PAGADO"}) {
            jdbc.update(INSERTAR_PAGO, reserva, "t-" + estado, new BigDecimal("1.00"), estado, null);
        }

        assertThat(pagosDe(reserva)).hasSize(6);
    }

    @Test
    @DisplayName("HU-13 R47: la base impide dos checkouts de la misma reserva")
    void unCheckoutPorReserva() {
        var reserva = vigente();
        var insertar = "INSERT INTO checkout_pago (reserva_id, id_checkout, url_pago, creado_en) "
                + "VALUES (?, ?, 'https://pago.invalid/x', now())";
        jdbc.update(insertar, reserva, "c-1");

        assertThatThrownBy(() -> jdbc.update(insertar, reserva, "c-2"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_checkout_pago_reserva");
    }

    // ── Correos ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-13 R48: el usuario recibe el comprobante con espacio, fecha, horario, monto y referencia")
    void comprobante() throws Exception {
        var id = pagoEnLaPasarela("approved", vigente());

        notificar(id).andExpect(status().isOk());

        assertThat(notificador.comprobantes()).containsExactly(new Comprobante(CORREO_USUARIO,
                new ComprobantePago(NOMBRE_ESPACIO, fecha, 14, 17, new BigDecimal(MONTO), "1234", id)));
    }

    @Test
    @DisplayName("HU-13 R49: el arrendador recibe espacio, fecha y horario, sin datos del usuario ni del pago")
    void avisoAlArrendador() throws Exception {
        notificar(pagoEnLaPasarela("approved", vigente())).andExpect(status().isOk());

        assertThat(notificador.avisos()).containsExactly(new AvisoArrendador(CORREO_ARRENDADOR,
                new AvisoReservaPagada(NOMBRE_ESPACIO, fecha, 14, 17)));
        assertThat(AvisoReservaPagada.class.getRecordComponents()).extracting("name")
                .containsExactly("espacio", "fecha", "horaInicio", "horaFin");
    }

    @Test
    @DisplayName("HU-25 R27: con los recordatorios desactivados, el usuario y el arrendador reciben igual el "
            + "comprobante y el aviso de reserva pagada")
    void pagoSinRecordatorios() throws Exception {
        jdbc.update("UPDATE usuario SET recordatorios_activos = false WHERE id IN (?, ?)", usuarioId, arrendadorId);
        var id = pagoEnLaPasarela("approved", vigente());

        notificar(id).andExpect(status().isOk());

        assertThat(notificador.comprobantes()).extracting(Comprobante::correo).containsExactly(CORREO_USUARIO);
        assertThat(notificador.avisos()).extracting(AvisoArrendador::correo).containsExactly(CORREO_ARRENDADOR);
    }

    @Test
    @DisplayName("HU-13 R50: sin confirmar no hay correos: tardío, otro monto y rechazado")
    void sinConfirmarNoHayCorreos() throws Exception {
        var expirada = reserva(usuarioId, "EXPIRADA", haceMinutos(1));
        notificar(pagoEnLaPasarela("approved", expirada)).andExpect(status().isOk());
        jdbc.update("DELETE FROM pago");
        jdbc.update("DELETE FROM reserva");

        var conMonto = vigente();
        var id = idDePago();
        DOBLE.pago(id, "approved", "10.00", "PEN", String.valueOf(conMonto), "1234");
        notificar(id).andExpect(status().isOk());

        notificar(pagoEnLaPasarela("rejected", conMonto)).andExpect(status().isOk());

        sinCorreos();
    }

    @Test
    @DisplayName("HU-13 R51: si falla el correo responde 200 y la reserva sigue CONFIRMADA")
    void correoFallido() throws Exception {
        var reserva = vigente();
        notificador.fallar();

        notificar(pagoEnLaPasarela("approved", reserva)).andExpect(status().isOk());

        assertThat(estadoDeReserva(reserva)).isEqualTo("CONFIRMADA");
        assertThat(pagosDe(reserva)).hasSize(1);
    }

    // ── Registro y datos de tarjeta ──────────────────────────────────────

    @Test
    @DisplayName("HU-13 R52: audita PAGO_APROBADO sin usuario, con la IP de la solicitud y el detalle exacto")
    void auditaElPago() throws Exception {
        var reserva = vigente();
        var id = pagoEnLaPasarela("approved", reserva);

        notificar(id).andExpect(status().isOk());

        var pagoId = pagosDe(reserva).getFirst().get("id");
        assertThat(auditorias("PAGO_APROBADO")).singleElement().satisfies(fila -> {
            assertThat(fila.get("usuario_id")).isNull();
            assertThat(fila.get("entidad")).isEqualTo("pago");
            assertThat(fila.get("entidad_id")).isEqualTo(pagoId);
            assertThat(fila.get("ip")).isEqualTo(ip);
            assertThat(fila.get("detalle"))
                    .isEqualTo("origen=WEBHOOK;reservaId=" + reserva + ";transaccion=" + id + ";monto=" + MONTO);
        });
    }

    @Test
    @DisplayName("HU-13 R52: audita PAGO_RECHAZADO, PAGO_TARDIO y PAGO_MONTO_DISTINTO según el estado")
    void auditaLosDemasEstados() throws Exception {
        var reserva = vigente();
        var otroMonto = idDePago();
        DOBLE.pago(otroMonto, "approved", "10.00", "PEN", String.valueOf(reserva), "1234");
        var vencida = reserva(usuarioId, "EXPIRADA", haceMinutos(1));

        notificar(pagoEnLaPasarela("rejected", reserva)).andExpect(status().isOk());
        notificar(otroMonto).andExpect(status().isOk());
        notificar(pagoEnLaPasarela("approved", vencida)).andExpect(status().isOk());

        assertThat(auditorias("PAGO_RECHAZADO")).hasSize(1);
        assertThat(auditorias("PAGO_MONTO_DISTINTO")).hasSize(1);
        assertThat(auditorias("PAGO_TARDIO")).hasSize(1);
        assertThat(auditorias("PAGO_APROBADO")).isEmpty();
    }

    @Test
    @DisplayName("HU-13 R53: audita RESERVA_CONFIRMADA sin usuario ni IP, con su detalle")
    void auditaLaConfirmacion() throws Exception {
        var reserva = vigente();

        notificar(pagoEnLaPasarela("approved", reserva)).andExpect(status().isOk());

        assertThat(auditorias("RESERVA_CONFIRMADA")).singleElement().satisfies(fila -> {
            assertThat(fila.get("usuario_id")).isNull();
            assertThat(fila.get("ip")).isNull();
            assertThat(fila.get("entidad")).isEqualTo("reserva");
            assertThat(fila.get("entidad_id")).isEqualTo(reserva);
            assertThat(fila.get("detalle")).isEqualTo("origen=WEBHOOK;espacioId=" + espacioId);
        });
    }

    @Test
    @DisplayName("HU-13 R54: pago no tiene más columnas de tarjeta que ultimos4")
    void soloUltimos4() {
        var columnas = jdbc.queryForList("SELECT column_name FROM information_schema.columns "
                + "WHERE table_name = 'pago'", String.class);

        assertThat(columnas).containsExactlyInAnyOrder("id", "reserva_id", "id_transaccion_pasarela", "monto", "estado",
                "ultimos4", "creado_en");
    }

    @Test
    @DisplayName("HU-13 R54: la auditoría no guarda la firma, el secreto, la llave ni la respuesta de la pasarela")
    void auditoriaSinSecretos() throws Exception {
        var reserva = vigente();
        var id = pagoEnLaPasarela("approved", reserva);
        var firma = firma(id, "req-1", TS, SECRETO);

        notificarConFirma(id, firma, "req-1").andExpect(status().isOk());

        var detalles = jdbc.queryForList("SELECT detalle FROM auditoria WHERE detalle IS NOT NULL", String.class);
        assertThat(detalles).isNotEmpty().allSatisfy(detalle -> assertThat(detalle).doesNotContain(firma, SECRETO,
                "llave-solo-pruebas", "last_four_digits", "1234", "transaction_amount"));
    }

    // ── Sin simulación con la pasarela real ──────────────────────────────

    @Test
    @DisplayName("HU-13 R81: con mercadopago no existen SimulacionPago ni PagoSimuladoController")
    void sinBeansDeSimulacion() {
        assertThat(contexto.getBeanNamesForType(SimulacionPago.class)).isEmpty();
        assertThat(contexto.getBeanNamesForType(PagoSimuladoController.class)).isEmpty();
        assertThat(contexto.getBeanNamesForType(PasarelaSimulada.class)).isEmpty();
    }

    @Test
    @DisplayName("HU-13 R81: con mercadopago Pagar no apunta a la pantalla simulada")
    void pagarNoLlevaALaPantalla() throws Exception {
        var respuesta = pagar(token(CORREO_USUARIO), vigente()).andExpect(status().isOk());

        assertThat(texto(respuesta)).doesNotContain("pago-simulado");
    }

    @Test
    @DisplayName("HU-13 R82: con mercadopago y sesión, GET y POST de la simulación son 404, sin escribir")
    void simulacionNoExisteConSesion() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var reserva = vigente();
        pagar(sesion, reserva).andExpect(status().isOk());
        var ruta = "/api/reservas/" + reserva + "/pago/simulacion";

        mvc.perform(con(sesion, get(ruta))).andExpect(status().isNotFound());
        mvc.perform(con(sesion, post(ruta)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resultado\":\"APROBADO\"}"))
                .andExpect(status().isNotFound());

        assertThat(filas("pago")).isZero();
        assertThat(estadoDeReserva(reserva)).isEqualTo("PENDIENTE_PAGO");
        assertThat(auditorias("PAGO_SIMULADO")).isEmpty();
    }

    @Test
    @DisplayName("HU-13 R82: con mercadopago y sin sesión, GET y POST de la simulación son 401")
    void simulacionSinSesion() throws Exception {
        var reserva = vigente();
        var ruta = "/api/reservas/" + reserva + "/pago/simulacion";

        mvc.perform(con(null, get(ruta))).andExpect(status().isUnauthorized());
        mvc.perform(con(null, post(ruta)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resultado\":\"APROBADO\"}"))
                .andExpect(status().isUnauthorized());

        assertThat(filas("pago")).isZero();
        assertThat(estadoDeReserva(reserva)).isEqualTo("PENDIENTE_PAGO");
    }

    @Test
    @DisplayName("HU-13 R1, R31: de punta a punta: se paga, llega la notificación y la reserva queda pagada")
    void puntaAPunta() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var reserva = vigente();
        pagar(sesion, reserva).andExpect(status().isOk());
        estadoDePago(sesion, reserva).andExpect(jsonPath("$.ultimoResultado").value((Object) null));

        notificar(pagoEnLaPasarela("rejected", reserva)).andExpect(status().isOk());
        estadoDePago(sesion, reserva).andExpect(jsonPath("$.ultimoResultado").value("RECHAZADO"));

        pagar(sesion, reserva).andExpect(status().isOk());
        notificar(pagoEnLaPasarela("approved", reserva)).andExpect(status().isOk());

        estadoDePago(sesion, reserva).andExpect(jsonPath("$.ultimoResultado").value("PAGADO"));
        assertThat(estadoDeReserva(reserva)).isEqualTo("CONFIRMADA");
        assertThat(DOBLE.solicitudes("POST")).hasSize(1);
        assertThat(notificador.comprobantes()).hasSize(1);
    }
}
