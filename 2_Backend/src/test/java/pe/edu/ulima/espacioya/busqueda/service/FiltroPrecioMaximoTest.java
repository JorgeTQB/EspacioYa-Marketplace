package pe.edu.ulima.espacioya.busqueda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.edu.ulima.espacioya.espacios.service.EspacioPublicado;

class FiltroPrecioMaximoTest {

    private static final CriteriosBusqueda CRITERIOS =
            new CriteriosBusqueda("CANCHA", "SURCO", LocalDate.of(2026, 11, 2));

    private static EspacioPublicado publicado(long id, String precio) {
        return new EspacioPublicado(id, "Cancha " + id, "CANCHA", "SURCO", "Santiago de Surco", new BigDecimal(precio),
                20, Set.of());
    }

    @Test
    @DisplayName("HU-23 R12: admite precioHora menor o igual que el máximo (inclusivo) y conserva el orden")
    void limiteInclusivoYOrden() {
        var envuelta = (BusquedaEspacios) criterios -> List.of(publicado(4, "30.00"), publicado(9, "80.00"),
                publicado(2, "80.01"), publicado(7, "80"), publicado(1, "120.00"));

        var filtrados = new FiltroPrecioMaximo(envuelta, new BigDecimal("80.00")).buscar(CRITERIOS);

        assertThat(filtrados).extracting(EspacioPublicado::id).containsExactly(4L, 9L, 7L);
    }

    @Test
    @DisplayName("HU-23 R12: llama una sola vez a la envuelta con los mismos criterios")
    void unaSolaLlamadaALaEnvuelta() {
        var llamadas = new AtomicInteger();
        BusquedaEspacios envuelta = criterios -> {
            assertThat(criterios).isEqualTo(CRITERIOS);
            llamadas.incrementAndGet();
            return List.of(publicado(1, "50.00"));
        };

        assertThat(new FiltroPrecioMaximo(envuelta, new BigDecimal("50")).buscar(CRITERIOS)).hasSize(1);
        assertThat(llamadas).hasValue(1);
    }

    @Test
    @DisplayName("HU-23 R12: sin envuelta o sin precio es un error de programación")
    void argumentosObligatorios() {
        BusquedaEspacios envuelta = criterios -> List.of();

        assertThatThrownBy(() -> new FiltroPrecioMaximo(envuelta, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FiltroPrecioMaximo(null, BigDecimal.TEN))
                .isInstanceOf(NullPointerException.class);
    }
}
