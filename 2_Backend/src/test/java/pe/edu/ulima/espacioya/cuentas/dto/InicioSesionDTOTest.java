package pe.edu.ulima.espacioya.cuentas.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InicioSesionDTOTest {

    @Test
    @DisplayName("HU-03 R35: toString no muestra el correo ni la contraseña")
    void toStringEnmascarado() {
        var dto = new InicioSesionDTO("ana@correo.pe", "Segura123!");

        assertThat(dto.toString())
                .doesNotContain("ana@correo.pe")
                .doesNotContain("Segura123!")
                .isEqualTo("InicioSesionDTO[correo=***, contrasena=***]");
    }
}
