package pe.edu.ulima.espacioya.espacios.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;

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
import pe.edu.ulima.espacioya.espacios.domain.DatosEspacio;
import pe.edu.ulima.espacioya.espacios.domain.Espacio;
import pe.edu.ulima.espacioya.espacios.domain.TipoEspacio;
import pe.edu.ulima.espacioya.espacios.repository.EspacioRepository;

/** Contexto real sin implementación del puerto de reservas (como en producción hasta HU-12). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DisponibilidadServiceImplIT {

    @Autowired
    private DisponibilidadService disponibilidad;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private PerfilArrendadorRepository perfiles;
    @Autowired
    private EspacioRepository espacios;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private Clock reloj;

    private long espacioId;
    private long arrendadorId;

    @BeforeEach
    void prepararBase() {
        jdbc.update("DELETE FROM foto_espacio");
        jdbc.update("DELETE FROM espacio");
        jdbc.update("DELETE FROM documento_identidad");
        jdbc.update("DELETE FROM sesion");
        jdbc.update("DELETE FROM auditoria");
        jdbc.update("DELETE FROM perfil_arrendador");
        jdbc.update("DELETE FROM usuario");
        var usuario = usuarios.save(Usuario.registrarArrendador("ana@correo.pe", "x", "Ana Pérez", "987654321",
                "2026-10", reloj.instant()));
        perfiles.save(PerfilArrendador.crear(usuario.getId(), TipoDocumento.RUC, "20100070970", "0".repeat(64),
                "Canchas del Sur SAC", "Av. Javier Prado 4600"));
        var datos = new DatosEspacio("Cancha Los Olivos", TipoEspacio.CANCHA, "Av. Universitaria 1234", "LOS_OLIVOS",
                20, new BigDecimal("80"), "Cancha de grass sintético techada.");
        espacioId = espacios.save(Espacio.crear(usuario.getId(), datos, reloj.instant())).getId();
        arrendadorId = usuario.getId();
    }

    private void franja(LocalDate fecha, int inicio, int fin) {
        jdbc.update("INSERT INTO horario_semanal (espacio_id, dia_semana, hora_inicio, hora_fin) VALUES (?, ?, ?, ?)",
                espacioId, fecha.getDayOfWeek().getValue(), inicio, fin);
    }

    @Test
    @DisplayName("HU-08 R21, R30: contra la base, un espacio publicado da sus horas libres sin el puerto de reservas")
    void horasLibresContraLaBase() {
        var fecha = ZonaHoraria.hoy(reloj).plusDays(7);
        franja(fecha, 14, 16);
        franja(fecha, 8, 10);
        jdbc.update("INSERT INTO bloqueo_fecha (espacio_id, fecha) VALUES (?, ?)", espacioId,
                Date.valueOf(fecha.plusDays(1)));

        assertThat(disponibilidad.horasLibres(espacioId, fecha)).isEmpty();

        jdbc.update("UPDATE espacio SET estado = 'PUBLICADO' WHERE id = ?", espacioId);

        assertThat(disponibilidad.horasLibres(espacioId, fecha)).containsExactly(8, 9, 14, 15);
        assertThat(disponibilidad.horasLibres(espacioId, fecha.plusDays(1))).isEmpty();
        assertThat(disponibilidad.horasLibres(espacioId, fecha.plusDays(56))).isEmpty();
    }

    @Test
    @DisplayName("HU-09 R23, R25: con el arrendador SUSPENDIDA no hay horas libres; al volver a ACTIVA, las mismas que "
            + "antes")
    void horasLibresSegunElEstadoDelArrendador() {
        var fecha = ZonaHoraria.hoy(reloj).plusDays(7);
        franja(fecha, 8, 10);
        jdbc.update("UPDATE espacio SET estado = 'PUBLICADO' WHERE id = ?", espacioId);
        var antes = disponibilidad.horasLibres(espacioId, fecha);
        assertThat(antes).containsExactly(8, 9);

        jdbc.update("UPDATE usuario SET estado = 'SUSPENDIDA' WHERE id = ?", arrendadorId);
        assertThat(disponibilidad.horasLibres(espacioId, fecha)).isEmpty();
        jdbc.update("UPDATE usuario SET estado = 'ELIMINADA' WHERE id = ?", arrendadorId);
        assertThat(disponibilidad.horasLibres(espacioId, fecha)).isEmpty();

        jdbc.update("UPDATE usuario SET estado = 'ACTIVA' WHERE id = ?", arrendadorId);
        assertThat(disponibilidad.horasLibres(espacioId, fecha)).isEqualTo(antes);
        assertThat(jdbc.queryForObject("SELECT estado FROM espacio WHERE id = ?", String.class, espacioId))
                .isEqualTo("PUBLICADO");
    }
}
