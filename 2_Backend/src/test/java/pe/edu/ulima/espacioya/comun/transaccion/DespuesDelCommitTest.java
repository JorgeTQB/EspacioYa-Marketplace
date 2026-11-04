package pe.edu.ulima.espacioya.comun.transaccion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class DespuesDelCommitTest {

    private final List<String> sucesos = new ArrayList<>();
    private final TransactionTemplate transacciones = new TransactionTemplate(new GestorDePrueba());

    @Test
    @DisplayName("HU-13 R48, R49: la acción corre después del commit, no antes")
    void correTrasElCommit() {
        transacciones.executeWithoutResult(estado -> {
            DespuesDelCommit.ejecutar(() -> sucesos.add("accion"));
            sucesos.add("fin de la transacción");
        });

        assertThat(sucesos).containsExactly("fin de la transacción", "commit", "accion");
    }

    @Test
    @DisplayName("HU-13 R50: con rollback la acción no corre")
    void noCorreConRollback() {
        transacciones.executeWithoutResult(estado -> {
            DespuesDelCommit.ejecutar(() -> sucesos.add("accion"));
            estado.setRollbackOnly();
        });

        assertThat(sucesos).containsExactly("rollback");
    }

    @Test
    @DisplayName("HU-13 R41, R51: un fallo de la acción no llega al llamador y no impide la siguiente")
    void atrapaElFallo() {
        assertThatCode(() -> transacciones.executeWithoutResult(estado -> {
            DespuesDelCommit.ejecutar(() -> {
                throw new IllegalStateException("el proveedor de correo falló");
            });
            DespuesDelCommit.ejecutar(() -> sucesos.add("segunda"));
        })).doesNotThrowAnyException();

        assertThat(sucesos).containsExactly("commit", "segunda");
    }

    private final class GestorDePrueba extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaccion, TransactionDefinition definicion) {
            // Sin recursos: solo importa cuándo corren las sincronizaciones.
        }

        @Override
        protected void doCommit(DefaultTransactionStatus estado) {
            sucesos.add("commit");
        }

        @Override
        protected void doRollback(DefaultTransactionStatus estado) {
            sucesos.add("rollback");
        }
    }
}
