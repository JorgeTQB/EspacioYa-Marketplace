package pe.edu.ulima.espacioya.pagos.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.pagos.domain.EstadoPago;
import pe.edu.ulima.espacioya.pagos.repository.PagoRepository;
import pe.edu.ulima.espacioya.pagos.repository.ReembolsoRepository;
import pe.edu.ulima.espacioya.pagos.service.HistorialPagosService;
import pe.edu.ulima.espacioya.pagos.service.comprobante.GeneradorComprobantePdf;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservasDelUsuario;

/** "Mis pagos" y comprobantes de HU-28 por la API contra PostgreSQL real, con la pasarela simulada. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PagosSoporte.Dobles.class})
class HistorialPagosControllerIT extends LiquidacionesSoporte {

    private static final String RUTA = "/api/transacciones";

    @Autowired
    private PagoRepository pagos;
    @Autowired
    private ReembolsoRepository reembolsos;
    @Autowired
    private ReservasDelUsuario puerto;
    @Autowired
    private GeneradorComprobantePdf generador;

    private long otroUsuarioId;

    // ── Datos ────────────────────────────────────────────────────────────

    /** Una reserva CONFIRMADA de 3 bloques desde {@code hora} el día {@code dia}, del usuario dado. */
    private long reservaDe(long usuario, LocalDate dia, int hora) {
        var id = jdbc.queryForObject("INSERT INTO reserva (usuario_id, espacio_id, estado, precio_hora_aplicado, "
                        + "monto_total, creada_en, expira_en) VALUES (?, ?, 'CONFIRMADA', 80.50, 241.50, ?, ?) "
                        + "RETURNING id", Long.class, usuario, espacioId, Timestamp.from(haceMinutos(60 * 24 * 20)),
                Timestamp.from(haceMinutos(60 * 24 * 20 - 15)));
        for (var h = hora; h < hora + 3; h++) {
            jdbc.update("INSERT INTO reserva_bloque (reserva_id, espacio_id, fecha, hora_inicio, activo) "
                    + "VALUES (?, ?, ?, ?, true)", id, espacioId, Date.valueOf(dia), h);
        }
        return id;
    }

    private long propia(int desplazamiento) {
        return reservaDe(usuarioId, ZonaHoraria.hoy(reloj).plusDays(10 + desplazamiento), 14);
    }

    private long pago(long reserva, String transaccion, String estado, Instant creadoEn) {
        return jdbc.queryForObject("INSERT INTO pago (reserva_id, id_transaccion_pasarela, monto, estado, ultimos4, "
                        + "creado_en) VALUES (?, ?, 241.50, ?, '4242', ?) RETURNING id", Long.class, reserva,
                transaccion, estado, Timestamp.from(creadoEn));
    }

    private void reembolso(long pago, String monto, String origen, String estado) {
        jdbc.update("INSERT INTO reembolso (pago_id, monto, origen, estado, intentos, creado_en, ultimo_intento_en, "
                        + "id_reembolso_pasarela) VALUES (?, ?, ?, ?, 1, ?, ?, ?)", pago, new BigDecimal(monto),
                origen, estado, Timestamp.from(haceMinutos(30)), Timestamp.from(haceMinutos(30)),
                "REEMBOLSADO".equals(estado) ? "sim-reembolso-" + pago + origen : null);
    }

    private ResultActions listar(String token, String parametros) throws Exception {
        return mvc.perform(con(token, get(RUTA + parametros)));
    }

    private ResultActions comprobante(String token, String id) throws Exception {
        return mvc.perform(con(token, get(RUTA + "/" + id + "/comprobante")));
    }

    private void conOtroUsuario() {
        otroUsuarioId = crearUsuario(OTRO_CORREO);
    }

    private Map<String, List<Map<String, Object>>> tablas() {
        return Map.of("pago", jdbc.queryForList("SELECT * FROM pago ORDER BY id"),
                "reembolso", jdbc.queryForList("SELECT * FROM reembolso ORDER BY id"),
                "reserva", jdbc.queryForList("SELECT * FROM reserva ORDER BY id"));
    }

    // ── Historial ────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-28 R1, R2, R4, R5, R21: lista PAGADO y REEMBOLSADO con fecha de Lima, espacio, monto y estado, "
            + "el más reciente primero, con no-store")
    void listaElHistorial() throws Exception {
        var instante = Instant.parse("2026-10-28T02:30:00Z"); // 27/10 21:30 en Lima
        var pagado = pago(propia(0), transaccionReal(), "PAGADO", instante);
        var reembolsado = pago(propia(1), transaccionReal(), "REEMBOLSADO", instante.plusSeconds(60));
        reembolso(reembolsado, "100.00", "CANCELACION", "REEMBOLSADO");
        reembolso(reembolsado, "41.50", "RECLAMO", "REEMBOLSADO");

        var respuesta = listar(token(CORREO_USUARIO), "").andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.pagina").value(1))
                .andExpect(jsonPath("$.hayMas").value(false))
                .andExpect(jsonPath("$.transacciones.length()").value(2))
                .andExpect(jsonPath("$.transacciones[0].id").value(reembolsado))
                .andExpect(jsonPath("$.transacciones[0].estado").value("REEMBOLSADO"))
                .andExpect(jsonPath("$.transacciones[0].montoReembolsado").value(141.5))
                .andExpect(jsonPath("$.transacciones[1].id").value(pagado))
                .andExpect(jsonPath("$.transacciones[1].estado").value("PAGADO"))
                .andExpect(jsonPath("$.transacciones[1].montoReembolsado").doesNotExist())
                .andExpect(jsonPath("$.transacciones[1].fecha").value("2026-10-27"))
                .andExpect(jsonPath("$.transacciones[1].espacio").value(NOMBRE_ESPACIO))
                .andExpect(jsonPath("$.transacciones[1].monto").value(241.5))
                .andExpect(jsonPath("$.transacciones[1].simulada").value(false));

        assertThat(texto(respuesta)).contains("\"reservaId\"");
    }

    @Test
    @DisplayName("HU-28 R3: los pagos RECHAZADO, PAGADO_TARDIO, MONTO_DISTINTO y REEMBOLSO_FALLIDO no salen ni se "
            + "descargan")
    void soloLosElegibles() throws Exception {
        var reserva = propia(0);
        var pagado = pago(reserva, transaccionReal(), "PAGADO", haceMinutos(50));
        var excluidos = new ArrayList<Long>();
        for (var estado : List.of("RECHAZADO", "PAGADO_TARDIO", "MONTO_DISTINTO", "REEMBOLSO_FALLIDO")) {
            excluidos.add(pago(reserva, transaccionReal(), estado, haceMinutos(40)));
        }
        var sesion = token(CORREO_USUARIO);

        listar(sesion, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.transacciones.length()").value(1))
                .andExpect(jsonPath("$.transacciones[0].id").value(pagado));
        for (var id : excluidos) {
            comprobante(sesion, String.valueOf(id)).andExpect(status().isNotFound());
        }
    }

    @Test
    @DisplayName("HU-28 R5: de 10 en 10, del más reciente al más antiguo, con hayMas y sin total")
    void paginaDeDiez() throws Exception {
        var ids = new ArrayList<Long>();
        for (var i = 0; i < 11; i++) {
            ids.add(pago(propia(i), transaccionReal(), "PAGADO", haceMinutos(100 - i)));
        }
        var sesion = token(CORREO_USUARIO);

        var primera = cuerpo(listar(sesion, "").andExpect(status().isOk()));
        var segunda = cuerpo(listar(sesion, "?pagina=2").andExpect(status().isOk()));

        var esperados = new ArrayList<>(ids).reversed();
        assertThat(lista(primera, "transacciones")).extracting(fila -> numero(fila.get("id")))
                .containsExactlyElementsOf(esperados.subList(0, 10));
        assertThat(primera.get("hayMas")).isEqualTo(true);
        assertThat(primera).containsOnlyKeys("pagina", "transacciones", "hayMas");
        assertThat(lista(segunda, "transacciones")).extracting(fila -> numero(fila.get("id")))
                .containsExactly(ids.getFirst());
        assertThat(segunda.get("hayMas")).isEqualTo(false);
        assertThat(segunda.get("pagina")).isEqualTo(2);
    }

    @Test
    @DisplayName("HU-28 R6: una página inválida responde 400 con el error en pagina")
    void paginaInvalida() throws Exception {
        var sesion = token(CORREO_USUARIO);

        for (var pagina : List.of("0", "-1", "abc", "1.5", "99999999999", "")) {
            listar(sesion, "?pagina=" + pagina).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores.length()").value(1))
                    .andExpect(jsonPath("$.errores[0].campo").value("pagina"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value(HistorialPagosService.MENSAJE_PAGINA));
        }
    }

    @Test
    @DisplayName("HU-28 R7: sin transacciones elegibles responde 200 con la lista vacía y hayMas falso")
    void listaVacia() throws Exception {
        var sesion = token(CORREO_USUARIO);

        listar(sesion, "").andExpect(status().isOk()).andExpect(jsonPath("$.transacciones.length()").value(0))
                .andExpect(jsonPath("$.hayMas").value(false));

        pago(propia(0), transaccionReal(), "RECHAZADO", haceMinutos(40));
        listar(sesion, "").andExpect(status().isOk()).andExpect(jsonPath("$.transacciones.length()").value(0))
                .andExpect(jsonPath("$.hayMas").value(false));
    }

    @Test
    @DisplayName("HU-28 R8: solo ve los pagos de sus reservas, aunque mande un usuarioId en la query")
    void soloLosDeLaSesion() throws Exception {
        conOtroUsuario();
        var mio = pago(propia(0), transaccionReal(), "PAGADO", haceMinutos(50));
        var suyo = pago(reservaDe(otroUsuarioId, ZonaHoraria.hoy(reloj).plusDays(30), 8), transaccionReal(),
                "PAGADO", haceMinutos(40));

        listar(token(CORREO_USUARIO), "?usuarioId=" + otroUsuarioId).andExpect(status().isOk())
                .andExpect(jsonPath("$.transacciones.length()").value(1))
                .andExpect(jsonPath("$.transacciones[0].id").value(mio));
        listar(token(OTRO_CORREO), "?usuarioId=" + usuarioId).andExpect(status().isOk())
                .andExpect(jsonPath("$.transacciones.length()").value(1))
                .andExpect(jsonPath("$.transacciones[0].id").value(suyo));
    }

    @Test
    @DisplayName("HU-28 R9: un ARRENDADOR o un ADMIN recibe 403 en el historial y en el comprobante, antes de validar")
    void otroRol() throws Exception {
        var propio = pago(propia(0), transaccionReal(), "PAGADO", haceMinutos(50));
        var admin = tokenAdmin();
        var arrendador = token(CORREO_ARRENDADOR);

        listar(arrendador, "?pagina=abc").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(ACCESO_DENEGADO));
        comprobante(arrendador, "abc").andExpect(status().isForbidden());
        comprobante(arrendador, String.valueOf(propio)).andExpect(status().isForbidden());
        mvc.perform(comoAdmin(admin, get(RUTA + "?pagina=abc"))).andExpect(status().isForbidden());
        mvc.perform(comoAdmin(admin, get(RUTA + "/abc/comprobante"))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("HU-28 R10: sin sesión válida responde 401 en el historial y en el comprobante")
    void sinSesion() throws Exception {
        var propio = pago(propia(0), transaccionReal(), "PAGADO", haceMinutos(50));

        listar(null, "").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.mensaje").value(SESION_INVALIDA));
        listar("token-inventado", "").andExpect(status().isUnauthorized());
        comprobante(null, String.valueOf(propio)).andExpect(status().isUnauthorized());
        comprobante("token-inventado", String.valueOf(propio)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("HU-28 R18: el JSON del historial no contiene ultimos4, idTransaccionPasarela ni usuarioId")
    void sinDatosSensibles() throws Exception {
        var transaccion = transaccionReal();
        pago(propia(0), transaccion, "PAGADO", haceMinutos(50));

        var respuesta = listar(token(CORREO_USUARIO), "").andExpect(status().isOk());

        assertThat(lista(cuerpo(respuesta), "transacciones").getFirst()).containsOnlyKeys("id", "reservaId", "fecha",
                "espacio", "monto", "estado", "montoReembolsado", "simulada");
        assertThat(texto(respuesta)).doesNotContain("ultimos4").doesNotContain("4242")
                .doesNotContain("idTransaccionPasarela").doesNotContain(transaccion).doesNotContain("usuarioId")
                .doesNotContain(CORREO_USUARIO);
    }

    @Test
    @DisplayName("HU-28 R19: con la pasarela simulada lista los pagos sim- con simulada = true")
    void simuladosListados() throws Exception {
        var simulado = pago(propia(0), transaccionSimulada(), "PAGADO", haceMinutos(40));
        pago(propia(1), transaccionReal(), "PAGADO", haceMinutos(50));

        listar(token(CORREO_USUARIO), "").andExpect(status().isOk())
                .andExpect(jsonPath("$.transacciones.length()").value(2))
                .andExpect(jsonPath("$.transacciones[0].id").value(simulado))
                .andExpect(jsonPath("$.transacciones[0].simulada").value(true))
                .andExpect(jsonPath("$.transacciones[1].simulada").value(false));
    }

    @Test
    @DisplayName("HU-28 R20: con una pasarela real no se listan los pagos sim- y su comprobante da 404")
    void simuladosConPasarelaReal() {
        var simulado = pago(propia(0), transaccionSimulada(), "PAGADO", haceMinutos(40));
        var real = pago(propia(1), transaccionReal(), "PAGADO", haceMinutos(50));
        var servicio = new HistorialPagosService(pagos, reembolsos, puerto, generador, "mercadopago");
        var carla = new UsuarioAutenticado(usuarioId, 1L, "USUARIO");

        var pagina = servicio.listar(carla, null);

        assertThat(pagina.transacciones()).extracting(t -> t.id()).containsExactly(real);
        assertThatThrownBy(() -> servicio.comprobante(carla, String.valueOf(simulado)))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(servicio.comprobante(carla, String.valueOf(real)).contenido()).isNotEmpty();
        // La consulta del repositorio con incluirSimulados = false deja fuera el sim-.
        var reservaIds = puerto.todas(usuarioId).stream().map(r -> r.reservaId()).toList();
        assertThat(pagos.deReservas(reservaIds, EstadoPago.EN_HISTORIAL, false,
                org.springframework.data.domain.PageRequest.of(0, 10)).getContent())
                .extracting(p -> p.getId()).containsExactly(real);
    }

    // ── Comprobante ──────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-28 R11, R12, R14, R21: el comprobante de un pago propio es un PDF adjunto con los datos del pago y "
            + "solo los últimos 4 dígitos")
    void descargaElComprobante() throws Exception {
        var id = pago(propia(0), transaccionReal(), "PAGADO", Instant.parse("2026-10-28T02:30:00Z"));

        var respuesta = comprobante(token(CORREO_USUARIO), String.valueOf(id)).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"comprobante-" + id + ".pdf\""))
                .andExpect(header().string("Cache-Control", "no-store"));

        var bytes = respuesta.andReturn().getResponse().getContentAsByteArray();
        var texto = new String(bytes, StandardCharsets.ISO_8859_1);
        assertThat(texto).startsWith("%PDF-").endsWith("%%EOF")
                .contains("(N.º " + id + ")", "(Fecha de pago: 27/10/2026)", "(Espacio: " + NOMBRE_ESPACIO + ")",
                        "(Monto pagado: S/ 241.50)", "(Estado: Pagado)", "(Tarjeta: terminada en 4242)")
                .contains("14:00 a 17:00")
                .doesNotContain("Monto reembolsado").doesNotContain("simulado").doesNotContain("MP-")
                .doesNotContain(CORREO_USUARIO).doesNotContain(CORREO_ARRENDADOR);
    }

    @Test
    @DisplayName("HU-28 R13, R19: el comprobante REEMBOLSADO trae el monto reembolsado y el de un pago sim- lo avisa")
    void comprobanteReembolsadoYSimulado() throws Exception {
        var id = pago(propia(0), transaccionSimulada(), "REEMBOLSADO", haceMinutos(50));
        reembolso(id, "241.50", "CANCELACION", "REEMBOLSADO");

        var bytes = comprobante(token(CORREO_USUARIO), String.valueOf(id)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(new String(bytes, StandardCharsets.ISO_8859_1)).contains("(Estado: Reembolsado)",
                "(Monto reembolsado: S/ 241.50)", "(Pago simulado: no se cobró dinero real.)")
                .doesNotContain("sim-");
    }

    @Test
    @DisplayName("HU-28 R17: ajena, inexistente, mal escrita y no elegible dan el mismo 404 sin PDF")
    void mismoNoEncontrado() throws Exception {
        conOtroUsuario();
        var ajena = pago(reservaDe(otroUsuarioId, ZonaHoraria.hoy(reloj).plusDays(30), 8), transaccionReal(), "PAGADO",
                haceMinutos(50));
        var rechazada = pago(propia(0), transaccionReal(), "RECHAZADO", haceMinutos(40));
        var sesion = token(CORREO_USUARIO);

        var cuerpos = new ArrayList<String>();
        for (var id : List.of(String.valueOf(ajena), "999999", "abc", "0", "-5", "1.5", String.valueOf(rechazada),
                "99999999999999999999")) {
            var respuesta = comprobante(sesion, id).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.mensaje").value(NO_ENCONTRADO));
            assertThat(respuesta.andReturn().getResponse().getContentType()).doesNotContain("pdf");
            cuerpos.add(texto(respuesta));
        }
        assertThat(cuerpos).doesNotContain("").allMatch(cuerpo -> cuerpo.equals(cuerpos.getFirst()));
    }

    // ── Solo lectura ─────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-28 R22: listar y descargar no modifican ninguna fila de pago, reembolso ni reserva")
    void noEscribe() throws Exception {
        var id = pago(propia(0), transaccionReal(), "REEMBOLSADO", haceMinutos(50));
        reembolso(id, "241.50", "CANCELACION", "REEMBOLSADO");
        pago(propia(1), transaccionSimulada(), "PAGADO", haceMinutos(40));
        var antes = tablas();
        var sesion = token(CORREO_USUARIO);

        listar(sesion, "").andExpect(status().isOk());
        comprobante(sesion, String.valueOf(id)).andExpect(status().isOk());
        comprobante(sesion, "999999").andExpect(status().isNotFound());

        assertThat(tablas()).isEqualTo(antes);
    }
}
