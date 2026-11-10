package pe.edu.ulima.espacioya.reservas.service.recordatorio;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;
import pe.edu.ulima.espacioya.comun.correo.EnvioCorreo;
import pe.edu.ulima.espacioya.comun.correo.FabricaCorreo;
import pe.edu.ulima.espacioya.comun.transaccion.DespuesDelCommit;
import pe.edu.ulima.espacioya.cuentas.service.CuentaService;
import pe.edu.ulima.espacioya.espacios.service.CatalogoEspaciosService;
import pe.edu.ulima.espacioya.reservas.domain.ReglaRecordatorio;
import pe.edu.ulima.espacioya.reservas.domain.Reserva;
import pe.edu.ulima.espacioya.reservas.domain.ReservaConHorario;
import pe.edu.ulima.espacioya.reservas.repository.ReservaBloqueRepository;
import pe.edu.ulima.espacioya.reservas.repository.ReservaRepository;

/**
 * Recordatorio 24 h antes de cada reserva confirmada al usuario y al arrendador (HU-25 CA1, RF-D-10). ★ Client del
 * Factory Method: elige la fábrica de cada destinatario. Los destinatarios salen solo de la reserva, leídos en el
 * servidor (STRIDE E). Solo toma el bloqueo de {@code reserva}, como {@link
 * pe.edu.ulima.espacioya.reservas.service.ExpiracionReservas}: respeta el orden {@code usuario → espacio → reserva}.
 */
@Service
public class RecordatoriosReserva {

    public static final String ACCION_RECORDATORIO = "RECORDATORIO_RESERVA";

    private static final String ENTIDAD_RESERVA = "reserva";
    private static final Logger log = LoggerFactory.getLogger(RecordatoriosReserva.class);

    private final ReservaRepository reservas;
    private final ReservaBloqueRepository bloques;
    private final CuentaService cuentas;
    private final CatalogoEspaciosService catalogo;
    private final AuditoriaService auditoria;
    private final TransactionTemplate transacciones;
    private final Clock reloj;
    private final FabricaRecordatorioUsuario alUsuario;
    private final FabricaRecordatorioArrendador alArrendador;

    public RecordatoriosReserva(ReservaRepository reservas, ReservaBloqueRepository bloques, CuentaService cuentas,
                                CatalogoEspaciosService catalogo, EnvioCorreo envio, AuditoriaService auditoria,
                                TransactionTemplate transacciones, Clock reloj) {
        this.reservas = reservas;
        this.bloques = bloques;
        this.cuentas = cuentas;
        this.catalogo = catalogo;
        this.auditoria = auditoria;
        this.transacciones = transacciones;
        this.reloj = reloj;
        this.alUsuario = new FabricaRecordatorioUsuario(envio);
        this.alArrendador = new FabricaRecordatorioArrendador(envio);
    }

    /** Una transacción por reserva (R25); devuelve cuántas reservas quedaron marcadas. */
    public int enviarPendientes() {
        var ahora = Instant.now(reloj);
        var desde = ZonaHoraria.hoy(reloj);
        var hasta = ReglaRecordatorio.ultimaFecha(ahora);
        var marcadas = 0;
        for (var id : reservas.findIdsPorRecordar(desde, hasta)) {
            try {
                if (Boolean.TRUE.equals(transacciones.execute(estado -> recordarSiCorresponde(id, ahora)))) {
                    marcadas++;
                }
            } catch (RuntimeException e) {
                log.warn("No se pudo procesar el recordatorio de la reserva {}", id);
            }
        }
        return marcadas;
    }

    // Se relee bajo bloqueo: pudo cancelarse o recordarse entre la lectura de ids y el bloqueo (R18).
    private boolean recordarSiCorresponde(Long id, Instant ahora) {
        var reserva = reservas.findParaActualizar(id).filter(Reserva::admiteRecordatorio);
        if (reserva.isEmpty()) {
            return false;
        }
        var horario = horario(reserva.get());
        // Fuera de plazo no se marca: vuelve a ser candidata en la próxima pasada (R15, R16).
        if (horario.isEmpty() || !ReglaRecordatorio.corresponde(horario.get().instanteDeInicio(), ahora)) {
            return false;
        }
        recordar(horario.get(), ahora);
        return true;
    }

    private Optional<ReservaConHorario> horario(Reserva reserva) {
        return bloques.horarios(List.of(reserva.getId())).stream()
                .findFirst()
                .map(h -> new ReservaConHorario(reserva, h.fecha(), h.horaInicio(), h.horaUltima() + 1));
    }

    private void recordar(ReservaConHorario horario, Instant ahora) {
        var reserva = horario.reserva();
        reserva.marcarRecordatorio(ahora);
        var espacioId = reserva.getEspacioId();
        var arrendadorId = catalogo.arrendadoresDe(List.of(espacioId)).get(espacioId);
        var espacio = catalogo.nombres(List.of(espacioId)).get(espacioId);
        // La preferencia y el estado de cada cuenta se leen por separado: cada parte decide por sí misma (R19–R21).
        var correoUsuario = cuentas.correoParaRecordatorio(reserva.getUsuarioId());
        var correoArrendador = arrendadorId == null ? Optional.<String>empty()
                : cuentas.correoParaRecordatorio(arrendadorId);
        auditoria.registrar(new EventoAuditoria(null, ACCION_RECORDATORIO, ENTIDAD_RESERVA, reserva.getId(), null,
                "origen=PROCESO;destinatarios=" + destinatarios(correoUsuario, correoArrendador)));
        var datos = new DatosRecordatorio(espacio, horario.fecha(), horario.horaInicio(), horario.horaFin());
        correoUsuario.ifPresent(correo -> programar(alUsuario, correo, datos, reserva.getId(), "USUARIO"));
        correoArrendador.ifPresent(correo -> programar(alArrendador, correo, datos, reserva.getId(), "ARRENDADOR"));
    }

    private static String destinatarios(Optional<String> usuario, Optional<String> arrendador) {
        var lista = new StringJoiner(",");
        usuario.ifPresent(correo -> lista.add("USUARIO"));
        arrendador.ifPresent(correo -> lista.add("ARRENDADOR"));
        return lista.length() == 0 ? "NINGUNO" : lista.toString();
    }

    // Uno por sincronización: si falla uno, el otro sale igual y la marca se conserva, sin reintento (R23, R24, D3).
    private static void programar(FabricaCorreo<DatosRecordatorio> fabrica, String correo, DatosRecordatorio datos,
                                  Long reservaId, String destinatario) {
        DespuesDelCommit.ejecutar(() -> {
            try {
                fabrica.enviar(correo, datos);
            } catch (RuntimeException e) {
                log.warn("No se pudo enviar el recordatorio de la reserva {} al {}", reservaId, destinatario);
            }
        });
    }
}
