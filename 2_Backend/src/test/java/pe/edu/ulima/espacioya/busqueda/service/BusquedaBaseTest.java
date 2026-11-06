package pe.edu.ulima.espacioya.busqueda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pe.edu.ulima.espacioya.espacios.service.CatalogoEspaciosService;
import pe.edu.ulima.espacioya.espacios.service.DisponibilidadService;
import pe.edu.ulima.espacioya.espacios.service.EspacioPublicado;

@ExtendWith(MockitoExtension.class)
class BusquedaBaseTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 11, 2);
    private static final CriteriosBusqueda CRITERIOS = new CriteriosBusqueda("CANCHA", "SURCO", FECHA);

    @Mock
    private CatalogoEspaciosService catalogo;
    @Mock
    private DisponibilidadService disponibilidad;

    private BusquedaBase busqueda;

    @BeforeEach
    void preparar() {
        busqueda = new BusquedaBase(catalogo, disponibilidad);
    }

    private static EspacioPublicado publicado(long id, String precio) {
        return new EspacioPublicado(id, "Cancha " + id, "CANCHA", "SURCO", "Santiago de Surco", new BigDecimal(precio),
                20, Set.of());
    }

    @Test
    @DisplayName("HU-10 R2, R10: devuelve solo los candidatos con bloques libres, en el orden del catálogo")
    void filtraConservandoElOrden() {
        var candidatos = List.of(publicado(5, "30.00"), publicado(2, "50.00"), publicado(9, "50.00"),
                publicado(1, "90.00"));
        when(catalogo.publicados("CANCHA", "SURCO")).thenReturn(candidatos);
        when(disponibilidad.conBloquesLibres(List.of(5L, 2L, 9L, 1L), FECHA)).thenReturn(Set.of(1L, 9L, 5L));

        assertThat(busqueda.buscar(CRITERIOS)).extracting(EspacioPublicado::id).containsExactly(5L, 9L, 1L);
    }

    @Test
    @DisplayName("HU-10 R11: sin candidatos devuelve una lista vacía sin calcular la disponibilidad")
    void sinCandidatosNoCalculaDisponibilidad() {
        when(catalogo.publicados("CANCHA", "SURCO")).thenReturn(List.of());

        assertThat(busqueda.buscar(CRITERIOS)).isEmpty();
        verifyNoInteractions(disponibilidad);
    }
}
