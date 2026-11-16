package pe.edu.ulima.espacioya.pagos.service.pasarela;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SolicitudReembolsoPasarelaTest {

    private static final BigDecimal MONTO = new BigDecimal("161.00");

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("HU-31 R3: un número de intento menor que 1 lanza IllegalArgumentException")
    void rechazaNumeroMenorQueUno(int numero) {
        assertThatThrownBy(() -> new SolicitudReembolsoPasarela("sim-7-APROBADO-1", MONTO, "espacioya-reembolso-4",
                numero)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("HU-31 R3: el número de intento 1 se acepta y se conserva")
    void aceptaElIntentoUno() {
        var solicitud = new SolicitudReembolsoPasarela("sim-7-APROBADO-1", MONTO, "espacioya-reembolso-4", 1);

        assertThat(solicitud.numeroIntento()).isEqualTo(1);
    }
}
