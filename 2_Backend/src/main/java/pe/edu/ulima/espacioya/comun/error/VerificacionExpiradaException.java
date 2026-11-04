package pe.edu.ulima.espacioya.comun.error;

public class VerificacionExpiradaException extends RuntimeException {

    public VerificacionExpiradaException() {
        super(ErrorDTO.MENSAJE_VERIFICACION_EXPIRADA);
    }
}
