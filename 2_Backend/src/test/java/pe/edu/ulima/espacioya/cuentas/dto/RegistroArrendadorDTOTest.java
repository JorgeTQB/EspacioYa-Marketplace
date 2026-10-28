package pe.edu.ulima.espacioya.cuentas.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RegistroArrendadorDTOTest {

    @Test
    @DisplayName("HU-02 R41: toString no muestra contraseña, correo, nombre, teléfono, documento ni facturación")
    void toStringEnmascarado() {
        var dto = new RegistroArrendadorDTO("ana@correo.pe", "Segura123!", true, "2026-10", "Ana Pérez",
                "987654321", "RUC", "20100070970", "Canchas del Sur SAC", "Av. Javier Prado 4600");

        assertThat(dto.toString())
                .doesNotContain("ana@correo.pe")
                .doesNotContain("Segura123!")
                .doesNotContain("Ana Pérez")
                .doesNotContain("987654321")
                .doesNotContain("20100070970")
                .doesNotContain("Canchas del Sur SAC")
                .doesNotContain("Av. Javier Prado 4600")
                .contains("tipoDocumento=RUC")
                .contains("versionPolitica=2026-10")
                .contains("aceptaPrivacidad=true");
    }
}
