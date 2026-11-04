package pe.edu.ulima.espacioya.cuentas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import pe.edu.ulima.espacioya.TestcontainersConfiguration;

/** {@code V6_1__perfil_usuario.sql} contra PostgreSQL real: columnas nuevas de {@code usuario} y el tipo de token. */
@SpringBootTest
// Sin volcar las solicitudes de MockMvc: R46 revisa que la salida no tenga contraseñas ni datos personales.
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import({TestcontainersConfiguration.class, PerfilSoporte.Dobles.class})
class PerfilUsuarioMigracionIT extends PerfilSoporte {

    private static final String FOTO = "perfiles/0f8fad5b-d9cb-469f-a165-70867728950e";

    private void actualizar(long id, String columna, Object valor) {
        jdbc.update("UPDATE usuario SET " + columna + " = ? WHERE id = ?", valor, id);
    }

    @Test
    @DisplayName("HU-20 R25: correo_pendiente admite un correo normalizado distinto del actual")
    void correoPendienteValido() {
        var id = crearUsuario(CORREO_USUARIO);

        actualizar(id, "correo_pendiente", "nuevo@correo.pe");

        assertThat(filaUsuario(id).get("correo_pendiente")).isEqualTo("nuevo@correo.pe");
    }

    @Test
    @DisplayName("HU-20 R25: un correo_pendiente sin normalizar o igual al correo viola ck_usuario_correo_pendiente")
    void correoPendienteInvalido() {
        var id = crearUsuario(CORREO_USUARIO);

        assertThatThrownBy(() -> actualizar(id, "correo_pendiente", "Nuevo@Correo.pe"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_usuario_correo_pendiente");
        assertThatThrownBy(() -> actualizar(id, "correo_pendiente", " nuevo@correo.pe"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_usuario_correo_pendiente");
        assertThatThrownBy(() -> actualizar(id, "correo_pendiente", CORREO_USUARIO))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_usuario_correo_pendiente");
    }

    @Test
    @DisplayName("HU-20 R33: dos cuentas pueden tener el mismo correo pendiente (sin UNIQUE que delate el correo)")
    void correoPendienteSinUnico() {
        var una = crearUsuario(CORREO_USUARIO);
        var otra = crearUsuario(CORREO_OTRO);

        actualizar(una, "correo_pendiente", "libre@correo.pe");
        actualizar(otra, "correo_pendiente", "libre@correo.pe");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario WHERE correo_pendiente = 'libre@correo.pe'",
                Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("HU-20 R45: la foto exige clave perfiles/<uuid> y un tipo JPG o PNG, o ninguno de los dos")
    void restriccionesDeLaFoto() {
        var id = crearUsuario(CORREO_USUARIO);
        var sql = "UPDATE usuario SET foto_ruta = ?, foto_tipo_mime = ? WHERE id = ?";

        jdbc.update(sql, FOTO, "image/png", id);
        assertThat(filaUsuario(id).get("foto_tipo_mime")).isEqualTo("image/png");
        jdbc.update(sql, null, null, id);

        assertThatThrownBy(() -> jdbc.update(sql, FOTO, null, id))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_usuario_foto");
        assertThatThrownBy(() -> jdbc.update(sql, null, "image/png", id))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_usuario_foto");
        assertThatThrownBy(() -> jdbc.update(sql, "espacios/0f8fad5b-d9cb-469f-a165-70867728950e", "image/png", id))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_usuario_foto");
        assertThatThrownBy(() -> jdbc.update(sql, FOTO, "application/pdf", id))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_usuario_foto");
    }

    @Test
    @DisplayName("HU-20 R25: ck_token_tipo admite CAMBIO_CORREO, conserva VERIFICACION y RECUPERACION y rechaza otros")
    void tipoDeToken() {
        var id = crearUsuario(CORREO_USUARIO);
        var sql = "INSERT INTO token (usuario_id, tipo, hash_token, creado_en, expira_en) VALUES (?, ?, ?, ?, ?)";
        var creado = Timestamp.from(reloj.instant());
        var expira = Timestamp.from(reloj.instant().plusSeconds(1800));

        jdbc.update(sql, id, "CAMBIO_CORREO", "a".repeat(64), creado, expira);
        jdbc.update(sql, id, "VERIFICACION", "b".repeat(64), creado, expira);
        jdbc.update(sql, id, "RECUPERACION", "c".repeat(64), creado, expira);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM token", Integer.class)).isEqualTo(3);
        assertThatThrownBy(() -> jdbc.update(sql, id, "OTRO", "d".repeat(64), creado, expira))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_token_tipo");
    }
}
