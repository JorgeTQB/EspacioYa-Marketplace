package pe.edu.ulima.espacioya.busqueda.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LimiteCatalogoTest {

    private static final Duration MINUTO = Duration.ofMinutes(1);

    @Test
    @DisplayName("HU-10 R40: un máximo menor que 1 falla al arrancar con IllegalStateException")
    void maximoInvalido() {
        assertThatThrownBy(() -> new LimiteCatalogo(0, MINUTO)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new LimiteCatalogo(-5, MINUTO)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("HU-10 R40: una ventana cero o negativa falla al arrancar con IllegalStateException")
    void ventanaInvalida() {
        assertThatThrownBy(() -> new LimiteCatalogo(120, Duration.ZERO)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new LimiteCatalogo(120, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new LimiteCatalogo(120, null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("HU-10 R41: las reglas del catálogo van en el grupo catalogo y comparten la clave por IP")
    void reglasCompartenGrupo() {
        var limite = new LimiteCatalogo(120, MINUTO);
        var busqueda = limite.regla("GET", "/api/espacios");
        var ficha = limite.regla("GET", "/api/espacios/{id}");

        assertThat(busqueda.grupo()).isEqualTo(LimiteCatalogo.GRUPO).isEqualTo("catalogo");
        assertThat(busqueda.maximo()).isEqualTo(120);
        assertThat(busqueda.ventana()).isEqualTo(MINUTO);
        assertThat(busqueda.clave("10.0.0.1")).isEqualTo(ficha.clave("10.0.0.1")).isEqualTo("catalogo|10.0.0.1");
        assertThat(busqueda.clave("10.0.0.2")).isNotEqualTo(busqueda.clave("10.0.0.1"));
    }

    @Test
    @DisplayName("HU-10 R41: la regla registrada de la búsqueda es GET /api/espacios del grupo catalogo")
    void reglaDeLaBusqueda() {
        var regla = new ReglasLimiteBusqueda().reglaLimiteBusqueda(new LimiteCatalogo(3, MINUTO));

        assertThat(regla.metodo()).isEqualTo("GET");
        assertThat(regla.ruta()).isEqualTo("/api/espacios");
        assertThat(regla.grupo()).isEqualTo("catalogo");
        assertThat(regla.maximo()).isEqualTo(3);
    }
}
