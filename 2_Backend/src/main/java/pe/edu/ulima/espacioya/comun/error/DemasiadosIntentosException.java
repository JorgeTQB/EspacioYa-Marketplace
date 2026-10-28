package pe.edu.ulima.espacioya.comun.error;

public class DemasiadosIntentosException extends RuntimeException {

    public DemasiadosIntentosException() {
        super(ErrorDTO.MENSAJE_DEMASIADOS_INTENTOS);
    }
}
