package pe.edu.ulima.espacioya.comun.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IpsPermitidasAdminTest {

    @Test
    @DisplayName("HU-06 R5: con la lista vacía ninguna IP está permitida")
    void listaVaciaNoPermiteNada() {
        for (var vacia : new String[] {"", "   ", null}) {
            var ips = new IpsPermitidasAdmin(vacia);

            assertThat(ips.permite("127.0.0.1")).isFalse();
            assertThat(ips.permite("0:0:0:0:0:0:0:1")).isFalse();
            assertThat(ips.permite("10.6.0.1")).isFalse();
        }
    }

    @Test
    @DisplayName("HU-06 R10: permite las IPs exactas de la lista, incluido ::1 escrito en forma larga")
    void permiteDireccionesExactas() {
        var ips = new IpsPermitidasAdmin("127.0.0.1,::1");

        assertThat(ips.permite("127.0.0.1")).isTrue();
        assertThat(ips.permite("::1")).isTrue();
        assertThat(ips.permite("0:0:0:0:0:0:0:1")).isTrue();
        assertThat(ips.permite("127.0.0.2")).isFalse();
        assertThat(ips.permite("10.6.0.1")).isFalse();
    }

    @Test
    @DisplayName("HU-06 R10: permite las IPs dentro de un rango CIDR IPv4 o IPv6 y rechaza las de fuera")
    void permiteRangosCidr() {
        var ips = new IpsPermitidasAdmin(" 192.168.10.0/24 , 2001:db8::/32 ");

        assertThat(ips.permite("192.168.10.1")).isTrue();
        assertThat(ips.permite("192.168.10.254")).isTrue();
        assertThat(ips.permite("192.168.11.1")).isFalse();
        assertThat(ips.permite("2001:db8:0:0:0:0:0:7")).isTrue();
        assertThat(ips.permite("2001:db9::7")).isFalse();
    }

    @Test
    @DisplayName("HU-06 R10: una IP nula o que no es una dirección literal no está permitida")
    void ipNulaONoLiteral() {
        var ips = new IpsPermitidasAdmin("127.0.0.1");

        assertThat(ips.permite(null)).isFalse();
        assertThat(ips.permite("localhost")).isFalse();
        assertThat(ips.permite("")).isFalse();
    }

    @Test
    @DisplayName("HU-06 R11: una entrada que no es IP ni CIDR impide el arranque nombrando la variable sin el valor")
    void entradaInvalidaLanza() {
        for (var invalida : new String[] {"abc", "10.0.0.0/99", "300.1.1.1", "127.0.0.1,", "10.0.0.1/abc",
                "::1/200", "10.0.0.1/8/8", "localhost"}) {
            assertThatThrownBy(() -> new IpsPermitidasAdmin(invalida))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ADMIN_IPS_PERMITIDAS")
                    .satisfies(e -> assertThat(e.getMessage()).doesNotContain(invalida.split(",")[0]));
        }
    }
}
