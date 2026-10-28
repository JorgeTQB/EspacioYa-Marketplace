package pe.edu.ulima.espacioya.comun.error;

import java.util.List;

/**
 * Varios errores de campo a la vez (p. ej. dos archivos inválidos), para que el usuario los corrija
 * todos en un solo intento. {@link ReglaNegocioException} admite un solo campo.
 */
public class ValidacionException extends RuntimeException {

    private final List<ErrorCampoDTO> errores;

    public ValidacionException(List<ErrorCampoDTO> errores) {
        super(ErrorDTO.MENSAJE_VALIDACION);
        this.errores = List.copyOf(errores);
    }

    public List<ErrorCampoDTO> getErrores() {
        return errores;
    }
}
