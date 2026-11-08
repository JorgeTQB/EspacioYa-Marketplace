package pe.edu.ulima.espacioya.reservas.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.reservas.domain.QuienCancela;
import pe.edu.ulima.espacioya.reservas.domain.Reserva;
import pe.edu.ulima.espacioya.reservas.domain.ReservaConHorario;
import pe.edu.ulima.espacioya.reservas.repository.ReservaDAO;
import pe.edu.ulima.espacioya.reservas.service.ReservaParaResena;

@ExtendWith(MockitoExtension.class)
class ReservasParaResenaImplTest {

    // 10:00 del miércoles 2026-10-28 en Lima.
    private static final Instant AHORA = Instant.parse("2026-10-28T15:00:00Z");
    private static final LocalDate FECHA = LocalDate.of(2026, 10, 20);
    private static final long USUARIO_ID = 7L;
    private static final long ESPACIO_ID = 40L;

    @Mock
    private ReservaDAO dao;

    private ReservasParaResenaImpl servicio;

    @BeforeEach
    void preparar() {
        servicio = new ReservasParaResenaImpl(dao, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private static Reserva pendiente(long id, Instant creadaEn) {
        var reserva = Reserva.pendiente(USUARIO_ID, ESPACIO_ID, new BigDecimal("80.50"), 2, creadaEn);
        ReflectionTestUtils.setField(reserva, "id", id);
        return reserva;
    }

    private static Reserva confirmada(long id) {
        var creadaEn = AHORA.minusSeconds(60);
        var reserva = pendiente(id, creadaEn);
        reserva.confirmar(creadaEn.plusSeconds(1));
        return reserva;
    }

    private static Reserva cancelada(long id) {
        var creadaEn = AHORA.minusSeconds(60);
        var reserva = pendiente(id, creadaEn);
        reserva.confirmar(creadaEn.plusSeconds(1));
        reserva.cancelar(QuienCancela.USUARIO, AHORA.plusSeconds(86_400), BigDecimal.ZERO, AHORA);
        return reserva;
    }

    private static ReservaConHorario conHorario(Reserva reserva, int inicio, int fin) {
        return new ReservaConHorario(reserva, FECHA, inicio, fin);
    }

    @Test
    @DisplayName("HU-24 R4: el fin es el del último bloque en hora de Lima")
    void finEnLima() {
        when(dao.delUsuario(USUARIO_ID)).thenReturn(List.of(conHorario(confirmada(90L), 14, 16)));

        assertThat(servicio.delUsuario(USUARIO_ID, 90L)).isEqualTo(
                new ReservaParaResena(90L, ESPACIO_ID, true, Instant.parse("2026-10-20T21:00:00Z")));
    }

    @Test
    @DisplayName("HU-24 R4: con horaFin 24 el fin son las 00:00 del día siguiente")
    void finConHora24() {
        when(dao.delUsuario(USUARIO_ID)).thenReturn(List.of(conHorario(confirmada(90L), 23, 24)));

        assertThat(servicio.delUsuario(USUARIO_ID, 90L).finEn()).isEqualTo(Instant.parse("2026-10-21T05:00:00Z"));
    }

    @Test
    @DisplayName("HU-24 R9: una reserva ajena o inexistente no aparece en las del usuario y responde 404")
    void ajenaOInexistente() {
        when(dao.delUsuario(USUARIO_ID)).thenReturn(List.of(conHorario(confirmada(90L), 14, 16)));

        assertThatThrownBy(() -> servicio.delUsuario(USUARIO_ID, 91L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("HU-24 R6: una pendiente vencida se lee EXPIRADA aunque su fila siga pendiente: no está confirmada")
    void pendienteVencida() {
        var vencida = pendiente(90L, AHORA.minusSeconds(3600));
        when(dao.delUsuario(USUARIO_ID)).thenReturn(List.of(conHorario(vencida, 14, 16)));

        assertThat(servicio.delUsuario(USUARIO_ID, 90L).confirmada()).isFalse();
    }

    @Test
    @DisplayName("HU-24 R6: una cancelada se devuelve como no confirmada")
    void canceladaNoEstaConfirmada() {
        when(dao.delUsuario(USUARIO_ID)).thenReturn(List.of(conHorario(cancelada(90L), 14, 16)));

        assertThat(servicio.delUsuario(USUARIO_ID, 90L).confirmada()).isFalse();
    }

    @Test
    @DisplayName("HU-24 R6: confirmadasDe deja fuera a las canceladas y a las pendientes, cualquiera sea su fin")
    void soloConfirmadas() {
        when(dao.delUsuario(USUARIO_ID)).thenReturn(List.of(conHorario(cancelada(1L), 8, 9),
                conHorario(pendiente(2L, AHORA), 9, 10), conHorario(confirmada(3L), 10, 11),
                new ReservaConHorario(confirmada(4L), LocalDate.of(2027, 1, 1), 10, 11)));

        assertThat(servicio.confirmadasDe(USUARIO_ID)).extracting(ReservaParaResena::id).containsExactly(3L, 4L);
    }
}
