package pe.edu.ulima.espacioya.busqueda.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import pe.edu.ulima.espacioya.RelojControlable;
import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.comun.limite.ReglaLimite;
import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.TipoDocumento;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;
import pe.edu.ulima.espacioya.espacios.service.puertos.BloqueReservado;
import pe.edu.ulima.espacioya.espacios.service.puertos.ReservasDelEspacio;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Búsqueda pública contra PostgreSQL real. El reloj está fijo en el miércoles 2026-10-28 a las 10:00 de Lima y el
 * puerto de reservas es un doble. Cada test usa su propia IP para no compartir cupo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, BusquedaControllerIT.Dobles.class})
class BusquedaControllerIT {

    private static final String BUSQUEDA = "/api/espacios";
    private static final LocalDate HOY = LocalDate.of(2026, 10, 28);
    private static final LocalDate LUNES = LocalDate.of(2026, 11, 2);
    private static final String SURCO = "SANTIAGO_DE_SURCO";
    private static final String MENSAJE_VALIDACION = "Revisa los datos del formulario.";
    private static final AtomicInteger SIGUIENTE_IP = new AtomicInteger(1);
    private static final AtomicInteger SIGUIENTE_ARRENDADOR = new AtomicInteger(1);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RelojControlable reloj;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private PerfilArrendadorRepository perfiles;
    @Autowired
    private ReservasFalsas reservas;
    @Autowired
    private JsonMapper json;
    @Autowired
    private LimiteCatalogo limite;
    @Autowired
    private List<ReglaLimite> reglas;

    private String ip;
    private long arrendador;

    @TestConfiguration(proxyBeanMethods = false)
    static class Dobles {

        @Bean
        @Primary
        RelojControlable relojControlable() {
            return new RelojControlable(Instant.parse("2026-10-28T15:00:00Z"));
        }

        @Bean
        @Primary
        ReservasFalsas reservasFalsas() {
            return new ReservasFalsas();
        }
    }

    /** Devuelve las reservas que el test cargue por espacio; ninguna por defecto. */
    static class ReservasFalsas implements ReservasDelEspacio {

        private final Map<Long, List<BloqueReservado>> porEspacio = new ConcurrentHashMap<>();

        void cargar(long espacioId, BloqueReservado... bloques) {
            porEspacio.computeIfAbsent(espacioId, id -> new ArrayList<>()).addAll(List.of(bloques));
        }

        void limpiar() {
            porEspacio.clear();
        }

        @Override
        public List<BloqueReservado> bloquesReservados(Long espacioId, LocalDate desde, LocalDate hasta) {
            return porEspacio.getOrDefault(espacioId, List.of()).stream()
                    .filter(bloque -> !bloque.fecha().isBefore(desde) && !bloque.fecha().isAfter(hasta))
                    .toList();
        }
    }

    @BeforeEach
    void prepararBase() {
        jdbc.update("DELETE FROM suspension_cuenta");
        jdbc.update("DELETE FROM foto_espacio");
        jdbc.update("DELETE FROM espacio");
        jdbc.update("DELETE FROM documento_identidad");
        jdbc.update("DELETE FROM sesion");
        jdbc.update("DELETE FROM auditoria");
        jdbc.update("DELETE FROM perfil_arrendador");
        jdbc.update("DELETE FROM usuario");
        reservas.limpiar();
        ip = siguienteIp();
        arrendador = crearArrendador();
    }

    // HU-01…HU-09 usan 10.1.x.x a 10.9.x.x.
    private static String siguienteIp() {
        var n = SIGUIENTE_IP.getAndIncrement();
        return "10.10." + (n / 250) + "." + (n % 250 + 1);
    }

    // ── Datos ────────────────────────────────────────────────────────────

    private long crearArrendador() {
        var n = SIGUIENTE_ARRENDADOR.getAndIncrement();
        var usuario = usuarios.save(Usuario.registrarArrendador("arrendador" + n + "@correo.pe", "x", "Ana Pérez",
                "987654321", "2026-10", reloj.instant()));
        perfiles.save(PerfilArrendador.crear(usuario.getId(), TipoDocumento.RUC, "20100070970",
                String.format("%064d", n), "Canchas del Sur SAC", "Av. Javier Prado 4600"));
        return usuario.getId();
    }

