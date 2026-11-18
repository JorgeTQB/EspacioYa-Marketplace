package pe.edu.ulima.espacioya.busqueda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pe.edu.ulima.espacioya.busqueda.domain.Resena;
import pe.edu.ulima.espacioya.busqueda.dto.CrearResenaDTO;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;

/** ★ ConcreteDecorator que sanitiza, con un doble de su envuelta (R45, R46). */
@ExtendWith(MockitoExtension.class)
class PublicacionSanitizadaTest {

    private static final SolicitudResena SOLICITUD =
            new SolicitudResena(7L, 90L, 5, "<b>Buena</b> cancha <script>", "10.0.0.1");

    @Mock
    private PublicacionResena envuelta;

    private PublicacionSanitizada sanitizada;

    @BeforeEach
    void preparar() {
        sanitizada = new PublicacionSanitizada(envuelta);
    }

    @Test
    @DisplayName("HU-24 R45: pasa a la envuelta la misma solicitud con el comentario limpio y devuelve lo que ella devuelve")
    void pasaElComentarioLimpio() {
        var devuelta = Resena.nueva(90L, 7L, 40L, 5, "Buena cancha", Instant.parse("2026-10-28T15:00:00Z"));
        when(envuelta.publicar(any())).thenReturn(devuelta);

        assertThat(sanitizada.publicar(SOLICITUD)).isSameAs(devuelta);

        verify(envuelta).publicar(new SolicitudResena(7L, 90L, 5, "Buena cancha", "10.0.0.1"));
    }

    @Test
    @DisplayName("HU-24 R46: un comentario que queda vacío falla en el campo comentario sin invocar a la envuelta")
    void vacioNoInvocaLaEnvuelta() {
        var error = catchThrowableOfType(ReglaNegocioException.class,
                () -> sanitizada.publicar(SOLICITUD.conComentario("<script></script>")));

        assertThat(error.getCampo()).isEqualTo("comentario");
        assertThat(error).hasMessage(CrearResenaDTO.M_COMENTARIO);
        verifyNoInteractions(envuelta);
    }
}
