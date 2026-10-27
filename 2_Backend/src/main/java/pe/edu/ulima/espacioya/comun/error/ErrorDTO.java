package pe.edu.ulima.espacioya.comun.error;

import java.util.List;

public record ErrorDTO(String mensaje, List<ErrorCampoDTO> errores) {

    public static final String MENSAJE_VALIDACION = "Revisa los datos del formulario.";
    public static final String MENSAJE_INESPERADO = "Ocurrió un problema. Intenta de nuevo más tarde.";
    public static final String MENSAJE_DEMASIADOS_INTENTOS =
            "Hiciste demasiados intentos. Espera un momento e inténtalo de nuevo.";

    public ErrorDTO {
        errores = errores == null ? List.of() : List.copyOf(errores);
    }

    public static ErrorDTO de(String mensaje) {
        return new ErrorDTO(mensaje, List.of());
    }
}
