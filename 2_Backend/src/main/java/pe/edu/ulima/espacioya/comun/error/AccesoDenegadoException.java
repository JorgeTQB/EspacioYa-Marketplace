package pe.edu.ulima.espacioya.comun.error;

public class AccesoDenegadoException extends RuntimeException {

    public AccesoDenegadoException() {
        super(ErrorDTO.MENSAJE_ACCESO_DENEGADO);
    }
}
