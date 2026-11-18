package pe.edu.ulima.espacioya.comun.correo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.edu.ulima.espacioya.comun.error.ServicioExternoException;

/** El Creator del Factory Method: crea el producto con el método fábrica y entrega un único mensaje al Target. */
class FabricaCorreoTest {

    private final List<MensajeCorreo> enviados = new ArrayList<>();
    private final List<String> eventosRecibidos = new ArrayList<>();
    private final EnvioCorreo envio = enviados::add;

    record CorreoDePrueba(String nombre) implements Correo {

        @Override
        public String asunto() {
            return "Asunto fijo";
        }

        @Override
        public String cuerpo() {
            return "Hola " + nombre;
        }
    }

    final class FabricaDePrueba extends FabricaCorreo<String> {

        FabricaDePrueba(EnvioCorreo envio) {
            super(envio);
        }

        @Override
        protected Correo crearCorreo(String evento) {
            eventosRecibidos.add(evento);
            return new CorreoDePrueba(evento);
        }
    }

    @Test
    @DisplayName("HU-25 R30: el Creator llama al método fábrica y entrega un único mensaje con los datos del producto")
    void creaElCorreoYLoEntrega() {
        new FabricaDePrueba(envio).enviar("ana@correo.pe", "Ana");

        assertThat(eventosRecibidos).containsExactly("Ana");
        assertThat(enviados).containsExactly(new MensajeCorreo("ana@correo.pe", "Asunto fijo", "Hola Ana"));
    }

    @Test
    @DisplayName("HU-25 R31: el asunto es el fijo del producto, aunque cambien los datos del evento")
    void asuntoFijo() {
        var fabrica = new FabricaDePrueba(envio);

        fabrica.enviar("ana@correo.pe", "Ana");
        fabrica.enviar("luis@correo.pe", "Luis");

        assertThat(enviados).extracting(MensajeCorreo::asunto).containsOnly("Asunto fijo");
        assertThat(enviados).extracting(MensajeCorreo::para).containsExactly("ana@correo.pe", "luis@correo.pe");
    }

    @Test
    @DisplayName("HU-25 R33: si el Target falla, la excepción llega a quien envía")
    void propagaLaFallaDelTarget() {
        EnvioCorreo falla = mensaje -> {
            throw new ServicioExternoException();
        };

        assertThatThrownBy(() -> new FabricaDePrueba(falla).enviar("ana@correo.pe", "Ana"))
                .isInstanceOf(ServicioExternoException.class);
    }

    @Test
    @DisplayName("HU-25 R30: sin destinatario no se entrega nada al Target")
    void sinDestinatarioNoEnvia() {
        assertThatThrownBy(() -> new FabricaDePrueba(envio).enviar(" ", "Ana"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(enviados).isEmpty();
    }
}
