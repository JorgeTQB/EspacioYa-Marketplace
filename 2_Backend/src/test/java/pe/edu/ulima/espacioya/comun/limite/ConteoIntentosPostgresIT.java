package pe.edu.ulima.espacioya.comun.limite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.zaxxer.hikari.HikariDataSource;

import pe.edu.ulima.espacioya.RelojControlable;
import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.comun.cifrado.HuellaCampos;
import pe.edu.ulima.espacioya.comun.error.ErrorDTO;
import pe.edu.ulima.espacioya.cuentas.controller.ReglasLimiteCuentas;

/**
 * Conteo compartido sobre PostgreSQL. La instancia A es el bean de la aplicación; la B, otro
 * {@link ConteoIntentosPostgres} con su propio pool contra la misma base: simula una segunda réplica.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, ConteoIntentosPostgresIT.Dobles.class})
class ConteoIntentosPostgresIT extends ContratoConteoIntentos {

    private static final Instant INICIO = Instant.parse("2026-10-20T15:00:00Z");
    private static final Duration VENTANA_LOGIN = Duration.ofMinutes(15);
    private static final String PATRON_HUELLA = "^[0-9a-f]{64}$";
    private static final int HILOS_POR_INSTANCIA = 10;
    private static final AtomicInteger SIGUIENTE_IP = new AtomicInteger(1);

    private static HikariDataSource otroPool;

    @Autowired
    private LimitadorIntentos limitador;
    @Autowired
    private ConteoIntentosPostgres conteo;
    @Autowired
    private DataSource datos;
    @Autowired
    private PlatformTransactionManager transacciones;
    @Autowired
    private HuellaCampos huella;
    @Autowired
    private RelojControlable reloj;
    @Autowired
    private PostgreSQLContainer postgres;
    @Autowired
    private MockMvc mvc;

    private NamedParameterJdbcTemplate jdbc;
    private ConteoIntentosPostgres conteoB;
    private LimitadorIntentos limitadorB;

    @TestConfiguration(proxyBeanMethods = false)
    static class Dobles {

        @Bean
        @Primary
        RelojControlable relojControlable() {
            return new RelojControlable(INICIO);
        }
    }

    @Override
    protected LimitadorIntentos crearLimitador(RelojControlable relojPrueba) {
        return new LimitadorIntentos(new ConteoIntentosPostgres(datos, transacciones, huella, relojPrueba));
    }

    @BeforeEach
    void preparar() {
        jdbc = new NamedParameterJdbcTemplate(datos);
        if (otroPool == null) {
            otroPool = new HikariDataSource();
            otroPool.setJdbcUrl(postgres.getJdbcUrl());
            otroPool.setUsername(postgres.getUsername());
            otroPool.setPassword(postgres.getPassword());
            otroPool.setMaximumPoolSize(HILOS_POR_INSTANCIA);
        }
        conteoB = new ConteoIntentosPostgres(otroPool, new DataSourceTransactionManager(otroPool), huella, reloj);
        limitadorB = new LimitadorIntentos(conteoB);
    }

    @AfterAll
    static void cerrarOtroPool() {
        if (otroPool != null) {
            otroPool.close();
            otroPool = null;
        }
    }

    private static String clave(String prefijo) {
        return prefijo + "|" + UUID.randomUUID();
    }

    private Map<String, Object> fila(String claveEnClaro) {
        return jdbc.queryForMap("""
                SELECT cardinality(instantes) AS cantidad, expira_en FROM intento_limite WHERE clave_hash = :clave
                """, Map.of("clave", huella.calcular(claveEnClaro)));
    }

    private int filas(String claveEnClaro) {
        var cantidad = jdbc.queryForObject("SELECT count(*) FROM intento_limite WHERE clave_hash = :clave",
                Map.of("clave", huella.calcular(claveEnClaro)), Integer.class);
        return cantidad == null ? 0 : cantidad;
    }

    private int cantidad(String claveEnClaro) {
        return ((Number) fila(claveEnClaro).get("cantidad")).intValue();
    }

    private Instant expiraEn(String claveEnClaro) {
        return jdbc.queryForObject("SELECT expira_en FROM intento_limite WHERE clave_hash = :clave",
                Map.of("clave", huella.calcular(claveEnClaro)), OffsetDateTime.class).toInstant();
    }

    private List<Instant> instantes(String claveEnClaro) {
        return jdbc.queryForList("""
                SELECT i FROM intento_limite AS l CROSS JOIN LATERAL unnest(l.instantes) AS i
                 WHERE l.clave_hash = :clave ORDER BY i
                """, Map.of("clave", huella.calcular(claveEnClaro)), OffsetDateTime.class)
                .stream().map(OffsetDateTime::toInstant).toList();
    }

    private static <T> List<T> enParalelo(List<Callable<T>> tareas) throws InterruptedException, ExecutionException {
        var salida = new CountDownLatch(1);
        try (var hilos = Executors.newFixedThreadPool(tareas.size())) {
            var futuros = new ArrayList<Future<T>>();
            for (var tarea : tareas) {
                futuros.add(hilos.submit(() -> {
                    salida.await();
                    return tarea.call();
                }));
            }
            salida.countDown();
            var resultados = new ArrayList<T>();
            for (var futuro : futuros) {
                resultados.add(futuro.get());
            }
            return resultados;
        }
    }

    private long aceptadosEnParalelo(String claveCupo) throws InterruptedException, ExecutionException {
        var tareas = new ArrayList<Callable<Boolean>>();
        for (int i = 0; i < HILOS_POR_INSTANCIA; i++) {
            tareas.add(() -> limitador.intentar(claveCupo, 5, Duration.ofMinutes(1)));
            tareas.add(() -> limitadorB.intentar(claveCupo, 5, Duration.ofMinutes(1)));
        }
        return enParalelo(tareas).stream().filter(Boolean::booleanValue).count();
    }

    @Test
    @DisplayName("HU-30 R1: el limitador de la aplicación deja cada intento como fila de intento_limite")
    void elBeanCuentaEnLaTabla() {
        var aceptado = clave("catalogo");
        var registrado = clave("login");

        limitador.intentar(aceptado, 5, Duration.ofMinutes(1));
        limitador.registrar(registrado);
        limitador.registrar(registrado);

        assertThat(cantidad(aceptado)).isEqualTo(1);
        assertThat(cantidad(registrado)).isEqualTo(2);
    }

    @Test
    @DisplayName("HU-30 R2: 3 fallos en una instancia y 2 en otra superan el límite de 5 en las dos")
    void fallosSumanEntreInstancias() {
        var claveLogin = "login|" + UUID.randomUUID() + "@correo.pe";
        for (int i = 0; i < 3; i++) {
            limitador.registrar(claveLogin);
        }
        limitadorB.registrar(claveLogin);
        assertThat(limitador.superaLimite(claveLogin, 5, VENTANA_LOGIN)).isFalse();
        assertThat(limitadorB.superaLimite(claveLogin, 5, VENTANA_LOGIN)).isFalse();

        limitadorB.registrar(claveLogin);

        assertThat(limitador.superaLimite(claveLogin, 5, VENTANA_LOGIN)).isTrue();
        assertThat(limitadorB.superaLimite(claveLogin, 5, VENTANA_LOGIN)).isTrue();
    }

    @Test
    @DisplayName("HU-30 R3: con 5 intentos aceptados repartidos entre instancias, el 6.º se rechaza en cualquiera")
    void intentarSumaEntreInstancias() {
        var claveCupo = clave("catalogo");
        for (int i = 0; i < 3; i++) {
            assertThat(limitador.intentar(claveCupo, 5, Duration.ofMinutes(1))).isTrue();
        }
        for (int i = 0; i < 2; i++) {
            assertThat(limitadorB.intentar(claveCupo, 5, Duration.ofMinutes(1))).isTrue();
        }

        assertThat(limitador.intentar(claveCupo, 5, Duration.ofMinutes(1))).isFalse();
        assertThat(limitadorB.intentar(claveCupo, 5, Duration.ofMinutes(1))).isFalse();
    }

    @Test
    @DisplayName("HU-30 R4: limpiar en una instancia borra los intentos para todas")
    void limpiarEnUnaBorraEnTodas() {
        var claveLogin = clave("login");
        for (int i = 0; i < 5; i++) {
            limitador.registrar(claveLogin);
        }
        assertThat(limitadorB.superaLimite(claveLogin, 5, VENTANA_LOGIN)).isTrue();

        limitadorB.limpiar(claveLogin);

        assertThat(limitador.superaLimite(claveLogin, 5, VENTANA_LOGIN)).isFalse();
        assertThat(limitadorB.superaLimite(claveLogin, 5, VENTANA_LOGIN)).isFalse();
        assertThat(filas(claveLogin)).isZero();
    }

    @Test
    @DisplayName("HU-30 R5: limpiar login|<correo> en una instancia desbloquea a la otra, que registró los fallos")
    void limpiarLoginCruzado() {
        var claveLogin = "login|" + UUID.randomUUID() + "@correo.pe";
        for (int i = 0; i < 5; i++) {
            limitadorB.registrar(claveLogin);
        }
        assertThat(limitadorB.superaLimite(claveLogin, 5, VENTANA_LOGIN)).isTrue();

        limitador.limpiar(claveLogin);

        assertThat(limitadorB.superaLimite(claveLogin, 5, VENTANA_LOGIN)).isFalse();
    }

    @Test
    @DisplayName("HU-30 R8: al aceptar un intento, la fila añade ahora y descarta los instantes no vigentes")
    void aceptarDescartaLosVencidos() {
        var claveCupo = clave("catalogo");
        for (int i = 0; i < 3; i++) {
            limitador.intentar(claveCupo, 5, Duration.ofMinutes(1));
        }
        reloj.avanzar(Duration.ofSeconds(30));
        limitador.intentar(claveCupo, 5, Duration.ofMinutes(1));
        assertThat(cantidad(claveCupo)).isEqualTo(4);

        reloj.avanzar(Duration.ofSeconds(30));
        assertThat(limitador.intentar(claveCupo, 5, Duration.ofMinutes(1))).isTrue();

        assertThat(instantes(claveCupo)).hasSize(2).last().isEqualTo(reloj.instant());
    }

    @Test
    @DisplayName("HU-30 R11: el instante guardado y la ventana salen del reloj de la aplicación, no de PostgreSQL")
    void instanteDelRelojDeLaAplicacion() {
        var claveCupo = clave("catalogo");
        var ahora = reloj.instant();

        limitador.intentar(claveCupo, 1, Duration.ofMinutes(1));

        assertThat(instantes(claveCupo)).containsExactly(ahora);
        assertThat(expiraEn(claveCupo)).isEqualTo(ahora.plus(Duration.ofMinutes(1)));
        // El reloj de prueba está en 2026-10 y la base en la fecha real: si contara con now(), no habría bloqueo.
        assertThat(limitador.intentar(claveCupo, 1, Duration.ofMinutes(1))).isFalse();
        reloj.avanzar(Duration.ofMinutes(1));
        assertThat(limitador.intentar(claveCupo, 1, Duration.ofMinutes(1))).isTrue();
    }

    @Test
    @DisplayName("HU-30 R14: registrar descarta los instantes anteriores o iguales a ahora menos 1 hora")
    void registrarDescartaLoMasViejoQueLaRetencion() {
        var claveLogin = clave("login");
        limitador.registrar(claveLogin);
        reloj.avanzar(Duration.ofMinutes(30));
        var mitad = reloj.instant();
        limitador.registrar(claveLogin);
        reloj.avanzar(Duration.ofMinutes(30));

        limitador.registrar(claveLogin);

        assertThat(instantes(claveLogin)).containsExactly(mitad, reloj.instant());
    }

    @RepeatedTest(5)
    @DisplayName("HU-30 R17: 20 intentar simultáneos desde dos instancias con máximo 5 aceptan exactamente 5")
    void intentarConcurrenteAceptaElMaximo() throws Exception {
        var claveCupo = clave("catalogo");

        assertThat(aceptadosEnParalelo(claveCupo)).isEqualTo(5);
        assertThat(cantidad(claveCupo)).isEqualTo(5);
    }

    @RepeatedTest(5)
    @DisplayName("HU-30 R17: con 2 intentos vigentes previos, 20 intentar simultáneos aceptan exactamente 3")
    void intentarConcurrenteAceptaElRestante() throws Exception {
        var claveCupo = clave("catalogo");
        limitador.intentar(claveCupo, 5, Duration.ofMinutes(1));
        limitadorB.intentar(claveCupo, 5, Duration.ofMinutes(1));

        assertThat(aceptadosEnParalelo(claveCupo)).isEqualTo(3);
        assertThat(cantidad(claveCupo)).isEqualTo(5);
    }

    @Test
    @DisplayName("HU-30 R18: 20 registrar simultáneos desde dos instancias guardan 20 instantes")
    void registrarConcurrenteNoPierde() throws Exception {
        var claveLogin = clave("login");
        var tareas = new ArrayList<Callable<Boolean>>();
        for (int i = 0; i < HILOS_POR_INSTANCIA; i++) {
            tareas.add(() -> {
                limitador.registrar(claveLogin);
                return true;
            });
            tareas.add(() -> {
                limitadorB.registrar(claveLogin);
                return true;
            });
        }

        enParalelo(tareas);

        assertThat(cantidad(claveLogin)).isEqualTo(20);
    }

    @Test
    @DisplayName("HU-30 R19: lo escrito por el limitador queda confirmado aunque el llamador revierta su transacción")
    void escriturasEnTransaccionPropia() {
        var registrada = clave("mfa");
        var aceptada = clave("cambio-correo");
        var limpiada = clave("login");
        limitador.registrar(limpiada);

        new TransactionTemplate(transacciones).executeWithoutResult(estado -> {
            limitador.registrar(registrada);
            limitador.intentar(aceptada, 3, Duration.ofHours(1));
            limitador.limpiar(limpiada);
            estado.setRollbackOnly();
        });

        assertThat(limitador.superaLimite(registrada, 1, VENTANA_LOGIN)).isTrue();
        assertThat(cantidad(aceptada)).isEqualTo(1);
        assertThat(filas(limpiada)).isZero();
    }

    @Test
    @DisplayName("HU-30 R21: la clave se guarda como su huella HMAC y ni el correo ni la IP aparecen en la fila")
    void claveComoHuella() {
        var correo = "ana-" + UUID.randomUUID() + "@correo.pe";
        var ip = "203.0.113." + SIGUIENTE_IP.getAndIncrement();
        var claveLogin = "login|" + correo;
        var claveIp = "catalogo|" + ip;

        limitador.registrar(claveLogin);
        limitador.intentar(claveIp, 120, Duration.ofMinutes(1));

        for (var claveEnClaro : List.of(claveLogin, claveIp)) {
            var guardada = jdbc.queryForObject("""
                    SELECT clave_hash FROM intento_limite WHERE clave_hash = :clave
                    """, Map.of("clave", huella.calcular(claveEnClaro)), String.class);
            assertThat(guardada).isEqualTo(huella.calcular(claveEnClaro)).matches(PATRON_HUELLA);
        }
        var contenido = jdbc.queryForList("SELECT CAST(row_to_json(l) AS TEXT) FROM intento_limite AS l",
                Map.of(), String.class);
        assertThat(contenido).isNotEmpty().noneMatch(texto -> texto.contains(correo) || texto.contains(ip));
    }

    @Test
    @DisplayName("HU-30 R22: intentar lleva expira_en a ahora + ventana y nunca lo hace retroceder")
    void intentarFijaExpiracion() {
        var claveCupo = clave("recuperacion");
        var inicio = reloj.instant();
        limitador.intentar(claveCupo, 10, Duration.ofMinutes(15));
        assertThat(expiraEn(claveCupo)).isEqualTo(inicio.plus(Duration.ofMinutes(15)));

        reloj.avanzar(Duration.ofMinutes(1));
        limitador.intentar(claveCupo, 10, Duration.ofMinutes(1));
        assertThat(expiraEn(claveCupo)).isEqualTo(inicio.plus(Duration.ofMinutes(15)));

        limitador.intentar(claveCupo, 10, Duration.ofMinutes(30));
        assertThat(expiraEn(claveCupo)).isEqualTo(reloj.instant().plus(Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("HU-30 R23: registrar lleva expira_en a ahora + 1 hora y nunca lo hace retroceder")
    void registrarFijaExpiracion() {
        var claveLogin = clave("login");
        limitador.registrar(claveLogin);
        assertThat(expiraEn(claveLogin)).isEqualTo(reloj.instant().plus(ConteoIntentosPostgres.RETENCION_REGISTRO));

        reloj.avanzar(Duration.ofMinutes(5));
        limitador.registrar(claveLogin);
        assertThat(expiraEn(claveLogin)).isEqualTo(reloj.instant().plus(ConteoIntentosPostgres.RETENCION_REGISTRO));

        var otra = clave("login");
        limitador.intentar(otra, 5, Duration.ofHours(2));
        var largo = expiraEn(otra);
        limitador.registrar(otra);
        assertThat(expiraEn(otra)).isEqualTo(largo);
    }

    @Test
    @DisplayName("HU-30 R24: la limpieza borra las filas con expira_en menor o igual a ahora y conserva las demás")
    void limpiezaBorraSoloVencidas() {
        var vencida = clave("catalogo");
        var enElBorde = clave("catalogo");
        var vigente = clave("catalogo");
        limitador.intentar(vencida, 5, Duration.ofSeconds(30));
        limitador.intentar(enElBorde, 5, Duration.ofMinutes(1));
        limitador.intentar(vigente, 5, Duration.ofMinutes(30));
        reloj.avanzar(Duration.ofMinutes(1));
        var vencidasAntes = jdbc.queryForObject("SELECT count(*) FROM intento_limite WHERE expira_en <= :ahora",
                Map.of("ahora", OffsetDateTime.ofInstant(reloj.instant(), reloj.getZone())), Integer.class);

        var borradas = conteo.eliminarVencidos();

        assertThat(borradas).isEqualTo(vencidasAntes).isGreaterThanOrEqualTo(2);
        assertThat(filas(vencida)).isZero();
        assertThat(filas(enElBorde)).isZero();
        assertThat(filas(vigente)).isEqualTo(1);
    }

    @Test
    @DisplayName("HU-30 R25: con la limpieza en curso en otra réplica, la pasada se omite sin borrar")
    void limpiezaSeOmiteConElBloqueoTomado() throws SQLException {
        var vencida = clave("catalogo");
        limitador.intentar(vencida, 5, Duration.ofSeconds(1));
        reloj.avanzar(Duration.ofSeconds(1));

        try (var conexion = otroPool.getConnection()) {
            conexion.setAutoCommit(false);
            try (var sentencia = conexion.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                sentencia.setLong(1, ConteoIntentosPostgres.CLAVE_BLOQUEO_LIMPIEZA);
                sentencia.execute();
            }

            assertThat(conteo.eliminarVencidos()).isZero();
            assertThat(filas(vencida)).isEqualTo(1);

            conexion.rollback();
        }
        assertThat(conteo.eliminarVencidos()).isPositive();
        assertThat(filas(vencida)).isZero();
    }

    @Test
    @DisplayName("HU-30 R16: el límite de registro por IP responde el mismo 429 sumando las dos instancias")
    void limiteDeRegistroSigueIgual() throws Exception {
        var ip = "10.30.0." + SIGUIENTE_IP.getAndIncrement();
        var reglaRegistro = new ReglaLimite("POST", "/api/usuarios", ReglasLimiteCuentas.MAXIMO_REGISTROS_POR_IP,
                ReglasLimiteCuentas.VENTANA_REGISTROS, ReglasLimiteCuentas.GRUPO_REGISTRO);
        for (int i = 0; i < 2; i++) {
            assertThat(limitadorB.intentar(reglaRegistro.clave(ip), ReglasLimiteCuentas.MAXIMO_REGISTROS_POR_IP,
                    ReglasLimiteCuentas.VENTANA_REGISTROS)).isTrue();
        }
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/usuarios").with(desde(ip)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
        }

        mvc.perform(post("/api/usuarios").with(desde(ip)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.mensaje").value(ErrorDTO.MENSAJE_DEMASIADOS_INTENTOS));
        assertThat(limitadorB.intentar(reglaRegistro.clave(ip), ReglasLimiteCuentas.MAXIMO_REGISTROS_POR_IP,
                ReglasLimiteCuentas.VENTANA_REGISTROS)).isFalse();
    }

    private static RequestPostProcessor desde(String ip) {
        return peticion -> {
            peticion.setRemoteAddr(ip);
            return peticion;
        };
    }
}
