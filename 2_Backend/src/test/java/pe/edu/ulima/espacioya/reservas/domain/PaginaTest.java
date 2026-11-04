package pe.edu.ulima.espacioya.reservas.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Iterator;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaginaTest {

    private static final int TAMANO = 10;

    /** Cuenta las llamadas a next: la página no debe consumir más allá de lo que entrega. */
    private static final class Contador implements Iterator<Integer> {
        private final Iterator<Integer> origen;
        private int consumidos;

        Contador(int cantidad) {
            this.origen = IntStream.rangeClosed(1, cantidad).boxed().toList().iterator();
        }

        @Override
        public boolean hasNext() {
            return origen.hasNext();
        }

        @Override
        public Integer next() {
            consumidos++;
            return origen.next();
        }
    }

    private static Pagina<Integer> pagina(int cantidad, int numero) {
        return Pagina.de(new Contador(cantidad), numero, TAMANO);
    }

    @Test
    @DisplayName("HU-14 R32: sin elementos, la página 1 es vacía y hayMas = false")
    void sinElementos() {
        assertThat(pagina(0, 1)).isEqualTo(new Pagina<>(1, List.of(), false));
    }

    @Test
    @DisplayName("HU-14 R26, R27: con 10 elementos, la página 1 los trae todos y hayMas = false")
    void diezElementos() {
        var pagina = pagina(10, 1);

        assertThat(pagina.elementos()).containsExactlyElementsOf(IntStream.rangeClosed(1, 10).boxed().toList());
        assertThat(pagina.hayMas()).isFalse();
    }

    @Test
    @DisplayName("HU-14 R26, R27: con 11 elementos, la página 1 trae 10 y hayMas = true")
    void onceElementosPaginaUno() {
        var pagina = pagina(11, 1);

        assertThat(pagina.numero()).isEqualTo(1);
        assertThat(pagina.elementos()).hasSize(10).startsWith(1).endsWith(10);
        assertThat(pagina.hayMas()).isTrue();
    }

    @Test
    @DisplayName("HU-14 R26, R27: con 11 elementos, la página 2 trae el 11 y hayMas = false")
    void onceElementosPaginaDos() {
        assertThat(pagina(11, 2)).isEqualTo(new Pagina<>(2, List.of(11), false));
    }

    @Test
    @DisplayName("HU-14 R32: una página más allá del final es vacía y hayMas = false")
    void paginaMasAllaDelFinal() {
        assertThat(pagina(11, 5)).isEqualTo(new Pagina<>(5, List.of(), false));
    }

    @Test
    @DisplayName("HU-14 R26, R32: la página 2 147 483 647 es vacía, sin desbordar el salto")
    void paginaMaxima() {
        assertThat(pagina(11, Integer.MAX_VALUE)).isEqualTo(new Pagina<>(Integer.MAX_VALUE, List.of(), false));
    }

    @Test
    @DisplayName("HU-14 R27: la página no consume el recorrido más allá de sus elementos (hayMas usa hasNext)")
    void noConsumeDeMas() {
        var recorrido = new Contador(25);

        var pagina = Pagina.de(recorrido, 2, TAMANO);

        assertThat(pagina.elementos()).containsExactlyElementsOf(IntStream.rangeClosed(11, 20).boxed().toList());
        assertThat(pagina.hayMas()).isTrue();
        assertThat(recorrido.consumidos).isEqualTo(20);
        assertThat(recorrido.next()).isEqualTo(21);
    }

    @Test
    @DisplayName("HU-14 R26: una página o un tamaño menores que 1 no son válidos")
    void argumentosInvalidos() {
        assertThatThrownBy(() -> Pagina.de(new Contador(1), 0, TAMANO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Pagina.de(new Contador(1), 1, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("HU-14 R26: los elementos de la página no se pueden modificar")
    void elementosInmutables() {
        var pagina = pagina(3, 1);

        assertThatThrownBy(() -> pagina.elementos().add(4)).isInstanceOf(UnsupportedOperationException.class);
    }
}
