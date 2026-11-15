package pe.edu.ulima.espacioya.comun.limite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.PlatformTransactionManager;

import pe.edu.ulima.espacioya.comun.cifrado.HuellaCampos;

class ConteoIntentosPostgresTest {

    private static final String LLAVE_HUELLA = "cHJ1ZWJhcy1lc3BhY2lveWEtbGxhdmUtaG1hYy0wMDI=";

    private DataSource datos;
    private PlatformTransactionManager transacciones;
    private ConteoIntentosPostgres conteo;

    @BeforeEach
    void preparar() {
        datos = mock(DataSource.class);
        transacciones = mock(PlatformTransactionManager.class);
        var reloj = Clock.fixed(Instant.parse("2026-10-27T15:00:00Z"), ZoneOffset.UTC);
        conteo = new ConteoIntentosPostgres(datos, transacciones, new HuellaCampos(LLAVE_HUELLA), reloj);
    }

    @Test
    @DisplayName("HU-30 R10: intentar con máximo menor que 1 devuelve false sin tocar la base")
    void maximoMenorQueUnoNoEscribe() throws SQLException {
        assertThat(conteo.intentar("registro|10.0.0.1", 0, Duration.ofMinutes(1))).isFalse();
        assertThat(conteo.intentar("registro|10.0.0.1", -1, Duration.ofMinutes(1))).isFalse();

        verify(datos, never()).getConnection();
        verifyNoInteractions(transacciones);
    }

    @Test
    @DisplayName("HU-30 R13: superaLimite con una ventana mayor que la retención lanza error sin consultar la base")
    void ventanaMayorQueLaRetencionFalla() throws SQLException {
        var ventana = ConteoIntentosPostgres.RETENCION_REGISTRO.plusSeconds(1);

        assertThatThrownBy(() -> conteo.superaLimite("login|ana@correo.pe", 5, ventana))
                .isInstanceOf(IllegalArgumentException.class);
        verify(datos, never()).getConnection();
    }

    @Test
    @DisplayName("HU-30 R13: superaLimite acepta una ventana igual a la retención")
    void ventanaIgualALaRetencionSeConsulta() throws SQLException {
        when(datos.getConnection()).thenThrow(new SQLException("sin base"));

        assertThatThrownBy(() -> conteo.superaLimite("login|ana@correo.pe", 5,
                ConteoIntentosPostgres.RETENCION_REGISTRO)).isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("HU-30 R20: si la base falla en intentar, la excepción se propaga y el intento no se acepta")
    void falloDeLaBasePropaga() throws SQLException {
        when(datos.getConnection()).thenThrow(new SQLException("sin base"));
        var limitador = new LimitadorIntentos(conteo);
        var resultado = new AtomicReference<Boolean>();

        assertThatThrownBy(() -> resultado.set(limitador.intentar("catalogo|10.0.0.1", 120, Duration.ofMinutes(1))))
                .isInstanceOf(DataAccessException.class);
        assertThat(resultado.get()).isNull();
    }
}
