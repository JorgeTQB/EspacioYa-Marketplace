package pe.edu.ulima.espacioya.comun.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class FiltroAutenticacionSesionTest {

    private static final String TOKEN = "tokenDePruebaDeCuarentaYTresCaracteres_abcd";

    @Mock
    private ValidadorSesion validador;

    private FiltroAutenticacionSesion filtro;
    private MockFilterChain cadena;

    @BeforeEach
    void preparar() {
        SecurityContextHolder.clearContext();
        filtro = new FiltroAutenticacionSesion(validador);
        cadena = new MockFilterChain();
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private static MockHttpServletRequest solicitud(String autorizacion) {
        var solicitud = new MockHttpServletRequest("GET", "/api/sesiones/actual");
        if (autorizacion != null) {
            solicitud.addHeader("Authorization", autorizacion);
        }
        return solicitud;
    }

    @Test
    @DisplayName("HU-03 R22: un Bearer de sesión vigente pone en el contexto el usuario y el rol de la sesión")
    void bearerValidoAutentica() throws Exception {
        var usuario = new UsuarioAutenticado(7L, 11L, "ARRENDADOR");
        when(validador.validar(TOKEN)).thenReturn(Optional.of(usuario));
        var solicitud = solicitud("Bearer " + TOKEN);
        solicitud.setParameter("usuarioId", "99");
        solicitud.setParameter("rol", "ADMIN");

        filtro.doFilter(solicitud, new MockHttpServletResponse(), cadena);

        var autenticacion = SecurityContextHolder.getContext().getAuthentication();
        assertThat(autenticacion).isNotNull();
        assertThat(autenticacion.isAuthenticated()).isTrue();
        assertThat(autenticacion.getPrincipal()).isEqualTo(usuario);
        assertThat(autenticacion.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ARRENDADOR");
        assertThat(cadena.getRequest()).isSameAs(solicitud);
    }

    @Test
    @DisplayName("HU-03 R28: sin encabezado Authorization el contexto queda vacío y la cadena continúa")
    void sinEncabezadoNoAutentica() throws Exception {
        var solicitud = solicitud(null);

        filtro.doFilter(solicitud, new MockHttpServletResponse(), cadena);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(cadena.getRequest()).isSameAs(solicitud);
        verify(validador, never()).validar(anyString());
    }

    @Test
    @DisplayName("HU-03 R28: con un esquema distinto de Bearer no se valida ni se autentica")
    void esquemaBasicNoAutentica() throws Exception {
        var solicitud = solicitud("Basic " + TOKEN);

        filtro.doFilter(solicitud, new MockHttpServletResponse(), cadena);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(cadena.getRequest()).isSameAs(solicitud);
        verify(validador, never()).validar(anyString());
    }

    @Test
    @DisplayName("HU-03 R26, R27, R28: un token sin sesión vigente deja el contexto vacío y la cadena continúa")
    void tokenSinSesionVigenteNoAutentica() throws Exception {
        when(validador.validar(TOKEN)).thenReturn(Optional.empty());
        var solicitud = solicitud("Bearer " + TOKEN);

        filtro.doFilter(solicitud, new MockHttpServletResponse(), cadena);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(cadena.getRequest()).isSameAs(solicitud);
    }

    @Test
    @DisplayName("HU-03 R28: un Bearer vacío no se valida")
    void bearerVacioNoAutentica() throws Exception {
        filtro.doFilter(solicitud("Bearer   "), new MockHttpServletResponse(), cadena);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(validador, never()).validar(anyString());
    }
}
