package pe.edu.ulima.espacioya.cuentas.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class NotificadorCorreoSimuladoTest {

    @Test
    @DisplayName("HU-01 R4: el envío simulado solo escribe una línea INFO sin el correo del usuario")
    void soloEscribeEnElLogSinElCorreo(CapturedOutput salida) {
        new NotificadorCorreoSimulado().notificarVerificacion("ana.secreta@correo.pe");

        assertThat(salida.getOut())
                .contains("INFO")
                .contains("Envío de correo de verificación simulado: no se envió ningún correo (HU-01).")
                .doesNotContain("ana.secreta");
        assertThat(salida.getErr()).doesNotContain("ana.secreta");
    }

    @Test
    @DisplayName("HU-01 R4: el notificador simulado no tiene colaboradores de red")
    void sinColaboradoresDeRed() {
        var constructores = NotificadorCorreoSimulado.class.getDeclaredConstructors();

        assertThat(constructores).allSatisfy(c -> assertThat(c.getParameterCount()).isZero());
        assertThat(NotificadorCorreoSimulado.class.getDeclaredFields())
                .allSatisfy(campo -> assertThat(campo.getType().getName()).startsWith("org.slf4j."));
    }
}
