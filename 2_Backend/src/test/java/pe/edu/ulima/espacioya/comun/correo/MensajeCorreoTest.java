package pe.edu.ulima.espacioya.comun.correo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MensajeCorreoTest {

    @Test
    @DisplayName("HU-25 R30: el mensaje lleva un único destinatario, el asunto y el cuerpo")
    void conservaLosDatos() {
        var mensaje = new MensajeCorreo("ana@correo.pe", "Asunto", "Cuerpo");

        assertThat(mensaje.para()).isEqualTo("ana@correo.pe");
        assertThat(mensaje.asunto()).isEqualTo("Asunto");
        assertThat(mensaje.cuerpo()).isEqualTo("Cuerpo");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("HU-25 R30: rechaza un mensaje sin destinatario")
    void rechazaSinDestinatario(String para) {
        assertThatThrownBy(() -> new MensajeCorreo(para, "Asunto", "Cuerpo"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("HU-25 R31: rechaza un mensaje sin asunto")
    void rechazaSinAsunto(String asunto) {
        assertThatThrownBy(() -> new MensajeCorreo("ana@correo.pe", asunto, "Cuerpo"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("HU-25 R30: rechaza un mensaje sin cuerpo")
    void rechazaSinCuerpo(String cuerpo) {
        assertThatThrownBy(() -> new MensajeCorreo("ana@correo.pe", "Asunto", cuerpo))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
