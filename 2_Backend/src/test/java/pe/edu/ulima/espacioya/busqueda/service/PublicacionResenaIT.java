package pe.edu.ulima.espacioya.busqueda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.busqueda.dto.CrearResenaDTO;
import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.TipoDocumento;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;

/**
 * ★ La cadena real de HU-24 con el contexto completo y PostgreSQL: la auditoría va dentro de la transacción de
 * {@link PublicacionTransaccional}, así que si falla no queda la reseña (R51).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PublicacionResenaIT {

    @Autowired
    private ResenaService servicio;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private PerfilArrendadorRepository perfiles;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private Clock reloj;
    @MockitoSpyBean
    private AuditoriaService auditoria;

    private long usuarioId;
    private long reservaId;

    @BeforeEach
    void prepararBase() {
        limpiar();
        var ahora = reloj.instant();
        var arrendador = usuarios.save(Usuario.registrarArrendador("ana.cadena@correo.pe", "x", "Ana Pérez",
                "987654321", "2026-10", ahora)).getId();
        perfiles.save(PerfilArrendador.crear(arrendador, TipoDocumento.RUC, "20100070970", "1".repeat(64),
                "Canchas del Sur SAC", "Av. Javier Prado 4600"));
        usuarioId = usuarios.save(Usuario.registrar("carla.cadena@correo.pe", "x", "2026-10", ahora)).getId();
        var espacioId = jdbc.queryForObject("INSERT INTO espacio (arrendador_id, nombre, tipo, direccion, zona, "
                        + "capacidad, precio_hora, descripcion, estado, creado_en, actualizado_en) VALUES (?, 'Cancha', "
                        + "'CANCHA', 'Av. Universitaria 1234', 'LOS_OLIVOS', 22, ?, 'Cancha de grass sintético.', "
                        + "'PUBLICADO', now(), now()) RETURNING id", Long.class, arrendador, new BigDecimal("80.00"));
        // Confirmada y terminada hace dos días: reseñable.
        var pagadaEn = ahora.minus(Duration.ofDays(30));
        reservaId = jdbc.queryForObject("INSERT INTO reserva (usuario_id, espacio_id, estado, precio_hora_aplicado, "
                        + "monto_total, creada_en, expira_en) VALUES (?, ?, 'CONFIRMADA', 80.00, 80.00, ?, ?) "
                        + "RETURNING id", Long.class, usuarioId, espacioId,
                Timestamp.from(pagadaEn.minus(Duration.ofMinutes(15))), Timestamp.from(pagadaEn));
        jdbc.update("INSERT INTO reserva_bloque (reserva_id, espacio_id, fecha, hora_inicio, activo) "
                + "VALUES (?, ?, ?, 9, true)", reservaId, espacioId, Date.valueOf(ZonaHoraria.hoy(reloj).minusDays(2)));
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

    private long filasDeResena() {
        return jdbc.queryForObject("SELECT count(*) FROM resena", Long.class);
    }

    @Test
    @DisplayName("HU-24 R51: si el registro de auditoría falla, la reseña no queda en la tabla")
    void auditoriaFallaSinFila() {
        var caida = new IllegalStateException("auditoría caída");
        doThrow(caida).when(auditoria).registrar(any());
        var usuario = new UsuarioAutenticado(usuarioId, 1L, "USUARIO");

        assertThatThrownBy(() -> servicio.crear(usuario, new CrearResenaDTO(reservaId, 5, "Excelente"), "10.24.9.9"))
                .isSameAs(caida);

        assertThat(filasDeResena()).isZero();
    }

    @Test
    @DisplayName("HU-24 R44, R51: con la auditoría funcionando, la cadena real deja la reseña y su auditoría")
    void cadenaRealGuardaYAudita() {
        var usuario = new UsuarioAutenticado(usuarioId, 1L, "USUARIO");

        var creada = servicio.crear(usuario, new CrearResenaDTO(reservaId, 5, "<b>Excelente</b>"), "10.24.9.9");

        assertThat(creada.comentario()).isEqualTo("Excelente");
        assertThat(filasDeResena()).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE accion = 'RESENA_CREADA' "
                + "AND entidad_id = ?", Long.class, creada.id())).isEqualTo(1L);
    }
}
