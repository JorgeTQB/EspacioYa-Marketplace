package pe.edu.ulima.espacioya.busqueda.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.busqueda.domain.EstadoResena;
import pe.edu.ulima.espacioya.busqueda.domain.Resena;
import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.TipoDocumento;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;

/**
 * La tabla {@code resena} contra PostgreSQL real. Comparte contexto con otras IT que borran {@code reserva} y
 * {@code usuario}, y {@code resena} no tiene cascada: sus filas se borran al terminar cada test.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ResenaRepositoryIT {

    private static final AtomicInteger SIGUIENTE = new AtomicInteger(1);

    @Autowired
    private ResenaRepository resenas;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private PerfilArrendadorRepository perfiles;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate transacciones;
    @Autowired
    private Clock reloj;

    private long usuarioId;
    private long otroUsuarioId;
    private long espacioId;
    private Instant ahora;

    @BeforeEach
    void prepararBase() {
        limpiar();
        ahora = reloj.instant();
        var arrendador = usuarios.save(Usuario.registrarArrendador("ana.resena@correo.pe", "x", "Ana Pérez",
                "987654321", "2026-10", ahora)).getId();
        perfiles.save(PerfilArrendador.crear(arrendador, TipoDocumento.RUC, "20100070970", "1".repeat(64),
                "Canchas del Sur SAC", "Av. Javier Prado 4600"));
        usuarioId = usuarios.save(Usuario.registrar("carla.resena@correo.pe", "x", "2026-10", ahora)).getId();
        otroUsuarioId = usuarios.save(Usuario.registrar("beto.resena@correo.pe", "x", "2026-10", ahora)).getId();
        espacioId = jdbc.queryForObject("INSERT INTO espacio (arrendador_id, nombre, tipo, direccion, zona, capacidad, "
                        + "precio_hora, descripcion, estado, creado_en, actualizado_en) VALUES (?, 'Cancha', 'CANCHA', "
                        + "'Av. Universitaria 1234', 'LOS_OLIVOS', 22, ?, 'Cancha de grass sintético.', 'PUBLICADO', "
                        + "now(), now()) RETURNING id", Long.class, arrendador, new BigDecimal("80.00"));
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM resena");
        jdbc.update("DELETE FROM reserva");
        jdbc.update("DELETE FROM foto_espacio");
        jdbc.update("DELETE FROM espacio");
        jdbc.update("DELETE FROM documento_identidad");
        jdbc.update("DELETE FROM sesion");
        jdbc.update("DELETE FROM auditoria");
        jdbc.update("DELETE FROM perfil_arrendador");
        jdbc.update("DELETE FROM usuario");
    }

    private long reserva(long usuario) {
        return jdbc.queryForObject("INSERT INTO reserva (usuario_id, espacio_id, estado, precio_hora_aplicado, "
                        + "monto_total, creada_en, expira_en) VALUES (?, ?, 'CONFIRMADA', 80.00, 80.00, ?, ?) "
                        + "RETURNING id", Long.class, usuario, espacioId, Timestamp.from(ahora),
                Timestamp.from(ahora.plus(Duration.ofMinutes(15))));
    }

    private long fila(long reserva, long usuario, int calificacion, String estado, Instant creadoEn) {
        return jdbc.queryForObject("INSERT INTO resena (reserva_id, usuario_id, espacio_id, calificacion, comentario, "
                        + "estado, creado_en) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id", Long.class, reserva, usuario,
                espacioId, calificacion, "Comentario " + SIGUIENTE.getAndIncrement(), estado, Timestamp.from(creadoEn));
    }

    private long fila(long reserva, int calificacion, String estado) {
        return fila(reserva, usuarioId, calificacion, estado, ahora);
    }

    @Test
    @DisplayName("HU-24 R1, R13: la entidad se guarda y se lee con todas sus columnas")
    void guardaYLee() {
        var reserva = reserva(usuarioId);

        var guardada = resenas.saveAndFlush(Resena.nueva(reserva, usuarioId, espacioId, 4, "Buena", ahora));

        var leida = resenas.findById(guardada.getId()).orElseThrow();
        assertThat(leida.getReservaId()).isEqualTo(reserva);
        assertThat(leida.getUsuarioId()).isEqualTo(usuarioId);
        assertThat(leida.getEspacioId()).isEqualTo(espacioId);
        assertThat(leida.getCalificacion()).isEqualTo(4);
        assertThat(leida.getComentario()).isEqualTo("Buena");
        assertThat(leida.getEstado()).isEqualTo(EstadoResena.VISIBLE);
        assertThat(resenas.existsByReservaId(reserva)).isTrue();
    }

    @Test
    @DisplayName("HU-24 R13: una segunda fila de la misma reserva choca con uq_resena_reserva")
    void unaPorReserva() {
        var reserva = reserva(usuarioId);
        fila(reserva, 5, "OCULTA");

        assertThatThrownBy(() -> fila(reserva, 4, "VISIBLE"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_resena_reserva");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6, -1})
    @DisplayName("HU-24 R18: una calificación fuera de 1 a 5 choca con ck_resena_calificacion")
    void calificacionFueraDeRango(int calificacion) {
        var reserva = reserva(usuarioId);

        assertThatThrownBy(() -> fila(reserva, calificacion, "VISIBLE"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_resena_calificacion");
    }

    @Test
    @DisplayName("HU-24 R19: un estado distinto de VISIBLE u OCULTA choca con ck_resena_estado")
    void estadoInvalido() {
        var reserva = reserva(usuarioId);

        assertThatThrownBy(() -> fila(reserva, 4, "BORRADA"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_resena_estado");
    }

    @Test
    @DisplayName("HU-24 R20: no se puede borrar una reserva con reseña")
    void reservaConResena() {
        var reserva = reserva(usuarioId);
        fila(reserva, 4, "VISIBLE");

        assertThatThrownBy(() -> jdbc.update("DELETE FROM reserva WHERE id = ?", reserva))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_resena_reserva");
    }

    @Test
    @DisplayName("HU-24 R20: no se puede borrar un espacio ni un usuario con reseña")
    void espacioYUsuarioConResena() {
        // La reseña apunta a un espacio y a un usuario sin reservas: así solo puede fallar la llave de resena.
        var arrendador = jdbc.queryForObject("SELECT arrendador_id FROM espacio WHERE id = ?", Long.class, espacioId);
        var espacioSinReservas = jdbc.queryForObject("INSERT INTO espacio (arrendador_id, nombre, tipo, direccion, "
                + "zona, capacidad, precio_hora, descripcion, estado, creado_en, actualizado_en) VALUES (?, 'Sala', "
                + "'AUDITORIO', 'Av. Universitaria 1234', 'LOS_OLIVOS', 22, 80.00, 'Sala de eventos.', 'PUBLICADO', "
                + "now(), now()) RETURNING id", Long.class, arrendador);
        jdbc.update("INSERT INTO resena (reserva_id, usuario_id, espacio_id, calificacion, comentario, estado, "
                + "creado_en) VALUES (?, ?, ?, 4, 'Buena', 'VISIBLE', ?)", reserva(usuarioId), otroUsuarioId,
                espacioSinReservas, Timestamp.from(ahora));

        assertThatThrownBy(() -> jdbc.update("DELETE FROM espacio WHERE id = ?", espacioSinReservas))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_resena_espacio");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM usuario WHERE id = ?", otroUsuarioId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_resena_usuario");
    }

    @Test
    @DisplayName("HU-24 R35: totales cuenta y suma solo las VISIBLE del espacio")
    void totalesSoloVisibles() {
        fila(reserva(usuarioId), 4, "VISIBLE");
        fila(reserva(usuarioId), 5, "VISIBLE");
        fila(reserva(usuarioId), 1, "OCULTA");

        var totales = resenas.totales(espacioId, EstadoResena.VISIBLE);

        assertThat(totales.getCantidad()).isEqualTo(2);
        assertThat(totales.getSuma()).isEqualTo(9);
    }

    @Test
    @DisplayName("HU-24 R29: sin reseñas, totales devuelve 0 y 0")
    void totalesSinResenas() {
        var totales = resenas.totales(espacioId, EstadoResena.VISIBLE);

        assertThat(totales.getCantidad()).isZero();
        assertThat(totales.getSuma()).isZero();
    }

    @Test
    @DisplayName("HU-24 R35: la consulta paginada trae solo las VISIBLE")
    void paginaSoloVisibles() {
        var visible = fila(reserva(usuarioId), 4, "VISIBLE");
        fila(reserva(usuarioId), 1, "OCULTA");

        var trozo = resenas.findByEspacioIdAndEstado(espacioId, EstadoResena.VISIBLE,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "creadoEn", "id")));

        assertThat(trozo.getContent()).extracting(Resena::getId).containsExactly(visible);
        assertThat(trozo.hasNext()).isFalse();
    }

    @Test
    @DisplayName("HU-24 R42: reemplazarComentarios toca solo las reseñas del usuario indicado")
    void reemplazaSoloLasDelUsuario() {
        var suya = fila(reserva(usuarioId), 4, "VISIBLE");
        var oculta = fila(reserva(usuarioId), 2, "OCULTA");
        var ajena = fila(reserva(otroUsuarioId), otroUsuarioId, 3, "VISIBLE", ahora);

        var cambiadas = transacciones.execute(estado -> resenas.reemplazarComentarios(usuarioId, "Sin texto."));

        assertThat(cambiadas).isEqualTo(2);
        assertThat(resenas.findById(suya).orElseThrow().getComentario()).isEqualTo("Sin texto.");
        assertThat(resenas.findById(oculta).orElseThrow().getComentario()).isEqualTo("Sin texto.");
        assertThat(resenas.findById(ajena).orElseThrow().getComentario()).isNotEqualTo("Sin texto.");
    }
}
