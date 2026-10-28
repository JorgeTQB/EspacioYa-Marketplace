package pe.edu.ulima.espacioya.comun.error;

public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException() {
        super(ErrorDTO.MENSAJE_CREDENCIALES_INVALIDAS);
    }
}
