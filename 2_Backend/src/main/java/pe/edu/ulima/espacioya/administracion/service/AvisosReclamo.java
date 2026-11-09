package pe.edu.ulima.espacioya.administracion.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import pe.edu.ulima.espacioya.administracion.service.correo.DatosReclamoAbierto;
import pe.edu.ulima.espacioya.administracion.service.correo.DatosResolucion;
import pe.edu.ulima.espacioya.administracion.service.correo.FabricaReclamoAbierto;
import pe.edu.ulima.espacioya.administracion.service.correo.FabricaResolucionArrendador;
import pe.edu.ulima.espacioya.administracion.service.correo.FabricaResolucionUsuario;
import pe.edu.ulima.espacioya.comun.correo.EnvioCorreo;
import pe.edu.ulima.espacioya.comun.correo.FabricaCorreo;
import pe.edu.ulima.espacioya.comun.transaccion.DespuesDelCommit;

/**
 * ★ Client del Factory Method de HU-25 para los correos de reclamos (HU-26 DT-16). Cada correo se envía en su propio
 * {@link DespuesDelCommit}: con rollback no sale ninguno (R59) y si uno falla el otro sale igual. Exige una
 * transacción activa.
 */
@Component
public class AvisosReclamo {

    private static final Logger log = LoggerFactory.getLogger(AvisosReclamo.class);

    private final FabricaReclamoAbierto abierto;
    private final FabricaResolucionUsuario resolucionUsuario;
    private final FabricaResolucionArrendador resolucionArrendador;

    public AvisosReclamo(EnvioCorreo envio) {
        this.abierto = new FabricaReclamoAbierto(envio);
        this.resolucionUsuario = new FabricaResolucionUsuario(envio);
        this.resolucionArrendador = new FabricaResolucionArrendador(envio);
    }

    public void reclamoAbierto(String correoArrendador, DatosReclamoAbierto datos) {
        programar(abierto, correoArrendador, datos, "ARRENDADOR");
    }

    public void reclamoResuelto(String correoUsuario, String correoArrendador, DatosResolucion datos) {
        programar(resolucionUsuario, correoUsuario, datos, "USUARIO");
        programar(resolucionArrendador, correoArrendador, datos, "ARRENDADOR");
    }

    private static <E> void programar(FabricaCorreo<E> fabrica, String correo, E datos, String destinatario) {
        DespuesDelCommit.ejecutar(() -> {
            try {
                fabrica.enviar(correo, datos);
            } catch (RuntimeException e) {
                log.warn("No se pudo enviar el correo del reclamo al {}", destinatario);
            }
        });
    }
}
