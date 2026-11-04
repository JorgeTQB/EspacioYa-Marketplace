package pe.edu.ulima.espacioya.pagos.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;
import pe.edu.ulima.espacioya.pagos.dto.MarcarLiquidacionPagadaDTO;
import pe.edu.ulima.espacioya.pagos.service.LiquidacionAdminService;

/**
 * HU-19 del lado del admin, por la API y contra PostgreSQL real: CA1 de punta a punta (reservar, pagar con la
 * simulada, avanzar el reloj 48 h y generar), CA2 (marcar pagada), CA3 (sin cuenta), la retención de HU-18, el número
 * completo solo en el detalle pagable, las listas y el acceso.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PagosSoporte.Dobles.class})
class LiquidacionAdminControllerIT extends LiquidacionesSoporte {

    private static final String CUENTA_ARRENDADOR = "/api/arrendadores/actual/cuenta-bancaria";
    private static final String OTRO_NUMERO = "99887766554433229999";
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZonaHoraria.LIMA);

    private ResultActions guardarCuenta(String sesion, String numero) throws Exception {
        return mvc.perform(con(sesion, put(CUENTA_ARRENDADOR)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"banco\":\"" + BANCO + "\",\"numero\":\"" + numero
                        + "\",\"declaraTitularidad\":true,\"contrasena\":\"" + CONTRASENA + "\"}"));
    }

    /** Una reserva del arrendador principal ya usada (hace 5 días), con su pago sim- PAGADO. */
    private long usada(int hora) {
        return usadaSembrada(espacioId, ZonaHoraria.hoy(reloj).minusDays(5), hora, transaccionSimulada());
    }

    private long idGenerada(ResultActions respuesta) throws Exception {
        return numero(lista(cuerpo(respuesta), "liquidaciones").getFirst().get("id"));
    }

    private int auditoriasDe(String accion) {
        return auditorias(accion).size();
    }

    // ── CA1 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-19 R7, R8, R13, R15, R19, R20, R45: CA1 reserva pagada con la simulada: a fin + 48 h − 1 s no se "
            + "liquida; a fin + 48 h se crea una GENERADA con bruto, 10 % y neto, simulada y auditada")
    void ca1GeneraAlPasar48Horas() throws Exception {
        verificarIdentidad(arrendadorId);
        cuentaDe(arrendadorId, NUMERO_CUENTA, null);
        var dia = dia();
        var reserva = pagadaEl(token(CORREO_USUARIO), dia, 14);
        var pagoId = numero(pagoDe(reserva).get("id"));
        var fin = finDe(dia, 14);
        llevarRelojA(fin.plus(ESPERA).minusSeconds(1));
        var admin = tokenAdmin();

        generar(admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.corte").value(fin.plus(ESPERA).minusSeconds(1).toString()))
                .andExpect(jsonPath("$.liquidaciones.length()").value(0));
        assertThat(filas("liquidacion")).isZero();

        llevarRelojA(fin.plus(ESPERA));
        var respuesta = generar(admin).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.corte").value(fin.plus(ESPERA).toString()))
                .andExpect(jsonPath("$.liquidaciones.length()").value(1))
                .andExpect(jsonPath("$.liquidaciones[0].arrendadorId").value(arrendadorId))
                .andExpect(jsonPath("$.liquidaciones[0].arrendadorNombre").value("Ana Reservada"))
                .andExpect(jsonPath("$.liquidaciones[0].periodo")
                        .value(LocalDate.ofInstant(fin.plus(ESPERA), ZonaHoraria.LIMA).toString()))
                .andExpect(jsonPath("$.liquidaciones[0].estado").value("GENERADA"))
                .andExpect(jsonPath("$.liquidaciones[0].montoBruto").value(241.5))
                .andExpect(jsonPath("$.liquidaciones[0].comision").value(24.15))
                .andExpect(jsonPath("$.liquidaciones[0].montoNeto").value(217.35))
                .andExpect(jsonPath("$.liquidaciones[0].reservas").value(1))
                .andExpect(jsonPath("$.liquidaciones[0].simulada").value(true))
                .andExpect(jsonPath("$.liquidaciones[0].avisos.length()").value(0));
        assertThat(texto(respuesta)).doesNotContain(NUMERO_CUENTA);

        var id = idGenerada(respuesta);
        var fila = liquidacion(id);
        assertThat(fila.get("estado")).isEqualTo("GENERADA");
        assertThat(decimal(fila.get("monto_bruto"))).isEqualByComparingTo("241.50");
        assertThat(decimal(fila.get("comision"))).isEqualByComparingTo("24.15");
        assertThat(decimal(fila.get("monto_neto"))).isEqualByComparingTo("217.35");
        assertThat(fila.get("simulada")).isEqualTo(true);
        assertThat(instante(fila.get("generada_en"))).isEqualTo(fin.plus(ESPERA));
        assertThat(numero(fila.get("generada_por"))).isEqualTo(idAdmin());
        var lineas = lineasDe(id);
        assertThat(lineas).hasSize(1);
        assertThat(numero(lineas.getFirst().get("reserva_id"))).isEqualTo(reserva);
        assertThat(numero(lineas.getFirst().get("pago_id"))).isEqualTo(pagoId);
        assertThat(decimal(lineas.getFirst().get("monto"))).isEqualByComparingTo("241.50");
        assertThat(lineas.getFirst().get("simulado")).isEqualTo(true);
        var generadas = auditorias(LiquidacionAdminService.ACCION_GENERADA);
        assertThat(generadas).hasSize(1);
        assertThat(numero(generadas.getFirst().get("usuario_id"))).isEqualTo(idAdmin());
        assertThat(generadas.getFirst().get("ip")).isEqualTo(IP_ADMIN);
        assertThat(generadas.getFirst().get("entidad")).isEqualTo("liquidacion");
        assertThat(numero(generadas.getFirst().get("entidad_id"))).isEqualTo(id);
        assertThat(generadas.getFirst().get("detalle")).isEqualTo("arrendadorId=" + arrendadorId
                + ";reservas=1;bruto=241.50;comision=24.15;neto=217.35;estado=GENERADA;periodo="
                + LocalDate.ofInstant(fin.plus(ESPERA), ZonaHoraria.LIMA) + ";simulada=true");
    }

    @Test
    @DisplayName("HU-19 R12, R18: una segunda generación sin reservas nuevas responde 200 con la lista vacía")
    void segundaGeneracionVacia() throws Exception {
        usada(8);
        usada(12);
        var admin = tokenAdmin();

        generar(admin).andExpect(status().isOk()).andExpect(jsonPath("$.liquidaciones.length()").value(1))
                .andExpect(jsonPath("$.liquidaciones[0].reservas").value(2));
        var antes = liquidaciones();

        generar(admin).andExpect(status().isOk()).andExpect(jsonPath("$.liquidaciones.length()").value(0));

        assertThat(liquidaciones()).isEqualTo(antes);
        assertThat(filas("liquidacion_detalle")).isEqualTo(2);
        assertThat(auditoriasDe(LiquidacionAdminService.ACCION_GENERADA)).isEqualTo(1);
    }

    @Test
    @DisplayName("HU-19 R14: la generación ignora un cuerpo con montos, arrendador, reservas y estado")
    void ignoraElCuerpo() throws Exception {
        var reserva = usada(8);
        var admin = tokenAdmin();

        mvc.perform(comoAdmin(admin, post(ADMIN_LIQUIDACIONES)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"montoBruto\":1,\"comision\":0,\"arrendadorId\":999,\"reservas\":[1,2,3],"
                                + "\"estado\":\"PAGADA\",\"corte\":\"2030-01-01T00:00:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.corte").value(reloj.instant().toString()))
                .andExpect(jsonPath("$.liquidaciones[0].arrendadorId").value(arrendadorId))
                .andExpect(jsonPath("$.liquidaciones[0].montoBruto").value(241.5))
                .andExpect(jsonPath("$.liquidaciones[0].estado").value("PENDIENTE_CUENTA"));

        assertThat(liquidaciones()).singleElement().satisfies(fila -> {
            assertThat(decimal(fila.get("monto_bruto"))).isEqualByComparingTo("241.50");
            assertThat(fila.get("estado")).isEqualTo("PENDIENTE_CUENTA");
        });
        assertThat(numero(jdbc.queryForMap("SELECT * FROM liquidacion_detalle").get("reserva_id")))
                .isEqualTo(reserva);
    }

    @Test
    @DisplayName("HU-19 R24, R37, R45: un arrendador suspendido y sin identidad verificada se liquida igual, con sus "
            + "avisos en la respuesta y en la auditoría")
    void suspendidoSeLiquidaConAvisos() throws Exception {
        usada(8);
        jdbc.update("UPDATE usuario SET estado = 'SUSPENDIDA' WHERE id = ?", arrendadorId);
        var admin = tokenAdmin();

        generar(admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.liquidaciones.length()").value(1))
                .andExpect(jsonPath("$.liquidaciones[0].avisos[0]").value("CUENTA_NO_ACTIVA"))
                .andExpect(jsonPath("$.liquidaciones[0].avisos[1]").value("IDENTIDAD_NO_VERIFICADA"));

        assertThat(auditorias(LiquidacionAdminService.ACCION_GENERADA).getFirst().get("detalle").toString())
                .endsWith(";simulada=true;avisos=CUENTA_NO_ACTIVA,IDENTIDAD_NO_VERIFICADA");
    }

    // ── CA3 y D7 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-19 R16, R26, R27, R38, R39: CA3 sin cuenta queda PENDIENTE_CUENTA y no se puede pagar; cuando el "
            + "arrendador registra su cuenta el detalle es pagable con el número completo y se marca PAGADA")
    void ca3PendienteDeCuenta() throws Exception {
        verificarIdentidad(arrendadorId);
        usada(8);
        var admin = tokenAdmin();
        var id = idGenerada(generar(admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.liquidaciones[0].estado").value("PENDIENTE_CUENTA")));

        detalle(admin, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE_CUENTA"))
                .andExpect(jsonPath("$.pagable").value(false))
                .andExpect(jsonPath("$.cuenta").doesNotExist());
        marcarPagada(admin, id, operacion("00123456")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(LiquidacionAdminService.MENSAJE_SIN_CUENTA));
        assertThat(liquidacion(id).get("estado")).isEqualTo("PENDIENTE_CUENTA");
        assertThat(auditoriasDe(LiquidacionAdminService.ACCION_PAGADA)).isZero();
        assertThat(auditoriasDe(LiquidacionAdminService.ACCION_CUENTA_CONSULTADA)).isZero();

        guardarCuenta(token(CORREO_ARRENDADOR), NUMERO_CUENTA).andExpect(status().isOk());

        detalle(admin, id).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.pagable").value(true))
                .andExpect(jsonPath("$.cuenta.banco").value(BANCO))
                .andExpect(jsonPath("$.cuenta.titular").value("Inversiones Ocultas SAC"))
                .andExpect(jsonPath("$.cuenta.numeroEnmascarado").value("****1234"))
                .andExpect(jsonPath("$.cuenta.numeroCompleto").value(NUMERO_CUENTA))
                .andExpect(jsonPath("$.cuenta.estado").value("DISPONIBLE"));
        var consultas = auditorias(LiquidacionAdminService.ACCION_CUENTA_CONSULTADA);
        assertThat(consultas).hasSize(1);
        var cuentaId = jdbc.queryForObject("SELECT id FROM cuenta_bancaria", Long.class);
        assertThat(numero(consultas.getFirst().get("usuario_id"))).isEqualTo(idAdmin());
        assertThat(consultas.getFirst().get("ip")).isEqualTo(IP_ADMIN);
        assertThat(consultas.getFirst().get("entidad")).isEqualTo("cuenta_bancaria");
        assertThat(numero(consultas.getFirst().get("entidad_id"))).isEqualTo(cuentaId);
        assertThat(consultas.getFirst().get("detalle"))
                .isEqualTo("liquidacionId=" + id + ";arrendadorId=" + arrendadorId + ";cuenta=****1234");

        marcarPagada(admin, id, operacion("00123456")).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PAGADA"));
        assertThat(liquidacion(id).get("estado")).isEqualTo("PAGADA");
        assertThat(auditorias(LiquidacionAdminService.ACCION_PAGADA).getFirst().get("detalle").toString())
                .startsWith("estadoAnterior=PENDIENTE_CUENTA;numeroOperacion=00123456;");
    }

    // ── CA2 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-19 R25, R40, R46: CA2 marcar con el número de operación deja PAGADA con fecha, admin y destino "
            + "enmascarado, audita y nunca devuelve ni audita el número completo")
    void ca2MarcaPagada() throws Exception {
        verificarIdentidad(arrendadorId);
        cuentaDe(arrendadorId, NUMERO_CUENTA, null);
        usada(8);
        var admin = tokenAdmin();
        var id = idGenerada(generar(admin));
        detalle(admin, id).andExpect(jsonPath("$.cuenta.numeroCompleto").value(NUMERO_CUENTA));
        reloj.avanzar(Duration.ofMinutes(5));
        var pagadaEn = reloj.instant();

        var respuesta = marcarPagada(admin, id, operacion("OP-00123456")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.estado").value("PAGADA"))
                .andExpect(jsonPath("$.pagable").value(false))
                .andExpect(jsonPath("$.pago.numeroOperacion").value("OP-00123456"))
                .andExpect(jsonPath("$.pago.pagadaEn").value(pagadaEn.toString()))
                .andExpect(jsonPath("$.pago.banco").value(BANCO))
                .andExpect(jsonPath("$.pago.cuentaEnmascarada").value("****1234"))
                .andExpect(jsonPath("$.cuenta.numeroCompleto").doesNotExist());
        assertThat(texto(respuesta)).doesNotContain(NUMERO_CUENTA);

        var fila = liquidacion(id);
        assertThat(fila.get("estado")).isEqualTo("PAGADA");
        assertThat(fila.get("numero_operacion")).isEqualTo("OP-00123456");
        assertThat(instante(fila.get("pagada_en"))).isEqualTo(pagadaEn);
        assertThat(numero(fila.get("pagada_por"))).isEqualTo(idAdmin());
        assertThat(fila.get("banco_destino")).isEqualTo(BANCO);
        assertThat(fila.get("cuenta_destino")).isEqualTo("****1234");
        var pagadas = auditorias(LiquidacionAdminService.ACCION_PAGADA);
        assertThat(pagadas).hasSize(1);
        assertThat(numero(pagadas.getFirst().get("usuario_id"))).isEqualTo(idAdmin());
        assertThat(pagadas.getFirst().get("ip")).isEqualTo(IP_ADMIN);
        assertThat(pagadas.getFirst().get("entidad")).isEqualTo("liquidacion");
        assertThat(numero(pagadas.getFirst().get("entidad_id"))).isEqualTo(id);
        assertThat(pagadas.getFirst().get("detalle")).isEqualTo("estadoAnterior=GENERADA;numeroOperacion=OP-00123456;"
                + "cuenta=" + BANCO + "/****1234;neto=217.35;simulada=true");

        var despues = detalle(admin, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.cuenta.numeroCompleto").doesNotExist());
        assertThat(texto(despues)).doesNotContain(NUMERO_CUENTA);
        assertThat(auditoriasDe(LiquidacionAdminService.ACCION_CUENTA_CONSULTADA)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT coalesce(detalle, '') FROM auditoria", String.class))
                .noneMatch(detalle -> detalle.contains(NUMERO_CUENTA));
        var pagadasLista = listarAdmin(admin, "?grupo=pagadas").andExpect(status().isOk())
                .andExpect(jsonPath("$.liquidaciones[0].numeroOperacion").value("OP-00123456"))
                .andExpect(jsonPath("$.liquidaciones[0].pagadaEn").value(pagadaEn.toString()));
        assertThat(texto(pagadasLista)).doesNotContain(NUMERO_CUENTA);
    }

    @Test
    @DisplayName("HU-19 R29: marcar otra vez una PAGADA responde 400 ya pagada y no cambia operación, fecha ni admin")
    void yaPagada() throws Exception {
        cuentaDe(arrendadorId, NUMERO_CUENTA, null);
        var admin = tokenAdmin();
        var id = liquidacionSembrada(arrendadorId, "GENERADA", "241.50", reloj.instant(), idAdmin());
        marcarPagada(admin, id, operacion("PRIMERA-1")).andExpect(status().isOk());
        var antes = liquidacion(id);
        reloj.avanzar(Duration.ofMinutes(1));

        marcarPagada(admin, id, operacion("SEGUNDA-2")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(LiquidacionAdminService.MENSAJE_YA_PAGADA));

        assertThat(liquidacion(id)).isEqualTo(antes);
        assertThat(auditoriasDe(LiquidacionAdminService.ACCION_PAGADA)).isEqualTo(1);
    }

    @Test
    @DisplayName("HU-19 R15, R28, R36, R40: con la cuenta recién cambiada la liquidación nace GENERADA, el detalle va "
            + "enmascarado y RETENIDA, y marcarla da 400 con la fecha en Lima hasta que pasan las 72 h")
    void cuentaRetenida() throws Exception {
        verificarIdentidad(arrendadorId);
        usada(8);
        var arrendador = token(CORREO_ARRENDADOR);
        guardarCuenta(arrendador, NUMERO_CUENTA).andExpect(status().isOk());
        guardarCuenta(arrendador, OTRO_NUMERO).andExpect(status().isOk());
        var retenerHasta = reloj.instant().plus(Duration.ofHours(72));
        var admin = tokenAdmin();

        var id = idGenerada(generar(admin).andExpect(jsonPath("$.liquidaciones[0].estado").value("GENERADA")));
        var respuesta = detalle(admin, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.pagable").value(false))
                .andExpect(jsonPath("$.cuenta.estado").value("RETENIDA"))
                .andExpect(jsonPath("$.cuenta.retenidaHasta").value(retenerHasta.toString()))
                .andExpect(jsonPath("$.cuenta.numeroEnmascarado").value("****9999"))
                .andExpect(jsonPath("$.cuenta.numeroCompleto").doesNotExist());
        assertThat(texto(respuesta)).doesNotContain(OTRO_NUMERO).doesNotContain(NUMERO_CUENTA);
        assertThat(auditoriasDe(LiquidacionAdminService.ACCION_CUENTA_CONSULTADA)).isZero();

        marcarPagada(admin, id, operacion("00123456")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La cuenta bancaria del arrendador cambió hace poco. Podrás "
                        + "marcar esta liquidación como pagada desde el " + FECHA_HORA.format(retenerHasta) + "."));
        assertThat(liquidacion(id).get("estado")).isEqualTo("GENERADA");
        assertThat(auditoriasDe(LiquidacionAdminService.ACCION_PAGADA)).isZero();

        llevarRelojA(retenerHasta);
        var sesion = tokenAdminOtraVez();
        detalle(sesion, id).andExpect(jsonPath("$.pagable").value(true))
                .andExpect(jsonPath("$.cuenta.numeroCompleto").value(OTRO_NUMERO));
        marcarPagada(sesion, id, operacion("00123456")).andExpect(status().isOk())
                .andExpect(jsonPath("$.pago.cuentaEnmascarada").value("****9999"));
    }

    // La sesión del admin vence a los 30 min de inactividad: tras mover el reloj se inicia otra.
    private String tokenAdminOtraVez() throws Exception {
        return token(CORREO_ADMIN, IP_ADMIN);
    }

    @Test
    @DisplayName("HU-19 R30: sin numeroOperacion o con uno inválido responde 400 en el campo y no toca la liquidación")
    void numeroDeOperacionInvalido() throws Exception {
        cuentaDe(arrendadorId, NUMERO_CUENTA, null);
        var admin = tokenAdmin();
        var id = liquidacionSembrada(arrendadorId, "GENERADA", "241.50", reloj.instant(), idAdmin());
        var antes = liquidacion(id);
        var auditoriaAntes = filas("auditoria");

        for (var cuerpo : List.of("{}", operacion("123"), operacion("OP 1234"), operacion("A".repeat(31)),
                operacion("OP_1234"))) {
            marcarPagada(admin, id, cuerpo).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores[0].campo").value("numeroOperacion"))
                    .andExpect(jsonPath("$.errores[0].mensaje")
                            .value(MarcarLiquidacionPagadaDTO.MENSAJE_NUMERO_OPERACION));
        }

        assertThat(liquidacion(id)).isEqualTo(antes);
        assertThat(filas("auditoria")).isEqualTo(auditoriaAntes);
    }

    @Test
    @DisplayName("HU-19 R31: un id mal escrito o inexistente responde 404 en el detalle y al marcar, sin escribir nada")
    void idNoEncontrado() throws Exception {
        cuentaDe(arrendadorId, NUMERO_CUENTA, null);
        var admin = tokenAdmin();
        var id = liquidacionSembrada(arrendadorId, "GENERADA", "241.50", reloj.instant(), idAdmin());
        var antes = liquidacion(id);
        var auditoriaAntes = filas("auditoria");

        for (var malo : List.of("abc", "0", "-1", "1.5", "12345678901234567890", String.valueOf(id + 1000))) {
            detalle(admin, malo).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.mensaje").value(NO_ENCONTRADO));
            marcarPagada(admin, malo, operacion("00123456")).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.mensaje").value(NO_ENCONTRADO));
        }

        assertThat(liquidacion(id)).isEqualTo(antes);
        assertThat(filas("auditoria")).isEqualTo(auditoriaAntes);
    }

    // ── Listas ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-19 R33, R37, R40: por-pagar (por defecto) lista GENERADA y PENDIENTE_CUENTA de la más antigua a "
            + "la más reciente, de 20 en 20 con hayMas y avisos, sin número de cuenta ni escritura")
    void listaPorPagar() throws Exception {
        cuentaDe(arrendadorId, NUMERO_CUENTA, null);
        var admin = tokenAdmin();
        var base = reloj.instant().minus(Duration.ofDays(1));
        var ids = new ArrayList<Long>();
        for (var i = 0; i < 21; i++) {
            ids.add(liquidacionSembrada(arrendadorId, i % 2 == 0 ? "GENERADA" : "PENDIENTE_CUENTA", "100.00",
                    base.plus(Duration.ofMinutes(i)), idAdmin()));
        }
        pagadaSembrada(arrendadorId, base, base.plus(Duration.ofHours(1)), idAdmin(), "OP-1234");
        jdbc.update("UPDATE usuario SET estado = 'SUSPENDIDA' WHERE id = ?", arrendadorId);
        var auditoriaAntes = filas("auditoria");

        var primera = cuerpo(listarAdmin(admin, "").andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")));
        var segunda = cuerpo(listarAdmin(admin, "?grupo=por-pagar&pagina=2").andExpect(status().isOk()));

        assertThat(primera.get("pagina")).isEqualTo(1);
        assertThat(primera.get("hayMas")).isEqualTo(true);
        assertThat(lista(primera, "liquidaciones")).extracting(fila -> numero(fila.get("id")))
                .containsExactlyElementsOf(ids.subList(0, 20));
        assertThat(segunda.get("hayMas")).isEqualTo(false);
        assertThat(lista(segunda, "liquidaciones")).extracting(fila -> numero(fila.get("id")))
                .containsExactly(ids.get(20));
        Map<String, Object> fila = lista(primera, "liquidaciones").getFirst();
        assertThat(fila).containsOnlyKeys("id", "arrendadorId", "arrendadorNombre", "periodo", "generadaEn",
                "estado", "montoBruto", "comision", "montoNeto", "reservas", "simulada", "avisos", "numeroOperacion",
                "pagadaEn");
        assertThat(fila.get("numeroOperacion")).isNull();
        assertThat(fila.get("avisos")).isEqualTo(List.of("CUENTA_NO_ACTIVA", "IDENTIDAD_NO_VERIFICADA"));
        assertThat(json.writeValueAsString(primera)).doesNotContain(NUMERO_CUENTA);
        assertThat(filas("auditoria")).isEqualTo(auditoriaAntes);
    }

    @Test
    @DisplayName("HU-19 R34: pagadas lista las PAGADA de la más reciente a la más antigua con operación y fecha")
    void listaPagadas() throws Exception {
        var admin = tokenAdmin();
        var base = reloj.instant().minus(Duration.ofDays(2));
        var vieja = pagadaSembrada(arrendadorId, base, base.plus(Duration.ofHours(1)), idAdmin(), "OP-VIEJA");
        var nueva = pagadaSembrada(arrendadorId, base, base.plus(Duration.ofHours(5)), idAdmin(), "OP-NUEVA");
        liquidacionSembrada(arrendadorId, "GENERADA", "100.00", base, idAdmin());

        var pagina = cuerpo(listarAdmin(admin, "?grupo=pagadas").andExpect(status().isOk()));

        assertThat(lista(pagina, "liquidaciones")).extracting(fila -> numero(fila.get("id")))
                .containsExactly(nueva, vieja);
        assertThat(lista(pagina, "liquidaciones").getFirst().get("numeroOperacion")).isEqualTo("OP-NUEVA");
        assertThat(Instant.parse(lista(pagina, "liquidaciones").getFirst().get("pagadaEn").toString()))
                .isEqualTo(base.plus(Duration.ofHours(5)));
        assertThat(pagina.get("hayMas")).isEqualTo(false);
    }

    @Test
    @DisplayName("HU-19 R35: un grupo o una página inválidos responden 400 con un error por parámetro")
    void parametrosInvalidos() throws Exception {
        var admin = tokenAdmin();

        listarAdmin(admin, "?grupo=todas&pagina=0").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.length()").value(2))
                .andExpect(jsonPath("$.errores[0].campo").value("grupo"))
                .andExpect(jsonPath("$.errores[0].mensaje").value(LiquidacionAdminService.MENSAJE_GRUPO))
                .andExpect(jsonPath("$.errores[1].campo").value("pagina"))
                .andExpect(jsonPath("$.errores[1].mensaje").value(LiquidacionAdminService.MENSAJE_PAGINA));
        for (var pagina : List.of("abc", "-1", "1.5", "99999999999")) {
            listarAdmin(admin, "?pagina=" + pagina).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores.length()").value(1))
                    .andExpect(jsonPath("$.errores[0].campo").value("pagina"));
        }
    }

    // ── Acceso ───────────────────────────────────────────────────────────

    private List<ResultActions> lasCuatroRutas(String sesion, String origen, long id) throws Exception {
        return List.of(
                mvc.perform(desde(conToken(sesion, post(ADMIN_LIQUIDACIONES)), origen)),
                mvc.perform(desde(conToken(sesion, get(ADMIN_LIQUIDACIONES)), origen)),
                mvc.perform(desde(conToken(sesion, get(ADMIN_LIQUIDACIONES + "/" + id)), origen)),
                mvc.perform(desde(conToken(sesion, post(ADMIN_LIQUIDACIONES + "/" + id + "/pago")), origen)
                        .contentType(MediaType.APPLICATION_JSON).content(operacion("00123456"))));
    }

    private static <T extends AbstractMockHttpServletRequestBuilder<T>> T conToken(String sesion, T solicitud) {
        if (sesion != null) {
            solicitud.header("Authorization", "Bearer " + sesion);
        }
        return solicitud;
    }

    @Test
    @DisplayName("HU-19 R1: sin sesión las cuatro rutas responden 401 sin leer ni escribir nada")
    void sinSesion() throws Exception {
        usada(8);
        cuentaDe(arrendadorId, NUMERO_CUENTA, null);
        var id = liquidacionSembrada(arrendadorId, "GENERADA", "100.00", reloj.instant(), usuarioId);
        var antes = liquidaciones();

        for (var respuesta : lasCuatroRutas(null, IP_ADMIN, id)) {
            respuesta.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.mensaje").value(SESION_INVALIDA));
        }
        for (var respuesta : lasCuatroRutas("token-inventado", IP_ADMIN, id)) {
            respuesta.andExpect(status().isUnauthorized());
        }

        assertThat(liquidaciones()).isEqualTo(antes);
        assertThat(filas("liquidacion_detalle")).isZero();
        assertThat(filas("auditoria")).isZero();
    }

    @Test
    @DisplayName("HU-19 R2: un USUARIO o un ARRENDADOR recibe 403 en las cuatro rutas y nada cambia")
    void sinRolAdmin() throws Exception {
        usada(8);
        cuentaDe(arrendadorId, NUMERO_CUENTA, null);
        var id = liquidacionSembrada(arrendadorId, "GENERADA", "100.00", reloj.instant(), usuarioId);
        var sesiones = List.of(token(CORREO_USUARIO, IP_ADMIN), token(CORREO_ARRENDADOR, IP_ADMIN));
        var antes = liquidaciones();
        var auditoriaAntes = filas("auditoria");

        for (var sesion : sesiones) {
            for (var respuesta : lasCuatroRutas(sesion, IP_ADMIN, id)) {
                var texto = texto(respuesta.andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.mensaje").value(ACCESO_DENEGADO)));
                assertThat(texto).doesNotContain(NUMERO_CUENTA);
            }
        }

        assertThat(liquidaciones()).isEqualTo(antes);
        assertThat(filas("liquidacion_detalle")).isZero();
        assertThat(filas("auditoria")).isEqualTo(auditoriaAntes);
    }

    @Test
    @DisplayName("HU-19 R3: un admin desde una IP no permitida recibe 403 genérico en las cuatro rutas y nada cambia")
    void ipNoPermitida() throws Exception {
        usada(8);
        cuentaDe(arrendadorId, NUMERO_CUENTA, null);
        var admin = tokenAdmin();
        var id = liquidacionSembrada(arrendadorId, "GENERADA", "100.00", reloj.instant(), idAdmin());
        var antes = liquidaciones();
        var auditoriaAntes = filas("auditoria");

        for (var respuesta : lasCuatroRutas(admin, ip, id)) {
            respuesta.andExpect(status().isForbidden()).andExpect(jsonPath("$.mensaje").value(ACCESO_DENEGADO));
        }

        assertThat(liquidaciones()).isEqualTo(antes);
        assertThat(filas("liquidacion_detalle")).isZero();
        assertThat(filas("auditoria")).isEqualTo(auditoriaAntes);
    }

    @Test
    @DisplayName("HU-19 R41: las cuatro rutas del admin responden Cache-Control no-store, también en los errores")
    void noStore() throws Exception {
        cuentaDe(arrendadorId, NUMERO_CUENTA, null);
        var admin = tokenAdmin();
        var id = liquidacionSembrada(arrendadorId, "GENERADA", "100.00", reloj.instant(), idAdmin());

        generar(admin).andExpect(header().string("Cache-Control", "no-store"));
        listarAdmin(admin, "").andExpect(header().string("Cache-Control", "no-store"));
        detalle(admin, id).andExpect(header().string("Cache-Control", "no-store"));
        marcarPagada(admin, id, operacion("00123456")).andExpect(header().string("Cache-Control", "no-store"));
        marcarPagada(admin, id, operacion("00123456")).andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", Matchers.containsString("no-store")));
        detalle(admin, "abc").andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", Matchers.containsString("no-store")));
    }
}
