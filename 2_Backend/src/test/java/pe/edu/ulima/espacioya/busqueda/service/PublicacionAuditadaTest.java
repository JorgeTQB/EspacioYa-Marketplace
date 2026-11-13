package pe.edu.ulima.espacioya.busqueda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import pe.edu.ulima.espacioya.busqueda.domain.Resena;
import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;

/** ★ ConcreteDecorator que audita, con un doble de su envuelta (R49, R50). */
@ExtendWith(MockitoExtension.class)
class PublicacionAuditadaTest {

    private static final String IP = "10.24.0.1";
    private static final SolicitudResena SOLICITUD = new SolicitudResena(7L, 90L, 4, "Buena cancha", IP);

    @Mock
    private PublicacionResena envuelta;
    @Mock
    private AuditoriaService auditoria;

    private PublicacionAuditada auditada;

    @BeforeEach
    void preparar() {
        auditada = new PublicacionAuditada(envuelta, auditoria);
    }

    @Test
    @DisplayName("HU-24 R49: registra RESENA_CREADA después de la envuelta, sin el comentario, y devuelve la misma reseña")
    void registraDespues() {
        var resena = Resena.nueva(90L, 7L, 40L, 4, "Buena cancha", Instant.parse("2026-10-28T15:00:00Z"));
        ReflectionTestUtils.setField(resena, "id", 300L);
        when(envuelta.publicar(SOLICITUD)).thenReturn(resena);

        assertThat(auditada.publicar(SOLICITUD)).isSameAs(resena);

        var orden = inOrder(envuelta, auditoria);
        orden.verify(envuelta).publicar(SOLICITUD);
        orden.verify(auditoria).registrar(new EventoAuditoria(7L, "RESENA_CREADA", "resena", 300L, IP,
                "reservaId=90;espacioId=40;calificacion=4"));
    }

    @Test
    @DisplayName("HU-24 R50: si la envuelta lanza, no registra auditoría y relanza la misma excepción")
    void noRegistraSiLanza() {
        var error = new ReglaNegocioException(ResenaService.MENSAJE_YA_RESENADA);
        when(envuelta.publicar(SOLICITUD)).thenThrow(error);

        assertThatThrownBy(() -> auditada.publicar(SOLICITUD)).isSameAs(error);

        verifyNoInteractions(auditoria);
    }
}
