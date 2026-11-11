package pe.edu.ulima.espacioya.reservas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import pe.edu.ulima.espacioya.cuentas.service.AvisoCancelacionArrendador;
import pe.edu.ulima.espacioya.cuentas.service.AvisoCancelacionUsuario;
import pe.edu.ulima.espacioya.cuentas.service.CuentaService;
import pe.edu.ulima.espacioya.cuentas.service.NotificadorCorreo;
import pe.edu.ulima.espacioya.reservas.domain.QuienCancela;
import pe.edu.ulima.espacioya.reservas.domain.ReembolsoCalculado;
import pe.edu.ulima.espacioya.reservas.domain.Reserva;
import pe.edu.ulima.espacioya.reservas.domain.ReservaConHorario;

@ExtendWith(MockitoExtension.class)
class AvisosCancelacionTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 11, 2);
    private static final String ESPACIO = "Cancha Los Olivos";
    private static final String CORREO_USUARIO = "carla@correo.pe";
    private static final String CORREO_ARRENDADOR = "ana@correo.pe";
    private static final ReservaConHorario HORARIO = new ReservaConHorario(
            Reserva.pendiente(7L, 40L, new BigDecimal("80.50"), 2, Instant.parse("2026-10-28T15:00:00Z")), FECHA,
            18, 20);
    private static final ReembolsoCalculado REEMBOLSO =
            new ReembolsoCalculado("USUARIO_CON_ANTICIPACION", 100, new BigDecimal("161.00"));
    private static final ReembolsoCalculado POR_MODERACION =
            new ReembolsoCalculado("MODERACION", 100, new BigDecimal("161.00"));
    private static final AvisoCancelacionUsuario AL_USUARIO =
            new AvisoCancelacionUsuario(ESPACIO, FECHA, 18, 20, "USUARIO", 100, new BigDecimal("161.00"));
    private static final AvisoCancelacionArrendador AL_ARRENDADOR =
            new AvisoCancelacionArrendador(ESPACIO, FECHA, 18, 20, "USUARIO");

    @Mock
    private CuentaService cuentas;
    @Mock
    private NotificadorCorreo notificador;

    private AvisosCancelacion avisos;

    @BeforeEach
    void preparar() {
        avisos = new AvisosCancelacion(cuentas, notificador);
        TransactionSynchronizationManager.initSynchronization();
        when(cuentas.correoDe(7L)).thenReturn(CORREO_USUARIO);
        // programarAlUsuario (HU-27) no lee el correo del arrendador.
        lenient().when(cuentas.correoDe(3L)).thenReturn(CORREO_ARRENDADOR);
    }

    @AfterEach
    void limpiar() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private void programar() {
        avisos.programar(7L, 3L, ESPACIO, HORARIO, QuienCancela.USUARIO, REEMBOLSO);
    }

    private static void confirmarTransaccion() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    @Test
    @DisplayName("HU-15 R29: lee los dos correos dentro de la transacción, registra dos envíos y no envía antes del "
            + "commit")
    void nadaAntesDelCommit() {
        programar();

        verify(cuentas).correoDe(7L);
        verify(cuentas).correoDe(3L);
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(2);
        verify(notificador, never()).notificarCancelacionAlUsuario(any(), any());
        verify(notificador, never()).notificarCancelacionAlArrendador(any(), any());
    }

    @Test
    @DisplayName("HU-15 R26: tras el commit avisa al usuario el espacio, fecha, horario, quién canceló, % y monto")
    void avisaAlUsuario() {
        programar();

        confirmarTransaccion();

        verify(notificador).notificarCancelacionAlUsuario(CORREO_USUARIO, AL_USUARIO);
    }

    @Test
    @DisplayName("HU-15 R27: tras el commit avisa al arrendador espacio, fecha, horario y quién canceló, sin montos")
    void avisaAlArrendadorSinMontos() {
        programar();

        confirmarTransaccion();

        verify(notificador).notificarCancelacionAlArrendador(CORREO_ARRENDADOR, AL_ARRENDADOR);
        assertThat(AvisoCancelacionArrendador.class.getRecordComponents())
                .extracting(componente -> componente.getName())
                .containsExactly("espacio", "fecha", "horaInicio", "horaFin", "canceladaPor");
    }

    @Test
    @DisplayName("HU-27 R48, R70: programarAlUsuario no envía sin commit y nunca avisa al arrendador")
    void alUsuarioSinCommitNoEnvia() {
        avisos.programarAlUsuario(7L, ESPACIO, HORARIO, QuienCancela.ADMIN, POR_MODERACION);

        verify(cuentas).correoDe(7L);
        verify(cuentas, never()).correoDe(3L);
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
        verify(notificador, never()).notificarCancelacionAlUsuario(any(), any());
        verify(notificador, never()).notificarCancelacionAlArrendador(any(), any());
    }

    @Test
    @DisplayName("HU-27 R69, R70: tras el commit, programarAlUsuario avisa solo al usuario con ADMIN y el 100 %")
    void alUsuarioTrasElCommit() {
        avisos.programarAlUsuario(7L, ESPACIO, HORARIO, QuienCancela.ADMIN, POR_MODERACION);

        confirmarTransaccion();

        verify(notificador).notificarCancelacionAlUsuario(CORREO_USUARIO,
                new AvisoCancelacionUsuario(ESPACIO, FECHA, 18, 20, "ADMIN", 100, new BigDecimal("161.00")));
        verify(notificador, never()).notificarCancelacionAlArrendador(any(), any());
    }

    @Test
    @DisplayName("HU-15 R28: si falla el aviso al usuario, el del arrendador sale igual")
    void unFalloNoImpideElOtro() {
        doThrow(new IllegalStateException("el proveedor de correo falló"))
                .when(notificador).notificarCancelacionAlUsuario(CORREO_USUARIO, AL_USUARIO);
        programar();

        confirmarTransaccion();

        verify(notificador).notificarCancelacionAlArrendador(CORREO_ARRENDADOR, AL_ARRENDADOR);
    }
}
