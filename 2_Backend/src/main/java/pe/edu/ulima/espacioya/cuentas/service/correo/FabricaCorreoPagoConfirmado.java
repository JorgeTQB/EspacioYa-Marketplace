package pe.edu.ulima.espacioya.cuentas.service.correo;

import pe.edu.ulima.espacioya.comun.correo.Correo;
import pe.edu.ulima.espacioya.comun.correo.EnvioCorreo;
import pe.edu.ulima.espacioya.comun.correo.FabricaCorreo;
import pe.edu.ulima.espacioya.cuentas.service.ComprobantePago;

/** ★ Factory Method · ConcreteCreator: crea {@link CorreoPagoConfirmado} (HU-25 D9). */
public final class FabricaCorreoPagoConfirmado extends FabricaCorreo<ComprobantePago> {

    public FabricaCorreoPagoConfirmado(EnvioCorreo envio) {
        super(envio);
    }

    @Override
    protected Correo crearCorreo(ComprobantePago comprobante) {
        return new CorreoPagoConfirmado(comprobante);
    }
}
