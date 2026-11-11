package pe.edu.ulima.espacioya.administracion.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReglasReporteTest {

    @Test
    @DisplayName("HU-27 R17: la ventana del cupo empieza exactamente 24 h antes de ahora y admite 10 reportes")
    void ventanaDe24Horas() {
        var ahora = Instant.parse("2026-11-04T15:00:00Z");

        assertThat(ReglasReporte.inicioVentana(ahora)).isEqualTo(Instant.parse("2026-11-03T15:00:00Z"));
        assertThat(ReglasReporte.MAXIMO_POR_DIA).isEqualTo(10);
    }
}
