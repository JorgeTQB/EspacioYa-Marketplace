package pe.edu.ulima.espacioya.reservas.service.recordatorio;

import pe.edu.ulima.espacioya.comun.correo.Correo;
import pe.edu.ulima.espacioya.comun.correo.EnvioCorreo;
import pe.edu.ulima.espacioya.comun.correo.FabricaCorreo;

/** ★ Factory Method · ConcreteCreator: crea {@link CorreoRecordatorioUsuario} (HU-25 D9). */
public final class FabricaRecordatorioUsuario extends FabricaCorreo<DatosRecordatorio> {

    public FabricaRecordatorioUsuario(EnvioCorreo envio) {
        super(envio);
    }

    @Override
    protected Correo crearCorreo(DatosRecordatorio datos) {
        return new CorreoRecordatorioUsuario(datos);
    }
}
