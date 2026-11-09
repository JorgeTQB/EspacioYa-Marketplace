package pe.edu.ulima.espacioya.reservas.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.espacios.service.CatalogoEspaciosService;
import pe.edu.ulima.espacioya.reservas.domain.QuienCancela;
import pe.edu.ulima.espacioya.reservas.domain.Reserva;
import pe.edu.ulima.espacioya.reservas.domain.ReservaConHorario;
import pe.edu.ulima.espacioya.reservas.repository.HorarioReserva;
import pe.edu.ulima.espacioya.reservas.repository.ReservaBloqueRepository;
import pe.edu.ulima.espacioya.reservas.repository.ReservaDAO;
import pe.edu.ulima.espacioya.reservas.repository.ReservaRepository;
import pe.edu.ulima.espacioya.reservas.service.ReservaParaReclamo;

@ExtendWith(MockitoExtension.class)
class ReservasParaReclamoImplTest {

    // 10:00 del miércoles 2026-11-04 en Lima.
    private static final Instant AHORA = Instant.parse("2026-11-04T15:00:00Z");
    private static final LocalDate FECHA = LocalDate.of(2026, 11, 3);
    private static final long USUARIO_ID = 7L;
    private static final long ESPACIO_ID = 40L;
    private static final long ARRENDADOR_ID = 3L;

    @Mock
    private ReservaDAO dao;
    @Mock
    private ReservaRepository reservas;
    @Mock
    private ReservaBloqueRepository bloques;
    @Mock
    private CatalogoEspaciosService catalogo;

    private ReservasParaReclamoImpl servicio;

    @BeforeEach
    void preparar() {
        servicio = new ReservasParaReclamoImpl(dao, reservas, bloques, catalogo, Clock.fixed(AHORA, ZoneOffset.UTC));
        lenient().when(catalogo.nombres(any())).thenReturn(Map.of(ESPACIO_ID, "Cancha Olivos"));
        lenient().when(catalogo.arrendadoresDe(any())).thenReturn(Map.of(ESPACIO_ID, ARRENDADOR_ID));
    }

    private static Reserva pendiente(long id, Instant creadaEn) {
        var reserva = Reserva.pendiente(USUARIO_ID, ESPACIO_ID, new BigDecimal("80.50"), 2, creadaEn);
        ReflectionTestUtils.setField(reserva, "id", id);
        return reserva;
    }

    private static Reserva confirmada(long id) {
        var creadaEn = AHORA.minusSeconds(86_400 * 3);
        var reserva = pendiente(id, creadaEn);
        reserva.confirmar(creadaEn.plusSeconds(1));
        return reserva;
    }

    private static Reserva cancelada(long id) {
        var reserva = confirmada(id);
        reserva.cancelar(QuienCancela.USUARIO, AHORA.plusSeconds(86_400), BigDecimal.ZERO, AHORA.minusSeconds(60));
        return reserva;
    }

    @Test
    @DisplayName("HU-26 R3, R4: devuelve el arrendador del espacio, el nombre y el inicio y fin en hora de Lima")
    void inicioFinYArrendador() {
        when(dao.delUsuario(USUARIO_ID)).thenReturn(List.of(new ReservaConHorario(confirmada(90L), FECHA, 14, 16)));

        assertThat(servicio.delUsuario(USUARIO_ID, 90L)).isEqualTo(new ReservaParaReclamo(90L, USUARIO_ID, ESPACIO_ID,
                "Cancha Olivos", ARRENDADOR_ID, true, FECHA, 14, 16, Instant.parse("2026-11-03T19:00:00Z"),
                Instant.parse("2026-11-03T21:00:00Z")));
    }

    @Test
    @DisplayName("HU-26 R4: con horaFin 24 el fin son las 00:00 del día siguiente en Lima")
    void finConHora24() {
        when(dao.delUsuario(USUARIO_ID)).thenReturn(List.of(new ReservaConHorario(confirmada(90L), FECHA, 23, 24)));

        var reserva = servicio.delUsuario(USUARIO_ID, 90L);

        assertThat(reserva.inicioEn()).isEqualTo(Instant.parse("2026-11-04T04:00:00Z"));
        assertThat(reserva.finEn()).isEqualTo(Instant.parse("2026-11-04T05:00:00Z"));
    }

    @Test
    @DisplayName("HU-26 R9: una reserva ajena o inexistente no aparece en las del usuario y responde 404")
    void ajenaOInexistente() {
        when(dao.delUsuario(USUARIO_ID)).thenReturn(List.of(new ReservaConHorario(confirmada(90L), FECHA, 14, 16)));

        assertThatThrownBy(() -> servicio.delUsuario(USUARIO_ID, 91L))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(reservas);
    }

    @Test
    @DisplayName("HU-26 R5: una cancelada y una pendiente vencida se devuelven como no confirmadas")
    void noConfirmadas() {
        when(dao.delUsuario(USUARIO_ID)).thenReturn(List.of(new ReservaConHorario(cancelada(90L), FECHA, 14, 16),
                new ReservaConHorario(pendiente(91L, AHORA.minusSeconds(3600)), FECHA, 10, 11)));

        assertThat(servicio.delUsuario(USUARIO_ID, 90L).confirmada()).isFalse();
        assertThat(servicio.delUsuario(USUARIO_ID, 91L).confirmada()).isFalse();
    }

    @Test
    @DisplayName("HU-26 R33: confirmadasDe deja fuera a las canceladas y a las pendientes")
    void soloConfirmadas() {
        when(dao.delUsuario(USUARIO_ID)).thenReturn(List.of(new ReservaConHorario(cancelada(1L), FECHA, 8, 9),
                new ReservaConHorario(pendiente(2L, AHORA), FECHA, 9, 10),
                new ReservaConHorario(confirmada(3L), FECHA, 10, 11)));

        assertThat(servicio.confirmadasDe(USUARIO_ID)).extracting(ReservaParaReclamo::id).containsExactly(3L);
    }

    @Test
    @DisplayName("HU-26 R4: describir usa todas las filas de bloque, activas o no, y omite las reservas sin bloques")
    void describirPorLote() {
        when(reservas.findAllById(Set.of(90L, 91L))).thenReturn(List.of(confirmada(91L), confirmada(90L)));
        when(bloques.horarios(any())).thenReturn(List.of(new HorarioReserva(90L, FECHA, (short) 8, (short) 20)));

        var descritas = servicio.describir(List.of(90L, 91L));

        assertThat(descritas).singleElement().satisfies(reserva -> {
            assertThat(reserva.id()).isEqualTo(90L);
            assertThat(reserva.horaInicio()).isEqualTo(8);
            assertThat(reserva.horaFin()).isEqualTo(21);
            assertThat(reserva.finEn()).isEqualTo(Instant.parse("2026-11-04T02:00:00Z"));
            assertThat(reserva.arrendadorId()).isEqualTo(ARRENDADOR_ID);
        });
    }

    @Test
    @DisplayName("HU-26 R4: describir con una colección vacía no consulta nada")
    void describirVacio() {
        assertThat(servicio.describir(List.of())).isEmpty();
        verifyNoInteractions(reservas, bloques, catalogo, dao);
    }
}
