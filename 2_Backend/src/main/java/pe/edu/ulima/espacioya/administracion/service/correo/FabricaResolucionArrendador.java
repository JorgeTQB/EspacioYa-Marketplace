package pe.edu.ulima.espacioya.administracion.service.correo;

import pe.edu.ulima.espacioya.comun.correo.Correo;
import pe.edu.ulima.espacioya.comun.correo.EnvioCorreo;
import pe.edu.ulima.espacioya.comun.correo.FabricaCorreo;

/** ★ Factory Method · ConcreteCreator: crea {@link CorreoResolucionArrendador} (HU-26 DT-16). */
public final class FabricaResolucionArrendador extends FabricaCorreo<DatosResolucion> {

    public FabricaResolucionArrendador(EnvioCorreo envio) {
        super(envio);
    }

    @Override
    protected Correo crearCorreo(DatosResolucion datos) {
        return new CorreoResolucionArrendador(datos);
    }
}
