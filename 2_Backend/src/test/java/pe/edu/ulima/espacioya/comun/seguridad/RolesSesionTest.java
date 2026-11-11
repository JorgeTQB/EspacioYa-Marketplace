package pe.edu.ulima.espacioya.comun.seguridad;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;

class RolesSesionTest {

    @Test
    @DisplayName("HU-27 R26, R62: sin sesión o con rol USUARIO o ARRENDADOR, exigirAdmin lanza 403")
    void otrosRolesSeRechazan() {
        assertThatThrownBy(() -> RolesSesion.exigirAdmin(null)).isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> RolesSesion.exigirAdmin(new UsuarioAutenticado(1L, 2L, "USUARIO")))
                .isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> RolesSesion.exigirAdmin(new UsuarioAutenticado(1L, 2L, "ARRENDADOR")))
                .isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> RolesSesion.exigirAdmin(new UsuarioAutenticado(1L, 2L, "admin")))
                .isInstanceOf(AccesoDenegadoException.class);
    }

    @Test
    @DisplayName("HU-27 R26: con rol ADMIN, exigirAdmin no lanza")
    void adminPasa() {
        assertThatCode(() -> RolesSesion.exigirAdmin(new UsuarioAutenticado(1L, 2L, "ADMIN")))
                .doesNotThrowAnyException();
    }
}
