package pe.edu.ulima.espacioya.cuentas.service.correo;

import pe.edu.ulima.espacioya.comun.correo.Correo;
import pe.edu.ulima.espacioya.comun.correo.EnvioCorreo;
import pe.edu.ulima.espacioya.comun.correo.FabricaCorreo;
import pe.edu.ulima.espacioya.cuentas.service.AvisoCancelacionUsuario;

/** ★ Factory Method · ConcreteCreator: crea {@link CorreoCancelacionUsuario} (HU-25 D9). */
public final class FabricaCorreoCancelacionUsuario extends FabricaCorreo<AvisoCancelacionUsuario> {

    public FabricaCorreoCancelacionUsuario(EnvioCorreo envio) {
        super(envio);
    }

    @Override
    protected Correo crearCorreo(AvisoCancelacionUsuario aviso) {
        return new CorreoCancelacionUsuario(aviso);
    }
}
