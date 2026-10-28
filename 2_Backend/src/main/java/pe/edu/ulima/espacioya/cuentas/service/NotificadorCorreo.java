package pe.edu.ulima.espacioya.cuentas.service;

public interface NotificadorCorreo {

    void notificarVerificacion(String correoNormalizado);

    void notificarRecuperacion(String correoNormalizado, String enlace);
}
