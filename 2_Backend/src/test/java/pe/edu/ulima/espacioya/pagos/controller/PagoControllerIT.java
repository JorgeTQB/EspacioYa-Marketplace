package pe.edu.ulima.espacioya.pagos.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.pagos.service.pasarela.PasarelaPago;
import pe.edu.ulima.espacioya.pagos.service.pasarela.PasarelaSimulada;

/** {@code POST} y {@code GET /api/reservas/{id}/pago} con la pasarela simulada, contra PostgreSQL real (HU-13). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PagosSoporte.Dobles.class})
class PagoControllerIT extends PagosSoporte {

    @Autowired
    private PasarelaPago pasarela;

    private void esNoDisponible(ResultActions respuesta) throws Exception {
        respuesta.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(NO_DISPONIBLE))
                .andExpect(jsonPath("$.errores").isEmpty());
    }

    private void esNoEncontrado(ResultActions respuesta) throws Exception {
        respuesta.andExpect(status().isNotFound()).andExpect(jsonPath("$.mensaje").value(NO_ENCONTRADO));
    }

    private void sinCheckout() {
        assertThat(filas("checkout_pago")).isZero();
        assertThat(auditorias("CHECKOUT_INICIADO")).isEmpty();
    }

    private void rechazos(long reserva, int cantidad) {
        for (var n = 0; n < cantidad; n++) {
            filaPago(reserva, "rechazo-" + reserva + "-" + n, "RECHAZADO", haceMinutos(1));
        }
    }

    // ── Iniciar el pago ──────────────────────────────────────────────────

    @Test
    @DisplayName("HU-13 R56: con espacioya.pagos.pasarela=simulada el backend usa la pasarela simulada")
    void usaLaSimulada() {
        assertThat(pasarela).isInstanceOf(PasarelaSimulada.class);
    }

    @Test
    @DisplayName("HU-13 R1, R20, R59: 200 con un CheckoutDTO que solo trae urlPago, la de la pantalla simulada")
    void iniciaElPago() throws Exception {
        var reserva = vigente();

        var respuesta = pagar(token(CORREO_USUARIO), reserva)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urlPago")
                        .value("http://localhost:5173/reservas/" + reserva + "/pago-simulado"));

        assertThat(mapa(texto(respuesta)).keySet()).containsExactly("urlPago");
    }

    @Test
    @DisplayName("HU-13 R3: ignora el cuerpo del cliente, aunque traiga otro monto o no sea JSON")
    void ignoraElCuerpo() throws Exception {
        var reserva = vigente();
        var sesion = token(CORREO_USUARIO);

        mvc.perform(con(sesion, post("/api/reservas/" + reserva + "/pago")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monto\":1.00,\"usuarioId\":999,\"estado\":\"CONFIRMADA\"}"))
                .andExpect(status().isOk());
        mvc.perform(con(sesion, post("/api/reservas/" + reserva + "/pago")).contentType(MediaType.APPLICATION_JSON)
                        .content("esto no es JSON"))
                .andExpect(status().isOk());

        assertThat(auditorias("CHECKOUT_INICIADO")).extracting(fila -> fila.get("detalle"))
                .containsOnly("monto=" + MONTO);
        assertThat(estadoDeReserva(reserva)).isEqualTo("PENDIENTE_PAGO");
    }

    @Test
    @DisplayName("HU-13 R6: un segundo Pagar responde la misma URL, sin otra fila")
    void reutilizaElCheckout() throws Exception {
        var reserva = vigente();
        var sesion = token(CORREO_USUARIO);

        var primera = texto(pagar(sesion, reserva).andExpect(status().isOk()));
        var segunda = texto(pagar(sesion, reserva).andExpect(status().isOk()));

        assertThat(segunda).isEqualTo(primera);
        assertThat(filas("checkout_pago")).isEqualTo(1);
    }

    @Test
    @DisplayName("HU-13 R8: iniciar el pago no cambia la reserva ni crea filas en pago")
    void iniciarNoConfirma() throws Exception {
        var reserva = vigente();

        pagar(token(CORREO_USUARIO), reserva).andExpect(status().isOk());

        assertThat(estadoDeReserva(reserva)).isEqualTo("PENDIENTE_PAGO");
        assertThat(filas("pago")).isZero();
        assertThat(bloquesActivosDe(reserva)).isEqualTo(3);
    }

    @Test
    @DisplayName("HU-13 R9: audita CHECKOUT_INICIADO con el usuario, la reserva, la IP y el monto")
    void auditaElInicio() throws Exception {
        var reserva = vigente();

        pagar(token(CORREO_USUARIO), reserva).andExpect(status().isOk());

        assertThat(auditorias("CHECKOUT_INICIADO")).singleElement().satisfies(fila -> {
            assertThat(fila.get("usuario_id")).isEqualTo(usuarioId);
            assertThat(fila.get("entidad")).isEqualTo("reserva");
            assertThat(fila.get("entidad_id")).isEqualTo(reserva);
            assertThat(fila.get("ip")).isEqualTo(ip);
            assertThat(fila.get("detalle")).isEqualTo("monto=" + MONTO);
        });
    }

    // ── Cuándo no se inicia ──────────────────────────────────────────────

    @Test
    @DisplayName("HU-13 R10: una reserva EXPIRADA es 400 sin campo y no abre el checkout")
    void expirada() throws Exception {
        esNoDisponible(pagar(token(CORREO_USUARIO), reserva(usuarioId, "EXPIRADA", haceMinutos(1))));
        sinCheckout();
    }

    @Test
    @DisplayName("HU-13 R10: una vencida aún sin procesar es 400, sin cambiarla")
    void vencidaSinProcesar() throws Exception {
        var reserva = reserva(usuarioId, "PENDIENTE_PAGO", haceMinutos(1));

        esNoDisponible(pagar(token(CORREO_USUARIO), reserva));

        assertThat(estadoDeReserva(reserva)).isEqualTo("PENDIENTE_PAGO");
        sinCheckout();
    }

    @Test
    @DisplayName("HU-13 R10: una reserva CONFIRMADA no se paga otra vez")
    void yaConfirmada() throws Exception {
        esNoDisponible(pagar(token(CORREO_USUARIO), reserva(usuarioId, "CONFIRMADA", dentroDe(10))));
        sinCheckout();
    }

    @Test
    @DisplayName("HU-13 R11: a 61 s de expirar se puede pagar y a 60 s ya no")
    void ultimoTramo() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var aTiempo = reserva(usuarioId, "PENDIENTE_PAGO", reloj.instant().plusSeconds(61));

        pagar(sesion, aTiempo).andExpect(status().isOk());
        jdbc.update("DELETE FROM reserva");

        esNoDisponible(pagar(sesion, reserva(usuarioId, "PENDIENTE_PAGO", reloj.instant().plusSeconds(60))));
    }

    @Test
    @DisplayName("HU-13 R12: con el espacio pausado es 400 y no abre el checkout")
    void espacioPausado() throws Exception {
        var reserva = vigente();
        jdbc.update("UPDATE espacio SET estado = 'PAUSADO' WHERE id = ?", espacioId);

        esNoDisponible(pagar(token(CORREO_USUARIO), reserva));
        sinCheckout();
    }

    @Test
    @DisplayName("HU-13 R12: con el arrendador suspendido es 400 y no abre el checkout")
    void arrendadorSuspendido() throws Exception {
        var reserva = vigente();
        jdbc.update("UPDATE usuario SET estado = 'SUSPENDIDA' WHERE id = ?", arrendadorId);

        esNoDisponible(pagar(token(CORREO_USUARIO), reserva));
        sinCheckout();
    }

    @Test
    @DisplayName("HU-13 R13: con 2 rechazos recientes se puede reintentar")
    void dosRechazos() throws Exception {
        var reserva = vigente();
        rechazos(reserva, 2);

        pagar(token(CORREO_USUARIO), reserva).andExpect(status().isOk());
    }

    @Test
    @DisplayName("HU-13 R13: con 3 rechazos recientes es 429, sin abrir el checkout ni cambiar la reserva")
    void tresRechazos() throws Exception {
        var reserva = vigente();
        rechazos(reserva, 3);

        pagar(token(CORREO_USUARIO), reserva)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.mensaje").value(DEMASIADOS_INTENTOS));

        assertThat(estadoDeReserva(reserva)).isEqualTo("PENDIENTE_PAGO");
        assertThat(bloquesActivosDe(reserva)).isEqualTo(3);
        sinCheckout();
    }

    @Test
    @DisplayName("HU-13 R13: un rechazo de hace 31 min no cuenta y uno de hace 30 min sí")
    void ventanaDeRechazos() throws Exception {
        var reserva = vigente();
        var sesion = token(CORREO_USUARIO);
        rechazos(reserva, 2);
        filaPago(reserva, "rechazo-viejo", "RECHAZADO", haceMinutos(31));

        pagar(sesion, reserva).andExpect(status().isOk());

        filaPago(reserva, "rechazo-en-el-limite", "RECHAZADO", haceMinutos(30));
        pagar(sesion, reserva).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("HU-13 R13: un pago tardío o de otro monto no cuenta como rechazo")
    void soloCuentanLosRechazados() throws Exception {
        var reserva = vigente();
        rechazos(reserva, 2);
        filaPago(reserva, "tardio", "PAGADO_TARDIO", haceMinutos(1));
        filaPago(reserva, "distinto", "MONTO_DISTINTO", haceMinutos(1));

        pagar(token(CORREO_USUARIO), reserva).andExpect(status().isOk());
    }

    @Test
    @DisplayName("HU-13 R14: un ARRENDADOR o un ADMIN reciben 403, sin abrir el checkout")
    void otrosRoles() throws Exception {
        crearAdmin("admin.pagos@correo.pe");
        var reserva = vigente();

        pagar(token(CORREO_ARRENDADOR), reserva)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(ACCESO_DENEGADO));
        pagar(token("admin.pagos@correo.pe", IP_ADMIN), reserva).andExpect(status().isForbidden());

        sinCheckout();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-1", "1.5", "99999999999999999999", "999999"})
    @DisplayName("HU-13 R15: un id mal escrito o inexistente es 404 en POST y en GET")
    void idInvalido(String id) throws Exception {
        var sesion = token(CORREO_USUARIO);

        esNoEncontrado(pagar(sesion, id));
        esNoEncontrado(estadoDePago(sesion, id));
    }

    @Test
    @DisplayName("HU-13 R15: una reserva de otro usuario es el mismo 404 en POST y en GET")
    void reservaAjena() throws Exception {
        crearUsuario(OTRO_CORREO);
        var reserva = vigente();
        var sesion = token(OTRO_CORREO);

        esNoEncontrado(pagar(sesion, reserva));
        esNoEncontrado(estadoDePago(sesion, reserva));
        sinCheckout();
    }

    @Test
    @DisplayName("HU-13 R16: sin sesión es 401 en POST y en GET")
    void sinSesion() throws Exception {
        var reserva = vigente();

        pagar(null, reserva).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(SESION_INVALIDA));
        estadoDePago(null, reserva).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(SESION_INVALIDA));
    }

    @Test
    @DisplayName("HU-13 R18: el rol va antes que la reserva: un ADMIN recibe 403 aunque el id no exista")
    void rolAntesQueReserva() throws Exception {
        crearAdmin("admin.pagos@correo.pe");

        pagar(token("admin.pagos@correo.pe", IP_ADMIN), "abc").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("HU-13 R18: la propiedad va antes que la vigencia: una ajena expirada es 404")
    void reservaAntesQueVigencia() throws Exception {
        crearUsuario(OTRO_CORREO);

        esNoEncontrado(pagar(token(OTRO_CORREO), reserva(usuarioId, "EXPIRADA", haceMinutos(1))));
    }

    @Test
    @DisplayName("HU-13 R18: vigencia, último tramo y espacio van antes que los rechazos: 400 y no 429")
    void rechazosAlFinal() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var vencida = reserva(usuarioId, "PENDIENTE_PAGO", haceMinutos(1));
        rechazos(vencida, 3);
        esNoDisponible(pagar(sesion, vencida));
        jdbc.update("DELETE FROM pago");
        jdbc.update("DELETE FROM reserva");

        var enTramo = reserva(usuarioId, "PENDIENTE_PAGO", reloj.instant().plusSeconds(30));
        rechazos(enTramo, 3);
        esNoDisponible(pagar(sesion, enTramo));
        jdbc.update("DELETE FROM pago");
        jdbc.update("DELETE FROM reserva");

        var conEspacioOculto = vigente();
        rechazos(conEspacioOculto, 3);
        jdbc.update("UPDATE espacio SET estado = 'PAUSADO' WHERE id = ?", espacioId);
        esNoDisponible(pagar(sesion, conEspacioOculto));
    }

    // ── Estado para la pantalla ──────────────────────────────────────────

    @Test
    @DisplayName("HU-13 R19, R20: sin pagos responde null y disponible, con exactamente sus claves")
    void estadoSinPagos() throws Exception {
        var respuesta = estadoDePago(token(CORREO_USUARIO), vigente())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ultimoResultado").value((Object) null))
                .andExpect(jsonPath("$.intentosAgotados").value(false))
                .andExpect(jsonPath("$.pagoDisponible").value(true));

        assertThat(mapa(texto(respuesta)).keySet())
                .containsExactlyInAnyOrder("ultimoResultado", "intentosAgotados", "pagoDisponible");
    }

    @Test
    @DisplayName("HU-13 R19: el último resultado es el del pago más reciente")
    void estadoDelUltimoPago() throws Exception {
        var reserva = vigente();
        filaPago(reserva, "antes", "RECHAZADO", haceMinutos(5));
        filaPago(reserva, "despues", "MONTO_DISTINTO", haceMinutos(1));

        estadoDePago(token(CORREO_USUARIO), reserva)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ultimoResultado").value("MONTO_DISTINTO"))
                .andExpect(jsonPath("$.intentosAgotados").value(false));
    }

    @Test
    @DisplayName("HU-13 R19: con 3 rechazos informa intentos agotados y sin pago disponible")
    void estadoConIntentosAgotados() throws Exception {
        var reserva = vigente();
        rechazos(reserva, 3);

        estadoDePago(token(CORREO_USUARIO), reserva)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ultimoResultado").value("RECHAZADO"))
                .andExpect(jsonPath("$.intentosAgotados").value(true))
                .andExpect(jsonPath("$.pagoDisponible").value(false));
    }

    @Test
    @DisplayName("HU-13 R19: en el último tramo, sin espacio visible o confirmada no hay pago disponible")
    void estadoSinPagoDisponible() throws Exception {
        var sesion = token(CORREO_USUARIO);

        estadoDePago(sesion, reserva(usuarioId, "PENDIENTE_PAGO", reloj.instant().plusSeconds(30)))
                .andExpect(jsonPath("$.pagoDisponible").value(false));
        jdbc.update("DELETE FROM reserva");

        estadoDePago(sesion, reserva(usuarioId, "CONFIRMADA", dentroDe(10)))
                .andExpect(jsonPath("$.pagoDisponible").value(false));
        jdbc.update("DELETE FROM reserva");

        var conEspacioOculto = vigente();
        jdbc.update("UPDATE espacio SET estado = 'OCULTO' WHERE id = ?", espacioId);
        estadoDePago(sesion, conEspacioOculto).andExpect(jsonPath("$.pagoDisponible").value(false));
    }

    @Test
    @DisplayName("HU-13 R20: ni el estado ni el checkout exponen transacción, tarjeta ni id del checkout")
    void noExponeDatosDelPago() throws Exception {
        var reserva = vigente();
        var sesion = token(CORREO_USUARIO);
        var checkout = texto(pagar(sesion, reserva).andExpect(status().isOk()));
        jdbc.update("INSERT INTO pago (reserva_id, id_transaccion_pasarela, monto, estado, ultimos4, creado_en) "
                + "VALUES (?, 'transaccion-secreta', 241.50, 'RECHAZADO', '1234', now())", reserva);

        var estado = texto(estadoDePago(sesion, reserva).andExpect(status().isOk()));

        assertThat(estado).doesNotContain("transaccion-secreta", "1234", "sim-checkout");
        assertThat(checkout).doesNotContain("sim-checkout");
    }

    @Test
    @DisplayName("HU-13 R21: consultar el estado no escribe en la base")
    void estadoNoEscribe() throws Exception {
        var reserva = vigente();
        filaPago(reserva, "antes", "RECHAZADO", haceMinutos(5));
        var sesion = token(CORREO_USUARIO);
        var auditoriasAntes = filas("auditoria");

        estadoDePago(sesion, reserva).andExpect(status().isOk());

        assertThat(filas("auditoria")).isEqualTo(auditoriasAntes);
        assertThat(filas("checkout_pago")).isZero();
        assertThat(filas("pago")).isEqualTo(1);
        assertThat(estadoDeReserva(reserva)).isEqualTo("PENDIENTE_PAGO");
    }
}
