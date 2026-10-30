package pe.edu.ulima.espacioya.cuentas.service;

/**
 * Envío de correos de {@code cuentas} (HU-01, HU-04, HU-06, HU-09). HU-25 lo moverá a {@code comun/correo}. Ningún
 * correo lleva documentos (RNF-SEG-16): los avisos de identidad y de suspensión solo reciben el destinatario y, si hay,
 * el motivo.
 */
public interface NotificadorCorreo {

    void notificarVerificacion(String correoNormalizado);

    void notificarRecuperacion(String correoNormalizado, String enlace);

    void notificarIdentidadAprobada(String correoNormalizado);

    void notificarIdentidadRechazada(String correoNormalizado, String motivo);

    void notificarCuentaSuspendida(String correoNormalizado, String motivo);

    void notificarCuentaReactivada(String correoNormalizado);
}
