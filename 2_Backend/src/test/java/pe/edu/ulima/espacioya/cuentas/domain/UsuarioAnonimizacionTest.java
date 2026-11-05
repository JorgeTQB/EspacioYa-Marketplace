package pe.edu.ulima.espacioya.cuentas.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UsuarioAnonimizacionTest {

    private static final Instant AHORA = Instant.parse("2026-11-04T15:00:00Z");
    private static final String HASH_NUEVO = "$2a$12$nuevo";
    private static final String FOTO = "perfiles/11111111-1111-1111-1111-111111111111";

    private static Usuario arrendadorCompleto() {
        var cuenta = Usuario.registrarArrendador("ana@correo.pe", "$2a$12$viejo", "Ana Ríos", "987654321",
                "2026-10", AHORA);
        ReflectionTestUtils.setField(cuenta, "id", 41L);
        cuenta.solicitarCambioCorreo("ana.nueva@correo.pe");
        cuenta.cambiarFoto(FOTO, "image/png");
        return cuenta;
    }

    @Test
    @DisplayName("HU-22 R21: anonimizar deja la cuenta ELIMINADA")
    void quedaEliminada() {
        var cuenta = arrendadorCompleto();

        cuenta.anonimizar(HASH_NUEVO);

        assertThat(cuenta.getEstado()).isEqualTo(EstadoUsuario.ELIMINADA);
        assertThat(cuenta.puedeEliminarse()).isFalse();
        assertThat(cuenta.motivoRechazoInicioSesion()).contains(MotivoRechazoInicioSesion.CUENTA_NO_ACTIVA);
    }

    @Test
    @DisplayName("HU-22 R22: el correo pasa al marcador en minúsculas y nombre, teléfono, correo pendiente y foto "
            + "quedan nulos")
    void datosPersonalesNulos() {
        var cuenta = arrendadorCompleto();

        var foto = cuenta.anonimizar(HASH_NUEVO);

        assertThat(foto).isEqualTo(FOTO);
        assertThat(cuenta.getCorreo()).isEqualTo("eliminada-41@espacioya.invalid")
                .isEqualTo(Usuario.marcadorCorreo(41L))
                .isEqualTo(Usuario.normalizarCorreo(cuenta.getCorreo()));
        assertThat(cuenta.getNombre()).isNull();
        assertThat(cuenta.getTelefono()).isNull();
        assertThat(cuenta.getCorreoPendiente()).isNull();
        assertThat(cuenta.getFotoRuta()).isNull();
        assertThat(cuenta.getFotoTipoMime()).isNull();
        assertThat(cuenta.tieneFoto()).isFalse();
    }

    @Test
    @DisplayName("HU-22 R22: sin foto, anonimizar devuelve null y conserva rol y consentimiento")
    void sinFotoDevuelveNull() {
        var cuenta = Usuario.registrar("leo@correo.pe", "$2a$12$viejo", "2026-10", AHORA);
        ReflectionTestUtils.setField(cuenta, "id", 9L);

        assertThat(cuenta.anonimizar(HASH_NUEVO)).isNull();
        assertThat(cuenta.getRol()).isEqualTo(Rol.USUARIO);
        assertThat(cuenta.getVersionPolitica()).isEqualTo("2026-10");
        assertThat(cuenta.getFechaConsentimiento()).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("HU-22 R23: anonimizar reemplaza el hash de la contraseña por el nuevo")
    void hashReemplazado() {
        var cuenta = arrendadorCompleto();

        cuenta.anonimizar(HASH_NUEVO);

        assertThat(cuenta.getHashContrasena()).isEqualTo(HASH_NUEVO);
    }

    @Test
    @DisplayName("HU-22 R23: anonimizar sin hash nuevo lanza IllegalArgumentException y no cambia nada")
    void sinHashRechaza() {
        var cuenta = arrendadorCompleto();

        assertThatThrownBy(() -> cuenta.anonimizar(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(cuenta.getEstado()).isEqualTo(EstadoUsuario.ACTIVA);
        assertThat(cuenta.getCorreo()).isEqualTo("ana@correo.pe");
    }

    @Test
    @DisplayName("HU-22 R21: un admin y una cuenta suspendida o ya eliminada no se pueden eliminar")
    void adminYSuspendidaRechazadas() {
        var admin = Usuario.registrarAdmin("admin@correo.pe", "$2a$12$x", "2026-10", AHORA);
        var suspendida = arrendadorCompleto();
        suspendida.suspender();
        var eliminada = arrendadorCompleto();
        eliminada.anonimizar(HASH_NUEVO);

        assertThat(admin.puedeEliminarse()).isFalse();
        assertThat(suspendida.puedeEliminarse()).isFalse();
        assertThatThrownBy(() -> admin.anonimizar(HASH_NUEVO)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> suspendida.anonimizar(HASH_NUEVO)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> eliminada.anonimizar(HASH_NUEVO)).isInstanceOf(IllegalStateException.class);
        assertThat(admin.getCorreo()).isEqualTo("admin@correo.pe");
        assertThat(suspendida.getNombre()).isEqualTo("Ana Ríos");
    }
}
