package pe.edu.ulima.espacioya.comun.limite;

import java.sql.ResultSet;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.comun.cifrado.HuellaCampos;

/**
 * Conteo compartido entre réplicas en {@code intento_limite} (HU-30): una fila por clave con los instantes vigentes.
 * "Ahora" sale del {@link Clock} de Spring, nunca de {@code now()} de PostgreSQL, y la clave se guarda como huella.
 */
@Component
public class ConteoIntentosPostgres implements ConteoIntentos {

    /** {@code registrar} no recibe ventana: conserva la más larga de RNF-SEG-17. */
    public static final Duration RETENCION_REGISTRO = Duration.ofHours(1);
    /** Distinta de {@code LiquidacionRepository.CLAVE_GENERACION} (190_019). */
    public static final long CLAVE_BLOQUEO_LIMPIEZA = 300_030L;

    private static final String UPSERT = """
            INSERT INTO intento_limite AS l (clave_hash, instantes, expira_en)
            VALUES (:clave, ARRAY[CAST(:ahora AS TIMESTAMPTZ)], :expira)
            ON CONFLICT (clave_hash) DO UPDATE
               SET instantes = ARRAY(SELECT i FROM unnest(l.instantes) AS i WHERE i > :desde ORDER BY i)
                               || CAST(:ahora AS TIMESTAMPTZ),
                   expira_en = GREATEST(l.expira_en, EXCLUDED.expira_en)
            """;
    // Contar, decidir y añadir en una sola sentencia: la fila de la clave queda bloqueada y una segunda réplica
    // reevalúa el WHERE sobre la versión ya confirmada. Sin fila devuelta, el rechazo no se registra.
    private static final String INTENTAR = UPSERT + """
             WHERE (SELECT count(*) FROM unnest(l.instantes) AS i WHERE i > :desde) < :maximo
            RETURNING clave_hash
            """;
    private static final String CONTAR_VIGENTES = """
            SELECT count(*) FROM intento_limite AS l CROSS JOIN LATERAL unnest(l.instantes) AS i
             WHERE l.clave_hash = :clave AND i > :desde
            """;
    private static final String LIMPIAR = "DELETE FROM intento_limite WHERE clave_hash = :clave";
    private static final String TOMAR_BLOQUEO_LIMPIEZA = "SELECT pg_try_advisory_xact_lock(:claveBloqueo)";
    private static final String ELIMINAR_VENCIDOS = "DELETE FROM intento_limite WHERE expira_en <= :ahora";
    private static final ResultSetExtractor<Boolean> HAY_FILA = ResultSet::next;

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate escrituras;
    private final HuellaCampos huella;
    private final Clock reloj;

    public ConteoIntentosPostgres(DataSource datos, PlatformTransactionManager transacciones,
                                  HuellaCampos huella, Clock reloj) {
        this.jdbc = new NamedParameterJdbcTemplate(datos);
        // Transacción propia: el intento queda contado aunque el llamador revierta la suya (MFA, DT-11 de HU-17),
        // y el bloqueo de la fila dura una sentencia en vez de toda la transacción del llamador.
        this.escrituras = new TransactionTemplate(transacciones);
        this.escrituras.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.huella = huella;
        this.reloj = reloj;
    }

    @Override
    public boolean intentar(String clave, int maximo, Duration ventana) {
        if (maximo < 1) {
            return false;
        }
        var ahora = ahora();
        var parametros = parametros(clave, ahora, ahora.minus(ventana), ahora.plus(ventana))
                .addValue("maximo", maximo);
        return Boolean.TRUE.equals(escrituras.execute(estado -> jdbc.query(INTENTAR, parametros, HAY_FILA)));
    }

    @Override
    public boolean superaLimite(String clave, int maximo, Duration ventana) {
        // Lo más viejo que se guarda es RETENCION_REGISTRO: con una ventana mayor se contaría de menos en silencio.
        if (ventana.compareTo(RETENCION_REGISTRO) > 0) {
            throw new IllegalArgumentException("La ventana supera la retención de los intentos registrados");
        }
        var parametros = new MapSqlParameterSource("clave", huella.calcular(clave))
                .addValue("desde", fecha(ahora().minus(ventana)));
        var vigentes = jdbc.queryForObject(CONTAR_VIGENTES, parametros, Long.class);
        return vigentes != null && vigentes >= maximo;
    }

    @Override
    public void registrar(String clave) {
        var ahora = ahora();
        var parametros = parametros(clave, ahora, ahora.minus(RETENCION_REGISTRO), ahora.plus(RETENCION_REGISTRO));
        escrituras.executeWithoutResult(estado -> jdbc.update(UPSERT, parametros));
    }

    @Override
    public void limpiar(String clave) {
        var parametros = new MapSqlParameterSource("clave", huella.calcular(clave));
        escrituras.executeWithoutResult(estado -> jdbc.update(LIMPIAR, parametros));
    }

    /** Filas vencidas borradas; 0 si otra réplica tiene la limpieza en curso. */
    public int eliminarVencidos() {
        var ahora = fecha(ahora());
        var borradas = escrituras.execute(estado -> {
            var tomado = jdbc.queryForObject(TOMAR_BLOQUEO_LIMPIEZA,
                    Map.of("claveBloqueo", CLAVE_BLOQUEO_LIMPIEZA), Boolean.class);
            if (!Boolean.TRUE.equals(tomado)) {
                return 0;
            }
            return jdbc.update(ELIMINAR_VENCIDOS, Map.of("ahora", ahora));
        });
        return borradas == null ? 0 : borradas;
    }

    private MapSqlParameterSource parametros(String clave, Instant ahora, Instant desde, Instant expira) {
        return new MapSqlParameterSource("clave", huella.calcular(clave))
                .addValue("ahora", fecha(ahora))
                .addValue("desde", fecha(desde))
                .addValue("expira", fecha(expira));
    }

    // TIMESTAMPTZ guarda microsegundos: truncar evita que el instante leído difiera del calculado.
    private Instant ahora() {
        return Instant.now(reloj).truncatedTo(ChronoUnit.MICROS);
    }

    private static OffsetDateTime fecha(Instant instante) {
        return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
    }
}
