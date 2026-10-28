package pe.edu.ulima.espacioya.comun.error;

public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException() {
        super(ErrorDTO.MENSAJE_RECURSO_NO_ENCONTRADO);
    }
}
