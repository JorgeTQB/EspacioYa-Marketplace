package pe.edu.ulima.espacioya.cuentas.service.correo;

import pe.edu.ulima.espacioya.comun.correo.Correo;
import pe.edu.ulima.espacioya.comun.correo.EnvioCorreo;
import pe.edu.ulima.espacioya.comun.correo.FabricaCorreo;

/** ★ Factory Method · ConcreteCreator: crea {@link CorreoRecuperacion} (HU-25 D9). */
public final class FabricaCorreoRecuperacion extends FabricaCorreo<String> {

    public FabricaCorreoRecuperacion(EnvioCorreo envio) {
        super(envio);
    }

    @Override
    protected Correo crearCorreo(String enlace) {
        return new CorreoRecuperacion(enlace);
    }
}
