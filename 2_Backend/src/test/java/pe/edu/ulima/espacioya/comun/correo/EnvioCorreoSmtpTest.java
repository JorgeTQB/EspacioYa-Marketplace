package pe.edu.ulima.espacioya.comun.correo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import pe.edu.ulima.espacioya.comun.error.ErrorDTO;
import pe.edu.ulima.espacioya.comun.error.ServicioExternoException;

/** El Adapter de SMTP probado contra el Adaptee {@link JavaMailSender} de Mockito (HU-25 D12). */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class EnvioCorreoSmtpTest {

    private static final String REMITENTE = "EspacioYa <no-responder@espacioya.pe>";
    private static final String HOST = "smtp.ejemplo.pe";
    private static final MensajeCorreo MENSAJE =
            new MensajeCorreo("ana@correo.pe", "Recordatorio: tu reserva es mañana", "Espacio: Sala Azul");

    @Mock
    JavaMailSender servidor;

    @Test
    @DisplayName("HU-25 R32: entrega un mensaje de texto plano con el remitente, el destinatario, el asunto y el "
            + "cuerpo")
    void traduceElMensaje() {
        new EnvioCorreoSmtp(servidor, REMITENTE, HOST).enviar(MENSAJE);

        var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(servidor).send(captor.capture());
        var enviado = captor.getValue();
        assertThat(enviado.getFrom()).isEqualTo(REMITENTE);
        assertThat(enviado.getSubject()).isEqualTo("Recordatorio: tu reserva es mañana");
        assertThat(enviado.getText()).isEqualTo("Espacio: Sala Azul");
    }

    @Test
    @DisplayName("HU-25 R30: un solo destinatario, sin copia ni copia oculta")
    void unSoloDestinatario() {
        new EnvioCorreoSmtp(servidor, REMITENTE, HOST).enviar(MENSAJE);

        var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(servidor).send(captor.capture());
        var enviado = captor.getValue();
        assertThat(enviado.getTo()).containsExactly("ana@correo.pe");
        assertThat(enviado.getCc()).isNull();
        assertThat(enviado.getBcc()).isNull();
        assertThat(enviado.getReplyTo()).isNull();
    }

    @Test
    @DisplayName("HU-25 R33: si SMTP falla lanza ServicioExternoException sin el texto del proveedor ni el "
            + "destinatario")
    void fallaDelServidor(CapturedOutput salida) {
        doThrow(new MailSendException("550 buzón ana@correo.pe no existe")).when(servidor)
                .send(any(SimpleMailMessage.class));
        var envio = new EnvioCorreoSmtp(servidor, REMITENTE, HOST);

        assertThatThrownBy(() -> envio.enviar(MENSAJE))
                .isInstanceOf(ServicioExternoException.class)
                .hasMessage(ErrorDTO.MENSAJE_SERVICIO_EXTERNO)
                .hasNoCause();
        assertThat(salida.getAll()).doesNotContain("ana@correo.pe").doesNotContain("550");
    }

    @Test
    @DisplayName("HU-25 R33: cualquier MailException se traduce a ServicioExternoException")
    void otraMailException() {
        doThrow(new MailAuthenticationException("credenciales inválidas")).when(servidor)
                .send(any(SimpleMailMessage.class));
        var envio = new EnvioCorreoSmtp(servidor, REMITENTE, HOST);

        assertThatThrownBy(() -> envio.enviar(MENSAJE))
                .isInstanceOf(ServicioExternoException.class)
                .hasMessageNotContaining("credenciales");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("HU-25 R34: sin remitente no se puede construir (el backend no arranca)")
    void sinRemitente(String remitente) {
        assertThatThrownBy(() -> new EnvioCorreoSmtp(servidor, remitente, HOST))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORREO_REMITENTE");
        verifyNoInteractions(servidor);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("HU-25 R34: sin host SMTP no se puede construir (el backend no arranca)")
    void sinHost(String host) {
        assertThatThrownBy(() -> new EnvioCorreoSmtp(servidor, REMITENTE, host))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORREO_SMTP_HOST");
        verifyNoInteractions(servidor);
    }
}
