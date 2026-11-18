package pe.edu.ulima.espacioya.espacios.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Arrays;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.TipoDocumento;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;
import pe.edu.ulima.espacioya.espacios.domain.Caracteristica;

/** Catálogo y relación de características contra PostgreSQL (HU-23, V7_1). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EspacioCaracteristicaIT {

    private static final String CORREO = "ana.caracteristicas@correo.pe";

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private PerfilArrendadorRepository perfiles;
    @Autowired
    private Clock reloj;

    private long espacioId;

    @BeforeEach
    void prepararBase() {
        limpiar();
        var arrendadorId = usuarios.save(Usuario.registrarArrendador(CORREO, "x", "Ana Pérez", "987654321",
                "2026-10", reloj.instant())).getId();
        perfiles.save(PerfilArrendador.crear(arrendadorId, TipoDocumento.RUC, "20100070970",
                String.format("%064d", 2323), "Canchas del Sur SAC", "Av. Javier Prado 4600"));
        espacioId = jdbc.queryForObject("INSERT INTO espacio (arrendador_id, nombre, tipo, direccion, zona, capacidad, "
                        + "precio_hora, descripcion, estado, creado_en, actualizado_en) VALUES (?, ?, ?, ?, ?, ?, ?, "
                        + "?, ?, now(), now()) RETURNING id", Long.class, arrendadorId, "Cancha Los Olivos", "CANCHA",
                "Av. Universitaria 1234", "LOS_OLIVOS", 22, new BigDecimal("80.00"),
                "Cancha de grass sintético con iluminación.", "PUBLICADO");
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM espacio WHERE arrendador_id IN (SELECT id FROM usuario WHERE correo = ?)", CORREO);
        jdbc.update("DELETE FROM perfil_arrendador WHERE usuario_id IN (SELECT id FROM usuario WHERE correo = ?)",
                CORREO);
        jdbc.update("DELETE FROM usuario WHERE correo = ?", CORREO);
    }

    @Test
    @DisplayName("HU-23 R10: el catálogo sembrado coincide con la lista fija del enum")
    void semillaCoincideConElEnum() {
        var codigos = jdbc.queryForList("SELECT codigo FROM caracteristica", String.class);

        assertThat(codigos).containsExactlyInAnyOrder(
                Arrays.stream(Caracteristica.values()).map(Enum::name).toArray(String[]::new));
    }

    @Test
    @DisplayName("HU-23 R10: la llave foránea rechaza un código fuera del catálogo")
    void llaveForaneaRechazaCodigoDesconocido() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO espacio_caracteristica (espacio_id, caracteristica) VALUES (?, 'PISCINA')", espacioId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("HU-23 R10: la llave primaria rechaza el mismo par espacio y característica")
    void llavePrimariaRechazaParRepetido() {
        jdbc.update("INSERT INTO espacio_caracteristica (espacio_id, caracteristica) VALUES (?, 'TECHADO')",
                espacioId);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO espacio_caracteristica (espacio_id, caracteristica) VALUES (?, 'TECHADO')", espacioId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("HU-23 R11: borrar el espacio borra en cascada sus características")
    void borrarEspacioBorraSusCaracteristicas() {
        jdbc.update("INSERT INTO espacio_caracteristica (espacio_id, caracteristica) VALUES (?, 'TECHADO'), "
                + "(?, 'VESTUARIOS')", espacioId, espacioId);

        jdbc.update("DELETE FROM espacio WHERE id = ?", espacioId);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM espacio_caracteristica WHERE espacio_id = ?",
                Integer.class, espacioId)).isZero();
    }
}
