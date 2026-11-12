package pe.edu.ulima.espacioya.administracion.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import pe.edu.ulima.espacioya.administracion.service.correo.DatosEspacioOcultado;
import pe.edu.ulima.espacioya.administracion.service.correo.FabricaEspacioOcultado;
import pe.edu.ulima.espacioya.comun.correo.EnvioCorreo;
import pe.edu.ulima.espacioya.comun.transaccion.DespuesDelCommit;

/**
 * ★ Client del Factory Method de HU-25 para el correo de moderación (HU-27 DT-12). Se envía en un
 * {@link DespuesDelCommit}: con rollback no sale (R48). Exige una transacción activa.
 */
@Component
public class AvisosModeracion {

    private static final Logger log = LoggerFactory.getLogger(AvisosModeracion.class);

    private final FabricaEspacioOcultado espacioOcultado;

    public AvisosModeracion(EnvioCorreo envio) {
        this.espacioOcultado = new FabricaEspacioOcultado(envio);
    }

    public void espacioOcultado(String correoArrendador, DatosEspacioOcultado datos) {
        DespuesDelCommit.ejecutar(() -> {
            try {
                espacioOcultado.enviar(correoArrendador, datos);
            } catch (RuntimeException e) {
                log.warn("No se pudo enviar el correo de moderación al arrendador");
            }
        });
    }
}
