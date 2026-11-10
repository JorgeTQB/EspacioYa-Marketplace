package pe.edu.ulima.espacioya.reservas.service.recordatorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import pe.edu.ulima.espacioya.RelojControlable;
import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.comun.correo.EnvioCorreo;
import pe.edu.ulima.espacioya.comun.correo.MensajeCorreo;
import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.TipoDocumento;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;

/**
 * HU-25 CA1 contra PostgreSQL real: el proceso de recordatorios con un doble de {@link EnvioCorreo} que guarda los
 * mensajes. El reloj no se mueve: ahora es el miércoles 28/10/2026 10:00 de Lima.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, RecordatoriosReservaIT.Dobles.class})
class RecordatoriosReservaIT {

    private static final Instant AHORA = Instant.parse("2026-10-28T15:00:00Z");
    private static final LocalDate HOY = LocalDate.of(2026, 10, 28);
    private static final LocalDate MANANA = LocalDate.of(2026, 10, 29);
    private static final String CORREO_USUARIO = "carla.recordatorio@correo.pe";
    private static final String CORREO_ARRENDADOR = "ana.recordatorio@correo.pe";
    private static final String CORREO_OTRO = "beto.recordatorio@correo.pe";
    private static final String TELEFONO_USUARIO = "988777666";
    private static final String TELEFONO_ARRENDADOR = "999111222";
    private static final String RUC = "20100070970";
    private static final String ULTIMOS4 = "4242";
    private static final String NOMBRE_ESPACIO = "Cancha Los Olivos";
    private static final AtomicInteger SIGUIENTE = new AtomicInteger(1);

    @Autowired
    private RecordatoriosReserva recordatorios;
    @Autowired
    private CorreoFalso correo;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private PerfilArrendadorRepository perfiles;

    private long usuarioId;
    private long arrendadorId;

    @TestConfiguration(proxyBeanMethods = false)
    static class Dobles {

        @Bean
        @Primary
        RelojControlable relojControlable() {
            return new RelojControlable(AHORA);
        }

        @Bean
        @Primary
        CorreoFalso correoFalso() {
            return new CorreoFalso();
        }
    }

    static class CorreoFalso implements EnvioCorreo {

        private final List<MensajeCorreo> enviados = new CopyOnWriteArrayList<>();
        private volatile String fallaPara;

        @Override
        public void enviar(MensajeCorreo mensaje) {
            if (mensaje.para().equals(fallaPara)) {
                throw new IllegalStateException("el proveedor de correo falló");
            }
            enviados.add(mensaje);
        }

        List<MensajeCorreo> enviados() {
            return enviados;
        }

        void fallarPara(String destinatario) {
            fallaPara = destinatario;
        }

        void reiniciar() {
            enviados.clear();
            fallaPara = null;
        }
    }

    @BeforeEach
    void preparar() {
        jdbc.update("DELETE FROM liquidacion_detalle");
        jdbc.update("DELETE FROM liquidacion");
        jdbc.update("DELETE FROM reembolso");
        jdbc.update("DELETE FROM checkout_pago");
        jdbc.update("DELETE FROM pago");
        jdbc.update("DELETE FROM resena");
        jdbc.update("DELETE FROM reserva");
        jdbc.update("DELETE FROM bloqueo_fecha");
        jdbc.update("DELETE FROM espacio");
        jdbc.update("DELETE FROM sesion");
        jdbc.update("DELETE FROM auditoria");
        jdbc.update("DELETE FROM cuenta_bancaria");
        jdbc.update("DELETE FROM perfil_arrendador");
        jdbc.update("DELETE FROM usuario");
        correo.reiniciar();
        var usuario = Usuario.registrar(CORREO_USUARIO, "$2a$12$hash", "2026-10", AHORA);
        usuario.actualizarDatos("Carla Recordada", TELEFONO_USUARIO);
        usuarioId = usuarios.save(usuario).getId();
        usuarios.save(Usuario.registrar(CORREO_OTRO, "$2a$12$hash", "2026-10", AHORA));
        arrendadorId = usuarios.save(Usuario.registrarArrendador(CORREO_ARRENDADOR, "$2a$12$hash", "Ana Arrendadora",
                TELEFONO_ARRENDADOR, "2026-10", AHORA)).getId();
        perfiles.save(PerfilArrendador.crear(arrendadorId, TipoDocumento.RUC, RUC,
                String.format("%064d", SIGUIENTE.getAndIncrement()), "Inversiones Ocultas SAC", "Jr. Fiscal 777"));
    }

    // ── Datos ────────────────────────────────────────────────────────────

    private long espacio() {
        return jdbc.queryForObject("INSERT INTO espacio (arrendador_id, nombre, tipo, direccion, zona, capacidad, "
                        + "precio_hora, descripcion, estado, creado_en, actualizado_en) VALUES (?, ?, ?, ?, ?, ?, ?, "
                        + "?, ?, now(), now()) RETURNING id", Long.class, arrendadorId, NOMBRE_ESPACIO, "CANCHA",
                "Av. Universitaria 1234, Los Olivos", "LOS_OLIVOS", 22, new BigDecimal("80.50"),
                "Cancha de grass sintético con iluminación.", "PUBLICADO");
    }

    /** Una reserva de un bloque en su propio espacio, con un pago PAGADO que guarda los últimos 4. */
    private long reserva(String estado, LocalDate fecha, int hora) {
        var espacioId = espacio();
        var creadaEn = AHORA.minus(Duration.ofDays(3));
        var id = jdbc.queryForObject("INSERT INTO reserva (usuario_id, espacio_id, estado, precio_hora_aplicado, "
                        + "monto_total, creada_en, expira_en) VALUES (?, ?, ?, 80.50, 80.50, ?, ?) RETURNING id",
                Long.class, usuarioId, espacioId, estado, Timestamp.from(creadaEn),
                Timestamp.from(creadaEn.plus(Duration.ofMinutes(15))));
        var activo = "CONFIRMADA".equals(estado) || "PENDIENTE_PAGO".equals(estado);
        jdbc.update("INSERT INTO reserva_bloque (reserva_id, espacio_id, fecha, hora_inicio, activo) "
                + "VALUES (?, ?, ?, ?, ?)", id, espacioId, Date.valueOf(fecha), hora, activo);
        if ("CONFIRMADA".equals(estado)) {
            jdbc.update("INSERT INTO pago (reserva_id, id_transaccion_pasarela, monto, estado, ultimos4, creado_en) "
                    + "VALUES (?, ?, 80.50, 'PAGADO', ?, ?)", id, "MP-" + id, ULTIMOS4, Timestamp.from(creadaEn));
        }
        return id;
    }

    private Instant recordatorioDe(long id) {
        var marca = jdbc.queryForObject("SELECT recordatorio_enviado_en FROM reserva WHERE id = ?", Timestamp.class,
                id);
        return marca == null ? null : marca.toInstant();
    }

    private List<Map<String, Object>> auditorias() {
        return jdbc.queryForList("SELECT * FROM auditoria WHERE accion = ? ORDER BY id",
                RecordatoriosReserva.ACCION_RECORDATORIO);
    }

    private List<String> destinatarios() {
        return correo.enviados().stream().map(MensajeCorreo::para).toList();
    }

    // ── CA1 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-25 R11, R12, R13, R22: faltando 23 h, el usuario y el arrendador reciben un recordatorio y a "
            + "nadie más")
    void recordatorioAlUsuarioYAlArrendador() {
        var id = reserva("CONFIRMADA", MANANA, 9);

        assertThat(recordatorios.enviarPendientes()).isEqualTo(1);

        assertThat(destinatarios()).containsExactlyInAnyOrder(CORREO_USUARIO, CORREO_ARRENDADOR);
        assertThat(correo.enviados()).extracting(MensajeCorreo::asunto)
                .containsExactlyInAnyOrder(CorreoRecordatorioUsuario.ASUNTO, CorreoRecordatorioArrendador.ASUNTO);
        assertThat(correo.enviados()).allSatisfy(mensaje ->
                assertThat(mensaje.cuerpo()).contains(NOMBRE_ESPACIO, "29/10/2026", "09:00 a 10:00"));
        assertThat(recordatorioDe(id)).isEqualTo(AHORA);
        var registros = auditorias();
        assertThat(registros).hasSize(1);
        assertThat(registros.getFirst().get("usuario_id")).isNull();
        assertThat(registros.getFirst().get("ip")).isNull();
        assertThat(registros.getFirst().get("entidad")).isEqualTo("reserva");
        assertThat(((Number) registros.getFirst().get("entidad_id")).longValue()).isEqualTo(id);
        assertThat(registros.getFirst().get("detalle")).isEqualTo("origen=PROCESO;destinatarios=USUARIO,ARRENDADOR");
    }

    @Test
    @DisplayName("HU-25 R29: el cuerpo no tiene los últimos 4 del pago, el documento ni el contacto de la otra parte")
    void contenidoMinimo() {
        reserva("CONFIRMADA", MANANA, 9);

        recordatorios.enviarPendientes();

        assertThat(correo.enviados()).hasSize(2).allSatisfy(mensaje -> assertThat(mensaje.cuerpo())
                .doesNotContain(ULTIMOS4, RUC, "S/", "80.50", CORREO_USUARIO, CORREO_ARRENDADOR, TELEFONO_USUARIO,
                        TELEFONO_ARRENDADOR, "Carla Recordada", "Ana Arrendadora"));
    }

    @Test
    @DisplayName("HU-25 R11 (D2): una reserva confirmada que empieza en 2 h también recibe el recordatorio")
    void reservaHechaConMenosDeVeinticuatroHoras() {
        reserva("CONFIRMADA", HOY, 12);

        assertThat(recordatorios.enviarPendientes()).isEqualTo(1);

        assertThat(destinatarios()).containsExactlyInAnyOrder(CORREO_USUARIO, CORREO_ARRENDADOR);
    }

    @Test
    @DisplayName("HU-25 R14: una segunda pasada no reenvía el recordatorio")
    void segundaPasadaNoReenvia() {
        var id = reserva("CONFIRMADA", MANANA, 9);
        recordatorios.enviarPendientes();
        correo.reiniciar();

        assertThat(recordatorios.enviarPendientes()).isZero();

        assertThat(correo.enviados()).isEmpty();
        assertThat(recordatorioDe(id)).isEqualTo(AHORA);
        assertThat(auditorias()).hasSize(1);
    }

    @Test
    @DisplayName("HU-25 R15, R16: fuera de plazo (faltan 25 h) o ya empezada no envía ni marca")
    void fueraDePlazo() {
        var lejana = reserva("CONFIRMADA", MANANA, 11);
        var empezada = reserva("CONFIRMADA", HOY, 9);
        var pasada = reserva("CONFIRMADA", HOY.minusDays(1), 15);

        assertThat(recordatorios.enviarPendientes()).isZero();

        assertThat(correo.enviados()).isEmpty();
        assertThat(recordatorioDe(lejana)).isNull();
        assertThat(recordatorioDe(empezada)).isNull();
        assertThat(recordatorioDe(pasada)).isNull();
        assertThat(auditorias()).isEmpty();
    }

    @Test
    @DisplayName("HU-25 R17: una reserva pendiente, expirada o cancelada dentro del plazo no recibe recordatorio")
    void noConfirmadas() {
        var pendiente = reserva("PENDIENTE_PAGO", MANANA, 9);
        var expirada = reserva("EXPIRADA", MANANA, 9);
        var cancelada = reserva("CANCELADA", MANANA, 9);

        assertThat(recordatorios.enviarPendientes()).isZero();

        assertThat(correo.enviados()).isEmpty();
        assertThat(List.of(pendiente, expirada, cancelada)).allSatisfy(id -> assertThat(recordatorioDe(id)).isNull());
    }

    @Test
    @DisplayName("HU-25 R19: con la preferencia del usuario desactivada solo recibe el arrendador")
    void usuarioDesactivado() {
        jdbc.update("UPDATE usuario SET recordatorios_activos = false WHERE id = ?", usuarioId);
        var id = reserva("CONFIRMADA", MANANA, 9);

        assertThat(recordatorios.enviarPendientes()).isEqualTo(1);

        assertThat(destinatarios()).containsExactly(CORREO_ARRENDADOR);
        assertThat(recordatorioDe(id)).isEqualTo(AHORA);
        assertThat(auditorias().getFirst().get("detalle")).isEqualTo("origen=PROCESO;destinatarios=ARRENDADOR");
    }

    @Test
    @DisplayName("HU-25 R20: con la preferencia del arrendador desactivada solo recibe el usuario")
    void arrendadorDesactivado() {
        jdbc.update("UPDATE usuario SET recordatorios_activos = false WHERE id = ?", arrendadorId);
        reserva("CONFIRMADA", MANANA, 9);

        recordatorios.enviarPendientes();

        assertThat(destinatarios()).containsExactly(CORREO_USUARIO);
        assertThat(auditorias().getFirst().get("detalle")).isEqualTo("origen=PROCESO;destinatarios=USUARIO");
    }

    @Test
    @DisplayName("HU-25 R21: una cuenta suspendida no recibe el recordatorio")
    void cuentaSuspendida() {
        jdbc.update("UPDATE usuario SET estado = 'SUSPENDIDA' WHERE id = ?", arrendadorId);
        reserva("CONFIRMADA", MANANA, 9);

        recordatorios.enviarPendientes();

        assertThat(destinatarios()).containsExactly(CORREO_USUARIO);
    }

    @Test
    @DisplayName("HU-25 R24: si falla el correo al usuario, el arrendador lo recibe y la marca se conserva")
    void fallaUnEnvio() {
        correo.fallarPara(CORREO_USUARIO);
        var id = reserva("CONFIRMADA", MANANA, 9);

        assertThat(recordatorios.enviarPendientes()).isEqualTo(1);

        assertThat(destinatarios()).containsExactly(CORREO_ARRENDADOR);
        assertThat(recordatorioDe(id)).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("HU-25 R13: la base no deja marcar el recordatorio de una reserva pendiente o expirada")
    void restriccionDeLaMarca() {
        var pendiente = reserva("PENDIENTE_PAGO", MANANA, 9);

        assertThatThrownBy(() -> jdbc.update("UPDATE reserva SET recordatorio_enviado_en = now() WHERE id = ?",
                pendiente)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("HU-25 R10: las cuentas nuevas quedan con recordatorios_activos = true")
    void preferenciaPorDefecto() {
        var valores = jdbc.queryForList("SELECT recordatorios_activos FROM usuario", Boolean.class);

        assertThat(valores).hasSize(3).containsOnly(true);
    }
}
