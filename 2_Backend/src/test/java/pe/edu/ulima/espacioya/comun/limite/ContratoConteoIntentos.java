package pe.edu.ulima.espacioya.comun.limite;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.edu.ulima.espacioya.RelojControlable;

/**
 * Semántica de {@link LimitadorIntentos} que debe cumplir todo {@link ConteoIntentos} (HU-30 CA3, CA5). Cada prueba
 * usa claves nuevas: en PostgreSQL la tabla no se vacía entre pruebas.
 */
abstract class ContratoConteoIntentos {

    private static final int MAXIMO = 5;
    private static final Duration VENTANA = Duration.ofHours(1);
    private static final Duration VENTANA_LOGIN = Duration.ofMinutes(15);

    private RelojControlable reloj;
    private LimitadorIntentos limitador;

    protected abstract LimitadorIntentos crearLimitador(RelojControlable reloj);

    @BeforeEach
    void prepararContrato() {
        reloj = new RelojControlable(Instant.parse("2026-10-27T15:00:00Z"));
        limitador = crearLimitador(reloj);
    }

    private static String claveNueva(String prefijo) {
        return prefijo + "|" + UUID.randomUUID();
    }

    @Test
    @DisplayName("HU-30 R8, R9: acepta 5 intentos en la ventana y rechaza el 6.º")
    void aceptaCincoYRechazaElSexto() {
        var clave = claveNueva("registro");
        for (int i = 0; i < MAXIMO; i++) {
            assertThat(limitador.intentar(clave, MAXIMO, VENTANA)).isTrue();
            reloj.avanzar(Duration.ofMinutes(1));
        }

        assertThat(limitador.intentar(clave, MAXIMO, VENTANA)).isFalse();
    }

    @Test
    @DisplayName("HU-30 R9: los intentos rechazados no se registran ni alargan el bloqueo")
    void elRechazadoNoCuenta() {
        var clave = claveNueva("registro");
        for (int i = 0; i < MAXIMO; i++) {
            limitador.intentar(clave, MAXIMO, VENTANA);
        }
        reloj.avanzar(Duration.ofMinutes(30));
        assertThat(limitador.intentar(clave, MAXIMO, VENTANA)).isFalse();
        assertThat(limitador.intentar(clave, MAXIMO, VENTANA)).isFalse();

        reloj.avanzar(Duration.ofMinutes(30));

        for (int i = 0; i < MAXIMO; i++) {
            assertThat(limitador.intentar(clave, MAXIMO, VENTANA)).isTrue();
        }
        assertThat(limitador.intentar(clave, MAXIMO, VENTANA)).isFalse();
    }

    @Test
    @DisplayName("HU-30 R6, R8: la ventana se desliza desde el intento más antiguo")
    void ventanaDeslizanteDesdeElMasAntiguo() {
        var clave = claveNueva("registro");
        limitador.intentar(clave, MAXIMO, VENTANA);
        reloj.avanzar(Duration.ofMinutes(10));
        for (int i = 1; i < MAXIMO; i++) {
            limitador.intentar(clave, MAXIMO, VENTANA);
        }
        reloj.avanzar(Duration.ofMinutes(49));
        assertThat(limitador.intentar(clave, MAXIMO, VENTANA)).isFalse();

        reloj.avanzar(Duration.ofMinutes(1));

        assertThat(limitador.intentar(clave, MAXIMO, VENTANA)).isTrue();
        assertThat(limitador.intentar(clave, MAXIMO, VENTANA)).isFalse();
    }

    @Test
    @DisplayName("HU-30 R6: un fallo registrado justo en el borde de la ventana ya no cuenta")
    void bordeExactoNoCuentaEnSuperaLimite() {
        var clave = claveNueva("login");
        for (int i = 0; i < MAXIMO; i++) {
            limitador.registrar(clave);
        }
        reloj.avanzar(VENTANA_LOGIN.minusSeconds(1));
        assertThat(limitador.superaLimite(clave, MAXIMO, VENTANA_LOGIN)).isTrue();

        reloj.avanzar(Duration.ofSeconds(1));

        assertThat(limitador.superaLimite(clave, MAXIMO, VENTANA_LOGIN)).isFalse();
    }

