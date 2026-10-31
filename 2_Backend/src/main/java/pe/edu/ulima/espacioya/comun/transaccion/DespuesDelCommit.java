package pe.edu.ulima.espacioya.comun.transaccion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Ejecuta una acción solo si la transacción en curso se confirma: no se avisa de algo que hizo rollback (HU-06 R40,
 * HU-09 R39, HU-13 R50). Un fallo de la acción no revierte lo confirmado ni llega al llamador (HU-13 R41, R51).
 */
public final class DespuesDelCommit {

    private static final Logger log = LoggerFactory.getLogger(DespuesDelCommit.class);

    private DespuesDelCommit() {
    }

    public static void ejecutar(Runnable accion) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    accion.run();
                } catch (RuntimeException e) {
                    log.warn("No se pudo completar una acción tras confirmar la transacción");
                }
            }
        });
    }
}
