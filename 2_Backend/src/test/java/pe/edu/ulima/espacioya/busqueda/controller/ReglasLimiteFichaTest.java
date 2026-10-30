package pe.edu.ulima.espacioya.busqueda.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReglasLimiteFichaTest {

    private final LimiteCatalogo limite = new LimiteCatalogo(120, Duration.ofMinutes(1));
    private final ReglasLimiteFicha reglas = new ReglasLimiteFicha();

    @Test
    @DisplayName("HU-11 R30: la regla de la ficha es GET /api/espacios/{id} del grupo catalogo")
    void reglaDeLaFicha() {
        var regla = reglas.reglaLimiteFicha(limite);

        assertThat(regla.metodo()).isEqualTo("GET");
        assertThat(regla.ruta()).isEqualTo("/api/espacios/{id}").isEqualTo(ReglasLimiteFicha.RUTA_FICHA);
        assertThat(regla.grupo()).isEqualTo(LimiteCatalogo.GRUPO);
        assertThat(regla.aplicaA("GET", "/api/espacios/7")).isTrue();
        assertThat(regla.aplicaA("GET", "/api/espacios/abc")).isTrue();
        assertThat(regla.aplicaA("GET", "/api/espacios")).isFalse();
        assertThat(regla.aplicaA("GET", "/api/espacios/7/disponibilidad")).isFalse();
        assertThat(regla.aplicaA("POST", "/api/espacios/7")).isFalse();
    }

    @Test
    @DisplayName("HU-11 R30: la regla de la disponibilidad es GET /api/espacios/{id}/disponibilidad del grupo catalogo")
    void reglaDeLaDisponibilidad() {
        var regla = reglas.reglaLimiteDisponibilidad(limite);

        assertThat(regla.metodo()).isEqualTo("GET");
        assertThat(regla.ruta()).isEqualTo("/api/espacios/{id}/disponibilidad")
                .isEqualTo(ReglasLimiteFicha.RUTA_DISPONIBILIDAD);
        assertThat(regla.grupo()).isEqualTo(LimiteCatalogo.GRUPO);
        assertThat(regla.aplicaA("GET", "/api/espacios/7/disponibilidad")).isTrue();
        assertThat(regla.aplicaA("GET", "/api/espacios/7")).isFalse();
    }

    @Test
    @DisplayName("HU-11 R28, R30: la ficha, la disponibilidad y la búsqueda comparten la clave por IP")
    void compartenElCupoConLaBusqueda() {
        var busqueda = new ReglasLimiteBusqueda().reglaLimiteBusqueda(limite);

        assertThat(reglas.reglaLimiteFicha(limite).clave("10.13.0.1"))
                .isEqualTo(reglas.reglaLimiteDisponibilidad(limite).clave("10.13.0.1"))
                .isEqualTo(busqueda.clave("10.13.0.1"));
    }
}
