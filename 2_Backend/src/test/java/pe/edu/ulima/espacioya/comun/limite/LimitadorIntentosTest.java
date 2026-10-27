package pe.edu.ulima.espacioya.comun.limite;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.edu.ulima.espacioya.RelojControlable;

class LimitadorIntentosTest {

    private static final String CLAVE = "POST /api/usuarios|10.0.0.1";
    private static final int MAXIMO = 5;
    private static final Duration VENTANA = Duration.ofHours(1);

    private RelojControlable reloj;
    private LimitadorIntentos limitador;

    @BeforeEach
    void preparar() {
        reloj = new RelojControlable(Instant.parse("2026-10-27T15:00:00Z"));
        limitador = new LimitadorIntentos(reloj);
    }

    @Test
    @DisplayName("HU-01 R21: acepta 5 intentos en la ventana y rechaza el 6.º")
    void aceptaCincoYRechazaElSexto() {
        for (int i = 0; i < MAXIMO; i++) {
            assertThat(limitador.intentar(CLAVE, MAXIMO, VENTANA)).isTrue();
            reloj.avanzar(Duration.ofMinutes(1));
        }

        assertThat(limitador.intentar(CLAVE, MAXIMO, VENTANA)).isFalse();
    }

    @Test
    @DisplayName("HU-01 R21: los intentos rechazados no alargan el bloqueo")
    void elRechazadoNoCuenta() {
        for (int i = 0; i < MAXIMO; i++) {
            limitador.intentar(CLAVE, MAXIMO, VENTANA);
        }
        reloj.avanzar(Duration.ofMinutes(30));
        assertThat(limitador.intentar(CLAVE, MAXIMO, VENTANA)).isFalse();

        reloj.avanzar(Duration.ofMinutes(30));

        assertThat(limitador.intentar(CLAVE, MAXIMO, VENTANA)).isTrue();
    }

    @Test
    @DisplayName("HU-01 R23: a los 60 minutos del intento más antiguo vuelve a aceptar")
    void vuelveAAceptarAlPasarLaVentana() {
        limitador.intentar(CLAVE, MAXIMO, VENTANA);
        reloj.avanzar(Duration.ofMinutes(10));
        for (int i = 1; i < MAXIMO; i++) {
            limitador.intentar(CLAVE, MAXIMO, VENTANA);
        }
        reloj.avanzar(Duration.ofMinutes(49));
        assertThat(limitador.intentar(CLAVE, MAXIMO, VENTANA)).isFalse();

        reloj.avanzar(Duration.ofMinutes(1));

        assertThat(limitador.intentar(CLAVE, MAXIMO, VENTANA)).isTrue();
        assertThat(limitador.intentar(CLAVE, MAXIMO, VENTANA)).isFalse();
    }

    @Test
    @DisplayName("HU-01 R21: cada IP tiene su propio contador")
    void clavesIndependientes() {
        for (int i = 0; i < MAXIMO; i++) {
            limitador.intentar(CLAVE, MAXIMO, VENTANA);
        }

        assertThat(limitador.intentar(CLAVE, MAXIMO, VENTANA)).isFalse();
        assertThat(limitador.intentar("POST /api/usuarios|10.0.0.2", MAXIMO, VENTANA)).isTrue();
    }
}
