package pe.edu.ulima.espacioya.administracion.service.correo;

import pe.edu.ulima.espacioya.comun.correo.Correo;
import pe.edu.ulima.espacioya.comun.correo.EnvioCorreo;
import pe.edu.ulima.espacioya.comun.correo.FabricaCorreo;

/** ★ Factory Method · ConcreteCreator: crea {@link CorreoEspacioOcultado} (HU-27 DT-12). */
public final class FabricaEspacioOcultado extends FabricaCorreo<DatosEspacioOcultado> {

    public FabricaEspacioOcultado(EnvioCorreo envio) {
        super(envio);
    }

    @Override
    protected Correo crearCorreo(DatosEspacioOcultado datos) {
        return new CorreoEspacioOcultado(datos);
    }
}
