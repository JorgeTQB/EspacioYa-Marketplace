package pe.edu.ulima.espacioya.comun.error;

/** Un servicio externo falló o no respondió (HU-13 DT-13). Sin detalle: lo técnico va a los logs de quien lo lanza. */
public class ServicioExternoException extends RuntimeException {

    public ServicioExternoException() {
        super(ErrorDTO.MENSAJE_SERVICIO_EXTERNO);
    }
}
