package pe.edu.ulima.espacioya.comun.seguridad;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import pe.edu.ulima.espacioya.comun.error.ErrorDTO;
import tools.jackson.databind.json.JsonMapper;

public class PuntoEntradaNoAutenticado implements AuthenticationEntryPoint {

    private final JsonMapper json;

    public PuntoEntradaNoAutenticado(JsonMapper json) {
        this.json = json;
    }

    @Override
    public void commence(HttpServletRequest solicitud, HttpServletResponse respuesta,
                         AuthenticationException excepcion) throws IOException {
        respuesta.setStatus(HttpStatus.UNAUTHORIZED.value());
        respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        respuesta.setCharacterEncoding("UTF-8");
        json.writeValue(respuesta.getOutputStream(), ErrorDTO.de(ErrorDTO.MENSAJE_SESION_INVALIDA));
    }
}
