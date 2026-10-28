package pe.edu.ulima.espacioya.comun.limite;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import pe.edu.ulima.espacioya.comun.error.ErrorDTO;
import pe.edu.ulima.espacioya.comun.web.IpCliente;
import tools.jackson.databind.json.JsonMapper;

@Component
public class FiltroLimiteIntentos extends OncePerRequestFilter {

    private final List<ReglaLimite> reglas;
    private final LimitadorIntentos limitador;
    private final JsonMapper json;

    public FiltroLimiteIntentos(List<ReglaLimite> reglas, LimitadorIntentos limitador, JsonMapper json) {
        this.reglas = List.copyOf(reglas);
        this.limitador = limitador;
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest solicitud, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        var ruta = solicitud.getRequestURI().substring(solicitud.getContextPath().length());
        for (var regla : reglas) {
            if (regla.aplicaA(solicitud.getMethod(), ruta)
                    && !limitador.intentar(regla.clave(IpCliente.de(solicitud)), regla.maximo(), regla.ventana())) {
                rechazar(respuesta);
                return;
            }
        }
        cadena.doFilter(solicitud, respuesta);
    }

    private void rechazar(HttpServletResponse respuesta) throws IOException {
        respuesta.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        respuesta.setCharacterEncoding("UTF-8");
        json.writeValue(respuesta.getOutputStream(), ErrorDTO.de(ErrorDTO.MENSAJE_DEMASIADOS_INTENTOS));
    }
}
