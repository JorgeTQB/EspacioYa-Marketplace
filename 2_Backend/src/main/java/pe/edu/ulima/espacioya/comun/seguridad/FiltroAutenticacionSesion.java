package pe.edu.ulima.espacioya.comun.seguridad;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autentica la solicitud con el token de {@code Authorization: Bearer}. No es un {@code @Component}: si lo fuera,
 * Spring Boot también lo registraría como filtro de servlet y se ejecutaría dos veces.
 */
public class FiltroAutenticacionSesion extends OncePerRequestFilter {

    private static final String ESQUEMA = "Bearer ";

    private final ValidadorSesion validador;

    public FiltroAutenticacionSesion(ValidadorSesion validador) {
        this.validador = validador;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest solicitud, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        var token = tokenDe(solicitud.getHeader(HttpHeaders.AUTHORIZATION));
        if (token != null) {
            validador.validar(token).ifPresent(FiltroAutenticacionSesion::autenticar);
        }
        cadena.doFilter(solicitud, respuesta);
    }

    private static String tokenDe(String encabezado) {
        if (encabezado == null || !encabezado.regionMatches(true, 0, ESQUEMA, 0, ESQUEMA.length())) {
            return null;
        }
        var token = encabezado.substring(ESQUEMA.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private static void autenticar(UsuarioAutenticado usuario) {
        var autenticacion = UsernamePasswordAuthenticationToken.authenticated(
                usuario, null, List.of(new SimpleGrantedAuthority("ROLE_" + usuario.rol())));
        var contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(autenticacion);
        SecurityContextHolder.setContext(contexto);
    }
}
