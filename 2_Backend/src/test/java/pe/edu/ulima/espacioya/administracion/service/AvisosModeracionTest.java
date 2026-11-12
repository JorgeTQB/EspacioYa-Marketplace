package pe.edu.ulima.espacioya.administracion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import pe.edu.ulima.espacioya.administracion.service.correo.CorreoEspacioOcultado;
import pe.edu.ulima.espacioya.administracion.service.correo.DatosEspacioOcultado;
import pe.edu.ulima.espacioya.comun.correo.EnvioCorreo;

class AvisosModeracionTest {

    private final EnvioCorreo envio = mock(EnvioCorreo.class);
    private final AvisosModeracion avisos = new AvisosModeracion(envio);

    @BeforeEach
    void abrirTransaccion() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void cerrarTransaccion() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private static void confirmar() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    @Test
    @DisplayName("HU-27 R48: sin transacción confirmada no sale el correo; tras el commit sale al arrendador")
    void soloTrasElCommit() {
        avisos.espacioOcultado("ana@correo.pe", new DatosEspacioOcultado("Cancha", "Fotos falsas", false, 0));

        verifyNoInteractions(envio);
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
        confirmar();
        verify(envio).enviar(argThat(m -> m.para().equals("ana@correo.pe")
                && m.asunto().equals(CorreoEspacioOcultado.ASUNTO)));
    }

    @Test
    @DisplayName("HU-27 R47: si el proveedor de correo falla, el aviso no lanza")
    void falloDelProveedorNoLanza() {
        doThrow(new IllegalStateException("caído")).when(envio).enviar(any());
        avisos.espacioOcultado("ana@correo.pe", new DatosEspacioOcultado("Cancha", "Fotos falsas", false, 0));

        assertThatCode(AvisosModeracionTest::confirmar).doesNotThrowAnyException();
    }
}
