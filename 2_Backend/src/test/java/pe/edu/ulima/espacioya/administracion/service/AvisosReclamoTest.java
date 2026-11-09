package pe.edu.ulima.espacioya.administracion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import pe.edu.ulima.espacioya.administracion.domain.MotivoReclamo;
import pe.edu.ulima.espacioya.administracion.domain.PapelReclamo;
import pe.edu.ulima.espacioya.administracion.service.correo.CorreoReclamoAbierto;
import pe.edu.ulima.espacioya.administracion.service.correo.DatosReclamoAbierto;
import pe.edu.ulima.espacioya.administracion.service.correo.DatosResolucion;
import pe.edu.ulima.espacioya.comun.correo.EnvioCorreo;
import pe.edu.ulima.espacioya.comun.correo.MensajeCorreo;

class AvisosReclamoTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 11, 3);

    private final EnvioCorreo envio = mock(EnvioCorreo.class);
    private final AvisosReclamo avisos = new AvisosReclamo(envio);

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

    private static DatosResolucion resolucion() {
        return new DatosResolucion("Cancha", FECHA, 8, 9, PapelReclamo.USUARIO, "Procede", BigDecimal.TEN);
    }

    @Test
    @DisplayName("HU-26 R22: el aviso al arrendador sale solo después del commit")
    void abiertoTrasElCommit() {
        avisos.reclamoAbierto("ana@correo.pe", new DatosReclamoAbierto("Cancha", FECHA, 8, 9, MotivoReclamo.OTRO));

        verifyNoInteractions(envio);
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
        confirmar();
        verify(envio).enviar(argThat(m -> m.para().equals("ana@correo.pe")
                && m.asunto().equals(CorreoReclamoAbierto.ASUNTO)));
    }

    @Test
    @DisplayName("HU-26 R57, R58, R59: dos correos, uno por sincronización; sin commit no sale ninguno")
    void resolucionUnoPorCorreo() {
        avisos.reclamoResuelto("carla@correo.pe", "ana@correo.pe", resolucion());

        verifyNoInteractions(envio);
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(2);
        confirmar();
        var enviados = ArgumentCaptor.forClass(MensajeCorreo.class);
        verify(envio, times(2)).enviar(enviados.capture());
        assertThat(enviados.getAllValues()).extracting(MensajeCorreo::para)
                .containsExactly("carla@correo.pe", "ana@correo.pe");
    }

    @Test
    @DisplayName("HU-26 R57, R58: si falla el correo al usuario, el del arrendador sale igual")
    void unoFallaElOtroSale() {
        doThrow(new IllegalStateException("caído")).when(envio)
                .enviar(argThat(m -> m != null && m.para().equals("carla@correo.pe")));

        avisos.reclamoResuelto("carla@correo.pe", "ana@correo.pe", resolucion());
        confirmar();

        verify(envio).enviar(argThat(m -> m != null && m.para().equals("ana@correo.pe")));
    }
}
