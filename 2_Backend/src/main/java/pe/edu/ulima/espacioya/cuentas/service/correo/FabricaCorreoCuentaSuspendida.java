package pe.edu.ulima.espacioya.cuentas.service.correo;

import pe.edu.ulima.espacioya.comun.correo.Correo;
import pe.edu.ulima.espacioya.comun.correo.EnvioCorreo;
import pe.edu.ulima.espacioya.comun.correo.FabricaCorreo;

/** ★ Factory Method · ConcreteCreator: crea {@link CorreoCuentaSuspendida} (HU-25 D9). */
public final class FabricaCorreoCuentaSuspendida extends FabricaCorreo<String> {

    public FabricaCorreoCuentaSuspendida(EnvioCorreo envio) {
        super(envio);
    }

    @Override
    protected Correo crearCorreo(String motivo) {
        return new CorreoCuentaSuspendida(motivo);
    }
}
