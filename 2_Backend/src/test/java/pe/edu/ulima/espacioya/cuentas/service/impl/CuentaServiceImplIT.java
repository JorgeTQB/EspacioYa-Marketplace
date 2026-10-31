package pe.edu.ulima.espacioya.cuentas.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.cuentas.service.CuentaService;

/** Los métodos sin rol de {@link CuentaService} que usan otros módulos (HU-09 D1), contra la BD real. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CuentaServiceImplIT {

    private static final Timestamp CONSENTIMIENTO = Timestamp.from(Instant.parse("2026-10-29T15:00:00Z"));

    @Autowired
    private CuentaService cuentas;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM suspension_cuenta");
        jdbc.update("DELETE FROM revision_identidad");
        jdbc.update("DELETE FROM sesion");
        jdbc.update("DELETE FROM auditoria");
        jdbc.update("DELETE FROM perfil_arrendador");
        jdbc.update("DELETE FROM usuario");
    }

    private long insertar(String correo, String rol, String estado) {
        return jdbc.queryForObject("""
                INSERT INTO usuario (correo, hash_contrasena, rol, correo_verificado, estado, version_politica,
                                     fecha_consentimiento)
                VALUES (?, '$2a$12$hash', ?, TRUE, ?, '2026-10', ?) RETURNING id
                """, Long.class, correo, rol, estado, CONSENTIMIENTO);
    }

    @Test
    @DisplayName("HU-09 R27: cuentaActiva es true para una cuenta ACTIVA y false para SUSPENDIDA, ELIMINADA o "
            + "inexistente, sin sesión")
    void cuentaActivaContraLaBaseDeDatos() {
        var activa = insertar("activa@correo.pe", "ARRENDADOR", "ACTIVA");
        var suspendida = insertar("suspendida@correo.pe", "ARRENDADOR", "SUSPENDIDA");
        var eliminada = insertar("eliminada@correo.pe", "USUARIO", "ELIMINADA");

        assertThat(cuentas.cuentaActiva(activa)).isTrue();
        assertThat(cuentas.cuentaActiva(suspendida)).isFalse();
        assertThat(cuentas.cuentaActiva(eliminada)).isFalse();
        assertThat(cuentas.cuentaActiva(eliminada + 1000)).isFalse();
    }

    @Test
    @DisplayName("HU-09 R28: arrendadoresNoActivos trae los ARRENDADOR SUSPENDIDA y ELIMINADA, sin USUARIO ni ADMIN "
            + "suspendidos ni arrendadores activos")
    void arrendadoresNoActivosContraLaBaseDeDatos() {
        var suspendido = insertar("arr.suspendido@correo.pe", "ARRENDADOR", "SUSPENDIDA");
        var eliminado = insertar("arr.eliminado@correo.pe", "ARRENDADOR", "ELIMINADA");
        insertar("arr.activo@correo.pe", "ARRENDADOR", "ACTIVA");
        insertar("usu.suspendido@correo.pe", "USUARIO", "SUSPENDIDA");
        insertar("admin.suspendido@correo.pe", "ADMIN", "SUSPENDIDA");

        assertThat(cuentas.arrendadoresNoActivos()).containsExactlyInAnyOrder(suspendido, eliminado);
    }

    @Test
    @DisplayName("HU-09 R28: arrendadoresNoActivos devuelve un conjunto vacío si todos los arrendadores están activos")
    void arrendadoresNoActivosVacio() {
        insertar("arr.activo@correo.pe", "ARRENDADOR", "ACTIVA");
        insertar("usu.suspendido@correo.pe", "USUARIO", "SUSPENDIDA");

        assertThat(cuentas.arrendadoresNoActivos()).isEmpty();
    }
}
