package pe.edu.ulima.espacioya.comun.seguridad;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import pe.edu.ulima.espacioya.comun.error.ErrorDTO;
import pe.edu.ulima.espacioya.comun.web.IpCliente;
import tools.jackson.databind.json.JsonMapper;

/**
 * Control del panel admin que complementa a MFA (HU-06, D2; HU-17, P3): las rutas {@code /api/admin/} solo se atienden
 * desde una IP permitida. Va antes de la autenticación, así que desde fuera no se distingue si hay sesión ni si
 * la ruta existe, y responde el mismo 403 que la falta de rol. No es un {@code @Component} para que Spring Boot
 * no lo registre además como filtro de servlet (igual que {@link FiltroAutenticacionSesion}).
 */
public class FiltroIpsAdmin extends OncePerRequestFilter {

    public static final String PREFIJO = "/api/admin/";

    // Mismo análisis de ruta que Spring MVC: una ruta con caracteres codificados no esquiva el filtro.
    private static final RequestMatcher RUTAS_ADMIN = PathPatternRequestMatcher.pathPattern(PREFIJO + "**");

    private final IpsPermitidasAdmin ips;
    private final JsonMapper json;

    public FiltroIpsAdmin(IpsPermitidasAdmin ips, JsonMapper json) {
        this.ips = ips;
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest solicitud, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        if (RUTAS_ADMIN.matches(solicitud) && !ips.permite(IpCliente.de(solicitud))) {
            rechazar(respuesta);
            return;
        }
        cadena.doFilter(solicitud, respuesta);
    }

    private void rechazar(HttpServletResponse respuesta) throws IOException {
        respuesta.setStatus(HttpStatus.FORBIDDEN.value());
        respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        respuesta.setCharacterEncoding("UTF-8");
        json.writeValue(respuesta.getOutputStream(), ErrorDTO.de(ErrorDTO.MENSAJE_ACCESO_DENEGADO));
    }
}
