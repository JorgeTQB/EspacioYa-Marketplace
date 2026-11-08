package pe.edu.ulima.espacioya.cuentas.service.correo;

import pe.edu.ulima.espacioya.comun.correo.Correo;
import pe.edu.ulima.espacioya.comun.correo.EnvioCorreo;
import pe.edu.ulima.espacioya.comun.correo.FabricaCorreo;
import pe.edu.ulima.espacioya.comun.correo.SinDatos;

/** ★ Factory Method · ConcreteCreator: crea {@link CorreoIdentidadAprobada} (HU-25 D9). */
public final class FabricaCorreoIdentidadAprobada extends FabricaCorreo<SinDatos> {

    public FabricaCorreoIdentidadAprobada(EnvioCorreo envio) {
        super(envio);
    }

    @Override
    protected Correo crearCorreo(SinDatos evento) {
        return new CorreoIdentidadAprobada(evento);
    }
}
