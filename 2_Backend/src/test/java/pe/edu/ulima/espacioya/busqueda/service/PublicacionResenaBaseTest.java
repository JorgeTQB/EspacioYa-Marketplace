package pe.edu.ulima.espacioya.busqueda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pe.edu.ulima.espacioya.busqueda.domain.EstadoResena;
import pe.edu.ulima.espacioya.busqueda.domain.Resena;
import pe.edu.ulima.espacioya.busqueda.repository.ResenaDAO;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.reservas.service.ReservaParaResena;
import pe.edu.ulima.espacioya.reservas.service.ReservasParaResena;

/** ★ ConcreteComponent de HU-24: la regla del negocio sola, sin sanitizar, transacción ni auditoría (R52). */
@ExtendWith(MockitoExtension.class)
class PublicacionResenaBaseTest {

    private static final Instant AHORA = Instant.parse("2026-10-28T15:00:00Z");
    private static final long USUARIO_ID = 7L;
    private static final long ESPACIO_ID = 40L;
    private static final long RESERVA_ID = 90L;
    private static final SolicitudResena SOLICITUD = new SolicitudResena(USUARIO_ID, RESERVA_ID, 4, "Buena", "10.0.0.1");

    @Mock
    private ResenaDAO resenas;
    @Mock
    private ReservasParaResena reservas;

    private PublicacionResenaBase base;

    @BeforeEach
    void preparar() {
        base = new PublicacionResenaBase(resenas, reservas, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private void conReserva(boolean confirmada, Instant fin) {
        when(reservas.delUsuario(USUARIO_ID, RESERVA_ID))
                .thenReturn(new ReservaParaResena(RESERVA_ID, ESPACIO_ID, confirmada, fin));
    }

    @Test
    @DisplayName("HU-24 R52: comprueba la reserva propia, luego la reseña previa y al final guarda con ResenaDAO")
    void ordenDeLaRegla() {
        conReserva(true, AHORA.minus(Duration.ofDays(1)));
        when(resenas.guardar(any(Resena.class))).thenAnswer(llamada -> llamada.getArgument(0));

        var resena = base.publicar(SOLICITUD);

        var orden = inOrder(reservas, resenas);
        orden.verify(reservas).delUsuario(USUARIO_ID, RESERVA_ID);
        orden.verify(resenas).existeParaReserva(RESERVA_ID);
        orden.verify(resenas).guardar(any(Resena.class));
        assertThat(resena.getUsuarioId()).isEqualTo(USUARIO_ID);
        assertThat(resena.getEspacioId()).isEqualTo(ESPACIO_ID);
        assertThat(resena.getEstado()).isEqualTo(EstadoResena.VISIBLE);
        assertThat(resena.getCreadoEn()).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("HU-24 R52: una reserva ajena o inexistente responde 404 sin consultar ResenaDAO")
    void ajenaSinDao() {
        when(reservas.delUsuario(USUARIO_ID, RESERVA_ID)).thenThrow(new RecursoNoEncontradoException());

        assertThatThrownBy(() -> base.publicar(SOLICITUD)).isInstanceOf(RecursoNoEncontradoException.class);

        verifyNoInteractions(resenas);
    }

    @Test
    @DisplayName("HU-24 R52: ya reseñada se comprueba antes que el plazo, sin guardar")
    void yaResenadaAntesQueElPlazo() {
        conReserva(true, AHORA.minus(Duration.ofDays(30)));
        when(resenas.existeParaReserva(RESERVA_ID)).thenReturn(true);

        assertThatThrownBy(() -> base.publicar(SOLICITUD)).isInstanceOf(ReglaNegocioException.class)
                .hasMessage(ResenaService.MENSAJE_YA_RESENADA);

        verify(resenas, never()).guardar(any());
    }

    @Test
    @DisplayName("HU-24 R52: fuera de plazo responde plazo vencido sin guardar")
    void fueraDePlazo() {
        conReserva(true, AHORA.minus(Duration.ofDays(14)));

        assertThatThrownBy(() -> base.publicar(SOLICITUD)).isInstanceOf(ReglaNegocioException.class)
                .hasMessage(ResenaService.MENSAJE_PLAZO_VENCIDO);

        verify(resenas, never()).guardar(any());
    }

    @Test
    @DisplayName("HU-24 R52: no sanitiza: guarda el comentario con <b> tal cual lo recibe")
    void noSanitiza() {
        conReserva(true, AHORA.minus(Duration.ofDays(1)));
        when(resenas.guardar(any(Resena.class))).thenAnswer(llamada -> llamada.getArgument(0));

        base.publicar(SOLICITUD.conComentario("<b>Buena</b>"));

        var fila = ArgumentCaptor.forClass(Resena.class);
        verify(resenas).guardar(fila.capture());
        assertThat(fila.getValue().getComentario()).isEqualTo("<b>Buena</b>");
    }
}
