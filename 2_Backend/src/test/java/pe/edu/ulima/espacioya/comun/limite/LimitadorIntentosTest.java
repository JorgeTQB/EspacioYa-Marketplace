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
    private static final String CLAVE_LOGIN = "login|ana@correo.pe";
    private static final Duration VENTANA_LOGIN = Duration.ofMinutes(15);

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

    @Test
    @DisplayName("HU-03 R16, R17: con 4 fallos registrados no supera el límite y con 5 sí")
    void superaLimiteAlQuintoFallo() {
        for (int i = 0; i < MAXIMO - 1; i++) {
            limitador.registrar(CLAVE_LOGIN);
        }
        assertThat(limitador.superaLimite(CLAVE_LOGIN, MAXIMO, VENTANA_LOGIN)).isFalse();

        limitador.registrar(CLAVE_LOGIN);

        assertThat(limitador.superaLimite(CLAVE_LOGIN, MAXIMO, VENTANA_LOGIN)).isTrue();
    }

    @Test
    @DisplayName("HU-03 R18: consultar superaLimite no registra un intento")
    void superaLimiteNoRegistra() {
        for (int i = 0; i < 10; i++) {
            assertThat(limitador.superaLimite(CLAVE_LOGIN, 1, VENTANA_LOGIN)).isFalse();
        }

        limitador.registrar(CLAVE_LOGIN);

        assertThat(limitador.superaLimite(CLAVE_LOGIN, 1, VENTANA_LOGIN)).isTrue();
        assertThat(limitador.superaLimite(CLAVE_LOGIN, 2, VENTANA_LOGIN)).isFalse();
    }

    @Test
    @DisplayName("HU-03 R19: a los 15 minutos de un fallo deja de contarlo")
    void dejaDeContarAlPasarLaVentana() {
        for (int i = 0; i < MAXIMO; i++) {
            limitador.registrar(CLAVE_LOGIN);
        }
        reloj.avanzar(Duration.ofMinutes(15).minusSeconds(1));
        assertThat(limitador.superaLimite(CLAVE_LOGIN, MAXIMO, VENTANA_LOGIN)).isTrue();

        reloj.avanzar(Duration.ofSeconds(1));

        assertThat(limitador.superaLimite(CLAVE_LOGIN, MAXIMO, VENTANA_LOGIN)).isFalse();
    }

    @Test
    @DisplayName("HU-03 R20: limpiar reinicia a cero los fallos de la clave")
    void limpiarReiniciaContador() {
        for (int i = 0; i < MAXIMO; i++) {
            limitador.registrar(CLAVE_LOGIN);
        }

        limitador.limpiar(CLAVE_LOGIN);

        assertThat(limitador.superaLimite(CLAVE_LOGIN, MAXIMO, VENTANA_LOGIN)).isFalse();
        for (int i = 0; i < MAXIMO - 1; i++) {
            limitador.registrar(CLAVE_LOGIN);
        }
        assertThat(limitador.superaLimite(CLAVE_LOGIN, MAXIMO, VENTANA_LOGIN)).isFalse();
    }

    @Test
    @DisplayName("HU-03 R21: el bloqueo de un correo no afecta a otro")
    void clavesDeLoginIndependientes() {
        for (int i = 0; i < MAXIMO; i++) {
            limitador.registrar(CLAVE_LOGIN);
        }

        assertThat(limitador.superaLimite(CLAVE_LOGIN, MAXIMO, VENTANA_LOGIN)).isTrue();
        assertThat(limitador.superaLimite("login|beto@correo.pe", MAXIMO, VENTANA_LOGIN)).isFalse();
    }
}
