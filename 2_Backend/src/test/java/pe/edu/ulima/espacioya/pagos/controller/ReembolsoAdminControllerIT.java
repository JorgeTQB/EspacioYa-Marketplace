package pe.edu.ulima.espacioya.pagos.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;

import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.comun.error.ServicioExternoException;
import pe.edu.ulima.espacioya.pagos.service.ReembolsoAdminService;
import pe.edu.ulima.espacioya.pagos.service.pasarela.PasarelaPago;
import pe.edu.ulima.espacioya.pagos.service.pasarela.SolicitudReembolsoPasarela;
import tools.jackson.core.type.TypeReference;

/**
 * CA2 de HU-16 por la API, contra PostgreSQL real y la pasarela simulada con {@code fallos-reembolso=1}: el intento
 * automático de cada reembolso falla, el admin lo ve en su panel y el reintento lo confirma. La pasarela va espiada.
 */
@SpringBootTest(properties = "espacioya.pagos.simulada.fallos-reembolso=1")
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PagosSoporte.Dobles.class})
class ReembolsoAdminControllerIT extends ReembolsosSoporte {

    private static final String YA_REEMBOLSADO = ReembolsoAdminService.MENSAJE_YA_REEMBOLSADO;
    private static final String EN_PROCESO = ReembolsoAdminService.MENSAJE_EN_PROCESO;
    private static final int SEGUNDOS_MAXIMOS = 30;

    @MockitoSpyBean
    private PasarelaPago pasarela;

    private ResultActions listar(String sesion) throws Exception {
        return mvc.perform(comoAdmin(sesion, get(ADMIN_REEMBOLSOS)));
    }

    private ResultActions reintentar(String sesion, Object id) throws Exception {
        return mvc.perform(comoAdmin(sesion, post(rutaReintento(id))));
    }

    private List<Map<String, Object>> lista(ResultActions respuesta) throws Exception {
        return json.readValue(texto(respuesta), new TypeReference<List<Map<String, Object>>>() { });
    }

    private static List<Long> ids(List<Map<String, Object>> lista) {
        return lista.stream().map(fila -> numero(fila.get("id"))).toList();
    }

    /** Una reserva pagada y cancelada por la API: su intento automático ya falló (fallos-reembolso = 1). */
    private long canceladaConReembolsoFallido(String sesion) throws Exception {
        var reserva = pagadaPorLaApi(sesion);
        cancelar(sesion, reserva, MONTO_PAGADO).andExpect(status().isOk());
        return reserva;
    }

    // ── CA2 de punta a punta ─────────────────────────────────────────────

    @Test
    @DisplayName("HU-16 R7, R15, R17, R18, R30, R36, R37, R38: la pasarela falla al cancelar, el admin ve el "
            + "reembolso y al reintentarlo queda REEMBOLSADO con su id y su IP (HU-31 R5, R6, R13: con N = 1 falla "
            + "el intento 1, auditado, y aprueba el 2; R15 queda reemplazado por HU-31 R5 y R8)")
    void fallaYElAdminLoReintenta() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var reserva = pagadaPorLaApi(sesion);
        var pagoId = numero(pagoDe(reserva).get("id"));

        cancelar(sesion, reserva, MONTO_PAGADO).andExpect(status().isOk())
                .andExpect(jsonPath("$.reservaId").value(reserva))
                .andExpect(jsonPath("$.estado").value("CANCELADA"))
                .andExpect(jsonPath("$.porcentaje").value(100))
                .andExpect(jsonPath("$.montoReembolso").value(241.5));

