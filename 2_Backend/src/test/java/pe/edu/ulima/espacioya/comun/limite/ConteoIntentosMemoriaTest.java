package pe.edu.ulima.espacioya.comun.limite;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.edu.ulima.espacioya.RelojControlable;

/** Línea base del contrato: {@link ConteoIntentosMemoria} detrás de {@code new LimitadorIntentos(reloj)}. */
class ConteoIntentosMemoriaTest extends ContratoConteoIntentos {

    @Override
    protected LimitadorIntentos crearLimitador(RelojControlable reloj) {
        return new LimitadorIntentos(reloj);
    }

    @Test
    @DisplayName("HU-30 R15: new LimitadorIntentos(reloj) sigue contando en memoria con el reloj recibido")
    void constructorConRelojCuentaEnMemoria() {
        var reloj = new RelojControlable(Instant.parse("2026-10-27T15:00:00Z"));
        var limitador = new LimitadorIntentos(reloj);
        var otroProceso = new LimitadorIntentos(reloj);

        assertThat(limitador.intentar("registro|10.0.0.1", 1, Duration.ofMinutes(1))).isTrue();
        assertThat(limitador.intentar("registro|10.0.0.1", 1, Duration.ofMinutes(1))).isFalse();
        assertThat(otroProceso.intentar("registro|10.0.0.1", 1, Duration.ofMinutes(1))).isTrue();

        reloj.avanzar(Duration.ofMinutes(1));

        assertThat(limitador.intentar("registro|10.0.0.1", 1, Duration.ofMinutes(1))).isTrue();
    }
}
