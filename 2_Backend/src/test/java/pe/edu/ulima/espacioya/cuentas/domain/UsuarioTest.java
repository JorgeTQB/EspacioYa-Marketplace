package pe.edu.ulima.espacioya.cuentas.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UsuarioTest {

    private static final Instant AHORA = Instant.parse("2026-10-27T15:00:00Z");

    @Test
    @DisplayName("HU-01 R1, R20: registrar crea un USUARIO ACTIVA con el correo verificado")
    void registrarFijaRolEstadoYVerificacion() {
        var usuario = Usuario.registrar("ana@correo.pe", "$2a$12$hash", "2026-10", AHORA);

        assertThat(usuario.getRol()).isEqualTo(Rol.USUARIO);
        assertThat(usuario.getEstado()).isEqualTo(EstadoUsuario.ACTIVA);
        assertThat(usuario.isCorreoVerificado()).isTrue();
        assertThat(usuario.getCorreo()).isEqualTo("ana@correo.pe");
        assertThat(usuario.getHashContrasena()).isEqualTo("$2a$12$hash");
        assertThat(usuario.getVersionPolitica()).isEqualTo("2026-10");
        assertThat(usuario.getFechaConsentimiento()).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("HU-01 R5: normalizarCorreo quita los espacios de los extremos y pasa a minúsculas")
    void normalizaCorreo() {
        assertThat(Usuario.normalizarCorreo("  Ana.Perez@Correo.PE ")).isEqualTo("ana.perez@correo.pe");
        assertThat(Usuario.normalizarCorreo(null)).isNull();
    }

    @Test
    @DisplayName("HU-02 R1, R3, R4, R31: registrarArrendador crea un ARRENDADOR ACTIVA verificado con contacto")
    void registrarArrendadorFijaRolYDatos() {
        var usuario = Usuario.registrarArrendador("ana@correo.pe", "$2a$12$hash", "  Ana Pérez  ", "987654321",
                "2026-10", AHORA);

        assertThat(usuario.getRol()).isEqualTo(Rol.ARRENDADOR);
        assertThat(usuario.getEstado()).isEqualTo(EstadoUsuario.ACTIVA);
        assertThat(usuario.isCorreoVerificado()).isTrue();
        assertThat(usuario.getNombre()).isEqualTo("Ana Pérez");
        assertThat(usuario.getTelefono()).isEqualTo("987654321");
        assertThat(usuario.getCorreo()).isEqualTo("ana@correo.pe");
        assertThat(usuario.getVersionPolitica()).isEqualTo("2026-10");
        assertThat(usuario.getFechaConsentimiento()).isEqualTo(AHORA);
    }

    private static Usuario usuarioActivo() {
        return Usuario.registrar("ana@correo.pe", "$2a$12$hash", "2026-10", AHORA);
    }

    @Test
    @DisplayName("HU-03 R1: un USUARIO o ARRENDADOR activo y con correo verificado no tiene motivo de rechazo")
    void cuentaHabilitadaSinMotivo() {
        var arrendador = Usuario.registrarArrendador("beto@correo.pe", "$2a$12$hash", "Beto", "987654321",
                "2026-10", AHORA);

        assertThat(usuarioActivo().motivoRechazoInicioSesion()).isEmpty();
        assertThat(arrendador.motivoRechazoInicioSesion()).isEmpty();
    }

    @Test
    @DisplayName("HU-03 R11: correo no verificado da el motivo CORREO_NO_VERIFICADO")
    void correoNoVerificado() {
        var usuario = usuarioActivo();
        ReflectionTestUtils.setField(usuario, "correoVerificado", false);

        assertThat(usuario.motivoRechazoInicioSesion()).contains(MotivoRechazoInicioSesion.CORREO_NO_VERIFICADO);
    }

    @Test
    @DisplayName("HU-03 R12: una cuenta SUSPENDIDA o ELIMINADA da el motivo CUENTA_NO_ACTIVA")
    void cuentaNoActiva() {
        var suspendida = usuarioActivo();
        ReflectionTestUtils.setField(suspendida, "estado", EstadoUsuario.SUSPENDIDA);
        var eliminada = usuarioActivo();
        ReflectionTestUtils.setField(eliminada, "estado", EstadoUsuario.ELIMINADA);

        assertThat(suspendida.motivoRechazoInicioSesion()).contains(MotivoRechazoInicioSesion.CUENTA_NO_ACTIVA);
        assertThat(eliminada.motivoRechazoInicioSesion()).contains(MotivoRechazoInicioSesion.CUENTA_NO_ACTIVA);
    }

    @Test
    @DisplayName("HU-03 R13: una cuenta ADMIN da el motivo ROL_NO_PERMITIDO antes que cualquier otro")
    void adminNoPermitido() {
        var admin = usuarioActivo();
        ReflectionTestUtils.setField(admin, "rol", Rol.ADMIN);
        ReflectionTestUtils.setField(admin, "correoVerificado", false);

        assertThat(admin.motivoRechazoInicioSesion()).contains(MotivoRechazoInicioSesion.ROL_NO_PERMITIDO);
    }
}
