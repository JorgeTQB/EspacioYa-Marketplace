package pe.edu.ulima.espacioya.pagos.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CuentaBancariaTest {

    private static final Instant AHORA = Instant.parse("2026-11-03T15:00:00Z");

    private static CuentaBancaria cuenta() {
        return CuentaBancaria.registrar(7L, "  Banco Andino ", "00112233445566778899", "Canchas SAC", AHORA);
    }

    @Test
    @DisplayName("HU-18 R17: la primera cuenta se registra sin retención y queda DISPONIBLE")
    void registrarSinRetencion() {
        var cuenta = cuenta();

        assertThat(cuenta.getRetenerHasta()).isNull();
        assertThat(cuenta.estaRetenida(AHORA)).isFalse();
        assertThat(cuenta.estado(AHORA)).isEqualTo(EstadoCuentaBancaria.DISPONIBLE);
        assertThat(cuenta.getBanco()).isEqualTo("Banco Andino");
        assertThat(cuenta.getArrendadorId()).isEqualTo(7L);
        assertThat(cuenta.getTitular()).isEqualTo("Canchas SAC");
    }

    @Test
    @DisplayName("HU-18 R12: cambiar fija retener_hasta en ahora + 72 h y reemplaza los datos")
    void cambiarRetiene72Horas() {
        var cuenta = cuenta();
        var despues = AHORA.plus(Duration.ofDays(1));

        cuenta.cambiar("Otro Banco", "99887766554433221100", "Canchas SAC", despues);

        assertThat(CuentaBancaria.RETENCION).isEqualTo(Duration.ofHours(72));
        assertThat(cuenta.getRetenerHasta()).isEqualTo(despues.plus(Duration.ofHours(72)));
        assertThat(cuenta.getBanco()).isEqualTo("Otro Banco");
        assertThat(cuenta.enmascarado()).isEqualTo("****1100");
        assertThat(cuenta.getActualizadaEn()).isEqualTo(despues);
        assertThat(cuenta.getCreadaEn()).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("HU-18 R12: un nuevo cambio reinicia la retención")
    void cambiarReiniciaLaRetencion() {
        var cuenta = cuenta();
        cuenta.cambiar("Otro Banco", "99887766554433221100", "Canchas SAC", AHORA);
        var luego = AHORA.plus(Duration.ofHours(10));

        cuenta.cambiar("Tercer Banco", "11111111111111", "Canchas SAC", luego);

        assertThat(cuenta.getRetenerHasta()).isEqualTo(luego.plus(Duration.ofHours(72)));
    }

    @Test
    @DisplayName("HU-18 R18: está RETENIDA antes de retener_hasta y DISPONIBLE desde ese instante")
    void estadoSegunElReloj() {
        var cuenta = cuenta();
        cuenta.cambiar("Otro Banco", "99887766554433221100", "Canchas SAC", AHORA);
        var limite = AHORA.plus(Duration.ofHours(72));

        assertThat(cuenta.estado(AHORA)).isEqualTo(EstadoCuentaBancaria.RETENIDA);
        assertThat(cuenta.estaRetenida(limite.minusSeconds(1))).isTrue();
        assertThat(cuenta.estado(limite)).isEqualTo(EstadoCuentaBancaria.DISPONIBLE);
        assertThat(cuenta.estaRetenida(limite.plusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("HU-18 R16: es la misma cuenta si coinciden banco (sin mayúsculas ni espacios extremos) y número")
    void esLaMisma() {
        var cuenta = cuenta();

        assertThat(cuenta.esLaMisma("banco andino", "00112233445566778899")).isTrue();
        assertThat(cuenta.esLaMisma(" BANCO ANDINO ", "00112233445566778899")).isTrue();
        assertThat(cuenta.esLaMisma("Banco Andino", "00112233445566778800")).isFalse();
        assertThat(cuenta.esLaMisma("Otro Banco", "00112233445566778899")).isFalse();
    }

    @Test
    @DisplayName("HU-18 R19: el número se enmascara con **** y los 4 últimos dígitos")
    void enmascara() {
        assertThat(cuenta().enmascarado()).isEqualTo("****8899");
        assertThat(CuentaBancaria.enmascarar("1234567890")).isEqualTo("****7890");
        assertThat(CuentaBancaria.enmascarar("123")).isEqualTo("****123");
    }

    @Test
    @DisplayName("HU-19 R38: numeroParaTransferir devuelve el número completo vigente, también tras un cambio")
    void numeroParaTransferir() {
        var cuenta = cuenta();

        assertThat(cuenta.numeroParaTransferir()).isEqualTo("00112233445566778899");
        cuenta.cambiar("Otro Banco", "99887766554433221100", "Canchas SAC", AHORA);
        assertThat(cuenta.numeroParaTransferir()).isEqualTo("99887766554433221100");
    }

    @Test
    @DisplayName("HU-18 R20: el número cifrado se declara con el convertidor de cifrado de campos")
    void numeroConConvertidorCifrado() throws Exception {
        var campo = CuentaBancaria.class.getDeclaredField("numero");

        var convertidor = campo.getAnnotation(jakarta.persistence.Convert.class);

        assertThat(convertidor).isNotNull();
        assertThat(convertidor.converter()).isEqualTo(pe.edu.ulima.espacioya.comun.cifrado.ConvertidorCifrado.class);
    }
}
