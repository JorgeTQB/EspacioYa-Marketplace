package pe.edu.ulima.espacioya.cuentas.service.impl;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import pe.edu.ulima.espacioya.comun.correo.EnvioCorreo;
import pe.edu.ulima.espacioya.comun.correo.SinDatos;
import pe.edu.ulima.espacioya.cuentas.service.AvisoCambioCuentaBancaria;
import pe.edu.ulima.espacioya.cuentas.service.AvisoCancelacionArrendador;
import pe.edu.ulima.espacioya.cuentas.service.AvisoCancelacionUsuario;
import pe.edu.ulima.espacioya.cuentas.service.AvisoReservaPagada;
import pe.edu.ulima.espacioya.cuentas.service.ComprobantePago;
import pe.edu.ulima.espacioya.cuentas.service.NotificadorCorreo;
import pe.edu.ulima.espacioya.cuentas.service.correo.FabricaCorreoAvisoCambioCorreo;
import pe.edu.ulima.espacioya.cuentas.service.correo.FabricaCorreoBienvenida;
import pe.edu.ulima.espacioya.cuentas.service.correo.FabricaCorreoCambioCorreo;
import pe.edu.ulima.espacioya.cuentas.service.correo.FabricaCorreoCambioCuentaBancaria;
import pe.edu.ulima.espacioya.cuentas.service.correo.FabricaCorreoCancelacionArrendador;
import pe.edu.ulima.espacioya.cuentas.service.correo.FabricaCorreoCancelacionUsuario;
import pe.edu.ulima.espacioya.cuentas.service.correo.FabricaCorreoCuentaReactivada;
import pe.edu.ulima.espacioya.cuentas.service.correo.FabricaCorreoCuentaSuspendida;
import pe.edu.ulima.espacioya.cuentas.service.correo.FabricaCorreoIdentidadAprobada;
import pe.edu.ulima.espacioya.cuentas.service.correo.FabricaCorreoIdentidadRechazada;
import pe.edu.ulima.espacioya.cuentas.service.correo.FabricaCorreoPagoConfirmado;
import pe.edu.ulima.espacioya.cuentas.service.correo.FabricaCorreoRecuperacion;
import pe.edu.ulima.espacioya.cuentas.service.correo.FabricaCorreoReservaPagada;

/**
 * Adaptador real de {@link NotificadorCorreo} (HU-25 D10, R36): ★ Client del Factory Method. Cada método elige la
 * fábrica de su evento y le pasa el destinatario; la fábrica arma el correo de la tabla C1 y lo entrega al Target
 * {@link EnvioCorreo} del Adapter. Las fábricas no son beans: se crean aquí con el {@link EnvioCorreo} inyectado.
 */
@Component
@ConditionalOnProperty(name = "espacioya.correo.proveedor", havingValue = "smtp")
public class NotificadorCorreoImpl implements NotificadorCorreo {

    private final FabricaCorreoBienvenida bienvenida;
    private final FabricaCorreoRecuperacion recuperacion;
    private final FabricaCorreoIdentidadAprobada identidadAprobada;
    private final FabricaCorreoIdentidadRechazada identidadRechazada;
    private final FabricaCorreoCuentaSuspendida cuentaSuspendida;
    private final FabricaCorreoCuentaReactivada cuentaReactivada;
    private final FabricaCorreoPagoConfirmado pagoConfirmado;
    private final FabricaCorreoReservaPagada reservaPagada;
    private final FabricaCorreoCancelacionUsuario cancelacionUsuario;
    private final FabricaCorreoCancelacionArrendador cancelacionArrendador;
    private final FabricaCorreoCambioCuentaBancaria cambioCuentaBancaria;
    private final FabricaCorreoCambioCorreo cambioCorreo;
    private final FabricaCorreoAvisoCambioCorreo avisoCambioCorreo;

    public NotificadorCorreoImpl(EnvioCorreo envio) {
        this.bienvenida = new FabricaCorreoBienvenida(envio);
        this.recuperacion = new FabricaCorreoRecuperacion(envio);
        this.identidadAprobada = new FabricaCorreoIdentidadAprobada(envio);
        this.identidadRechazada = new FabricaCorreoIdentidadRechazada(envio);
        this.cuentaSuspendida = new FabricaCorreoCuentaSuspendida(envio);
        this.cuentaReactivada = new FabricaCorreoCuentaReactivada(envio);
        this.pagoConfirmado = new FabricaCorreoPagoConfirmado(envio);
        this.reservaPagada = new FabricaCorreoReservaPagada(envio);
        this.cancelacionUsuario = new FabricaCorreoCancelacionUsuario(envio);
        this.cancelacionArrendador = new FabricaCorreoCancelacionArrendador(envio);
        this.cambioCuentaBancaria = new FabricaCorreoCambioCuentaBancaria(envio);
        this.cambioCorreo = new FabricaCorreoCambioCorreo(envio);
        this.avisoCambioCorreo = new FabricaCorreoAvisoCambioCorreo(envio);
    }

    // D11: con SMTP es un correo de bienvenida sin enlace; la cuenta sigue naciendo verificada (HU-01 S5).
    @Override
    public void notificarVerificacion(String correoNormalizado) {
        bienvenida.enviar(correoNormalizado, SinDatos.VALOR);
    }

    @Override
    public void notificarRecuperacion(String correoNormalizado, String enlace) {
        recuperacion.enviar(correoNormalizado, enlace);
    }

    @Override
    public void notificarIdentidadAprobada(String correoNormalizado) {
        identidadAprobada.enviar(correoNormalizado, SinDatos.VALOR);
    }

    @Override
    public void notificarIdentidadRechazada(String correoNormalizado, String motivo) {
        identidadRechazada.enviar(correoNormalizado, motivo);
    }

    @Override
    public void notificarCuentaSuspendida(String correoNormalizado, String motivo) {
        cuentaSuspendida.enviar(correoNormalizado, motivo);
    }

    @Override
    public void notificarCuentaReactivada(String correoNormalizado) {
        cuentaReactivada.enviar(correoNormalizado, SinDatos.VALOR);
    }

    @Override
    public void notificarPagoConfirmado(String correoNormalizado, ComprobantePago comprobante) {
        pagoConfirmado.enviar(correoNormalizado, comprobante);
    }

    @Override
    public void notificarReservaPagada(String correoArrendador, AvisoReservaPagada aviso) {
        reservaPagada.enviar(correoArrendador, aviso);
    }

    @Override
    public void notificarCancelacionAlUsuario(String correoUsuario, AvisoCancelacionUsuario aviso) {
        cancelacionUsuario.enviar(correoUsuario, aviso);
    }

    @Override
    public void notificarCancelacionAlArrendador(String correoArrendador, AvisoCancelacionArrendador aviso) {
        cancelacionArrendador.enviar(correoArrendador, aviso);
    }

    @Override
    public void notificarCambioCuentaBancaria(String correoArrendador, AvisoCambioCuentaBancaria aviso) {
        cambioCuentaBancaria.enviar(correoArrendador, aviso);
    }

    @Override
    public void notificarCambioCorreo(String correoNuevo, String enlace) {
        cambioCorreo.enviar(correoNuevo, enlace);
    }

    @Override
    public void notificarAvisoCambioCorreo(String correoActual, String correoNuevoEnmascarado) {
        avisoCambioCorreo.enviar(correoActual, correoNuevoEnmascarado);
    }
}
