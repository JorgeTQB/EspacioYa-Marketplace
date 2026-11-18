package pe.edu.ulima.espacioya.cuentas.service.correo;

import pe.edu.ulima.espacioya.comun.correo.Correo;
import pe.edu.ulima.espacioya.comun.correo.EnvioCorreo;
import pe.edu.ulima.espacioya.comun.correo.FabricaCorreo;
import pe.edu.ulima.espacioya.cuentas.service.AvisoReservaPagada;

/** ★ Factory Method · ConcreteCreator: crea {@link CorreoReservaPagada} (HU-25 D9). */
public final class FabricaCorreoReservaPagada extends FabricaCorreo<AvisoReservaPagada> {

    public FabricaCorreoReservaPagada(EnvioCorreo envio) {
        super(envio);
    }

    @Override
    protected Correo crearCorreo(AvisoReservaPagada aviso) {
        return new CorreoReservaPagada(aviso);
    }
}
