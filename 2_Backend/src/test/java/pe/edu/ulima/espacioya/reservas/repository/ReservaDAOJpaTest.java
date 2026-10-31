package pe.edu.ulima.espacioya.reservas.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import pe.edu.ulima.espacioya.reservas.domain.EstadoReserva;
import pe.edu.ulima.espacioya.reservas.domain.Reserva;
import pe.edu.ulima.espacioya.reservas.domain.ReservaConHorario;

@ExtendWith(MockitoExtension.class)
class ReservaDAOJpaTest {

    private static final Instant AHORA = Instant.parse("2026-10-28T15:00:00Z");
    private static final LocalDate FECHA = LocalDate.parse("2026-11-04");

    @Mock
    private ReservaRepository reservas;
    @Mock
    private ReservaBloqueRepository bloques;

    private ReservaDAOJpa dao;

    @BeforeEach
    void preparar() {
        dao = new ReservaDAOJpa(reservas, bloques);
    }

    private static Reserva reserva(long id) {
        var reserva = Reserva.pendiente(7L, 40L, new BigDecimal("80.50"), 2, AHORA);
        ReflectionTestUtils.setField(reserva, "id", id);
        return reserva;
    }

    private static HorarioReserva horario(long reservaId, int primera, int ultima) {
        return new HorarioReserva(reservaId, FECHA, (short) primera, (short) ultima);
    }

    @Test
    @DisplayName("HU-14 R33, R35: delUsuario hace una consulta a reserva y una a reserva_bloque, y une el horario")
    void delUsuarioConDosConsultas() {
        var primera = reserva(1L);
        var segunda = reserva(2L);
        when(reservas.findByUsuarioId(7L)).thenReturn(List.of(primera, segunda));
        when(bloques.horarios(any())).thenReturn(List.of(horario(2L, 23, 23), horario(1L, 14, 15)));

        assertThat(dao.delUsuario(7L)).containsExactly(new ReservaConHorario(primera, FECHA, 14, 16),
                new ReservaConHorario(segunda, FECHA, 23, 24));
        verify(reservas, times(1)).findByUsuarioId(7L);
        verify(bloques, times(1)).horarios(List.of(1L, 2L));
    }

    @Test
    @DisplayName("HU-14 R35: sin reservas no consulta reserva_bloque")
    void sinReservasNoConsultaHorarios() {
        when(reservas.findByUsuarioId(7L)).thenReturn(List.of());

        assertThat(dao.delUsuario(7L)).isEmpty();
        verifyNoInteractions(bloques);
    }

    @Test
    @DisplayName("HU-14 R34, R35: confirmadasDeEspacios pide solo CONFIRMADA de esos espacios y une el horario")
    void confirmadasDeEspacios() {
        var confirmada = reserva(5L);
        when(reservas.findByEspacioIdInAndEstado(Set.of(40L, 41L), EstadoReserva.CONFIRMADA))
                .thenReturn(List.of(confirmada));
        when(bloques.horarios(List.of(5L))).thenReturn(List.of(horario(5L, 8, 9)));

        assertThat(dao.confirmadasDeEspacios(Set.of(40L, 41L)))
                .containsExactly(new ReservaConHorario(confirmada, FECHA, 8, 10));
        verify(bloques, times(1)).horarios(any());
    }

    @Test
    @DisplayName("HU-14 R34: con la colección de espacios vacía devuelve vacío sin consultar")
    void espaciosVaciosNoConsulta() {
        assertThat(dao.confirmadasDeEspacios(List.of())).isEmpty();
        verifyNoInteractions(reservas, bloques);
    }

    @Test
    @DisplayName("HU-14 R33: una reserva sin filas de reserva_bloque se omite")
    void reservaSinHorarioSeOmite() {
        var conHorario = reserva(1L);
        when(reservas.findByUsuarioId(7L)).thenReturn(List.of(conHorario, reserva(2L)));
        when(bloques.horarios(any())).thenReturn(List.of(horario(1L, 10, 10)));

        assertThat(dao.delUsuario(7L)).containsExactly(new ReservaConHorario(conHorario, FECHA, 10, 11));
        verify(reservas, never()).findByEspacioIdInAndEstado(any(), any());
    }
}
