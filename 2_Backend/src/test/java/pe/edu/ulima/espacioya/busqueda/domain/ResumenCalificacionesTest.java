package pe.edu.ulima.espacioya.busqueda.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResumenCalificacionesTest {

    @Test
    @DisplayName("HU-24 R28: 4, 4, 4 y 5 dan 4.3 con redondeo HALF_UP exacto de 4.25")
    void medioExactoRedondeaHaciaArriba() {
        assertThat(ResumenCalificaciones.de(4, 17))
                .isEqualTo(new ResumenCalificaciones(new BigDecimal("4.3"), 4));
    }

    @Test
    @DisplayName("HU-24 R28: 4.35 exacto, que en double queda por debajo, también sube a 4.4")
    void medioQueUnDoubleRedondeariaMal() {
        assertThat(ResumenCalificaciones.de(20, 87).promedio()).isEqualByComparingTo("4.4");
    }

    @Test
    @DisplayName("HU-24 R28: por debajo de la mitad baja y siempre lleva 1 decimal")
    void unDecimal() {
        assertThat(ResumenCalificaciones.de(3, 13).promedio()).isEqualTo(new BigDecimal("4.3"));
        assertThat(ResumenCalificaciones.de(1, 5).promedio()).isEqualTo(new BigDecimal("5.0"));
    }

    @Test
    @DisplayName("HU-24 R29: sin reseñas, promedio nulo y total 0")
    void sinResenas() {
        assertThat(ResumenCalificaciones.de(0, 0)).isEqualTo(new ResumenCalificaciones(null, 0));
    }
}
