package pe.edu.ulima.espacioya.pagos.service.pasarela;

/** Una notificación sin id o sin firma válida. El controlador responde 401 vacío, sin datos en el log (HU-13 S9). */
public class FirmaInvalidaException extends RuntimeException {

    public FirmaInvalidaException() {
        super("Notificación sin firma válida");
    }
}
