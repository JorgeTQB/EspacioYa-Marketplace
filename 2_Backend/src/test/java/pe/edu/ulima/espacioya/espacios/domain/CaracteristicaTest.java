package pe.edu.ulima.espacioya.espacios.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CaracteristicaTest {

    @Test
    @DisplayName("HU-23 R30: la lista fija tiene los cuatro códigos en su orden")
    void listaFijaEnOrden() {
        assertThat(Caracteristica.values()).containsExactly(Caracteristica.TECHADO, Caracteristica.ILUMINACION,
                Caracteristica.VESTUARIOS, Caracteristica.ESTACIONAMIENTO);
    }

    @Test
    @DisplayName("HU-23 R4, R30: esCodigo acepta solo el código exacto, sensible a mayúsculas")
    void esCodigoExacto() {
        assertThat(Caracteristica.esCodigo("TECHADO")).isTrue();
        assertThat(Caracteristica.esCodigo("ESTACIONAMIENTO")).isTrue();
        assertThat(Caracteristica.esCodigo("techado")).isFalse();
        assertThat(Caracteristica.esCodigo("PISCINA")).isFalse();
        assertThat(Caracteristica.esCodigo("")).isFalse();
        assertThat(Caracteristica.esCodigo(" TECHADO")).isFalse();
        assertThat(Caracteristica.esCodigo("TECHADO,VESTUARIOS")).isFalse();
        assertThat(Caracteristica.esCodigo(null)).isFalse();
    }

    @Test
    @DisplayName("HU-23 R1: deCodigos devuelve el conjunto en el orden del enum; vacío si no hay códigos")
    void deCodigos() {
        assertThat(Caracteristica.deCodigos(List.of("VESTUARIOS", "TECHADO")))
                .containsExactly(Caracteristica.TECHADO, Caracteristica.VESTUARIOS);
        assertThat(Caracteristica.deCodigos(List.of())).isEmpty();
    }

    @Test
    @DisplayName("HU-23 R7: describir lista los códigos en el orden fijo separados por coma y espacio")
    void describirEnOrdenFijo() {
        assertThat(Caracteristica.describir(Set.of(Caracteristica.VESTUARIOS, Caracteristica.TECHADO)))
                .isEqualTo("[TECHADO, VESTUARIOS]");
        assertThat(Caracteristica.describir(Set.of())).isEqualTo("[]");
    }
}
