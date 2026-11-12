package pe.edu.ulima.espacioya.reservas.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReembolsoPorModeracionTest {

    private static final Instant AHORA = Instant.parse("2026-11-04T15:00:00Z");
    private final ReembolsoPorModeracion politica = new ReembolsoPorModeracion();

    private static SolicitudCancelacion solicitud(QuienCancela quien) {
        return new SolicitudCancelacion(quien, AHORA.plus(Duration.ofHours(2)), AHORA, new BigDecimal("80.50"));
    }

    @Test
    @DisplayName("HU-27 R67: MODERACION aplica solo cuando cancela ADMIN y devuelve el 100 % del monto pagado")
    void soloAdminAlCien() {
        assertThat(politica.codigo()).isEqualTo("MODERACION");
        assertThat(politica.aplicaA(solicitud(QuienCancela.ADMIN))).isTrue();
        assertThat(politica.aplicaA(solicitud(QuienCancela.USUARIO))).isFalse();
        assertThat(politica.aplicaA(solicitud(QuienCancela.ARRENDADOR))).isFalse();
        assertThat(politica.porcentaje()).isEqualTo(100);
        assertThat(politica.monto(new BigDecimal("80.50"))).isEqualTo(new BigDecimal("80.50"));
    }

    @Test
    @DisplayName("HU-27 R64: el rol requerido para cancelar como ADMIN es ADMIN")
    void rolRequeridoAdmin() {
        assertThat(QuienCancela.ADMIN.rolRequerido()).isEqualTo("ADMIN");
    }
}
