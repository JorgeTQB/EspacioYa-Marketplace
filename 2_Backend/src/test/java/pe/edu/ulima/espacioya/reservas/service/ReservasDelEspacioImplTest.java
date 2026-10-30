package pe.edu.ulima.espacioya.reservas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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

import pe.edu.ulima.espacioya.espacios.service.puertos.BloqueReservado;
import pe.edu.ulima.espacioya.reservas.domain.EstadoReserva;
import pe.edu.ulima.espacioya.reservas.repository.BloqueActivo;
import pe.edu.ulima.espacioya.reservas.repository.ReservaBloqueRepository;

@ExtendWith(MockitoExtension.class)
class ReservasDelEspacioImplTest {

    private static final Instant AHORA = Instant.parse("2026-10-28T15:00:00Z");
    private static final LocalDate FECHA = LocalDate.of(2026, 11, 2);

    @Mock
    private ReservaBloqueRepository bloques;

    private ReservasDelEspacioImpl puerto;

    @BeforeEach
    void preparar() {
        puerto = new ReservasDelEspacioImpl(bloques, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("HU-12 R6: traduce cada bloque activo y solo CONFIRMADA queda pagado")
    void traduceBloques() {
        when(bloques.bloquesActivos(40L, FECHA, FECHA, AHORA)).thenReturn(List.of(
                new BloqueActivo(40L, FECHA, (short) 8, EstadoReserva.PENDIENTE_PAGO),
                new BloqueActivo(40L, FECHA, (short) 9, EstadoReserva.CONFIRMADA)));

        assertThat(puerto.bloquesReservados(40L, FECHA, FECHA)).containsExactly(
                new BloqueReservado(FECHA, 8, false), new BloqueReservado(FECHA, 9, true));
    }

    @Test
    @DisplayName("HU-12 R7: el lote agrupa por espacio con una sola consulta, sin caer en la de un espacio")
    void loteConUnaConsulta() {
        when(bloques.bloquesActivosDelDia(anyCollection(), eq(FECHA), eq(AHORA))).thenReturn(List.of(
                new BloqueActivo(1L, FECHA, (short) 8, EstadoReserva.CONFIRMADA),
                new BloqueActivo(1L, FECHA, (short) 9, EstadoReserva.PENDIENTE_PAGO),
                new BloqueActivo(2L, FECHA, (short) 8, EstadoReserva.PENDIENTE_PAGO)));

        var porEspacio = puerto.bloquesReservadosDelDia(List.of(1L, 2L, 3L), FECHA);

        assertThat(porEspacio).containsOnlyKeys(1L, 2L);
        assertThat(porEspacio.get(1L)).containsExactly(new BloqueReservado(FECHA, 8, true),
                new BloqueReservado(FECHA, 9, false));
        assertThat(porEspacio.get(2L)).containsExactly(new BloqueReservado(FECHA, 8, false));
        verify(bloques, times(1)).bloquesActivosDelDia(anyCollection(), any(), any());
        verify(bloques, never()).bloquesActivos(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("HU-12 R7: con una colección vacía devuelve un mapa vacío sin consultar")
    void loteVacioNoConsulta() {
        assertThat(puerto.bloquesReservadosDelDia(List.of(), FECHA)).isEmpty();
        verifyNoInteractions(bloques);
    }
}