    @Test
    @DisplayName("HU-30 R6: un intento aceptado justo en el borde de la ventana ya no cuenta en intentar")
    void bordeExactoNoCuentaEnIntentar() {
        var clave = claveNueva("catalogo");
        assertThat(limitador.intentar(clave, 1, Duration.ofMinutes(1))).isTrue();
        reloj.avanzar(Duration.ofSeconds(59));
        assertThat(limitador.intentar(clave, 1, Duration.ofMinutes(1))).isFalse();

        reloj.avanzar(Duration.ofSeconds(1));

        assertThat(limitador.intentar(clave, 1, Duration.ofMinutes(1))).isTrue();
    }

    @Test
    @DisplayName("HU-30 R7: con todos los intentos vencidos cuenta desde cero y queda un solo intento vigente")
    void vencidosCuentanDesdeCero() {
        var clave = claveNueva("recuperacion");
        for (int i = 0; i < MAXIMO; i++) {
            limitador.intentar(clave, MAXIMO, VENTANA);
        }
        reloj.avanzar(VENTANA.plusMinutes(5));

        assertThat(limitador.intentar(clave, MAXIMO, VENTANA)).isTrue();
        assertThat(limitador.superaLimite(clave, 1, VENTANA)).isTrue();
        assertThat(limitador.superaLimite(clave, 2, VENTANA)).isFalse();
    }

    @Test
    @DisplayName("HU-30 R12: los intentos de una clave no cuentan para otra")
    void clavesIndependientes() {
        var clave = claveNueva("registro");
        var otra = claveNueva("registro");
        for (int i = 0; i < MAXIMO; i++) {
            limitador.intentar(clave, MAXIMO, VENTANA);
            limitador.registrar(clave);
        }

        assertThat(limitador.intentar(clave, MAXIMO, VENTANA)).isFalse();
        assertThat(limitador.superaLimite(clave, MAXIMO, VENTANA_LOGIN)).isTrue();
        assertThat(limitador.intentar(otra, MAXIMO, VENTANA)).isTrue();
        assertThat(limitador.superaLimite(otra, MAXIMO, VENTANA_LOGIN)).isFalse();
    }

    @Test
    @DisplayName("HU-30 R2: con 4 fallos registrados no supera el límite y con 5 sí, sin que consultar registre")
    void superaLimiteAlQuintoSinRegistrar() {
        var clave = claveNueva("login");
        for (int i = 0; i < 10; i++) {
            assertThat(limitador.superaLimite(clave, 1, VENTANA_LOGIN)).isFalse();
        }
        for (int i = 0; i < MAXIMO - 1; i++) {
            limitador.registrar(clave);
        }
        assertThat(limitador.superaLimite(clave, MAXIMO, VENTANA_LOGIN)).isFalse();

        limitador.registrar(clave);

        assertThat(limitador.superaLimite(clave, MAXIMO, VENTANA_LOGIN)).isTrue();
    }

    @Test
    @DisplayName("HU-30 R4: limpiar reinicia a cero los intentos de la clave")
    void limpiarReinicia() {
        var clave = claveNueva("login");
        for (int i = 0; i < MAXIMO; i++) {
            limitador.registrar(clave);
        }

        limitador.limpiar(clave);

        assertThat(limitador.superaLimite(clave, MAXIMO, VENTANA_LOGIN)).isFalse();
        for (int i = 0; i < MAXIMO - 1; i++) {
            limitador.registrar(clave);
        }
        assertThat(limitador.superaLimite(clave, MAXIMO, VENTANA_LOGIN)).isFalse();
    }
}
