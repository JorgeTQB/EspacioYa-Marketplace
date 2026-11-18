package pe.edu.ulima.espacioya.cuentas.service.correo;

import pe.edu.ulima.espacioya.comun.correo.Correo;
import pe.edu.ulima.espacioya.comun.correo.EnvioCorreo;
import pe.edu.ulima.espacioya.comun.correo.FabricaCorreo;

/** ★ Factory Method · ConcreteCreator: crea {@link CorreoIdentidadRechazada} (HU-25 D9). */
public final class FabricaCorreoIdentidadRechazada extends FabricaCorreo<String> {

    public FabricaCorreoIdentidadRechazada(EnvioCorreo envio) {
        super(envio);
    }

    @Override
    protected Correo crearCorreo(String motivo) {
        return new CorreoIdentidadRechazada(motivo);
    }
}
