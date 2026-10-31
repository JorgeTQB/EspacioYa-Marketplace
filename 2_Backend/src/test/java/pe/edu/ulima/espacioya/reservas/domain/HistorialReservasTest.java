package pe.edu.ulima.espacioya.reservas.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Iterator de HU-14: el miércoles 2026-10-28 a las 10:00 de Lima (15:00 UTC). */
class HistorialReservasTest {

    private static final Instant AHORA = Instant.parse("2026-10-28T15:00:00Z");
    private static final LocalDateTime AHORA_LOCAL = LocalDateTime.parse("2026-10-28T10:00:00");
    private static final LocalDate HOY = AHORA_LOCAL.toLocalDate();

    private static ReservaConHorario reserva(long id, EstadoReserva estado, Instant creadaEn, LocalDate fecha,
                                             int horaInicio, int horaFin) {
        var reserva = Reserva.pendiente(7L, 40L, new BigDecimal("80.50"), horaFin - horaInicio, creadaEn);
        ReflectionTestUtils.setField(reserva, "id", id);
        ReflectionTestUtils.setField(reserva, "estado", estado);
        return new ReservaConHorario(reserva, fecha, horaInicio, horaFin);
    }

    private static ReservaConHorario confirmada(long id, LocalDate fecha, int horaInicio, int horaFin) {
        return reserva(id, EstadoReserva.CONFIRMADA, AHORA.minus(Duration.ofDays(3)), fecha, horaInicio, horaFin);
    }

    private static List<Long> ids(Iterator<ReservaConHorario> recorrido) {
        var ids = new ArrayList<Long>();
        recorrido.forEachRemaining(reserva -> ids.add(reserva.reserva().getId()));
        return ids;
    }

    private static HistorialReservas historial(List<ReservaConHorario> reservas) {
        return new HistorialReservas(reservas, AHORA, AHORA_LOCAL);
    }

    // ── Corte (R22, R23) ─────────────────────────────────────────────────

    @Test
    @DisplayName("HU-14 R22: a las 10:00, una reserva que termina a las 10:00 es pasada y una que termina a las 11:00 "
            + "es próxima")
    void limiteDeLasDiez() {
        var termina10 = confirmada(1L, HOY, 9, 10);
        var termina11 = confirmada(2L, HOY, 10, 11);
        var historial = historial(List.of(termina10, termina11));

        assertThat(ids(historial.proximas())).containsExactly(2L);
        assertThat(ids(historial.pasadas())).containsExactly(1L);
        assertThat(termina10.fin()).isEqualTo(AHORA_LOCAL);
    }

    @Test
    @DisplayName("HU-14 R22: el bloque de 23 a 24 termina a las 00:00 del día siguiente y a las 23:59 sigue próximo")
    void bloqueDeMedianoche() {
        var ultima = confirmada(1L, HOY, 23, 24);
        var historial = new HistorialReservas(List.of(ultima), AHORA, HOY.atTime(23, 59));

        assertThat(ultima.fin()).isEqualTo(HOY.plusDays(1).atStartOfDay());
        assertThat(ids(historial.proximas())).containsExactly(1L);
        assertThat(ids(historial.pasadas())).isEmpty();
    }

    @Test
    @DisplayName("HU-14 R22: una pendiente vigente y una CONFIRMADA futuras son próximas")
    void vigenteYConfirmadaFuturas() {
        var vigente = reserva(1L, EstadoReserva.PENDIENTE_PAGO, AHORA, HOY.plusDays(7), 14, 16);
        var confirmada = confirmada(2L, HOY.plusDays(7), 18, 19);
        var historial = historial(List.of(vigente, confirmada));

        assertThat(ids(historial.proximas())).containsExactly(1L, 2L);
        assertThat(ids(historial.pasadas())).isEmpty();
    }

    @Test
    @DisplayName("HU-14 R22, R23: una pendiente vencida justo en expiraEn, una EXPIRADA y una CANCELADA futuras son "
            + "pasadas")
    void noVigentesFuturasSonPasadas() {
        var vencida = reserva(1L, EstadoReserva.PENDIENTE_PAGO, AHORA.minus(Duration.ofMinutes(15)), HOY.plusDays(7),
                14, 15);
        var expirada = reserva(2L, EstadoReserva.EXPIRADA, AHORA.minus(Duration.ofHours(1)), HOY.plusDays(8), 14, 15);
        var cancelada = confirmada(3L, HOY.plusDays(9), 14, 15);
        ReflectionTestUtils.setField(cancelada.reserva(), "estado", EstadoReserva.CANCELADA);
        var historial = historial(List.of(vencida, expirada, cancelada));

        assertThat(vencida.reserva().getExpiraEn()).isEqualTo(AHORA);
        assertThat(ids(historial.proximas())).isEmpty();
        assertThat(ids(historial.pasadas())).containsExactly(3L, 2L, 1L);
    }

