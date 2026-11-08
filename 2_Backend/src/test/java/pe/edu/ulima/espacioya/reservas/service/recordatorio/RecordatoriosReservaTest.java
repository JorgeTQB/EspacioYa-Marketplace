package pe.edu.ulima.espacioya.reservas.service.recordatorio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.correo.EnvioCorreo;
import pe.edu.ulima.espacioya.comun.correo.MensajeCorreo;
import pe.edu.ulima.espacioya.comun.error.ServicioExternoException;
import pe.edu.ulima.espacioya.cuentas.service.CuentaService;
import pe.edu.ulima.espacioya.espacios.service.CatalogoEspaciosService;
import pe.edu.ulima.espacioya.reservas.domain.EstadoReserva;
import pe.edu.ulima.espacioya.reservas.domain.Reserva;
import pe.edu.ulima.espacioya.reservas.repository.HorarioReserva;
import pe.edu.ulima.espacioya.reservas.repository.ReservaBloqueRepository;
import pe.edu.ulima.espacioya.reservas.repository.ReservaRepository;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class RecordatoriosReservaTest {

    // Miércoles 28/10/2026 10:00 de Lima.
    private static final Instant AHORA = Instant.parse("2026-10-28T15:00:00Z");
    private static final LocalDate HOY = LocalDate.of(2026, 10, 28);
    private static final LocalDate MANANA = LocalDate.of(2026, 10, 29);
    private static final long ID = 90L;
    private static final long USUARIO = 7L;
    private static final long ESPACIO = 40L;
    private static final long ARRENDADOR = 3L;
    private static final String CORREO_USUARIO = "carla@correo.pe";
    private static final String CORREO_ARRENDADOR = "ana@correo.pe";
    private static final String ESPACIO_NOMBRE = "Cancha Los Olivos";

    @Mock
    private ReservaRepository reservas;
    @Mock
    private ReservaBloqueRepository bloques;
    @Mock
    private CuentaService cuentas;
    @Mock
    private CatalogoEspaciosService catalogo;
    @Mock
    private AuditoriaService auditoria;

    private final List<MensajeCorreo> enviados = new ArrayList<>();
    private EnvioCorreo envio = enviados::add;
    private RecordatoriosReserva recordatorios;

    @BeforeEach
    void preparar() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void limpiar() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private RecordatoriosReserva servicio() {
        return new RecordatoriosReserva(reservas, bloques, cuentas, catalogo, envio, auditoria,
                new TransactionTemplate(mock(PlatformTransactionManager.class)), Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private static Reserva reserva(long id, EstadoReserva estado) {
        var reserva = Reserva.pendiente(USUARIO, ESPACIO, new BigDecimal("80.50"), 2, AHORA.minusSeconds(86_400 * 3));
        ReflectionTestUtils.setField(reserva, "id", id);
        ReflectionTestUtils.setField(reserva, "estado", estado);
        return reserva;
    }

    private Reserva candidata(long id, LocalDate fecha, int horaInicio) {
        var reserva = reserva(id, EstadoReserva.CONFIRMADA);
        when(reservas.findParaActualizar(id)).thenReturn(Optional.of(reserva));
        when(bloques.horarios(List.of(id)))
                .thenReturn(List.of(new HorarioReserva(id, fecha, (short) horaInicio, (short) (horaInicio + 1))));
        return reserva;
    }

    private void participantes(Optional<String> correoUsuario, Optional<String> correoArrendador) {
        when(catalogo.arrendadoresDe(any())).thenReturn(Map.of(ESPACIO, ARRENDADOR));
        when(catalogo.nombres(any())).thenReturn(Map.of(ESPACIO, ESPACIO_NOMBRE));
        when(cuentas.correoParaRecordatorio(USUARIO)).thenReturn(correoUsuario);
        when(cuentas.correoParaRecordatorio(ARRENDADOR)).thenReturn(correoArrendador);
    }

    private void candidatas(Long... ids) {
        when(reservas.findIdsPorRecordar(HOY, MANANA)).thenReturn(List.of(ids));
    }

    private static void confirmarTransaccion() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    private EventoAuditoria auditado() {
        var evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        return evento.getValue();
    }

    @Test
    @DisplayName("HU-25 R11, R12, R13: dentro del plazo marca la reserva y, tras el commit, envía un correo a cada "
            + "parte")
    void enviaAlUsuarioYAlArrendador() {
        candidatas(ID);
        // Jueves 29/10 09:00 Lima: faltan 23 h.
        var reserva = candidata(ID, MANANA, 9);
        participantes(Optional.of(CORREO_USUARIO), Optional.of(CORREO_ARRENDADOR));

        var marcadas = servicio().enviarPendientes();

        assertThat(marcadas).isEqualTo(1);
        assertThat(reserva.getRecordatorioEnviadoEn()).isEqualTo(AHORA);
        assertThat(enviados).isEmpty();
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(2);
        confirmarTransaccion();
        assertThat(enviados).extracting(MensajeCorreo::para).containsExactly(CORREO_USUARIO, CORREO_ARRENDADOR);
        assertThat(enviados).extracting(MensajeCorreo::asunto)
                .containsExactly(CorreoRecordatorioUsuario.ASUNTO, CorreoRecordatorioArrendador.ASUNTO);
        assertThat(enviados).allSatisfy(mensaje ->
                assertThat(mensaje.cuerpo()).contains(ESPACIO_NOMBRE, "29/10/2026", "09:00 a 11:00"));
    }

    @Test
    @DisplayName("HU-25 R22: audita RECORDATORIO_RESERVA sin usuario ni IP, con los destinatarios")
    void auditaConDestinatarios() {
        candidatas(ID);
        candidata(ID, MANANA, 9);
        participantes(Optional.of(CORREO_USUARIO), Optional.of(CORREO_ARRENDADOR));

        servicio().enviarPendientes();

        assertThat(auditado()).isEqualTo(new EventoAuditoria(null, RecordatoriosReserva.ACCION_RECORDATORIO, "reserva",
                ID, null, "origen=PROCESO;destinatarios=USUARIO,ARRENDADOR"));
    }

    @Test
    @DisplayName("HU-25 R19: con la preferencia del usuario desactivada solo recibe el arrendador")
    void usuarioSinPreferencia() {
        candidatas(ID);
        candidata(ID, MANANA, 9);
        participantes(Optional.empty(), Optional.of(CORREO_ARRENDADOR));

        servicio().enviarPendientes();
        confirmarTransaccion();

        assertThat(enviados).extracting(MensajeCorreo::para).containsExactly(CORREO_ARRENDADOR);
        assertThat(auditado().detalle()).isEqualTo("origen=PROCESO;destinatarios=ARRENDADOR");
    }

    @Test
    @DisplayName("HU-25 R20: con la preferencia del arrendador desactivada solo recibe el usuario")
    void arrendadorSinPreferencia() {
        candidatas(ID);
        candidata(ID, MANANA, 9);
        participantes(Optional.of(CORREO_USUARIO), Optional.empty());

        servicio().enviarPendientes();
        confirmarTransaccion();

        assertThat(enviados).extracting(MensajeCorreo::para).containsExactly(CORREO_USUARIO);
        assertThat(auditado().detalle()).isEqualTo("origen=PROCESO;destinatarios=USUARIO");
    }

    @Test
    @DisplayName("HU-25 R19, R20, R21: sin destinatarios marca igual, audita NINGUNO y no envía")
    void ningunDestinatario() {
        candidatas(ID);
        var reserva = candidata(ID, MANANA, 9);
        participantes(Optional.empty(), Optional.empty());

        assertThat(servicio().enviarPendientes()).isEqualTo(1);
        confirmarTransaccion();

        assertThat(reserva.getRecordatorioEnviadoEn()).isEqualTo(AHORA);
        assertThat(enviados).isEmpty();
        assertThat(auditado().detalle()).isEqualTo("origen=PROCESO;destinatarios=NINGUNO");
    }

    @Test
    @DisplayName("HU-25 R18: si al bloquearla ya está cancelada no envía, no marca y no audita")
    void canceladaAlReleer() {
        candidatas(ID);
        var reserva = reserva(ID, EstadoReserva.CANCELADA);
        when(reservas.findParaActualizar(ID)).thenReturn(Optional.of(reserva));

        assertThat(servicio().enviarPendientes()).isZero();

        assertThat(reserva.getRecordatorioEnviadoEn()).isNull();
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        verify(auditoria, never()).registrar(any());
    }

    @Test
    @DisplayName("HU-25 R14, R18: si al bloquearla ya tiene recordatorio no envía otro")
    void yaRecordadaAlReleer() {
        candidatas(ID);
        var reserva = reserva(ID, EstadoReserva.CONFIRMADA);
        reserva.marcarRecordatorio(AHORA.minusSeconds(300));
        when(reservas.findParaActualizar(ID)).thenReturn(Optional.of(reserva));

        assertThat(servicio().enviarPendientes()).isZero();

        assertThat(reserva.getRecordatorioEnviadoEn()).isEqualTo(AHORA.minusSeconds(300));
        verify(auditoria, never()).registrar(any());
        verify(cuentas, never()).correoParaRecordatorio(any());
    }

    @Test
    @DisplayName("HU-25 R15: si faltan más de 24 h no envía ni marca")
    void todaviaLejos() {
        candidatas(ID);
        // Jueves 29/10 11:00 Lima: faltan 25 h.
        var reserva = candidata(ID, MANANA, 11);

        assertThat(servicio().enviarPendientes()).isZero();

        assertThat(reserva.getRecordatorioEnviadoEn()).isNull();
        verify(auditoria, never()).registrar(any());
        verify(cuentas, never()).correoParaRecordatorio(any());
    }

    @Test
    @DisplayName("HU-25 R16: si la reserva ya empezó no envía ni marca")
    void yaEmpezo() {
        candidatas(ID);
        // Hoy 10:00 Lima: empieza justo ahora.
        var reserva = candidata(ID, HOY, 10);

        assertThat(servicio().enviarPendientes()).isZero();

        assertThat(reserva.getRecordatorioEnviadoEn()).isNull();
        verify(auditoria, never()).registrar(any());
    }

    @Test
    @DisplayName("HU-25 R23: sin commit no sale ningún recordatorio")
    void sinCommitNoEnvia() {
        candidatas(ID);
        candidata(ID, MANANA, 9);
        participantes(Optional.of(CORREO_USUARIO), Optional.of(CORREO_ARRENDADOR));

        servicio().enviarPendientes();
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sincronizacion -> sincronizacion.afterCompletion(
                        TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(enviados).isEmpty();
    }

    @Test
    @DisplayName("HU-25 R24: si falla el correo al usuario, el arrendador lo recibe igual y el log no tiene correos")
    void fallaUnEnvio(CapturedOutput salida) {
        envio = mensaje -> {
            if (mensaje.para().equals(CORREO_USUARIO)) {
                throw new ServicioExternoException();
            }
            enviados.add(mensaje);
        };
        candidatas(ID);
        var reserva = candidata(ID, MANANA, 9);
        participantes(Optional.of(CORREO_USUARIO), Optional.of(CORREO_ARRENDADOR));

        servicio().enviarPendientes();
        confirmarTransaccion();

        assertThat(enviados).extracting(MensajeCorreo::para).containsExactly(CORREO_ARRENDADOR);
        assertThat(reserva.getRecordatorioEnviadoEn()).isEqualTo(AHORA);
        assertThat(salida.getAll()).contains("No se pudo enviar el recordatorio de la reserva " + ID)
                .doesNotContain(CORREO_USUARIO).doesNotContain(CORREO_ARRENDADOR);
    }

    @Test
    @DisplayName("HU-25 R25: si una reserva falla, el proceso sigue con las demás de la pasada")
    void unaFallaNoDetieneLasDemas(CapturedOutput salida) {
        candidatas(1L, ID);
        when(reservas.findParaActualizar(1L)).thenThrow(new IllegalStateException("la base falló"));
        candidata(ID, MANANA, 9);
        participantes(Optional.of(CORREO_USUARIO), Optional.of(CORREO_ARRENDADOR));

        assertThat(servicio().enviarPendientes()).isEqualTo(1);
        confirmarTransaccion();

        assertThat(enviados).hasSize(2);
        assertThat(salida.getAll()).contains("No se pudo procesar el recordatorio de la reserva 1")
                .doesNotContain(CORREO_USUARIO);
    }

    @Test
    @DisplayName("HU-25 R11 (D2): una reserva que empieza en 2 h también recibe su recordatorio")
    void reservaConMenosDeVeinticuatroHoras() {
        when(reservas.findIdsPorRecordar(HOY, MANANA)).thenReturn(List.of(ID));
        // Hoy 12:00 Lima.
        candidata(ID, HOY, 12);
        participantes(Optional.of(CORREO_USUARIO), Optional.of(CORREO_ARRENDADOR));

        assertThat(servicio().enviarPendientes()).isEqualTo(1);
        confirmarTransaccion();

        assertThat(enviados).hasSize(2);
    }
}
