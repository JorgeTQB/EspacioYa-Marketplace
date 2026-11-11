package pe.edu.ulima.espacioya.busqueda.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import pe.edu.ulima.espacioya.busqueda.domain.EstadoResena;
import pe.edu.ulima.espacioya.busqueda.domain.Resena;
import pe.edu.ulima.espacioya.busqueda.repository.ResenaRepository;
import pe.edu.ulima.espacioya.busqueda.service.ResenaModerada;
import pe.edu.ulima.espacioya.busqueda.service.ResenaReportable;
import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.espacios.service.CatalogoEspaciosService;

@ExtendWith(MockitoExtension.class)
class ModeracionResenasImplTest {

    private static final long RESENA_ID = 60L;
    private static final long AUTOR_ID = 5L;
    private static final long ESPACIO_ID = 40L;
    private static final String IP = "127.0.0.1";
    private static final UsuarioAutenticado ADMIN = new UsuarioAutenticado(1L, 33L, "ADMIN");
    // 2026-11-02 00:30 en Lima = 05:30 UTC.
    private static final Instant CREADA = Instant.parse("2026-11-02T05:30:00Z");

    @Mock
    private ResenaRepository resenas;
    @Mock
    private CatalogoEspaciosService catalogo;
    @Mock
    private AuditoriaService auditoria;

    private ModeracionResenasImpl moderacion;

    @BeforeEach
    void preparar() {
        moderacion = new ModeracionResenasImpl(resenas, catalogo, auditoria);
    }

    private static Resena resena(EstadoResena estado) {
        var resena = Resena.nueva(3L, AUTOR_ID, ESPACIO_ID, 2, "Sucio y caro", CREADA);
        ReflectionTestUtils.setField(resena, "id", RESENA_ID);
        ReflectionTestUtils.setField(resena, "estado", estado);
        return resena;
    }

    @Test
    @DisplayName("HU-27 R7: una reseña VISIBLE de un espacio visible es reportable con su autor")
    void visibleEsReportable() {
        when(resenas.findById(RESENA_ID)).thenReturn(Optional.of(resena(EstadoResena.VISIBLE)));
        when(catalogo.esVisible(ESPACIO_ID)).thenReturn(true);

        assertThat(moderacion.reportable(RESENA_ID)).contains(new ResenaReportable(RESENA_ID, AUTOR_ID, ESPACIO_ID));
    }

    @Test
    @DisplayName("HU-27 R7: una reseña OCULTA, inexistente, null o de un espacio no visible no es reportable")
    void noReportable() {
        when(resenas.findById(RESENA_ID)).thenReturn(Optional.of(resena(EstadoResena.OCULTA)));
        assertThat(moderacion.reportable(RESENA_ID)).isEmpty();

        when(resenas.findById(RESENA_ID)).thenReturn(Optional.empty());
        assertThat(moderacion.reportable(RESENA_ID)).isEmpty();

        assertThat(moderacion.reportable(null)).isEmpty();

        when(resenas.findById(RESENA_ID)).thenReturn(Optional.of(resena(EstadoResena.VISIBLE)));
        when(catalogo.esVisible(ESPACIO_ID)).thenReturn(false);
        assertThat(moderacion.reportable(RESENA_ID)).isEmpty();
    }

    @Test
    @DisplayName("HU-27 R25: describir devuelve la reseña en cualquier estado con su fecha de Lima, sin su autor")
    void describe() {
        when(resenas.findAllById(Set.of(RESENA_ID))).thenReturn(List.of(resena(EstadoResena.OCULTA)));

        assertThat(moderacion.describir(List.of(RESENA_ID))).containsExactlyEntriesOf(Map.of(RESENA_ID,
                new ResenaModerada(RESENA_ID, ESPACIO_ID, 2, "Sucio y caro", LocalDate.of(2026, 11, 2), "OCULTA")));
        assertThat(moderacion.describir(List.of())).isEmpty();
    }

    @Test
    @DisplayName("HU-27 R34, R53: ocultar una VISIBLE la deja OCULTA y audita OCULTACION_RESENA")
    void oculta() {
        var resena = resena(EstadoResena.VISIBLE);
        when(resenas.findParaActualizar(RESENA_ID)).thenReturn(Optional.of(resena));

        moderacion.ocultar(ADMIN, RESENA_ID, IP);

        assertThat(resena.getEstado()).isEqualTo(EstadoResena.OCULTA);
        verify(auditoria).registrar(new EventoAuditoria(1L, "OCULTACION_RESENA", "resena", RESENA_ID, IP,
                "estado=VISIBLE->OCULTA;actor=ADMIN;espacioId=" + ESPACIO_ID));
    }

    @Test
    @DisplayName("HU-27 R40, R54: ocultar una reseña ya OCULTA da 400 no ocultable sin auditar")
    void yaOculta() {
        when(resenas.findParaActualizar(RESENA_ID)).thenReturn(Optional.of(resena(EstadoResena.OCULTA)));

        assertThatThrownBy(() -> moderacion.ocultar(ADMIN, RESENA_ID, IP))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage(ModeracionResenasImpl.MENSAJE_NO_OCULTABLE);
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("HU-27 R27: ocultar una reseña inexistente da 404")
    void inexistente() {
        when(resenas.findParaActualizar(RESENA_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> moderacion.ocultar(ADMIN, RESENA_ID, IP))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("HU-27 R62: ocultar con una sesión que no es ADMIN da 403 sin leer ni bloquear la reseña")
    void sinRolAdmin() {
        for (var sesion : new UsuarioAutenticado[] {new UsuarioAutenticado(5L, 1L, "USUARIO"),
                new UsuarioAutenticado(7L, 2L, "ARRENDADOR"), null}) {
            assertThatThrownBy(() -> moderacion.ocultar(sesion, RESENA_ID, IP))
                    .isInstanceOf(AccesoDenegadoException.class);
        }
        verifyNoInteractions(resenas, auditoria);
    }
}
