package pe.edu.ulima.espacioya.cuentas.service;

/**
 * Envío de correos (HU-01, HU-04, HU-06, HU-09, HU-13, HU-15, HU-18, HU-20). HU-25 lo moverá a {@code comun/correo}. Ningún
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

    /** Enlace para confirmar el correo nuevo, al correo nuevo (HU-20 R26). */
    void notificarCambioCorreo(String correoNuevo, String enlace);

    /** Aviso al correo actual de que se pidió cambiarlo, con el nuevo ya enmascarado (HU-20 R27, D2). */
    void notificarAvisoCambioCorreo(String correoActual, String correoNuevoEnmascarado);
}
