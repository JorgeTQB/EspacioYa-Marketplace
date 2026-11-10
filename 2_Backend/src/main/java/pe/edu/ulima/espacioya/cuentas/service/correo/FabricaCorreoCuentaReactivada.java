package pe.edu.ulima.espacioya.cuentas.service.correo;

import pe.edu.ulima.espacioya.comun.correo.Correo;
import pe.edu.ulima.espacioya.comun.correo.EnvioCorreo;
import pe.edu.ulima.espacioya.comun.correo.FabricaCorreo;
import pe.edu.ulima.espacioya.comun.correo.SinDatos;

/** ★ Factory Method · ConcreteCreator: crea {@link CorreoCuentaReactivada} (HU-25 D9). */
public final class FabricaCorreoCuentaReactivada extends FabricaCorreo<SinDatos> {

    public FabricaCorreoCuentaReactivada(EnvioCorreo envio) {
        super(envio);
    }

    @Override
    protected Correo crearCorreo(SinDatos evento) {
        return new CorreoCuentaReactivada(evento);
    }
}
