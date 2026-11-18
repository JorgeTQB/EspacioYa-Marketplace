package pe.edu.ulima.espacioya.administracion.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;

class ConversionReportesTest {

    @Test
    @DisplayName("HU-27 R27: un id de reporte bien escrito se convierte a número")
    void idBienEscrito() {
        assertThat(ConversionReportes.idReporte("42")).isEqualTo(42L);
        assertThat(ConversionReportes.idReporte("9223372036854775807")).isEqualTo(Long.MAX_VALUE);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "0", "-1", "abc", "1.5", " 7", "99999999999999999999", "9223372036854775808"})
    @DisplayName("HU-27 R27: un id de reporte mal escrito es el mismo 404 genérico que uno inexistente")
    void idMalEscrito(String texto) {
        assertThatThrownBy(() -> ConversionReportes.idReporte(texto))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }
}
