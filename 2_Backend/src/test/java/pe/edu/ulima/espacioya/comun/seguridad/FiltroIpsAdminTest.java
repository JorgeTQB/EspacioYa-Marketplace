package pe.edu.ulima.espacioya.comun.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import tools.jackson.databind.json.JsonMapper;

class FiltroIpsAdminTest {

    private static final String ACCESO_DENEGADO = "No tienes permiso para realizar esta acción.";

    private FiltroIpsAdmin filtro;
    private MockFilterChain cadena;
    private MockHttpServletResponse respuesta;

    @BeforeEach
    void preparar() {
        filtro = new FiltroIpsAdmin(new IpsPermitidasAdmin("127.0.0.1,::1"), JsonMapper.builder().build());
        cadena = new MockFilterChain();
        respuesta = new MockHttpServletResponse();
    }

    private static MockHttpServletRequest solicitud(String metodo, String ruta, String ip) {
        var solicitud = new MockHttpServletRequest(metodo, ruta);
        solicitud.setRemoteAddr(ip);
        return solicitud;
    }

    @Test
    @DisplayName("HU-06 R7: una ruta admin desde una IP no permitida responde 403 sin seguir la cadena")
    void rutaAdminDesdeIpNoPermitida() throws Exception {
        var solicitud = solicitud("GET", "/api/admin/arrendadores/en-revision", "10.6.0.1");
        solicitud.addHeader("Authorization", "Bearer token-cualquiera");

        filtro.doFilter(solicitud, respuesta, cadena);

        assertThat(respuesta.getStatus()).isEqualTo(403);
        assertThat(respuesta.getContentType()).startsWith("application/json");
        assertThat(respuesta.getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo("{\"mensaje\":\"" + ACCESO_DENEGADO + "\",\"errores\":[]}");
        assertThat(cadena.getRequest()).isNull();
    }

    @Test
    @DisplayName("HU-06 R7: un POST admin con la ruta codificada tampoco pasa desde una IP no permitida")
    void rutaAdminCodificadaNoEsquivaElFiltro() throws Exception {
        var solicitud = solicitud("POST", "/api/%61dmin/arrendadores/7/identidad/aprobacion", "10.6.0.2");

        filtro.doFilter(solicitud, respuesta, cadena);

        assertThat(respuesta.getStatus()).isEqualTo(403);
        assertThat(cadena.getRequest()).isNull();
    }

    @Test
    @DisplayName("HU-06 R7: una ruta admin desde una IP permitida sigue la cadena")
    void rutaAdminDesdeIpPermitida() throws Exception {
        for (var ip : new String[] {"127.0.0.1", "0:0:0:0:0:0:0:1"}) {
            var solicitud = solicitud("GET", "/api/admin/arrendadores/en-revision", ip);
            var cadenaDeEstaSolicitud = new MockFilterChain();

            filtro.doFilter(solicitud, new MockHttpServletResponse(), cadenaDeEstaSolicitud);

            assertThat(cadenaDeEstaSolicitud.getRequest()).isSameAs(solicitud);
        }
    }

    @Test
    @DisplayName("HU-06 R12: una ruta que no es admin sigue la cadena desde cualquier IP")
    void rutaNoAdminDesdeCualquierIp() throws Exception {
        for (var ruta : new String[] {"/api/archivos", "/api/sesiones", "/api/administracion", "/api/espacios"}) {
            var solicitud = solicitud("GET", ruta, "10.6.0.3");
            var cadenaDeEstaSolicitud = new MockFilterChain();
            var respuestaDeEstaSolicitud = new MockHttpServletResponse();

            filtro.doFilter(solicitud, respuestaDeEstaSolicitud, cadenaDeEstaSolicitud);

            assertThat(cadenaDeEstaSolicitud.getRequest()).isSameAs(solicitud);
            assertThat(respuestaDeEstaSolicitud.getStatus()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("HU-06 R5: con la lista vacía la ruta admin responde 403 incluso desde 127.0.0.1")
    void listaVaciaBloqueaTodo() throws Exception {
        filtro = new FiltroIpsAdmin(new IpsPermitidasAdmin(""), JsonMapper.builder().build());

        filtro.doFilter(solicitud("GET", "/api/admin/arrendadores/en-revision", "127.0.0.1"), respuesta, cadena);

        assertThat(respuesta.getStatus()).isEqualTo(403);
        assertThat(cadena.getRequest()).isNull();
    }
}
