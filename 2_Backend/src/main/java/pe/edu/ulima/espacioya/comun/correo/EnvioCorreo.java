package pe.edu.ulima.espacioya.comun.correo;

/**
 * ★ Adapter · Target (HU-25 D8, RNF-ARQ-08): envía un correo sin exponer tipos del proveedor. Implementaciones:
 * {@link EnvioCorreoSmtp} ({@code espacioya.correo.proveedor=smtp}) y {@link EnvioCorreoSimulado} ({@code simulado}).
 * Con otro valor no hay bean y el backend no arranca (R34).
 */
public interface EnvioCorreo {

    /** Lanza {@link pe.edu.ulima.espacioya.comun.error.ServicioExternoException} si el proveedor falla (R33). */
    void enviar(MensajeCorreo mensaje);
}
