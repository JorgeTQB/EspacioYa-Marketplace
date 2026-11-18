package pe.edu.ulima.espacioya.busqueda.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.edu.ulima.espacioya.espacios.service.EspacioPublicado;

/** ★ Prueba de composición del Decorator de HU-23. */
class FiltrosBusquedaTest {

    private static final CriteriosBusqueda CRITERIOS =
            new CriteriosBusqueda("CANCHA", "SURCO", LocalDate.of(2026, 11, 2));
    private static final BigDecimal PRECIO = new BigDecimal("80.00");
    private static final int CAPACIDAD = 10;
    private static final Set<String> CARACTERISTICAS = Set.of("TECHADO", "VESTUARIOS");

    private static final List<EspacioPublicado> CANDIDATOS = List.of(
            publicado(1, "40.00", 12, "TECHADO", "VESTUARIOS"),
            publicado(2, "60.00", 30, "TECHADO", "VESTUARIOS", "ILUMINACION"),
            publicado(3, "80.00", 10, "VESTUARIOS", "TECHADO"),
            publicado(4, "80.01", 50, "TECHADO", "VESTUARIOS"),
            publicado(5, "50.00", 9, "TECHADO", "VESTUARIOS"),
            publicado(6, "50.00", 40, "TECHADO"),
            publicado(7, "20.00", 100),
            publicado(8, "70.00", 10, "ESTACIONAMIENTO", "VESTUARIOS", "TECHADO"));
    private static final BusquedaEspacios BASE = criterios -> CANDIDATOS;

    private static EspacioPublicado publicado(long id, String precio, int capacidad, String... caracteristicas) {
        return new EspacioPublicado(id, "Cancha " + id, "CANCHA", "SURCO", "Santiago de Surco", new BigDecimal(precio),
                capacidad, Set.of(caracteristicas));
    }

    /** Clases de la cadena de afuera hacia adentro, recorrida con envuelta(). */
    private static List<Class<?>> cadena(BusquedaEspacios exterior) {
        var clases = new ArrayList<Class<?>>();
        var actual = exterior;
        while (actual instanceof FiltroBusqueda filtro) {
            clases.add(filtro.getClass());
            actual = filtro.envuelta();
        }
        assertThat(actual).isSameAs(BASE);
        return clases;
    }

    @Test
    @DisplayName("HU-23 R16: sin filtros envolver devuelve la misma instancia de la base")
    void sinFiltrosDevuelveLaBase() {
        assertThat(FiltrosBusqueda.NINGUNO.vacios()).isTrue();
        assertThat(FiltrosBusqueda.NINGUNO.envolver(BASE)).isSameAs(BASE);
        assertThat(new FiltrosBusqueda(null, null, null).envolver(BASE)).isSameAs(BASE);
    }

    @Test
    @DisplayName("HU-23 R15: con los tres, la cadena es Caracteristicas → CapacidadMinima → PrecioMaximo → base")
    void cadenaConLosTres() {
        var filtros = new FiltrosBusqueda(PRECIO, CAPACIDAD, CARACTERISTICAS);

        assertThat(filtros.vacios()).isFalse();
        assertThat(cadena(filtros.envolver(BASE))).containsExactly(FiltroCaracteristicas.class,
                FiltroCapacidadMinima.class, FiltroPrecioMaximo.class);
    }

    @Test
    @DisplayName("HU-23 R15: solo se apilan los filtros presentes")
    void soloLosPresentes() {
        assertThat(cadena(new FiltrosBusqueda(null, CAPACIDAD, CARACTERISTICAS).envolver(BASE)))
                .containsExactly(FiltroCaracteristicas.class, FiltroCapacidadMinima.class);
        assertThat(cadena(new FiltrosBusqueda(PRECIO, null, Set.of()).envolver(BASE)))
                .containsExactly(FiltroPrecioMaximo.class);
        assertThat(cadena(new FiltrosBusqueda(PRECIO, null, CARACTERISTICAS).envolver(BASE)))
                .containsExactly(FiltroCaracteristicas.class, FiltroPrecioMaximo.class);
        assertThat(cadena(new FiltrosBusqueda(null, CAPACIDAD, null).envolver(BASE)))
                .containsExactly(FiltroCapacidadMinima.class);
    }

    @Test
    @DisplayName("HU-23 R15: las 6 permutaciones de los tres decoradores dan la misma lista que envolver")
    void permutacionesDanLaMismaLista() {
        UnaryOperator<BusquedaEspacios> precio = envuelta -> new FiltroPrecioMaximo(envuelta, PRECIO);
        UnaryOperator<BusquedaEspacios> capacidad = envuelta -> new FiltroCapacidadMinima(envuelta, CAPACIDAD);
        UnaryOperator<BusquedaEspacios> caracteristicas =
                envuelta -> new FiltroCaracteristicas(envuelta, CARACTERISTICAS);
        var esperada = new FiltrosBusqueda(PRECIO, CAPACIDAD, CARACTERISTICAS).envolver(BASE).buscar(CRITERIOS);
        var permutaciones = List.of(
                List.of(precio, capacidad, caracteristicas),
                List.of(precio, caracteristicas, capacidad),
                List.of(capacidad, precio, caracteristicas),
                List.of(capacidad, caracteristicas, precio),
                List.of(caracteristicas, precio, capacidad),
                List.of(caracteristicas, capacidad, precio));

        assertThat(esperada).extracting(EspacioPublicado::id).containsExactly(1L, 2L, 3L, 8L);
        for (var orden : permutaciones) {
            BusquedaEspacios cadena = BASE;
            for (var decorar : orden) {
                cadena = decorar.apply(cadena);
            }
            assertThat(cadena.buscar(CRITERIOS)).isEqualTo(esperada);
        }
    }
}