    private long espacio(long arrendadorId, String tipo, String zona, String precio, String estado) {
        return jdbc.queryForObject("INSERT INTO espacio (arrendador_id, nombre, tipo, direccion, zona, capacidad, "
                        + "precio_hora, descripcion, estado, creado_en, actualizado_en) VALUES (?, ?, ?, ?, ?, ?, ?, "
                        + "?, ?, now(), now()) RETURNING id", Long.class, arrendadorId, "Espacio " + tipo, tipo,
                "Av. Secreta 123", zona, 20, new BigDecimal(precio), "Descripción privada", estado);
    }

    private long publicado(String precio) {
        return espacio(arrendador, "CANCHA", SURCO, precio, "PUBLICADO");
    }

    private void franja(long espacioId, LocalDate fecha, int inicio, int fin) {
        jdbc.update("INSERT INTO horario_semanal (espacio_id, dia_semana, hora_inicio, hora_fin) VALUES (?, ?, ?, ?)",
                espacioId, fecha.getDayOfWeek().getValue(), inicio, fin);
    }

    private String foto(long espacioId, int orden) {
        var uuid = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO foto_espacio (espacio_id, ruta_archivo, tipo_mime, orden) VALUES (?, ?, ?, ?)",
                espacioId, "espacios/" + uuid, "image/jpeg", orden);
        return uuid;
    }

    private long disponibleElLunes(String precio) {
        var id = publicado(precio);
        franja(id, LUNES, 8, 10);
        return id;
    }

    // ── Llamadas ─────────────────────────────────────────────────────────

    private ResultActions buscar(String consulta) throws Exception {
        var origen = ip;
        return mvc.perform(get(BUSQUEDA + "?" + consulta).with(peticion -> {
            peticion.setRemoteAddr(origen);
            return peticion;
        }));
    }

    private Map<String, Object> pagina(String consulta) throws Exception {
        var cuerpo = buscar(consulta).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readValue(cuerpo, new TypeReference<Map<String, Object>>() { });
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> resultados(Map<String, Object> pagina) {
        return (List<Map<String, Object>>) pagina.get("resultados");
    }

    private static List<Long> ids(Map<String, Object> pagina) {
        return resultados(pagina).stream().map(r -> ((Number) r.get("id")).longValue()).toList();
    }

    private static String lunes(String tipo, String zona) {
        return "tipo=" + tipo + "&zona=" + zona + "&fecha=" + LUNES;
    }

    // ── Búsqueda ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-10 R1, R2, R14, R15: sin sesión responde 200 solo con el tipo y la zona pedidos y seis campos")
    void buscaPorTipoYZona() throws Exception {
        var buscado = disponibleElLunes("80.00");
        var foto1 = foto(buscado, 1);
        foto(buscado, 2);
        var otroTipo = espacio(arrendador, "ESTADIO", SURCO, "50.00", "PUBLICADO");
        franja(otroTipo, LUNES, 8, 10);
        var otraZona = espacio(arrendador, "CANCHA", "MIRAFLORES", "50.00", "PUBLICADO");
        franja(otraZona, LUNES, 8, 10);

        var pagina = pagina(lunes("CANCHA", SURCO));

        assertThat(pagina).containsOnlyKeys("pagina", "resultados", "hayMas");
        assertThat(ids(pagina)).containsExactly(buscado);
        var resultado = resultados(pagina).getFirst();
        assertThat(resultado).containsOnlyKeys("id", "nombre", "zona", "zonaNombre", "precioHora", "fotoPrincipal");
        assertThat(resultado).containsEntry("nombre", "Espacio CANCHA").containsEntry("zona", SURCO)
                .containsEntry("zonaNombre", "Santiago de Surco")
                .containsEntry("fotoPrincipal", "/api/fotos/" + foto1);
        assertThat(new BigDecimal(resultado.get("precioHora").toString())).isEqualByComparingTo("80");
        assertThat(pagina).containsEntry("pagina", 1).containsEntry("hayMas", false);
    }

    @Test
    @DisplayName("HU-10 R3: excluye los espacios BORRADOR, PAUSADO, BAJA y OCULTO aunque tengan horario")
    void excluyeNoPublicados() throws Exception {
        var visible = disponibleElLunes("80.00");
        for (var estado : List.of("BORRADOR", "PAUSADO", "BAJA", "OCULTO")) {
            franja(espacio(arrendador, "CANCHA", SURCO, "10.00", estado), LUNES, 8, 10);
        }

        assertThat(ids(pagina(lunes("CANCHA", SURCO)))).containsExactly(visible);
    }

    @Test
    @DisplayName("HU-10 R4: excluye los espacios PUBLICADO de arrendadores SUSPENDIDA o ELIMINADA")
    void excluyeArrendadoresNoActivos() throws Exception {
        var visible = disponibleElLunes("80.00");
        for (var estado : List.of("SUSPENDIDA", "ELIMINADA")) {
            var otro = crearArrendador();
            jdbc.update("UPDATE usuario SET estado = ? WHERE id = ?", estado, otro);
            franja(espacio(otro, "CANCHA", SURCO, "10.00", "PUBLICADO"), LUNES, 8, 10);
        }

        assertThat(ids(pagina(lunes("CANCHA", SURCO)))).containsExactly(visible);
    }

    @Test
    @DisplayName("HU-10 R5: excluye el espacio sin franjas el día de la semana de la fecha pedida")
    void excluyeSinHorarioEseDia() throws Exception {
        var visible = disponibleElLunes("80.00");
        var soloMartes = publicado("10.00");
        franja(soloMartes, LUNES.plusDays(1), 8, 10);

        assertThat(ids(pagina(lunes("CANCHA", SURCO)))).containsExactly(visible);
    }

    @Test
    @DisplayName("HU-10 R6: excluye el espacio con la fecha pedida bloqueada")
    void excluyeFechaBloqueada() throws Exception {
        var visible = disponibleElLunes("80.00");
        var bloqueado = disponibleElLunes("10.00");
        jdbc.update("INSERT INTO bloqueo_fecha (espacio_id, fecha) VALUES (?, ?)", bloqueado, Date.valueOf(LUNES));

        assertThat(ids(pagina(lunes("CANCHA", SURCO)))).containsExactly(visible);
    }

    @Test
    @DisplayName("HU-10 R7: excluye el espacio con todas las horas del día reservadas y conserva el parcialmente libre")
    void excluyeTodoReservado() throws Exception {
        var lleno = disponibleElLunes("10.00");
        var parcial = disponibleElLunes("80.00");
        reservas.cargar(lleno, new BloqueReservado(LUNES, 8, true), new BloqueReservado(LUNES, 9, false));
        reservas.cargar(parcial, new BloqueReservado(LUNES, 8, true));

        assertThat(ids(pagina(lunes("CANCHA", SURCO)))).containsExactly(parcial);
    }

    @Test
    @DisplayName("HU-10 R8: hoy a las 10:00 de Lima excluye el espacio cuyas horas ya empezaron todas")
    void hoyExcluyeHorasEmpezadas() throws Exception {
        var empezado = publicado("10.00");
        franja(empezado, HOY, 8, 11);
        var conTarde = publicado("80.00");
        franja(conTarde, HOY, 8, 12);

        assertThat(ids(pagina("tipo=CANCHA&zona=" + SURCO + "&fecha=" + HOY))).containsExactly(conTarde);
    }

    @Test
    @DisplayName("HU-10 R9, R10, R17, R18, R20: 45 disponibles en 3 páginas de 20, 20 y 5 sin repetir, por precio e id")
    void paginaTodoElCatalogo() throws Exception {
        var precios = new HashMap<Long, Integer>();
        for (int i = 0; i < 45; i++) {
            var precio = 100 - i % 15;
            precios.put(disponibleElLunes(precio + ".00"), precio);
        }
        var esperados = precios.keySet().stream()
                .sorted(Comparator.<Long, Integer>comparing(precios::get).thenComparing(Comparator.naturalOrder()))
                .toList();

        var primera = pagina(lunes("CANCHA", SURCO) + "&pagina=1");
        var segunda = pagina(lunes("CANCHA", SURCO) + "&pagina=2");
        var tercera = pagina(lunes("CANCHA", SURCO) + "&pagina=3");

        assertThat(ids(primera)).hasSize(20);
        assertThat(ids(segunda)).hasSize(20);
        assertThat(ids(tercera)).hasSize(5);
        assertThat(List.of(primera.get("hayMas"), segunda.get("hayMas"), tercera.get("hayMas")))
                .containsExactly(true, true, false);
        var todos = new ArrayList<Long>(ids(primera));
        todos.addAll(ids(segunda));
        todos.addAll(ids(tercera));
        assertThat(new HashSet<>(todos)).hasSize(45);
        assertThat(todos).containsExactlyElementsOf(esperados);
    }

    @Test
    @DisplayName("HU-10 R10: a igual precio ordena por id ascendente")
    void empatePorId() throws Exception {
        var caro = disponibleElLunes("90.00");
        var barato1 = disponibleElLunes("50.00");
        var barato2 = disponibleElLunes("50.00");

        assertThat(ids(pagina(lunes("CANCHA", SURCO)))).containsExactly(barato1, barato2, caro);
    }

    @Test
    @DisplayName("HU-10 R11: sin disponibles responde 200 con resultados vacío y hayMas false")
    void sinResultados() throws Exception {
        disponibleElLunes("80.00");

        var pagina = pagina(lunes("AUDITORIO", SURCO));

        assertThat(resultados(pagina)).isEmpty();
        assertThat(pagina).containsEntry("hayMas", false).containsEntry("pagina", 1);
    }

    @Test
    @DisplayName("HU-10 R12: con un Bearer inexistente responde 200 igual que sin sesión")
    void tokenInvalidoComoAnonimo() throws Exception {
        var visible = disponibleElLunes("80.00");
        var origen = ip;

        mvc.perform(get(BUSQUEDA + "?" + lunes("CANCHA", SURCO))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-que-no-existe")
                        .with(peticion -> {
                            peticion.setRemoteAddr(origen);
                            return peticion;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados[0].id").value(visible));
    }

    @Test
    @DisplayName("HU-10 R16: un espacio sin foto con orden 1 aparece con fotoPrincipal null")
    void sinFotoPrincipal() throws Exception {
        var sinFoto = disponibleElLunes("80.00");

        var resultado = resultados(pagina(lunes("CANCHA", SURCO))).getFirst();

        assertThat(resultado).containsEntry("id", (int) sinFoto).containsKey("fotoPrincipal");
        assertThat(resultado.get("fotoPrincipal")).isNull();
    }

    @Test
    @DisplayName("HU-10 R19, R21: sin pagina responde la 1; una página posterior a la última viene vacía")
    void paginaPorDefectoYPosterior() throws Exception {
        for (int i = 0; i < 3; i++) {
            disponibleElLunes("80.00");
        }

        var porDefecto = pagina(lunes("CANCHA", SURCO));
        var quinta = pagina(lunes("CANCHA", SURCO) + "&pagina=5");

        assertThat(porDefecto).containsEntry("pagina", 1);
        assertThat(ids(porDefecto)).hasSize(3);
        assertThat(quinta).containsEntry("pagina", 5).containsEntry("hayMas", false);
        assertThat(resultados(quinta)).isEmpty();
    }

    @Test
    @DisplayName("HU-10 R22: los parámetros tamano, size y limit se ignoran y la página sigue siendo de 20")
    void ignoraParametrosDeTamano() throws Exception {
        for (int i = 0; i < 25; i++) {
            disponibleElLunes("80.00");
        }

        var pagina = pagina(lunes("CANCHA", SURCO) + "&tamano=100&size=100&limit=100");

        assertThat(ids(pagina)).hasSize(20);
        assertThat(pagina).containsEntry("hayMas", true);
    }

    // ── Validación ───────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-10 R23: tipo ausente o inexistente responde 400 en el campo tipo")
    void tipoInvalido() throws Exception {
        for (var consulta : List.of("zona=" + SURCO + "&fecha=" + LUNES, lunes("PISCINA", SURCO),
                lunes("cancha", SURCO))) {
            buscar(consulta).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje").value(MENSAJE_VALIDACION))
                    .andExpect(jsonPath("$.errores.length()").value(1))
                    .andExpect(jsonPath("$.errores[0].campo").value("tipo"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Elige un tipo de espacio."));
        }
    }

    @Test
    @DisplayName("HU-10 R24: zona ausente, vacía o inexistente responde 400 en el campo zona")
    void zonaInvalida() throws Exception {
        for (var consulta : List.of("tipo=CANCHA&fecha=" + LUNES, lunes("CANCHA", ""), lunes("CANCHA", "ATLANTIDA"))) {
            buscar(consulta).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores.length()").value(1))
                    .andExpect(jsonPath("$.errores[0].campo").value("zona"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Elige una zona de la lista."));
        }
    }

    @Test
    @DisplayName("HU-10 R25: fecha ausente o mal escrita responde 400 en el campo fecha")
    void fechaInvalida() throws Exception {
        for (var consulta : List.of("tipo=CANCHA&zona=" + SURCO, "tipo=CANCHA&zona=" + SURCO + "&fecha=2026-02-30",
                "tipo=CANCHA&zona=" + SURCO + "&fecha=manana")) {
            buscar(consulta).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores.length()").value(1))
                    .andExpect(jsonPath("$.errores[0].campo").value("fecha"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value("Elige una fecha válida."));
        }
    }

    @Test
    @DisplayName("HU-10 R26: una fecha fuera de hoy…hoy+55 responde 400 con el texto de fuera de ventana")
    void fechaFueraDeVentana() throws Exception {
        for (var fecha : List.of(HOY.minusDays(1), HOY.plusDays(56))) {
            buscar("tipo=CANCHA&zona=" + SURCO + "&fecha=" + fecha).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores[0].campo").value("fecha"))
                    .andExpect(jsonPath("$.errores[0].mensaje")
                            .value("Elige una fecha entre hoy y las próximas 8 semanas."));
        }
        buscar("tipo=CANCHA&zona=" + SURCO + "&fecha=" + HOY.plusDays(55)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("HU-10 R27: cada página no válida responde 400 en el campo pagina")
    void paginaInvalida() throws Exception {
        for (var pagina : List.of("0", "-1", "1.5", "abc", "", "99999999999")) {
            buscar(lunes("CANCHA", SURCO) + "&pagina=" + pagina).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores.length()").value(1))
                    .andExpect(jsonPath("$.errores[0].campo").value("pagina"))
                    .andExpect(jsonPath("$.errores[0].mensaje").value("La página no es válida."));
        }
    }

    @Test
    @DisplayName("HU-10 R28: con varios parámetros inválidos responde un error por campo en orden tipo, zona, fecha, "
            + "pagina")
    void variosErrores() throws Exception {
        buscar("tipo=PISCINA&zona=ATLANTIDA&fecha=2026-02-30&pagina=0").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_VALIDACION))
                .andExpect(jsonPath("$.errores.length()").value(4))
                .andExpect(jsonPath("$.errores[0].campo").value("tipo"))
                .andExpect(jsonPath("$.errores[1].campo").value("zona"))
                .andExpect(jsonPath("$.errores[2].campo").value("fecha"))
                .andExpect(jsonPath("$.errores[3].campo").value("pagina"));
    }

    // ── Configuración ────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-10 R39, R41: sin propiedades el cupo es 120 por minuto y la regla de la búsqueda es del grupo "
            + "catalogo")
    void cupoPorDefecto() {
        assertThat(limite.maximo()).isEqualTo(120);
        assertThat(limite.ventana()).isEqualTo(Duration.ofMinutes(1));
        assertThat(reglas).filteredOn(regla -> regla.aplicaA("GET", BUSQUEDA)).singleElement()
                .satisfies(regla -> {
                    assertThat(regla.grupo()).isEqualTo("catalogo");
                    assertThat(regla.maximo()).isEqualTo(120);
                });
    }
}
