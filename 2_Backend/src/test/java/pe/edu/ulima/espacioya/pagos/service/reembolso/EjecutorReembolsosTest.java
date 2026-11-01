package pe.edu.ulima.espacioya.pagos.service.reembolso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class EjecutorReembolsosTest {

    private final EjecutorReembolsos ejecutor = new EjecutorReembolsos();
    private final ComandoReembolso comando = mock(ComandoReembolso.class);

    private static void conTransaccion() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    @AfterEach
    void limpiar() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    @DisplayName("HU-16 R3: ejecutarDespuesDelCommit no corre el comando antes del commit y registra una sincronización")
    void noCorreAntesDelCommit() {
        conTransaccion();

        ejecutor.ejecutarDespuesDelCommit(comando);

        verifyNoInteractions(comando);
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
    }

    @Test
    @DisplayName("HU-16 R5: al confirmarse la transacción, el comando corre una vez")
    void correTrasElCommit() {
        conTransaccion();
        ejecutor.ejecutarDespuesDelCommit(comando);

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        verify(comando).ejecutar();
    }

    @Test
    @DisplayName("HU-16 R4: si la transacción se revierte, el comando no corre")
    void noCorreConRollback() {
        conTransaccion();
        ejecutor.ejecutarDespuesDelCommit(comando);

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sincronizacion -> sincronizacion.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verifyNoInteractions(comando);
    }

    @Test
    @DisplayName("HU-16 R3: ejecutarDespuesDelCommit sin transacción lanza IllegalStateException y no corre")
    void despuesDelCommitExigeTransaccion() {
        assertThatThrownBy(() -> ejecutor.ejecutarDespuesDelCommit(comando)).isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(comando);
    }

    @Test
    @DisplayName("HU-16 R16: ejecutarAhora con una transacción activa lanza IllegalStateException y no corre")
    void ahoraSinTransaccion() {
        conTransaccion();

        assertThatThrownBy(() -> ejecutor.ejecutarAhora(comando)).isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(comando);
    }

    @Test
    @DisplayName("HU-16 R40: ejecutarAhora fuera de transacción corre el comando y devuelve su resultado")
    void ahoraDevuelveElResultado() {
        when(comando.ejecutar()).thenReturn(ResultadoReembolso.REEMBOLSADO);

        assertThat(ejecutor.ejecutarAhora(comando)).isEqualTo(ResultadoReembolso.REEMBOLSADO);
    }
}
