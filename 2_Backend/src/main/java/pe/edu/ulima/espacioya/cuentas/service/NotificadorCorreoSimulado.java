package pe.edu.ulima.espacioya.cuentas.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NotificadorCorreoSimulado implements NotificadorCorreo {

    private static final Logger log = LoggerFactory.getLogger(NotificadorCorreoSimulado.class);

    private final boolean registrarEnlace;

    public NotificadorCorreoSimulado(
            @Value("${espacioya.correo.simulado.registrar-enlace:false}") boolean registrarEnlace) {
        this.registrarEnlace = registrarEnlace;
    }

    @Override
    public void notificarVerificacion(String correoNormalizado) {
        // El correo no va al log: es un dato personal (Ley 29733). HU-25 conecta el envío real.
        log.info("Envío de correo de verificación simulado: no se envió ningún correo (HU-01).");
    }

    // Sin envío real, el enlace solo se puede probar si se lee del log. Por eso se registra únicamente con la
    // propiedad de desarrollo activada (apagada por defecto y nunca en UAT) y nunca junto al correo (HU-04, D1).
    @Override
    public void notificarRecuperacion(String correoNormalizado, String enlace) {
        if (registrarEnlace) {
            log.info("[Solo desarrollo] Enlace de recuperación simulado: {}", enlace);
            return;
        }
        log.info("Envío de correo de recuperación simulado: no se envió ningún correo (HU-04).");
    }

    // Ni el correo ni el motivo van al log: son datos personales del arrendador (Ley 29733).
    @Override
    public void notificarIdentidadAprobada(String correoNormalizado) {
        log.info("Envío de correo de identidad aprobada simulado: no se envió ningún correo (HU-06).");
    }

    @Override
    public void notificarIdentidadRechazada(String correoNormalizado, String motivo) {
        log.info("Envío de correo de identidad rechazada simulado: no se envió ningún correo (HU-06).");
    }

    // Igual que los avisos de identidad: ni el correo ni el motivo de la suspensión van al log (HU-09, R47).
    @Override
    public void notificarCuentaSuspendida(String correoNormalizado, String motivo) {
        log.info("Envío de correo de cuenta suspendida simulado: no se envió ningún correo (HU-09).");
    }

    @Override
    public void notificarCuentaReactivada(String correoNormalizado) {
        log.info("Envío de correo de cuenta reactivada simulado: no se envió ningún correo (HU-09).");
    }

    // Ni el correo ni los datos del comprobante van al log (HU-13, RNF-CUM-01).
    @Override
    public void notificarPagoConfirmado(String correoNormalizado, ComprobantePago comprobante) {
        log.info("Envío de comprobante de pago simulado: no se envió ningún correo (HU-13).");
    }

    @Override
    public void notificarReservaPagada(String correoArrendador, AvisoReservaPagada aviso) {
        log.info("Envío de aviso de reserva pagada simulado: no se envió ningún correo (HU-13).");
    }

    // Ni el correo ni los datos de la reserva o del reembolso van al log (HU-15, Ley 29733).
    @Override
    public void notificarCancelacionAlUsuario(String correoUsuario, AvisoCancelacionUsuario aviso) {
        log.info("Envío de aviso de cancelación al usuario simulado: no se envió ningún correo (HU-15).");
    }

    @Override
    public void notificarCancelacionAlArrendador(String correoArrendador, AvisoCancelacionArrendador aviso) {
        log.info("Envío de aviso de cancelación al arrendador simulado: no se envió ningún correo (HU-15).");
    }

    // Ni el correo, ni el banco, ni la cuenta van al log: son datos financieros y personales (HU-18 R22).
    @Override
    public void notificarCambioCuentaBancaria(String correoArrendador, AvisoCambioCuentaBancaria aviso) {
        log.info("Envío de aviso de cambio de cuenta bancaria simulado: no se envió ningún correo (HU-18).");
    }

    // Como la recuperación (HU-04): el enlace solo con la propiedad de desarrollo y nunca junto al correo (HU-20 R46).
    @Override
    public void notificarCambioCorreo(String correoNuevo, String enlace) {
        if (registrarEnlace) {
            log.info("[Solo desarrollo] Enlace de cambio de correo simulado: {}", enlace);
            return;
        }
        log.info("Envío de correo para confirmar el cambio de correo simulado: no se envió ningún correo (HU-20).");
    }

    // Ni el correo actual ni el nuevo (aunque vaya enmascarado) van al log (HU-20 R46).
    @Override
    public void notificarAvisoCambioCorreo(String correoActual, String correoNuevoEnmascarado) {
        log.info("Envío de aviso de cambio de correo simulado: no se envió ningún correo (HU-20).");
    }
}
