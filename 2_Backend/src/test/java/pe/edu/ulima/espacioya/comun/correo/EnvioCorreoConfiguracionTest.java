package pe.edu.ulima.espacioya.comun.correo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

import pe.edu.ulima.espacioya.cuentas.service.NotificadorCorreo;
import pe.edu.ulima.espacioya.cuentas.service.NotificadorCorreoSimulado;
import pe.edu.ulima.espacioya.cuentas.service.impl.NotificadorCorreoImpl;

/** El proveedor de correo se elige solo con {@code espacioya.correo.proveedor}, sin fábrica (HU-25 D8). */
class EnvioCorreoConfiguracionTest {

    private static final String REMITENTE = "espacioya.correo.remitente=EspacioYa <no-responder@espacioya.pe>";
    private static final String HOST = "spring.mail.host=smtp.ejemplo.pe";

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(Configuracion.class, EnvioCorreoSmtp.class, EnvioCorreoSimulado.class,
                    NotificadorCorreoImpl.class, NotificadorCorreoSimulado.class);

    @Configuration(proxyBeanMethods = false)
    static class Configuracion {

        @Bean
        JavaMailSender servidor() {
            return mock(JavaMailSender.class);
        }

        // Como los servicios que notifican: sin un EnvioCorreo ni un NotificadorCorreo, el contexto no arranca.
        @Bean
        UsuarioDelCorreo usuarioDelCorreo(EnvioCorreo envio, NotificadorCorreo notificador) {
            return new UsuarioDelCorreo(envio, notificador);
        }
    }

    record UsuarioDelCorreo(EnvioCorreo envio, NotificadorCorreo notificador) {
    }

    private static void noArranca(AssertableApplicationContext arrancado) {
        assertThat(arrancado).hasFailed();
    }

    @Test
    @DisplayName("HU-25 R34: con smtp, remitente y host solo existen el adaptador SMTP y el notificador real")
    void smtp() {
        contexto.withPropertyValues("espacioya.correo.proveedor=smtp", REMITENTE, HOST).run(arrancado -> {
            assertThat(arrancado).hasSingleBean(EnvioCorreo.class);
            assertThat(arrancado.getBean(EnvioCorreo.class)).isInstanceOf(EnvioCorreoSmtp.class);
            assertThat(arrancado).hasSingleBean(NotificadorCorreo.class);
            assertThat(arrancado.getBean(NotificadorCorreo.class)).isInstanceOf(NotificadorCorreoImpl.class);
        });
    }

    @Test
    @DisplayName("HU-25 R35: con simulado solo existen el envío simulado y el notificador simulado, sin pedir SMTP")
    void simulado() {
        contexto.withPropertyValues("espacioya.correo.proveedor=simulado").run(arrancado -> {
            assertThat(arrancado).hasSingleBean(EnvioCorreo.class);
            assertThat(arrancado.getBean(EnvioCorreo.class)).isInstanceOf(EnvioCorreoSimulado.class);
            assertThat(arrancado).hasSingleBean(NotificadorCorreo.class);
            assertThat(arrancado.getBean(NotificadorCorreo.class)).isInstanceOf(NotificadorCorreoSimulado.class);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "sendgrid", "SMTP", "simulada"})
    @DisplayName("HU-25 R34: vacío o con otro valor no hay EnvioCorreo y el backend no arranca")
    void otroValor(String valor) {
        contexto.withPropertyValues("espacioya.correo.proveedor=" + valor)
                .run(EnvioCorreoConfiguracionTest::noArranca);
    }

    @Test
    @DisplayName("HU-25 R34: con smtp y sin remitente el backend no arranca")
    void smtpSinRemitente() {
        contexto.withPropertyValues("espacioya.correo.proveedor=smtp", HOST).run(arrancado -> {
            assertThat(arrancado).hasFailed();
            assertThat(arrancado.getStartupFailure()).rootCause().hasMessageContaining("CORREO_REMITENTE");
        });
    }

    @Test
    @DisplayName("HU-25 R34: con smtp y sin host el backend no arranca")
    void smtpSinHost() {
        contexto.withPropertyValues("espacioya.correo.proveedor=smtp", REMITENTE).run(arrancado -> {
            assertThat(arrancado).hasFailed();
            assertThat(arrancado.getStartupFailure()).rootCause().hasMessageContaining("CORREO_SMTP_HOST");
        });
    }
}