        assertThat(estadoDeReserva(reserva)).isEqualTo("CANCELADA");
        var fila = reembolsosDe(reserva).getFirst();
        var id = numero(fila.get("id"));
        assertThat(fila.get("estado")).isEqualTo("FALLIDO");
        assertThat(fila.get("intentos")).isEqualTo(1);
        assertThat(fila.get("id_reembolso_pasarela")).isNull();
        assertThat(pagoDe(reserva).get("estado")).isEqualTo("REEMBOLSO_FALLIDO");
        var fallidos = auditorias("REEMBOLSO_FALLIDO");
        assertThat(fallidos).hasSize(1);
        assertThat(fallidos.getFirst().get("usuario_id")).isEqualTo(usuarioId);
        assertThat(fallidos.getFirst().get("ip")).isEqualTo(ip);
        assertThat(fallidos.getFirst().get("entidad")).isEqualTo("reembolso");
        assertThat(numero(fallidos.getFirst().get("entidad_id"))).isEqualTo(id);
        assertThat(fallidos.getFirst().get("detalle")).isEqualTo("pagoId=" + pagoId + ";reservaId=" + reserva
                + ";intento=1;disparo=AUTOMATICO;monto=241.50");

        var admin = tokenAdmin();
        var adminId = jdbc.queryForObject("SELECT id FROM usuario WHERE correo = ?", Long.class, CORREO_ADMIN);
        assertThat(ids(lista(listar(admin).andExpect(status().isOk())))).containsExactly(id);
        reloj.avanzar(Duration.ofMinutes(10));
        var reintento = reloj.instant();

        reintentar(admin, id).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.reservaId").value(reserva))
                .andExpect(jsonPath("$.estado").value("REEMBOLSADO"))
                .andExpect(jsonPath("$.intentos").value(2));

