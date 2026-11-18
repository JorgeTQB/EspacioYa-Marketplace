package pe.edu.ulima.espacioya.busqueda.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.busqueda.domain.Resena;
import pe.edu.ulima.espacioya.busqueda.domain.ResumenCalificaciones;
import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.TipoDocumento;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;

/**
 * ★ DAO de HU-24 contra PostgreSQL real (R54, R56, R57). Comparte contexto con otras IT; {@code resena} no tiene
 * cascada, así que sus filas se borran al terminar cada test.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ResenaDAOJpaIT {

    private static final AtomicInteger SIGUIENTE = new AtomicInteger(1);

    @Autowired
    private ResenaDAO dao;
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
        var arrendador = usuarios.save(Usuario.registrarArrendador("ana.dao@correo.pe", "x", "Ana Pérez",
                "987654321", "2026-10", ahora)).getId();
        perfiles.save(PerfilArrendador.crear(arrendador, TipoDocumento.RUC, "20100070970", "1".repeat(64),
                "Canchas del Sur SAC", "Av. Javier Prado 4600"));
        usuarioId = usuarios.save(Usuario.registrar("carla.dao@correo.pe", "x", "2026-10", ahora)).getId();
        otroUsuarioId = usuarios.save(Usuario.registrar("beto.dao@correo.pe", "x", "2026-10", ahora)).getId();
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

    private long fila(long usuario, int calificacion, String estado, Instant creadoEn) {
        return jdbc.queryForObject("INSERT INTO resena (reserva_id, usuario_id, espacio_id, calificacion, comentario, "
                        + "estado, creado_en) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id", Long.class, reserva(usuario),
                usuario, espacioId, calificacion, "Comentario " + SIGUIENTE.getAndIncrement(), estado,
                Timestamp.from(creadoEn));
    }

    private String comentarioDe(long id) {
        return jdbc.queryForObject("SELECT comentario FROM resena WHERE id = ?", String.class, id);
    }

    @Test
    @DisplayName("HU-24 R54: 11 visibles y una OCULTA dan dos páginas, más nuevas primero y con hayMas correcto")
    void dosPaginasSoloVisibles() {
        var visibles = new ArrayList<Long>();
        for (var i = 0; i < 11; i++) {
            visibles.add(fila(usuarioId, 4, "VISIBLE", ahora.minusSeconds(100 - i)));
        }
        fila(usuarioId, 1, "OCULTA", ahora);
        var esperadas = new ArrayList<>(visibles).reversed();

        var primera = dao.visiblesDeEspacio(espacioId, 1, 10);
        var segunda = dao.visiblesDeEspacio(espacioId, 2, 10);

        assertThat(primera.resenas()).extracting(Resena::getId).containsExactlyElementsOf(esperadas.subList(0, 10));
        assertThat(primera.hayMas()).isTrue();
        assertThat(segunda.resenas()).extracting(Resena::getId).containsExactly(esperadas.get(10));
        assertThat(segunda.hayMas()).isFalse();
    }

    @Test
    @DisplayName("HU-24 R56: el resumen promedia y cuenta solo las VISIBLE del espacio")
    void resumenSoloVisibles() {
        fila(usuarioId, 4, "VISIBLE", ahora);
        fila(usuarioId, 5, "VISIBLE", ahora);
        fila(otroUsuarioId, 1, "OCULTA", ahora);

        assertThat(dao.resumenVisibles(espacioId)).isEqualTo(new ResumenCalificaciones(new BigDecimal("4.5"), 2));
    }

    @Test
    @DisplayName("HU-24 R57: guardar una segunda reseña de la misma reserva lanza desde guardar, dentro de la transacción")
    void guardarDuplicadoLanzaDesdeGuardar() {
        var reserva = reserva(usuarioId);
        transacciones.executeWithoutResult(estado ->
                dao.guardar(Resena.nueva(reserva, usuarioId, espacioId, 4, "Primera", ahora)));

        var lanzada = transacciones.execute(estado -> {
            try {
                dao.guardar(Resena.nueva(reserva, usuarioId, espacioId, 5, "Segunda", ahora));
                return null;
            } catch (DataIntegrityViolationException conflicto) {
                // Llega aquí antes de confirmar: la transacción sigue activa y se revierte.
                assertThat(estado.isCompleted()).isFalse();
                estado.setRollbackOnly();
                return conflicto;
            }
        });

        assertThat(lanzada).isNotNull();
        assertThat(lanzada.getMostSpecificCause().getMessage()).contains("uq_resena_reserva");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM resena WHERE reserva_id = ?", Long.class, reserva))
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("HU-24 R53: delUsuario trae las del usuario en orden ascendente y anonimizarComentarios solo las suyas")
    void delUsuarioYAnonimizar() {
        var segunda = fila(usuarioId, 2, "OCULTA", ahora);
        var primera = fila(usuarioId, 4, "VISIBLE", ahora.minusSeconds(60));
        var ajena = fila(otroUsuarioId, 3, "VISIBLE", ahora);

        assertThat(dao.delUsuario(usuarioId)).extracting(Resena::getId).containsExactly(primera, segunda);

        var cambiadas = transacciones.execute(estado -> dao.anonimizarComentarios(usuarioId));

        assertThat(cambiadas).isEqualTo(2);
        assertThat(comentarioDe(primera)).isEqualTo(Resena.COMENTARIO_ELIMINADO);
        assertThat(comentarioDe(segunda)).isEqualTo(Resena.COMENTARIO_ELIMINADO);
        assertThat(comentarioDe(ajena)).isNotEqualTo(Resena.COMENTARIO_ELIMINADO);
    }

    @Test
    @DisplayName("HU-24 R53: buscar, buscarVarias y buscarParaActualizar leen la reseña por id")
    void busquedasDeModeracion() {
        var una = fila(usuarioId, 4, "VISIBLE", ahora);
        var otra = fila(usuarioId, 3, "OCULTA", ahora);

        assertThat(dao.buscar(una)).map(Resena::getId).contains(una);
        assertThat(dao.buscarVarias(List.of(una, otra, -1L))).extracting(Resena::getId)
                .containsExactlyInAnyOrder(una, otra);
        Long bloqueada = transacciones.execute(estado -> dao.buscarParaActualizar(otra).map(Resena::getId)
                .orElse(null));
        assertThat(bloqueada).isEqualTo(otra);
    }
}
