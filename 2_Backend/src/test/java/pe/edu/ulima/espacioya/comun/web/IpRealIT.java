package pe.edu.ulima.espacioya.comun.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.test.context.TestPropertySource;

import pe.edu.ulima.espacioya.TestcontainersConfiguration;

/**
 * HU-06, D3: la IP del cliente la decide Tomcat con {@code RemoteIpValve}. Se prueba con HTTP real (MockMvc no
 * pasa por el valve) y se lee la IP que quedó en la auditoría de un inicio de sesión fallido.
 */
class IpRealIT {

    private static final String CUERPO = "{\"correo\":\"nadie@correo.pe\",\"contrasena\":\"Errada9999\"}";

    abstract static class Base {

        @LocalServerPort
        private int puerto;
        @Autowired
        private JdbcTemplate jdbc;

        @BeforeEach
        void limpiar() {
            jdbc.update("DELETE FROM auditoria");
        }

        String ipAuditadaCon(String xForwardedFor) throws Exception {
            var solicitud = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + "/api/sesiones"))
                    .header("Content-Type", "application/json")
                    .header("X-Forwarded-For", xForwardedFor)
                    .POST(HttpRequest.BodyPublishers.ofString(CUERPO))
                    .build();
            try (var cliente = HttpClient.newHttpClient()) {
                var respuesta = cliente.send(solicitud, HttpResponse.BodyHandlers.ofString());
                assertThat(respuesta.statusCode()).isEqualTo(401);
            }
            return jdbc.queryForObject("SELECT ip FROM auditoria WHERE accion = 'LOGIN_FALLIDO' ORDER BY id DESC "
                    + "LIMIT 1", String.class);
        }
    }

    @Nested
    @NestedTestConfiguration(NestedTestConfiguration.EnclosingConfiguration.OVERRIDE)
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @Import(TestcontainersConfiguration.class)
    @TestPropertySource(properties = "server.tomcat.remoteip.internal-proxies=127[.]0[.]0[.]1")
    class ConexionDesdeProxyDeConfianza extends Base {

        @Test
        @DisplayName("HU-06 R13: desde un proxy de confianza la IP es la más a la derecha de X-Forwarded-For "
                + "que no es un proxy de confianza")
        void usaLaIpDelEncabezado() throws Exception {
            assertThat(ipAuditadaCon("203.0.113.7")).isEqualTo("203.0.113.7");
            assertThat(ipAuditadaCon("198.51.100.1, 203.0.113.9")).isEqualTo("203.0.113.9");
            assertThat(ipAuditadaCon("203.0.113.10, 127.0.0.1")).isEqualTo("203.0.113.10");
        }
    }

    @Nested
    @NestedTestConfiguration(NestedTestConfiguration.EnclosingConfiguration.OVERRIDE)
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @Import(TestcontainersConfiguration.class)
    @TestPropertySource(properties = "server.tomcat.remoteip.internal-proxies=")
    class SinProxiesDeConfianza extends Base {

        @Test
        @DisplayName("HU-06 R14: sin proxies de confianza se usa la IP de la conexión e ignora X-Forwarded-For")
        void ignoraElEncabezado() throws Exception {
            assertThat(ipAuditadaCon("203.0.113.7")).isEqualTo("127.0.0.1");
            assertThat(ipAuditadaCon("10.6.0.1, 203.0.113.9")).isEqualTo("127.0.0.1");
        }
    }
}