        var despues = reembolso(id);
        assertThat(despues.get("estado")).isEqualTo("REEMBOLSADO");
        assertThat(despues.get("intentos")).isEqualTo(2);
        assertThat(instante(despues.get("ultimo_intento_en"))).isEqualTo(reintento);
        assertThat(despues.get("id_reembolso_pasarela")).isEqualTo("sim-reembolso-espacioya-reembolso-" + id);
        assertThat(pagoDe(reserva).get("estado")).isEqualTo("REEMBOLSADO");
        var realizados = auditorias("REEMBOLSO_REALIZADO");
        assertThat(realizados).hasSize(1);
        assertThat(realizados.getFirst().get("usuario_id")).isEqualTo(adminId);
        assertThat(realizados.getFirst().get("ip")).isEqualTo(IP_ADMIN);
        assertThat(instante(realizados.getFirst().get("fecha"))).isEqualTo(reintento);
        assertThat(realizados.getFirst().get("detalle")).isEqualTo("pagoId=" + pagoId + ";reservaId=" + reserva
                + ";intento=2;disparo=ADMIN;monto=241.50;idReembolso=sim-reembolso-espacioya-reembolso-" + id);
        assertThat(lista(listar(admin).andExpect(status().isOk()))).isEmpty();
    }

    @Test
    @DisplayName("HU-31 R4: el reintento envía la misma transacción, monto y clave de idempotencia que el intento "
            + "automático (HU-16 R24), con números de intento 1 y 2")
    void mismaClaveEnElReintento() throws Exception {
        var reserva = canceladaConReembolsoFallido(token(CORREO_USUARIO));
        var id = numero(reembolsosDe(reserva).getFirst().get("id"));

        reintentar(tokenAdmin(), id).andExpect(status().isOk());

        var pedidos = ArgumentCaptor.forClass(SolicitudReembolsoPasarela.class);
        verify(pasarela, times(2)).reembolsar(pedidos.capture());
        var automatico = pedidos.getAllValues().get(0);
        var delAdmin = pedidos.getAllValues().get(1);
        assertThat(pedidos.getAllValues()).extracting(SolicitudReembolsoPasarela::claveIdempotencia)
                .containsExactly("espacioya-reembolso-" + id, "espacioya-reembolso-" + id);
        assertThat(delAdmin.idTransaccion()).isEqualTo(automatico.idTransaccion());
        assertThat(delAdmin.monto()).isEqualByComparingTo(automatico.monto());
        assertThat(pedidos.getAllValues()).extracting(SolicitudReembolsoPasarela::numeroIntento).containsExactly(1, 2);
    }

    @Test
    @DisplayName("HU-16 R31: si la pasarela vuelve a fallar, el reintento responde 200 FALLIDO con un intento más")
    void elReintentoVuelveAFallar() throws Exception {
        var reserva = canceladaConReembolsoFallido(token(CORREO_USUARIO));
        var id = numero(reembolsosDe(reserva).getFirst().get("id"));
        var admin = tokenAdmin();
        doThrow(new ServicioExternoException()).when(pasarela).reembolsar(any());

        reintentar(admin, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("FALLIDO"))
                .andExpect(jsonPath("$.intentos").value(2));

        assertThat(reembolso(id).get("estado")).isEqualTo("FALLIDO");
        assertThat(reembolso(id).get("intentos")).isEqualTo(2);
        assertThat(pagoDe(reserva).get("estado")).isEqualTo("REEMBOLSO_FALLIDO");
        var fallidos = auditorias("REEMBOLSO_FALLIDO");
        assertThat(fallidos).hasSize(2);
        assertThat(fallidos.getLast().get("detalle").toString()).contains(";intento=2;disparo=ADMIN;")
                .doesNotContain("idReembolso");
        assertThat(ids(lista(listar(admin)))).containsExactly(id);
    }

    @Test
    @DisplayName("HU-16 R16: mientras espera a la pasarela no retiene bloqueos sobre el reembolso, el pago ni la reserva")
    void sinBloqueosDuranteLaLlamada() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var reserva = pagadaPorLaApi(sesion);
        var problemas = new CopyOnWriteArrayList<String>();
        var llamadas = new CopyOnWriteArrayList<String>();
        doAnswer(invocacion -> {
            SolicitudReembolsoPasarela pedido = invocacion.getArgument(0);
            llamadas.add(pedido.claveIdempotencia());
            problemas.addAll(bloqueosDesdeOtroHilo(pedido.idTransaccion()));
            return invocacion.callRealMethod();
        }).when(pasarela).reembolsar(any());

        cancelar(sesion, reserva, MONTO_PAGADO).andExpect(status().isOk());
        var id = numero(reembolsosDe(reserva).getFirst().get("id"));
        reintentar(tokenAdmin(), id).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("REEMBOLSADO"));

        assertThat(llamadas).hasSize(2);
        assertThat(problemas).isEmpty();
    }

    // Intenta tomar con NOWAIT, desde otra conexión, las filas del reembolso, su pago y su reserva.
    private List<String> bloqueosDesdeOtroHilo(String idTransaccion) throws Exception {
        try (var hilo = Executors.newSingleThreadExecutor()) {
            return hilo.submit(() -> {
                var fallas = new ArrayList<String>();
                for (var consulta : List.of(
                        "SELECT r.id FROM reembolso r JOIN pago p ON p.id = r.pago_id "
                                + "WHERE p.id_transaccion_pasarela = ? FOR UPDATE OF r NOWAIT",
                        "SELECT id FROM pago WHERE id_transaccion_pasarela = ? FOR UPDATE NOWAIT",
                        "SELECT s.id FROM reserva s JOIN pago p ON p.reserva_id = s.id "
                                + "WHERE p.id_transaccion_pasarela = ? FOR UPDATE OF s NOWAIT")) {
                    try {
                        jdbc.queryForList(consulta, Long.class, idTransaccion);
                    } catch (RuntimeException e) {
                        fallas.add(consulta + " → " + e.getClass().getSimpleName());
                    }
                }
                return fallas;
            }).get(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS);
        }
    }

    // ── Listado ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-16 R28, R29: lista FALLIDO y PENDIENTE sin respuesta por creado_en e id, con las claves exactas y "
            + "no-store; excluye REEMBOLSADO y PENDIENTE en proceso, sin escribir nada")
    void listadoPorAtender() throws Exception {
        var admin = tokenAdmin();
        var fallido = reembolsoSembrado("FALLIDO", 1, haceMinutos(30), haceMinutos(25), "REEMBOLSO_FALLIDO");
        var sinRespuesta = reembolsoSembrado("PENDIENTE", 1, haceMinutos(40), haceMinutos(2), "PAGADO");
        var nuncaIntentado = reembolsoSembrado("PENDIENTE", 0, haceMinutos(3), null, "PAGADO");
        var mismoMomento = reembolsoSembrado("FALLIDO", 2, haceMinutos(30), haceMinutos(5), "REEMBOLSO_FALLIDO");
        reembolsoSembrado("PENDIENTE", 1, haceMinutos(50), haceMinutos(1), "PAGADO");
        reembolsoSembrado("PENDIENTE", 0, haceMinutos(1), null, "PAGADO");
        reembolsoSembrado("REEMBOLSADO", 1, haceMinutos(60), haceMinutos(60), "REEMBOLSADO");
        var auditoriaAntes = filas("auditoria");
        var reembolsosAntes = jdbc.queryForList("SELECT * FROM reembolso ORDER BY id");
        var pagosAntes = jdbc.queryForList("SELECT * FROM pago ORDER BY id");

        var respuesta = listar(admin).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));

        var lista = lista(respuesta);
        assertThat(ids(lista)).containsExactly(sinRespuesta, fallido, mismoMomento, nuncaIntentado);
        var deFallido = lista.get(1);
        assertThat(deFallido).containsOnlyKeys("id", "reservaId", "monto", "origen", "estado", "intentos", "creadoEn",
                "ultimoIntentoEn");
        assertThat(numero(deFallido.get("reservaId"))).isEqualTo(jdbc.queryForObject(
                "SELECT p.reserva_id FROM pago p JOIN reembolso r ON r.pago_id = p.id WHERE r.id = ?", Long.class,
                fallido));
        assertThat(decimal(deFallido.get("monto"))).isEqualByComparingTo(MONTO_PAGADO);
        assertThat(deFallido.get("origen")).isEqualTo("CANCELACION");
        assertThat(deFallido.get("estado")).isEqualTo("FALLIDO");
        assertThat(deFallido.get("intentos")).isEqualTo(1);
        assertThat(Instant.parse(deFallido.get("creadoEn").toString())).isEqualTo(haceMinutos(30));
        assertThat(Instant.parse(deFallido.get("ultimoIntentoEn").toString())).isEqualTo(haceMinutos(25));
        assertThat(lista.get(0).get("estado")).isEqualTo("PENDIENTE");
        assertThat(filas("auditoria")).isEqualTo(auditoriaAntes);
        assertThat(jdbc.queryForList("SELECT * FROM reembolso ORDER BY id")).isEqualTo(reembolsosAntes);
        assertThat(jdbc.queryForList("SELECT * FROM pago ORDER BY id")).isEqualTo(pagosAntes);
    }

    @Test
    @DisplayName("HU-16 R28: sin reembolsos por atender responde 200 con una lista vacía")
    void listadoVacio() throws Exception {
        listar(tokenAdmin()).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    // ── Acceso ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-16 R25, R39: sin sesión las dos rutas responden 401 y el reintento no cambia nada")
    void sinSesion() throws Exception {
        var id = fallidoSembrado();
        var antes = reembolso(id);
        var auditoriaAntes = filas("auditoria");

        listar(null).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.mensaje").value(SESION_INVALIDA));
        reintentar(null, id).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(SESION_INVALIDA));
        reintentar("token-inventado", id).andExpect(status().isUnauthorized());

        assertThat(reembolso(id)).isEqualTo(antes);
        assertThat(filas("auditoria")).isEqualTo(auditoriaAntes);
        verify(pasarela, never()).reembolsar(any());
    }

    @Test
    @DisplayName("HU-16 R26, R39: un USUARIO o un ARRENDADOR recibe 403 en las dos rutas y el reembolso no cambia")
    void sinRolAdmin() throws Exception {
        var id = fallidoSembrado();
        var antes = reembolso(id);
        var pagoAntes = estadoDelPagoDelReembolso(id);
        var sesiones = List.of(token(CORREO_USUARIO, IP_ADMIN), token(CORREO_ARRENDADOR, IP_ADMIN));
        var auditoriaAntes = filas("auditoria");

        for (var sesion : sesiones) {
            listar(sesion).andExpect(status().isForbidden()).andExpect(jsonPath("$.mensaje").value(ACCESO_DENEGADO));
            reintentar(sesion, id).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.mensaje").value(ACCESO_DENEGADO));
        }

        assertThat(reembolso(id)).isEqualTo(antes);
        assertThat(estadoDelPagoDelReembolso(id)).isEqualTo(pagoAntes);
        assertThat(filas("auditoria")).isEqualTo(auditoriaAntes);
        verify(pasarela, never()).reembolsar(any());
    }

    @Test
    @DisplayName("HU-16 R27, R39: un admin desde una IP no permitida recibe 403 en las dos rutas y nada cambia")
    void ipNoPermitida() throws Exception {
        var id = fallidoSembrado();
        var antes = reembolso(id);
        var admin = tokenAdmin();
        var auditoriaAntes = filas("auditoria");

        mvc.perform(con(admin, get(ADMIN_REEMBOLSOS))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(ACCESO_DENEGADO));
        mvc.perform(con(admin, post(rutaReintento(id)))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(ACCESO_DENEGADO));

        assertThat(reembolso(id)).isEqualTo(antes);
        assertThat(filas("auditoria")).isEqualTo(auditoriaAntes);
        verify(pasarela, never()).reembolsar(any());
    }

    // ── Reintentos rechazados ────────────────────────────────────────────

    @Test
    @DisplayName("HU-16 R32, R39: un id mal escrito o inexistente responde 404 sin llamar a la pasarela ni auditar")
    void idNoEncontrado() throws Exception {
        var admin = tokenAdmin();
        var id = fallidoSembrado();
        var antes = reembolso(id);
        var auditoriaAntes = filas("auditoria");

        for (var malo : List.of("abc", "0", "-1", "1.5", "12345678901234567890", "9223372036854775807",
                String.valueOf(id + 1000))) {
            reintentar(admin, malo).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.mensaje").value(NO_ENCONTRADO));
        }

        assertThat(reembolso(id)).isEqualTo(antes);
        assertThat(filas("auditoria")).isEqualTo(auditoriaAntes);
        verify(pasarela, never()).reembolsar(any());
    }

    @Test
    @DisplayName("HU-16 R21, R33, R39: un REEMBOLSADO responde 400 ya reembolsado sin llamar a la pasarela ni cambiar nada")
    void yaReembolsado() throws Exception {
        var admin = tokenAdmin();
        var id = reembolsoSembrado("REEMBOLSADO", 1, haceMinutos(60), haceMinutos(60), "REEMBOLSADO");
        var antes = reembolso(id);
        var auditoriaAntes = filas("auditoria");

        reintentar(admin, id).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(YA_REEMBOLSADO));

        assertThat(reembolso(id)).isEqualTo(antes);
        assertThat(estadoDelPagoDelReembolso(id)).isEqualTo("REEMBOLSADO");
        assertThat(filas("auditoria")).isEqualTo(auditoriaAntes);
        verify(pasarela, never()).reembolsar(any());
    }

    @Test
    @DisplayName("HU-16 R22, R34, R39: un PENDIENTE en proceso (recién creado o con un intento de hace 1 min) responde "
            + "400 en proceso sin llamar a la pasarela")
    void enProceso() throws Exception {
        var admin = tokenAdmin();
        var conIntento = reembolsoSembrado("PENDIENTE", 1, haceMinutos(30), haceMinutos(1), "PAGADO");
        var recien = reembolsoSembrado("PENDIENTE", 0, reloj.instant(), null, "PAGADO");
        var antes = List.of(reembolso(conIntento), reembolso(recien));
        var auditoriaAntes = filas("auditoria");

        for (var id : List.of(conIntento, recien)) {
            reintentar(admin, id).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje").value(EN_PROCESO));
        }

        assertThat(List.of(reembolso(conIntento), reembolso(recien))).isEqualTo(antes);
        assertThat(filas("auditoria")).isEqualTo(auditoriaAntes);
        verify(pasarela, never()).reembolsar(any());
    }

    @Test
    @DisplayName("HU-31 R2: el reintento ignora el cuerpo (también numeroIntento e intentos) y usa el monto, la "
            + "transacción y el número de intento de la fila (HU-16 R9, R35)")
    void ignoraElCuerpo() throws Exception {
        var admin = tokenAdmin();
        var id = fallidoSembrado();
        var otro = fallidoSembrado();
        var transaccion = jdbc.queryForObject("SELECT p.id_transaccion_pasarela FROM pago p JOIN reembolso r "
                + "ON r.pago_id = p.id WHERE r.id = ?", String.class, id);
        var otroPago = jdbc.queryForObject("SELECT pago_id FROM reembolso WHERE id = ?", Long.class, otro);

        mvc.perform(comoAdmin(admin, post(rutaReintento(id))).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monto\":1,\"pagoId\":" + otroPago + ",\"idTransaccion\":\"x\","
                                + "\"numeroIntento\":99,\"intentos\":99}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));

        var pedido = ArgumentCaptor.forClass(SolicitudReembolsoPasarela.class);
        verify(pasarela).reembolsar(pedido.capture());
        assertThat(pedido.getValue().idTransaccion()).isEqualTo(transaccion);
        assertThat(pedido.getValue().monto()).isEqualByComparingTo(MONTO_PAGADO);
        assertThat(pedido.getValue().claveIdempotencia()).isEqualTo("espacioya-reembolso-" + id);
        assertThat(pedido.getValue().numeroIntento()).isEqualTo(2);
        assertThat(reembolso(id).get("intentos")).isEqualTo(2);
        assertThat(reembolso(otro).get("intentos")).isEqualTo(1);
    }

    // ── Sin estado en memoria (HU-31) ────────────────────────────────────

    @Test
    @DisplayName("HU-31 R11: un FALLIDO con 1 intento que la simulada en ejecución nunca vio (como tras un "
            + "reinicio) se reintenta y queda REEMBOLSADO con 2 intentos")
    void reintentoTrasReiniciar() throws Exception {
        var admin = tokenAdmin();
        var id = fallidoSembrado();
        var pagoId = jdbc.queryForObject("SELECT pago_id FROM reembolso WHERE id = ?", Long.class, id);
        var reservaId = jdbc.queryForObject("SELECT reserva_id FROM pago WHERE id = ?", Long.class, pagoId);

        reintentar(admin, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("REEMBOLSADO"))
                .andExpect(jsonPath("$.intentos").value(2));

        var despues = reembolso(id);
        assertThat(despues.get("estado")).isEqualTo("REEMBOLSADO");
        assertThat(despues.get("intentos")).isEqualTo(2);
        assertThat(despues.get("id_reembolso_pasarela")).isEqualTo("sim-reembolso-espacioya-reembolso-" + id);
        var realizados = auditorias("REEMBOLSO_REALIZADO");
        assertThat(realizados).hasSize(1);
        assertThat(realizados.getFirst().get("detalle")).isEqualTo("pagoId=" + pagoId + ";reservaId=" + reservaId
                + ";intento=2;disparo=ADMIN;monto=241.50;idReembolso=sim-reembolso-espacioya-reembolso-" + id);
    }

    @Test
    @DisplayName("HU-31 R13: la simulada falla el intento 1 de un PENDIENTE sin respuesta y queda "
            + "REEMBOLSO_FALLIDO con intento=1 en la auditoría")
    void elFalloPorNumeroQuedaAuditado() throws Exception {
        var admin = tokenAdmin();
        var id = reembolsoSembrado("PENDIENTE", 0, haceMinutos(10), null, "PAGADO");

        reintentar(admin, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("FALLIDO"))
                .andExpect(jsonPath("$.intentos").value(1));

        assertThat(reembolso(id).get("estado")).isEqualTo("FALLIDO");
        assertThat(reembolso(id).get("intentos")).isEqualTo(1);
        var fallidos = auditorias("REEMBOLSO_FALLIDO");
        assertThat(fallidos).hasSize(1);
        assertThat(fallidos.getFirst().get("entidad")).isEqualTo("reembolso");
        assertThat(numero(fallidos.getFirst().get("entidad_id"))).isEqualTo(id);
        assertThat(fallidos.getFirst().get("detalle").toString()).contains(";intento=1;disparo=ADMIN;")
                .doesNotContain("idReembolso");
    }
}
