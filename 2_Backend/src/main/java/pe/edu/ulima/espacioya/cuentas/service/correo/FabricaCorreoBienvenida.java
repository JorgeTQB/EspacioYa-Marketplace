package pe.edu.ulima.espacioya.cuentas.service.correo;

import pe.edu.ulima.espacioya.comun.correo.Correo;
import pe.edu.ulima.espacioya.comun.correo.EnvioCorreo;
import pe.edu.ulima.espacioya.comun.correo.FabricaCorreo;
import pe.edu.ulima.espacioya.comun.correo.SinDatos;

/** ★ Factory Method · ConcreteCreator: crea {@link CorreoBienvenida} (HU-25 D9). */
public final class FabricaCorreoBienvenida extends FabricaCorreo<SinDatos> {

    public FabricaCorreoBienvenida(EnvioCorreo envio) {
        super(envio);
    }

    @Override
    protected Correo crearCorreo(SinDatos evento) {
        return new CorreoBienvenida(evento);
    }
}
