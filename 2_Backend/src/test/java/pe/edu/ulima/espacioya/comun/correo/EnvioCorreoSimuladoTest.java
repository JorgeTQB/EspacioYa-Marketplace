package pe.edu.ulima.espacioya.comun.correo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class EnvioCorreoSimuladoTest {

    @Test
    @DisplayName("HU-25 R35: no envía nada y el log no tiene destinatario, asunto ni cuerpo")
    void soloUnLogSinDatos(CapturedOutput salida) {
        new EnvioCorreoSimulado().enviar(new MensajeCorreo("ana@correo.pe", "Asunto secreto", "Cuerpo con Sala Azul"));

        assertThat(salida.getAll())
                .contains("Envío de correo simulado")
                .doesNotContain("ana@correo.pe")
                .doesNotContain("Asunto secreto")
                .doesNotContain("Sala Azul");
    }
}
