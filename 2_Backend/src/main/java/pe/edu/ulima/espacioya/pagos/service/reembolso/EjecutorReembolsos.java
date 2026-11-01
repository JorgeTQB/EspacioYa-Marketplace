package pe.edu.ulima.espacioya.pagos.service.reembolso;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import pe.edu.ulima.espacioya.comun.transaccion.DespuesDelCommit;

/** Invoker (★ HU-16): decide cuándo corre un {@link ComandoReembolso}, sin saber qué hace. */
@Component
public class EjecutorReembolsos {

    /** Intento automático: corre solo si la transacción en curso se confirma; con rollback no corre (R3–R5). */
    public void ejecutarDespuesDelCommit(ComandoReembolso comando) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("El reembolso automático necesita la transacción que lo creó");
        }
        DespuesDelCommit.ejecutar(comando::ejecutar);
    }

    /** Reintento: corre ya, fuera de toda transacción, para no retener filas durante la llamada externa (R16). */
    public ResultadoReembolso ejecutarAhora(ComandoReembolso comando) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Un reembolso no se ejecuta dentro de una transacción");
        }
        return comando.ejecutar();
    }
}
