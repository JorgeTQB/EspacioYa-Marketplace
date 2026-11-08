package pe.edu.ulima.espacioya.cuentas.service.correo;

import pe.edu.ulima.espacioya.comun.correo.Correo;
import pe.edu.ulima.espacioya.comun.correo.EnvioCorreo;
import pe.edu.ulima.espacioya.comun.correo.FabricaCorreo;

/** ★ Factory Method · ConcreteCreator: crea {@link CorreoAvisoCambioCorreo} (HU-25 D9). */
public final class FabricaCorreoAvisoCambioCorreo extends FabricaCorreo<String> {

    public FabricaCorreoAvisoCambioCorreo(EnvioCorreo envio) {
        super(envio);
    }

    @Override
    protected Correo crearCorreo(String correoNuevoEnmascarado) {
        return new CorreoAvisoCambioCorreo(correoNuevoEnmascarado);
    }
}
