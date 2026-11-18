package pe.edu.ulima.espacioya.cuentas.service.correo;

import pe.edu.ulima.espacioya.comun.correo.Correo;
import pe.edu.ulima.espacioya.comun.correo.EnvioCorreo;
import pe.edu.ulima.espacioya.comun.correo.FabricaCorreo;
import pe.edu.ulima.espacioya.cuentas.service.AvisoCambioCuentaBancaria;

/** ★ Factory Method · ConcreteCreator: crea {@link CorreoCambioCuentaBancaria} (HU-25 D9). */
public final class FabricaCorreoCambioCuentaBancaria extends FabricaCorreo<AvisoCambioCuentaBancaria> {

    public FabricaCorreoCambioCuentaBancaria(EnvioCorreo envio) {
        super(envio);
    }

    @Override
    protected Correo crearCorreo(AvisoCambioCuentaBancaria aviso) {
        return new CorreoCambioCuentaBancaria(aviso);
    }
}
