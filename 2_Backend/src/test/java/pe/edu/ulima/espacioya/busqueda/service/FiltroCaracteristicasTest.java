package pe.edu.ulima.espacioya.busqueda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.edu.ulima.espacioya.espacios.service.EspacioPublicado;

class FiltroCaracteristicasTest {

    private static final CriteriosBusqueda CRITERIOS =
            new CriteriosBusqueda("CANCHA", "SURCO", LocalDate.of(2026, 11, 2));

    private static EspacioPublicado publicado(long id, String... caracteristicas) {
        return new EspacioPublicado(id, "Cancha " + id, "CANCHA", "SURCO", "Santiago de Surco", new BigDecimal("50.00"),
                20, Set.of(caracteristicas));
    }

    @Test
    @DisplayName("HU-23 R14: admite solo los espacios que tienen todas las características pedidas (AND)")
    void todasLasPedidas() {
        BusquedaEspacios envuelta = criterios -> List.of(
                publicado(6, "TECHADO", "VESTUARIOS", "ILUMINACION"),
                publicado(2, "TECHADO"),
                publicado(4, "VESTUARIOS", "TECHADO"),
                publicado(1, "VESTUARIOS"));

        var filtrados = new FiltroCaracteristicas(envuelta, Set.of("TECHADO", "VESTUARIOS")).buscar(CRITERIOS);

        assertThat(filtrados).extracting(EspacioPublicado::id).containsExactly(6L, 4L);
    }

    @Test
    @DisplayName("HU-23 R14: un espacio sin características no cumple ningún filtro de características")
    void espacioSinCaracteristicas() {
        BusquedaEspacios envuelta = criterios -> List.of(publicado(1), publicado(2, "ESTACIONAMIENTO"));

        assertThat(new FiltroCaracteristicas(envuelta, Set.of("ESTACIONAMIENTO")).buscar(CRITERIOS))
                .extracting(EspacioPublicado::id).containsExactly(2L);
    }

    @Test
    @DisplayName("HU-23 R14: sin características pedidas es un error de programación")
    void conjuntoVacioLanza() {
        BusquedaEspacios envuelta = criterios -> List.of();

        assertThatThrownBy(() -> new FiltroCaracteristicas(envuelta, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FiltroCaracteristicas(envuelta, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