    @Test
    @DisplayName("HU-14 R22: una pendiente vigente que ya terminó es pasada")
    void vigenteYaTerminadaEsPasada() {
        var vigente = reserva(1L, EstadoReserva.PENDIENTE_PAGO, AHORA, HOY, 8, 9);

        assertThat(ids(historial(List.of(vigente)).pasadas())).containsExactly(1L);
    }

    // ── Orden (R24, R25) ─────────────────────────────────────────────────

    @Test
    @DisplayName("HU-14 R24, R25: próximas ascendentes y pasadas descendentes por fecha, hora de inicio e id")
    void ordenEnLosDosSentidos() {
        var manana18 = confirmada(5L, HOY.plusDays(1), 18, 19);
        var manana08 = confirmada(9L, HOY.plusDays(1), 8, 9);
        var pasado = confirmada(1L, HOY.plusDays(2), 7, 8);
        var ayer20 = confirmada(4L, HOY.minusDays(1), 20, 21);
        var ayer06 = confirmada(8L, HOY.minusDays(1), 6, 7);
        var semanaPasada = confirmada(2L, HOY.minusDays(7), 22, 24);
        var historial = historial(List.of(ayer06, manana18, semanaPasada, pasado, ayer20, manana08));

        assertThat(ids(historial.proximas())).containsExactly(9L, 5L, 1L);
        assertThat(ids(historial.pasadas())).containsExactly(4L, 8L, 2L);
    }

    @Test
    @DisplayName("HU-14 R24, R25: a igual fecha y hora de inicio desempata el id, en los dos sentidos")
    void empatePorId() {
        var futuraA = confirmada(30L, HOY.plusDays(1), 18, 19);
        var futuraB = confirmada(10L, HOY.plusDays(1), 18, 20);
        var futuraC = confirmada(20L, HOY.plusDays(1), 18, 19);
        var pasadaA = confirmada(3L, HOY.minusDays(1), 18, 19);
        var pasadaB = confirmada(7L, HOY.minusDays(1), 18, 19);
        var historial = historial(List.of(futuraA, pasadaA, futuraB, pasadaB, futuraC));

        assertThat(ids(historial.proximas())).containsExactly(10L, 20L, 30L);
        assertThat(ids(historial.pasadas())).containsExactly(7L, 3L);
    }

    @Test
    @DisplayName("HU-14 R22: recorrer(grupo) entrega el mismo recorrido que proximas() y pasadas()")
    void recorrerPorGrupo() {
        var futura = confirmada(1L, HOY.plusDays(1), 18, 19);
        var pasada = confirmada(2L, HOY.minusDays(1), 18, 19);
        var historial = historial(List.of(futura, pasada));

        assertThat(ids(historial.recorrer(GrupoReservas.PROXIMAS))).containsExactly(1L);
        assertThat(ids(historial.recorrer(GrupoReservas.PASADAS))).containsExactly(2L);
    }

    // ── Contrato del Iterator ────────────────────────────────────────────

    @Test
    @DisplayName("HU-14 R32: sin reservas, hasNext es false y next lanza NoSuchElementException")
    void listaVacia() {
        var historial = historial(List.of());

        assertThat(historial.proximas().hasNext()).isFalse();
        assertThat(historial.pasadas().hasNext()).isFalse();
        assertThatThrownBy(() -> historial.proximas().next()).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("HU-14 R22: next tras agotar el recorrido lanza NoSuchElementException y hasNext no consume")
    void nextAgotado() {
        var recorrido = historial(List.of(confirmada(1L, HOY.plusDays(1), 18, 19))).proximas();

        assertThat(recorrido.hasNext()).isTrue();
        assertThat(recorrido.hasNext()).isTrue();
        assertThat(recorrido.next().reserva().getId()).isEqualTo(1L);
        assertThat(recorrido.hasNext()).isFalse();
        assertThatThrownBy(recorrido::next).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("HU-14 R22: el recorrido no permite borrar de la colección (remove lanza)")
    void removeNoSoportado() {
        var recorrido = historial(List.of(confirmada(1L, HOY.plusDays(1), 18, 19))).proximas();
        recorrido.next();

        assertThatThrownBy(recorrido::remove).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("HU-14 R22: modificar la lista de entrada después no altera el recorrido (copia privada)")
    void copiaPrivada() {
        var entrada = new ArrayList<>(List.of(confirmada(1L, HOY.plusDays(1), 18, 19)));
        var historial = historial(entrada);

        entrada.add(confirmada(2L, HOY.plusDays(2), 18, 19));
        entrada.removeFirst();

        assertThat(ids(historial.proximas())).containsExactly(1L);
    }

    @Test
    @DisplayName("HU-14 R22: cada recorrido es independiente: dos recorridos del mismo grupo dan lo mismo")
    void recorridosIndependientes() {
        var historial = historial(List.of(confirmada(1L, HOY.plusDays(1), 18, 19),
                confirmada(2L, HOY.plusDays(2), 18, 19)));
        var primero = historial.proximas();
        primero.next();

        assertThat(ids(historial.proximas())).containsExactly(1L, 2L);
        assertThat(ids(primero)).containsExactly(2L);
    }
}
