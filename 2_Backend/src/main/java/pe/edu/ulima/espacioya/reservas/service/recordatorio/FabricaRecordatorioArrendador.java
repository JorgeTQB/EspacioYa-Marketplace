package pe.edu.ulima.espacioya.reservas.service.recordatorio;

import pe.edu.ulima.espacioya.comun.correo.Correo;
import pe.edu.ulima.espacioya.comun.correo.EnvioCorreo;
import pe.edu.ulima.espacioya.comun.correo.FabricaCorreo;

/** ★ Factory Method · ConcreteCreator: crea {@link CorreoRecordatorioArrendador} (HU-25 D9). */
public final class FabricaRecordatorioArrendador extends FabricaCorreo<DatosRecordatorio> {

    public FabricaRecordatorioArrendador(EnvioCorreo envio) {
        super(envio);
    }

    @Override
    protected Correo crearCorreo(DatosRecordatorio datos) {
        return new CorreoRecordatorioArrendador(datos);
    }
}
