package pe.edu.ulima.espacioya.cuentas.service;

/**
 * Envío de correos (HU-01, HU-04, HU-06, HU-09, HU-13, HU-15). HU-25 lo moverá a {@code comun/correo}. Ningún
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

    void notificarPagoConfirmado(String correoNormalizado, ComprobantePago comprobante);

    void notificarReservaPagada(String correoArrendador, AvisoReservaPagada aviso);

    void notificarCancelacionAlUsuario(String correoUsuario, AvisoCancelacionUsuario aviso);

    void notificarCancelacionAlArrendador(String correoArrendador, AvisoCancelacionArrendador aviso);

    void notificarCambioCuentaBancaria(String correoArrendador, AvisoCambioCuentaBancaria aviso);
}
