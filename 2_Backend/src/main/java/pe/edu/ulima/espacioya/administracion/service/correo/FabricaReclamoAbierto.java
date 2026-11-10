package pe.edu.ulima.espacioya.administracion.service.correo;

import pe.edu.ulima.espacioya.comun.correo.Correo;
import pe.edu.ulima.espacioya.comun.correo.EnvioCorreo;
import pe.edu.ulima.espacioya.comun.correo.FabricaCorreo;

/** ★ Factory Method · ConcreteCreator: crea {@link CorreoReclamoAbierto} (HU-26 DT-16). */
public final class FabricaReclamoAbierto extends FabricaCorreo<DatosReclamoAbierto> {

    public FabricaReclamoAbierto(EnvioCorreo envio) {
        super(envio);
    }

    @Override
    protected Correo crearCorreo(DatosReclamoAbierto datos) {
        return new CorreoReclamoAbierto(datos);
    }
}
