package pe.edu.ulima.espacioya.cuentas.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class NotificadorCorreoSimuladoTest {

    private static final String TOKEN = "Q2xhdmVTZWNyZXRhRGVQcnVlYmFQYXJhSFUwNEVuTG9n";
    private static final String ENLACE = "http://localhost:5173/restablecer-contrasena#token=" + TOKEN;

    @Test
    @DisplayName("HU-01 R29, R30: el envío simulado solo escribe una línea INFO sin el correo del usuario")
    void soloEscribeEnElLogSinElCorreo(CapturedOutput salida) {
        new NotificadorCorreoSimulado(false).notificarVerificacion("ana.secreta@correo.pe");

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

        assertThat(constructores).allSatisfy(c -> assertThat(c.getParameterTypes())
                .allSatisfy(tipo -> assertThat(tipo).isEqualTo(boolean.class)));
        assertThat(NotificadorCorreoSimulado.class.getDeclaredFields())
                .allSatisfy(campo -> assertThat(campo.getType().getName().startsWith("org.slf4j.")
                        || campo.getType() == boolean.class).isTrue());
    }

    @Test
    @DisplayName("HU-04 R13: con la propiedad de desarrollo apagada no escribe el enlace, el token ni el correo")
    void apagadaNoRegistraEnlace(CapturedOutput salida) {
        new NotificadorCorreoSimulado(false).notificarRecuperacion("ana.secreta@correo.pe", ENLACE);

        assertThat(salida.getOut())
                .contains("INFO")
                .contains("Envío de correo de recuperación simulado: no se envió ningún correo (HU-04).")
                .doesNotContain(TOKEN)
                .doesNotContain("restablecer-contrasena")
                .doesNotContain("ana.secreta");
        assertThat(salida.getErr()).doesNotContain(TOKEN).doesNotContain("ana.secreta");
    }

    @Test
    @DisplayName("HU-04 R14: con la propiedad de desarrollo activada escribe el enlace, sin el correo")
    void encendidaRegistraEnlaceSinCorreo(CapturedOutput salida) {
        new NotificadorCorreoSimulado(true).notificarRecuperacion("ana.secreta@correo.pe", ENLACE);

        assertThat(salida.getOut())
                .contains("[Solo desarrollo] Enlace de recuperación simulado: " + ENLACE)
                .doesNotContain("ana.secreta");
        assertThat(salida.getErr()).doesNotContain("ana.secreta");
    }

    @Test
    @DisplayName("HU-06 R45: el aviso simulado de identidad aprobada no escribe el correo del arrendador")
    void identidadAprobadaSinCorreo(CapturedOutput salida) {
        new NotificadorCorreoSimulado(true).notificarIdentidadAprobada("ana.secreta@correo.pe");

        assertThat(salida.getOut())
                .contains("INFO")
                .contains("Envío de correo de identidad aprobada simulado: no se envió ningún correo (HU-06).")
                .doesNotContain("ana.secreta");
        assertThat(salida.getErr()).doesNotContain("ana.secreta");
    }

    @Test
    @DisplayName("HU-06 R45: el aviso simulado de identidad rechazada no escribe el correo ni el motivo")
    void identidadRechazadaSinCorreoNiMotivo(CapturedOutput salida) {
        var motivo = "El DNI 44556677 no coincide con la foto";

        new NotificadorCorreoSimulado(true).notificarIdentidadRechazada("ana.secreta@correo.pe", motivo);

        assertThat(salida.getOut())
                .contains("INFO")
                .contains("Envío de correo de identidad rechazada simulado: no se envió ningún correo (HU-06).")
                .doesNotContain("ana.secreta")
                .doesNotContain("44556677")
                .doesNotContain(motivo);
        assertThat(salida.getErr()).doesNotContain("ana.secreta").doesNotContain("44556677");
    }

    @Test
    @DisplayName("HU-09 R47: el aviso simulado de cuenta suspendida no escribe el correo ni el motivo")
    void cuentaSuspendidaSinCorreoNiMotivo(CapturedOutput salida) {
        var motivo = "Publicó un espacio con el teléfono 912345678 de otra persona";

        new NotificadorCorreoSimulado(true).notificarCuentaSuspendida("ana.secreta@correo.pe", motivo);

        assertThat(salida.getOut())
                .contains("INFO")
                .contains("Envío de correo de cuenta suspendida simulado: no se envió ningún correo (HU-09).")
                .doesNotContain("ana.secreta")
                .doesNotContain("912345678")
                .doesNotContain(motivo);
        assertThat(salida.getErr()).doesNotContain("ana.secreta").doesNotContain("912345678");
    }

    @Test
    @DisplayName("HU-09 R47: el aviso simulado de cuenta reactivada no escribe el correo")
    void cuentaReactivadaSinCorreo(CapturedOutput salida) {
        new NotificadorCorreoSimulado(true).notificarCuentaReactivada("ana.secreta@correo.pe");

        assertThat(salida.getOut())
                .contains("INFO")
                .contains("Envío de correo de cuenta reactivada simulado: no se envió ningún correo (HU-09).")
                .doesNotContain("ana.secreta");
        assertThat(salida.getErr()).doesNotContain("ana.secreta");
    }

    @Test
    @DisplayName("HU-15 R26: el aviso simulado de cancelación al usuario no escribe el correo ni los datos")
    void cancelacionAlUsuarioSinCorreoNiDatos(CapturedOutput salida) {
        var aviso = new AvisoCancelacionUsuario("Cancha Escondida", LocalDate.of(2026, 11, 2), 18, 20, "ARRENDADOR",
                100, new BigDecimal("161.37"));

        new NotificadorCorreoSimulado(true).notificarCancelacionAlUsuario("carla.secreta@correo.pe", aviso);

        assertThat(salida.getOut())
                .contains("INFO")
                .contains("Envío de aviso de cancelación al usuario simulado: no se envió ningún correo (HU-15).")
                .doesNotContain("carla.secreta")
                .doesNotContain("Cancha Escondida")
                .doesNotContain("161.37");
        assertThat(salida.getErr()).doesNotContain("carla.secreta");
    }

    @Test
    @DisplayName("HU-15 R27: el aviso simulado de cancelación al arrendador no escribe el correo ni los datos")
    void cancelacionAlArrendadorSinCorreoNiDatos(CapturedOutput salida) {
        var aviso = new AvisoCancelacionArrendador("Cancha Escondida", LocalDate.of(2026, 11, 2), 18, 20, "USUARIO");

        new NotificadorCorreoSimulado(true).notificarCancelacionAlArrendador("ana.secreta@correo.pe", aviso);

        assertThat(salida.getOut())
                .contains("INFO")
                .contains("Envío de aviso de cancelación al arrendador simulado: no se envió ningún correo (HU-15).")
                .doesNotContain("ana.secreta")
                .doesNotContain("Cancha Escondida");
        assertThat(salida.getErr()).doesNotContain("ana.secreta");
    }
}
