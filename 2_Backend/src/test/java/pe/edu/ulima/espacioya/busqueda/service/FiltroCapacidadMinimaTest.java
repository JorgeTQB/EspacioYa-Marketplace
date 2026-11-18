package pe.edu.ulima.espacioya.busqueda.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.edu.ulima.espacioya.espacios.service.EspacioPublicado;

class FiltroCapacidadMinimaTest {

    private static final CriteriosBusqueda CRITERIOS =
            new CriteriosBusqueda("CANCHA", "SURCO", LocalDate.of(2026, 11, 2));

    private static EspacioPublicado publicado(long id, int capacidad) {
        return new EspacioPublicado(id, "Cancha " + id, "CANCHA", "SURCO", "Santiago de Surco", new BigDecimal("50.00"),
                capacidad, Set.of());
    }

    @Test
    @DisplayName("HU-23 R13: admite capacidad mayor o igual que la mínima (inclusivo) y conserva el orden")
    void limiteInclusivoYOrden() {
        BusquedaEspacios envuelta = criterios -> List.of(publicado(8, 10), publicado(3, 9), publicado(5, 200),
                publicado(1, 1));

        var filtrados = new FiltroCapacidadMinima(envuelta, 10).buscar(CRITERIOS);

        assertThat(filtrados).extracting(EspacioPublicado::id).containsExactly(8L, 5L);
    }

    @Test
    @DisplayName("HU-23 R13: con capacidad mínima 1 admite a todos")
    void minimaUnoAdmiteTodos() {
        BusquedaEspacios envuelta = criterios -> List.of(publicado(1, 1), publicado(2, 100_000));

        assertThat(new FiltroCapacidadMinima(envuelta, 1).buscar(CRITERIOS)).hasSize(2);
    }
}
