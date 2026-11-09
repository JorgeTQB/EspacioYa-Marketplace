package pe.edu.ulima.espacioya.administracion.service.correo;

import pe.edu.ulima.espacioya.comun.correo.Correo;
import pe.edu.ulima.espacioya.comun.correo.EnvioCorreo;
import pe.edu.ulima.espacioya.comun.correo.FabricaCorreo;

/** ★ Factory Method · ConcreteCreator: crea {@link CorreoResolucionUsuario} (HU-26 DT-16). */
public final class FabricaResolucionUsuario extends FabricaCorreo<DatosResolucion> {

    public FabricaResolucionUsuario(EnvioCorreo envio) {
        super(envio);
    }

    @Override
    protected Correo crearCorreo(DatosResolucion datos) {
        return new CorreoResolucionUsuario(datos);
    }
}
