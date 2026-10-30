package pe.edu.ulima.espacioya.comun.limite;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import pe.edu.ulima.espacioya.RelojControlable;
import pe.edu.ulima.espacioya.comun.error.ErrorDTO;
import tools.jackson.databind.json.JsonMapper;

class FiltroLimiteIntentosTest {

    private static final Duration MINUTO = Duration.ofMinutes(1);
    private static final String IP = "10.20.0.1";

    private RelojControlable reloj;
    private LimitadorIntentos limitador;
    private JsonMapper json;

    @BeforeEach
    void preparar() {
        reloj = new RelojControlable(Instant.parse("2026-10-28T15:00:00Z"));
        limitador = new LimitadorIntentos(reloj);
        json = JsonMapper.builder().build();
    }

    private FiltroLimiteIntentos filtro(ReglaLimite... reglas) {
        return new FiltroLimiteIntentos(List.of(reglas), limitador, json);
    }

    private static MockHttpServletRequest solicitud(String metodo, String ruta, String ip) {
        var solicitud = new MockHttpServletRequest(metodo, ruta);
        solicitud.setRemoteAddr(ip);
        return solicitud;
    }

    private static MockFilterChain pasar(FiltroLimiteIntentos filtro, MockHttpServletRequest solicitud,
                                         MockHttpServletResponse respuesta) throws Exception {
        var cadena = new MockFilterChain();
        filtro.doFilter(solicitud, respuesta, cadena);
        return cadena;
    }

    @Test
    @DisplayName("HU-10 R46: si dos reglas del mismo grupo coinciden con la solicitud, se descuenta un solo intento")
    void dosReglasDelMismoGrupoDescuentanUnIntento() throws Exception {
        var literal = new ReglaLimite("GET", "/api/espacios/7", 2, MINUTO, "catalogo");
        var patron = new ReglaLimite("GET", "/api/espacios/{id}", 2, MINUTO, "catalogo");
        var filtro = filtro(literal, patron);

        for (int i = 0; i < 2; i++) {
            var respuesta = new MockHttpServletResponse();
            var cadena = pasar(filtro, solicitud("GET", "/api/espacios/7", IP), respuesta);
            assertThat(respuesta.getStatus()).isEqualTo(200);
            assertThat(cadena.getRequest()).isNotNull();
        }

        var respuesta = new MockHttpServletResponse();
        pasar(filtro, solicitud("GET", "/api/espacios/7", IP), respuesta);
        assertThat(respuesta.getStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("HU-10 R33: con el cupo agotado responde 429 con el mensaje genérico y no sigue la cadena")
    void cupoAgotadoResponde429() throws Exception {
        var filtro = filtro(new ReglaLimite("GET", "/api/espacios", 1, MINUTO, "catalogo"));
        pasar(filtro, solicitud("GET", "/api/espacios", IP), new MockHttpServletResponse());

        var respuesta = new MockHttpServletResponse();
        var cadena = pasar(filtro, solicitud("GET", "/api/espacios", IP), respuesta);

        assertThat(respuesta.getStatus()).isEqualTo(429);
        assertThat(respuesta.getContentType()).startsWith("application/json");
        assertThat(respuesta.getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo("{\"mensaje\":\"" + ErrorDTO.MENSAJE_DEMASIADOS_INTENTOS + "\",\"errores\":[]}");
        assertThat(cadena.getRequest()).isNull();
    }

    @Test
    @DisplayName("HU-10 R36: las solicitudes rechazadas con 429 no cuentan como aceptadas del cupo")
    void rechazadasNoCuentan() throws Exception {
        var filtro = filtro(new ReglaLimite("GET", "/api/espacios", 2, MINUTO, "catalogo"));
        pasar(filtro, solicitud("GET", "/api/espacios", IP), new MockHttpServletResponse());
        reloj.avanzar(Duration.ofSeconds(30));
        pasar(filtro, solicitud("GET", "/api/espacios", IP), new MockHttpServletResponse());
        for (int i = 0; i < 3; i++) {
            var rechazada = new MockHttpServletResponse();
            pasar(filtro, solicitud("GET", "/api/espacios", IP), rechazada);
            assertThat(rechazada.getStatus()).isEqualTo(429);
        }

        reloj.avanzar(Duration.ofSeconds(30));

        var primera = new MockHttpServletResponse();
        pasar(filtro, solicitud("GET", "/api/espacios", IP), primera);
        assertThat(primera.getStatus()).isEqualTo(200);
        var segunda = new MockHttpServletResponse();
        pasar(filtro, solicitud("GET", "/api/espacios", IP), segunda);
        assertThat(segunda.getStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("HU-10 R46: grupos distintos que coinciden con la misma ruta descuentan cada uno su intento")
    void gruposDistintosCuentanPorSeparado() throws Exception {
        var catalogo = new ReglaLimite("GET", "/api/espacios", 5, MINUTO, "catalogo");
        var estricta = new ReglaLimite("GET", "/api/espacios", 1, MINUTO, "otro");
        var filtro = filtro(catalogo, estricta);

        pasar(filtro, solicitud("GET", "/api/espacios", IP), new MockHttpServletResponse());
        var respuesta = new MockHttpServletResponse();
        pasar(filtro, solicitud("GET", "/api/espacios", IP), respuesta);

        assertThat(respuesta.getStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("HU-10 R43: una ruta sin regla pasa sin consumir cupo")
    void rutaSinReglaNoConsume() throws Exception {
        var filtro = filtro(new ReglaLimite("GET", "/api/espacios", 1, MINUTO, "catalogo"));

        for (int i = 0; i < 3; i++) {
            var respuesta = new MockHttpServletResponse();
            var cadena = pasar(filtro, solicitud("GET", "/api/zonas", IP), respuesta);
            assertThat(cadena.getRequest()).isNotNull();
        }
        var respuesta = new MockHttpServletResponse();
        pasar(filtro, solicitud("GET", "/api/espacios", IP), respuesta);
        assertThat(respuesta.getStatus()).isEqualTo(200);
    }
}
