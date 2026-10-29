package pe.edu.ulima.espacioya.cuentas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import pe.edu.ulima.espacioya.RelojControlable;
import pe.edu.ulima.espacioya.TestcontainersConfiguration;

@SpringBootTest
@Import({TestcontainersConfiguration.class, CreacionAdminServiceIT.Dobles.class})
class CreacionAdminServiceIT {

    private static final Instant INICIO = Instant.parse("2026-10-28T15:00:00Z");
    private static final String CONTRASENA = "ClaveAdmin2026";

    @Autowired
    private CreacionAdminService servicio;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PasswordEncoder encoder;
    @Autowired
    private RelojControlable reloj;
    @Autowired
    private ApplicationContext contexto;

    @TestConfiguration(proxyBeanMethods = false)
    static class Dobles {

        @Bean
        @Primary
        RelojControlable relojControlable() {
            return new RelojControlable(INICIO);
        }
    }

    @BeforeEach
    void limpiar() {
        jdbc.update("DELETE FROM auditoria");
        jdbc.update("DELETE FROM perfil_arrendador");
        jdbc.update("DELETE FROM usuario");
    }

    private static Instant instante(Object valor) {
        return valor instanceof Timestamp t ? t.toInstant() : ((OffsetDateTime) valor).toInstant();
    }

    @Test
    @DisplayName("HU-06 R15: guarda en la BD el ADMIN con el hash bcrypt de la contraseña y el consentimiento")
    void guardaElAdminEnLaBaseDeDatos() {
        var id = servicio.crear(" Admin@EspacioYa.PE ", CONTRASENA);

        var fila = jdbc.queryForMap("SELECT * FROM usuario WHERE id = ?", id);
        assertThat(fila.get("correo")).isEqualTo("admin@espacioya.pe");
        assertThat(fila.get("rol")).isEqualTo("ADMIN");
        assertThat(fila.get("estado")).isEqualTo("ACTIVA");
        assertThat(fila.get("correo_verificado")).isEqualTo(true);
        assertThat(fila.get("version_politica")).isEqualTo("2026-10");
        assertThat(instante(fila.get("fecha_consentimiento"))).isEqualTo(reloj.instant());
        var hash = (String) fila.get("hash_contrasena");
        assertThat(hash).startsWith("$2").doesNotContain(CONTRASENA);
        assertThat(encoder.matches(CONTRASENA, hash)).isTrue();
    }

    @Test
    @DisplayName("HU-06 R16: la auditoría CREACION_ADMIN lleva el id del admin y la entidad usuario, sin IP ni detalle")
    void auditaLaCreacion() {
        var id = servicio.crear("admin@espacioya.pe", CONTRASENA);

        var fila = jdbc.queryForMap("SELECT * FROM auditoria WHERE accion = 'CREACION_ADMIN'");
        assertThat(((Number) fila.get("usuario_id")).longValue()).isEqualTo(id);
        assertThat(fila.get("entidad")).isEqualTo("usuario");
        assertThat(((Number) fila.get("entidad_id")).longValue()).isEqualTo(id);
        assertThat(fila.get("ip")).isNull();
        assertThat(fila.get("detalle")).isNull();
        assertThat(instante(fila.get("fecha"))).isEqualTo(reloj.instant());
    }

    @Test
    @DisplayName("HU-06 R18: con un correo ya registrado falla y la cuenta existente no cambia")
    void correoExistenteNoCambiaNada() {
        servicio.crear("admin@espacioya.pe", CONTRASENA);
        var antes = jdbc.queryForMap("SELECT * FROM usuario");

        assertThatThrownBy(() -> servicio.crear("ADMIN@espacioya.pe", "OtraClave2026"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForList("SELECT * FROM usuario")).containsExactly(antes);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("HU-06 R19: sin espacioya.admin.crear=true la aplicación arranca sin el comando de creación de admins")
    void sinComandoPorDefecto() {
        assertThat(contexto.getBeanNamesForType(CreadorAdmin.class)).isEmpty();
    }
}
