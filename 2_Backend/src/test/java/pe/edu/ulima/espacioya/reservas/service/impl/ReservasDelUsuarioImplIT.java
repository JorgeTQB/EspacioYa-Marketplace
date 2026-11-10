package pe.edu.ulima.espacioya.reservas.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;
import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.TipoDocumento;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservaDelUsuario;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservasDelUsuario;

/** El puerto de HU-28 contra PostgreSQL real: solo reservas del usuario, con nombre y horario, sin escribir. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ReservasDelUsuarioImplIT {

    @Autowired
    private ReservasDelUsuario puerto;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private PerfilArrendadorRepository perfiles;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private Clock reloj;

    private long arrendadorId;
    private long carlaId;
    private long betoId;
    private long espacioId;
    private LocalDate fecha;

    @BeforeEach
    void prepararBase() {
        limpiar();
        var arrendador = usuarios.save(Usuario.registrarArrendador("ana.hu28@correo.pe", "x", "Ana Pérez",
                "987654321", "2026-10", reloj.instant()));
        arrendadorId = arrendador.getId();
        perfiles.save(PerfilArrendador.crear(arrendadorId, TipoDocumento.RUC, "20100070970", "1".repeat(64),
                "Canchas del Sur SAC", "Av. Javier Prado 4600"));
        carlaId = usuarios.save(Usuario.registrar("carla.hu28@correo.pe", "x", "2026-10", reloj.instant())).getId();
        betoId = usuarios.save(Usuario.registrar("beto.hu28@correo.pe", "x", "2026-10", reloj.instant())).getId();
        espacioId = espacio();
        fecha = ZonaHoraria.hoy(reloj).plusDays(7);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM reserva");
        jdbc.update("DELETE FROM foto_espacio");
        jdbc.update("DELETE FROM espacio");
        jdbc.update("DELETE FROM documento_identidad");
        jdbc.update("DELETE FROM sesion");
        jdbc.update("DELETE FROM auditoria");
        jdbc.update("DELETE FROM perfil_arrendador");
        jdbc.update("DELETE FROM usuario");
    }

    private long espacio() {
        return jdbc.queryForObject("INSERT INTO espacio (arrendador_id, nombre, tipo, direccion, zona, capacidad, "
                        + "precio_hora, descripcion, estado, creado_en, actualizado_en) VALUES (?, ?, ?, ?, ?, ?, ?, "
                        + "?, ?, now(), now()) RETURNING id", Long.class, arrendadorId, "Cancha Los Olivos", "CANCHA",
                "Av. Universitaria 1234, Los Olivos", "LOS_OLIVOS", 22, new BigDecimal("80.00"),
                "Cancha de grass sintético con iluminación.", "PUBLICADO");
    }

    /** Una reserva CONFIRMADA de 3 bloques desde {@code hora}. */
    private long reservaDe(long usuario, int hora) {
        var creada = reloj.instant().minus(Duration.ofDays(1));
        var id = jdbc.queryForObject("INSERT INTO reserva (usuario_id, espacio_id, estado, precio_hora_aplicado, "
                        + "monto_total, creada_en, expira_en) VALUES (?, ?, 'CONFIRMADA', 80.00, 240.00, ?, ?) "
                        + "RETURNING id", Long.class, usuario, espacioId, Timestamp.from(creada),
                Timestamp.from(creada.plus(Duration.ofMinutes(15))));
        for (var h = hora; h < hora + 3; h++) {
            jdbc.update("INSERT INTO reserva_bloque (reserva_id, espacio_id, fecha, hora_inicio, activo) "
                    + "VALUES (?, ?, ?, ?, true)", id, espacioId, Date.valueOf(fecha), h);
        }
        return id;
    }

    @Test
    @DisplayName("HU-28 R8: todas trae solo las reservas del usuario, con nombre del espacio, fecha y horario")
    void todasSoloLasSuyas() {
        var mia = reservaDe(carlaId, 14);
        reservaDe(betoId, 8);

        assertThat(puerto.todas(carlaId)).containsExactly(
                new ReservaDelUsuario(mia, espacioId, "Cancha Los Olivos", fecha, 14, 17));
        assertThat(puerto.todas(betoId)).hasSize(1);
        assertThat(puerto.todas(arrendadorId)).isEmpty();
    }

    @Test
    @DisplayName("HU-28 R8, R17: una ajena o inexistente da vacío y la propia sale con su espacio y horario")
    void unaAjenaOInexistente() {
        var mia = reservaDe(carlaId, 14);
        var deBeto = reservaDe(betoId, 8);

        assertThat(puerto.una(carlaId, mia)).contains(
                new ReservaDelUsuario(mia, espacioId, "Cancha Los Olivos", fecha, 14, 17));
        assertThat(puerto.una(carlaId, deBeto)).isEmpty();
        assertThat(puerto.una(carlaId, 999_999L)).isEmpty();
    }

    @Test
    @DisplayName("HU-28 R22: consultar no modifica ninguna fila de reserva")
    void noEscribe() {
        var mia = reservaDe(carlaId, 14);
        var antes = jdbc.queryForList("SELECT * FROM reserva WHERE id = ?", mia);

        puerto.todas(carlaId);
        puerto.una(carlaId, mia);

        assertThat(jdbc.queryForList("SELECT * FROM reserva WHERE id = ?", mia)).isEqualTo(antes);
    }
}
