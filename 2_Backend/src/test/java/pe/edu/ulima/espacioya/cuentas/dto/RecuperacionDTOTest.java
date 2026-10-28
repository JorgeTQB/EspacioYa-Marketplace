package pe.edu.ulima.espacioya.cuentas.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecuperacionDTOTest {

    private static final String CORREO = "ana@correo.pe";
    private static final String TOKEN = "Q2xhdmVTZWNyZXRhRGVQcnVlYmFQYXJhSFUwNEVuTG9n";
    private static final String CONTRASENA = "Nueva12345";

    @Test
    @DisplayName("HU-04 R31: toString de la solicitud no muestra el correo")
    void solicitudEnmascarada() {
        assertThat(new SolicitudRecuperacionDTO(CORREO).toString())
                .doesNotContain(CORREO)
                .isEqualTo("SolicitudRecuperacionDTO[correo=***]");
    }

    @Test
    @DisplayName("HU-04 R31: toString de la validación no muestra el token")
    void tokenEnmascarado() {
        assertThat(new TokenRecuperacionDTO(TOKEN).toString())
                .doesNotContain(TOKEN)
                .isEqualTo("TokenRecuperacionDTO[token=***]");
    }

    @Test
    @DisplayName("HU-04 R31: toString de la confirmación no muestra el token ni la contraseña")
    void restablecimientoEnmascarado() {
        assertThat(new RestablecimientoContrasenaDTO(TOKEN, CONTRASENA).toString())
                .doesNotContain(TOKEN)
                .doesNotContain(CONTRASENA)
                .isEqualTo("RestablecimientoContrasenaDTO[token=***, contrasena=***]");
    }
}
