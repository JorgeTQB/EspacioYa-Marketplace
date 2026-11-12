package pe.edu.ulima.espacioya.cuentas.service.correo;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.edu.ulima.espacioya.cuentas.service.AvisoCancelacionUsuario;

class QuienCanceloTest {

    @Test
    @DisplayName("HU-27 R69: una cancelación por ADMIN dice \"Cancelada por: EspacioYa\" con el 100 % y el monto")
    void canceladaPorEspacioYa() {
        var correo = new CorreoCancelacionUsuario(new AvisoCancelacionUsuario("Cancha Olivos",
                LocalDate.of(2026, 11, 5), 10, 12, "ADMIN", 100, new BigDecimal("241.50")));

        assertThat(QuienCancelo.texto("ADMIN")).isEqualTo("EspacioYa");
        assertThat(correo.asunto()).isEqualTo("Tu reserva fue cancelada");
        assertThat(correo.cuerpo()).contains("Cancha Olivos", "05/11/2026", "10:00 a 12:00",
                "Cancelada por: EspacioYa", "Reembolso: 100 % (S/ 241.50)");
        assertThat(correo.cuerpo()).doesNotContain("fraude", "ADMIN");
    }

    @Test
    @DisplayName("HU-27 R69: los textos de USUARIO y ARRENDADOR no cambian")
    void otrosTextosSinCambios() {
        assertThat(QuienCancelo.texto("USUARIO")).isEqualTo("quien reservó");
        assertThat(QuienCancelo.texto("ARRENDADOR")).isEqualTo("el arrendador");
    }
}
