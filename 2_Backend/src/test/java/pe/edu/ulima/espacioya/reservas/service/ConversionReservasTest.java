package pe.edu.ulima.espacioya.reservas.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ConversionReservasTest {

    @Test
    @DisplayName("HU-15 R4: 1 y el mayor long de 19 dígitos son ids bien escritos")
    void idsBienEscritos() {
        assertThat(ConversionReservas.idReserva("1")).contains(1L);
        assertThat(ConversionReservas.idReserva("9223372036854775807")).contains(Long.MAX_VALUE);
        assertThat(ConversionReservas.idReserva("0000000000000000007")).contains(7L);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abc", "0", "-1", "1.5", " 1", "+1", "12345678901234567890", "9223372036854775808"})
    @DisplayName("HU-15 R4: lo que no es un id bien escrito da vacío")
    void idsMalEscritos(String texto) {
        assertThat(ConversionReservas.idReserva(texto)).isEmpty();
    }
}
