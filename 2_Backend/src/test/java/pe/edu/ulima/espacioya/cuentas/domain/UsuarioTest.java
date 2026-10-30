package pe.edu.ulima.espacioya.cuentas.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    // HU-06 reemplaza HU-03 R13: el rol ADMIN ya no es motivo de rechazo del inicio de sesión (la IP la mira el
    // servicio), pero sí de la recuperación de contraseña hasta HU-17.
    @Test
    @DisplayName("HU-06 R6: una cuenta ADMIN da el motivo de recuperación ROL_NO_PERMITIDO antes que cualquier otro")
    void adminNoPermitido() {
        var admin = usuarioActivo();
        ReflectionTestUtils.setField(admin, "rol", Rol.ADMIN);
        ReflectionTestUtils.setField(admin, "correoVerificado", false);

        assertThat(admin.esAdmin()).isTrue();
        assertThat(admin.motivoRechazoRecuperacion()).contains(MotivoRechazoInicioSesion.ROL_NO_PERMITIDO);
    }

    @Test
    @DisplayName("HU-06 R6: fuera del admin, la regla de recuperación es la misma que la de inicio de sesión")
    void recuperacionSinAdminIgualQueInicioSesion() {
        var suspendida = usuarioActivo();
        ReflectionTestUtils.setField(suspendida, "estado", EstadoUsuario.SUSPENDIDA);

        assertThat(usuarioActivo().esAdmin()).isFalse();
        assertThat(usuarioActivo().motivoRechazoRecuperacion()).isEmpty();
        assertThat(suspendida.motivoRechazoRecuperacion()).contains(MotivoRechazoInicioSesion.CUENTA_NO_ACTIVA);
    }

    @Test
    @DisplayName("HU-06 R1: un ADMIN activo y con correo verificado no tiene motivo de rechazo del inicio de sesión")
    void adminActivoSinMotivoDeInicioSesion() {
        var admin = Usuario.registrarAdmin("admin@correo.pe", "$2a$12$hash", "2026-10", AHORA);
        var suspendido = Usuario.registrarAdmin("admin2@correo.pe", "$2a$12$hash", "2026-10", AHORA);
        ReflectionTestUtils.setField(suspendido, "estado", EstadoUsuario.SUSPENDIDA);

        assertThat(admin.motivoRechazoInicioSesion()).isEmpty();
        assertThat(suspendido.motivoRechazoInicioSesion()).contains(MotivoRechazoInicioSesion.CUENTA_NO_ACTIVA);
    }

    @Test
    @DisplayName("HU-06 R15: registrarAdmin crea un ADMIN ACTIVA con correo verificado, versión y fecha")
    void registrarAdminFijaRolYDatos() {
        var admin = Usuario.registrarAdmin("admin@correo.pe", "$2a$12$hash", "2026-10", AHORA);

        assertThat(admin.getRol()).isEqualTo(Rol.ADMIN);
        assertThat(admin.getEstado()).isEqualTo(EstadoUsuario.ACTIVA);
        assertThat(admin.isCorreoVerificado()).isTrue();
        assertThat(admin.getCorreo()).isEqualTo("admin@correo.pe");
        assertThat(admin.getHashContrasena()).isEqualTo("$2a$12$hash");
        assertThat(admin.getVersionPolitica()).isEqualTo("2026-10");
        assertThat(admin.getFechaConsentimiento()).isEqualTo(AHORA);
        assertThat(admin.getNombre()).isNull();
    }

    private static Usuario conRolYEstado(Rol rol, EstadoUsuario estado) {
        var usuario = usuarioActivo();
        ReflectionTestUtils.setField(usuario, "rol", rol);
        ReflectionTestUtils.setField(usuario, "estado", estado);
        return usuario;
    }

    @Test
    @DisplayName("HU-09 R9, R35: solo USUARIO y ARRENDADOR no eliminados son gestionables por el admin")
    void gestionablePorRolYEstado() {
        assertThat(conRolYEstado(Rol.USUARIO, EstadoUsuario.ACTIVA).esGestionablePorAdmin()).isTrue();
        assertThat(conRolYEstado(Rol.ARRENDADOR, EstadoUsuario.ACTIVA).esGestionablePorAdmin()).isTrue();
        assertThat(conRolYEstado(Rol.USUARIO, EstadoUsuario.SUSPENDIDA).esGestionablePorAdmin()).isTrue();
        assertThat(conRolYEstado(Rol.ARRENDADOR, EstadoUsuario.SUSPENDIDA).esGestionablePorAdmin()).isTrue();
        assertThat(conRolYEstado(Rol.ADMIN, EstadoUsuario.ACTIVA).esGestionablePorAdmin()).isFalse();
        assertThat(conRolYEstado(Rol.ADMIN, EstadoUsuario.SUSPENDIDA).esGestionablePorAdmin()).isFalse();
        assertThat(conRolYEstado(Rol.USUARIO, EstadoUsuario.ELIMINADA).esGestionablePorAdmin()).isFalse();
        assertThat(conRolYEstado(Rol.ARRENDADOR, EstadoUsuario.ELIMINADA).esGestionablePorAdmin()).isFalse();
    }

    @Test
    @DisplayName("HU-09 R9: puede suspenderse solo gestionable ACTIVA y reactivarse solo gestionable SUSPENDIDA")
    void banderasDeSuspensionYReactivacion() {
        var activa = conRolYEstado(Rol.ARRENDADOR, EstadoUsuario.ACTIVA);
        var suspendida = conRolYEstado(Rol.USUARIO, EstadoUsuario.SUSPENDIDA);
        var admin = conRolYEstado(Rol.ADMIN, EstadoUsuario.ACTIVA);
        var eliminada = conRolYEstado(Rol.USUARIO, EstadoUsuario.ELIMINADA);

        assertThat(activa.puedeSuspenderse()).isTrue();
        assertThat(activa.puedeReactivarse()).isFalse();
        assertThat(suspendida.puedeSuspenderse()).isFalse();
        assertThat(suspendida.puedeReactivarse()).isTrue();
        assertThat(admin.puedeSuspenderse()).isFalse();
        assertThat(admin.puedeReactivarse()).isFalse();
        assertThat(eliminada.puedeSuspenderse()).isFalse();
        assertThat(eliminada.puedeReactivarse()).isFalse();
    }

    @Test
    @DisplayName("HU-09 R15, R30: suspender y reactivar llevan ACTIVA → SUSPENDIDA → ACTIVA")
    void suspenderYReactivar() {
        var usuario = conRolYEstado(Rol.ARRENDADOR, EstadoUsuario.ACTIVA);

        usuario.suspender();
        assertThat(usuario.getEstado()).isEqualTo(EstadoUsuario.SUSPENDIDA);
        assertThat(usuario.motivoRechazoInicioSesion()).contains(MotivoRechazoInicioSesion.CUENTA_NO_ACTIVA);

        usuario.reactivar();
        assertThat(usuario.getEstado()).isEqualTo(EstadoUsuario.ACTIVA);
        assertThat(usuario.motivoRechazoInicioSesion()).isEmpty();
    }

    @Test
    @DisplayName("HU-09 R35, R36: suspender una cuenta SUSPENDIDA, ADMIN o ELIMINADA lanza sin cambiar el estado")
    void suspenderFueraDeEstadoLanza() {
        var suspendida = conRolYEstado(Rol.USUARIO, EstadoUsuario.SUSPENDIDA);
        var admin = conRolYEstado(Rol.ADMIN, EstadoUsuario.ACTIVA);
        var eliminada = conRolYEstado(Rol.ARRENDADOR, EstadoUsuario.ELIMINADA);

        assertThatThrownBy(suspendida::suspender).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(admin::suspender).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(eliminada::suspender).isInstanceOf(IllegalStateException.class);
        assertThat(suspendida.getEstado()).isEqualTo(EstadoUsuario.SUSPENDIDA);
        assertThat(admin.getEstado()).isEqualTo(EstadoUsuario.ACTIVA);
        assertThat(eliminada.getEstado()).isEqualTo(EstadoUsuario.ELIMINADA);
    }

    @Test
    @DisplayName("HU-09 R35, R37: reactivar una cuenta ACTIVA, ADMIN o ELIMINADA lanza sin cambiar el estado")
    void reactivarFueraDeEstadoLanza() {
        var activa = conRolYEstado(Rol.USUARIO, EstadoUsuario.ACTIVA);
        var admin = conRolYEstado(Rol.ADMIN, EstadoUsuario.SUSPENDIDA);
        var eliminada = conRolYEstado(Rol.ARRENDADOR, EstadoUsuario.ELIMINADA);

        assertThatThrownBy(activa::reactivar).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(admin::reactivar).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(eliminada::reactivar).isInstanceOf(IllegalStateException.class);
        assertThat(activa.getEstado()).isEqualTo(EstadoUsuario.ACTIVA);
        assertThat(admin.getEstado()).isEqualTo(EstadoUsuario.SUSPENDIDA);
        assertThat(eliminada.getEstado()).isEqualTo(EstadoUsuario.ELIMINADA);
    }

    @Test
    @DisplayName("HU-04 R18: cambiarContrasena reemplaza el hash de la contraseña sin tocar el resto de la cuenta")
    void cambiarContrasenaReemplazaElHash() {
        var usuario = usuarioActivo();

        usuario.cambiarContrasena("$2a$12$nuevohash");

        assertThat(usuario.getHashContrasena()).isEqualTo("$2a$12$nuevohash");
        assertThat(usuario.getCorreo()).isEqualTo("ana@correo.pe");
        assertThat(usuario.getRol()).isEqualTo(Rol.USUARIO);
        assertThat(usuario.getEstado()).isEqualTo(EstadoUsuario.ACTIVA);
    }
}
