package pe.edu.ulima.espacioya.cuentas.service.correo;

import pe.edu.ulima.espacioya.comun.correo.Correo;
import pe.edu.ulima.espacioya.comun.correo.EnvioCorreo;
import pe.edu.ulima.espacioya.comun.correo.FabricaCorreo;
import pe.edu.ulima.espacioya.cuentas.service.AvisoCancelacionArrendador;

/** ★ Factory Method · ConcreteCreator: crea {@link CorreoCancelacionArrendador} (HU-25 D9). */
public final class FabricaCorreoCancelacionArrendador extends FabricaCorreo<AvisoCancelacionArrendador> {

    public FabricaCorreoCancelacionArrendador(EnvioCorreo envio) {
        super(envio);
    }

    @Override
    protected Correo crearCorreo(AvisoCancelacionArrendador aviso) {
        return new CorreoCancelacionArrendador(aviso);
    }
}
