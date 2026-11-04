package pe.edu.ulima.espacioya.reservas.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import pe.edu.ulima.espacioya.espacios.service.CatalogoEspaciosService;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservaLiquidable;
import pe.edu.ulima.espacioya.reservas.domain.EstadoReserva;
import pe.edu.ulima.espacioya.reservas.domain.QuienCancela;
import pe.edu.ulima.espacioya.reservas.domain.Reserva;
import pe.edu.ulima.espacioya.reservas.repository.HorarioReserva;
import pe.edu.ulima.espacioya.reservas.repository.ReservaBloqueRepository;
import pe.edu.ulima.espacioya.reservas.repository.ReservaRepository;

@ExtendWith(MockitoExtension.class)
class ReservasLiquidablesImplTest {

    private static final Instant CREADA = Instant.parse("2026-10-28T15:00:00Z");
    private static final LocalDate FECHA = LocalDate.parse("2026-11-04");

    @Mock
    private ReservaRepository reservas;
    @Mock
    private ReservaBloqueRepository bloques;
    @Mock
    private CatalogoEspaciosService catalogo;

    private ReservasLiquidablesImpl puerto;

    @BeforeEach
    void preparar() {
        puerto = new ReservasLiquidablesImpl(reservas, bloques, catalogo);
    }

    private static Reserva reserva(long id, long espacioId, EstadoReserva estado) {
        var reserva = Reserva.pendiente(9L, espacioId, new BigDecimal("80.50"), 3, CREADA);
        ReflectionTestUtils.setField(reserva, "id", id);
        ReflectionTestUtils.setField(reserva, "estado", estado);
        return reserva;
    }

    private static Reserva cancelada(long id, QuienCancela quien, String montoReembolso) {
        var reserva = reserva(id, 40L, EstadoReserva.CANCELADA);
        ReflectionTestUtils.setField(reserva, "canceladaPor", quien);
        ReflectionTestUtils.setField(reserva, "montoReembolso", new BigDecimal(montoReembolso));
        return reserva;
    }

    private static HorarioReserva horario(long reservaId, int primera, int ultima) {
        return new HorarioReserva(reservaId, FECHA, (short) primera, (short) ultima);
    }

    @Test
    @DisplayName("HU-19 R7: describe cada reserva con su arrendador, espacio, horario y fin en Lima, en una consulta "
            + "por lote")
    void describeConArrendadorYHorario() {
        when(reservas.findAllById(Set.of(1L, 2L))).thenReturn(List.of(reserva(2L, 41L, EstadoReserva.CONFIRMADA),
                reserva(1L, 40L, EstadoReserva.CONFIRMADA)));
        when(bloques.horarios(any())).thenReturn(List.of(horario(1L, 14, 16), horario(2L, 23, 23)));
        when(catalogo.nombres(Set.of(40L, 41L))).thenReturn(Map.of(40L, "Cancha A", 41L, "Cancha B"));
        when(catalogo.arrendadoresDe(Set.of(40L, 41L))).thenReturn(Map.of(40L, 7L, 41L, 8L));

        var descritas = puerto.describir(List.of(1L, 2L, 2L));

        assertThat(descritas).containsExactly(
                new ReservaLiquidable(1L, 7L, 40L, "Cancha A", FECHA, 14, 17,
                        Instant.parse("2026-11-04T22:00:00Z"), true, false),
                new ReservaLiquidable(2L, 8L, 41L, "Cancha B", FECHA, 23, 24,
                        Instant.parse("2026-11-05T05:00:00Z"), true, false));
        verify(reservas, times(1)).findAllById(Set.of(1L, 2L));
        verify(bloques, times(1)).horarios(any());
        verify(catalogo, times(1)).arrendadoresDe(Set.of(40L, 41L));
    }

    @Test
    @DisplayName("HU-19 R7: una colección vacía devuelve vacío sin consultar")
    void vaciaSinConsultar() {
        assertThat(puerto.describir(List.of())).isEmpty();
        verifyNoInteractions(reservas, bloques, catalogo);
    }

    @Test
    @DisplayName("HU-19 R7: una reserva inexistente o sin filas de bloque no aparece")
    void sinBloquesSeOmite() {
        when(reservas.findAllById(Set.of(1L, 2L, 3L))).thenReturn(List.of(reserva(1L, 40L, EstadoReserva.CONFIRMADA),
                reserva(2L, 40L, EstadoReserva.CONFIRMADA)));
        when(bloques.horarios(any())).thenReturn(List.of(horario(2L, 10, 10)));
        when(catalogo.nombres(Set.of(40L))).thenReturn(Map.of(40L, "Cancha A"));
        when(catalogo.arrendadoresDe(Set.of(40L))).thenReturn(Map.of(40L, 7L));

        assertThat(puerto.describir(List.of(1L, 2L, 3L))).extracting(ReservaLiquidable::reservaId)
                .containsExactly(2L);
    }

    @Test
    @DisplayName("HU-19 R10: PENDIENTE_PAGO, EXPIRADA y las CANCELADA con reembolso o por el arrendador no se admiten")
    void estadosNoAdmitidos() {
        when(reservas.findAllById(any())).thenReturn(List.of(reserva(1L, 40L, EstadoReserva.PENDIENTE_PAGO),
                reserva(2L, 40L, EstadoReserva.EXPIRADA), cancelada(3L, QuienCancela.USUARIO, "241.50"),
                cancelada(4L, QuienCancela.ARRENDADOR, "241.50")));
        when(bloques.horarios(any())).thenReturn(List.of(horario(1L, 10, 10), horario(2L, 10, 10),
                horario(3L, 10, 10), horario(4L, 10, 10)));
        when(catalogo.nombres(Set.of(40L))).thenReturn(Map.of(40L, "Cancha A"));
        when(catalogo.arrendadoresDe(Set.of(40L))).thenReturn(Map.of(40L, 7L));

        var descritas = puerto.describir(List.of(1L, 2L, 3L, 4L));

        assertThat(descritas).extracting(ReservaLiquidable::admitida).containsExactly(false, false, false, false);
        assertThat(descritas).extracting(ReservaLiquidable::cancelada).containsExactly(false, false, true, true);
    }

    @Test
    @DisplayName("HU-19 R11: una CANCELADA por el usuario con monto_reembolso = 0 se admite y sale marcada cancelada")
    void canceladaSinReembolsoSeAdmite() {
        when(reservas.findAllById(any())).thenReturn(List.of(cancelada(5L, QuienCancela.USUARIO, "0.00")));
        when(bloques.horarios(any())).thenReturn(List.of(horario(5L, 14, 16)));
        when(catalogo.nombres(Set.of(40L))).thenReturn(Map.of(40L, "Cancha A"));
        when(catalogo.arrendadoresDe(Set.of(40L))).thenReturn(Map.of(40L, 7L));

        assertThat(puerto.describir(List.of(5L))).singleElement().satisfies(descrita -> {
            assertThat(descrita.admitida()).isTrue();
            assertThat(descrita.cancelada()).isTrue();
            assertThat(descrita.arrendadorId()).isEqualTo(7L);
        });
    }
}
