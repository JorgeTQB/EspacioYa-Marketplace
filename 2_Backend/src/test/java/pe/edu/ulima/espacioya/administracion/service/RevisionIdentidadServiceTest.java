package pe.edu.ulima.espacioya.administracion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import pe.edu.ulima.espacioya.administracion.domain.DecisionIdentidad;
import pe.edu.ulima.espacioya.administracion.domain.RevisionIdentidad;
import pe.edu.ulima.espacioya.administracion.dto.RevisionIdentidadDTO;
import pe.edu.ulima.espacioya.administracion.repository.RevisionIdentidadRepository;
import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.dto.ArrendadorEnRevisionDTO;
import pe.edu.ulima.espacioya.cuentas.service.CuentaService;

@ExtendWith(MockitoExtension.class)
class RevisionIdentidadServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-28T15:00:00Z");
    private static final long ARRENDADOR = 7L;
    private static final long ID_REVISION = 31L;
    private static final String IP = "127.0.0.1";
    private static final String MOTIVO = "El DNI 44556677 está borroso.";
    private static final UsuarioAutenticado ADMIN = new UsuarioAutenticado(1L, 99L, "ADMIN");

    @Mock
    private CuentaService cuentas;
    @Mock
    private RevisionIdentidadRepository revisiones;
    @Mock
    private AuditoriaService auditoria;

    private RevisionIdentidadService servicio;

    @BeforeEach
    void preparar() {
        servicio = new RevisionIdentidadService(cuentas, revisiones, auditoria, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private void guardadoConId() {
        when(revisiones.save(any(RevisionIdentidad.class))).thenAnswer(invocacion -> {
            RevisionIdentidad revision = invocacion.getArgument(0);
            ReflectionTestUtils.setField(revision, "id", ID_REVISION);
            return revision;
        });
    }

    private RevisionIdentidad revisionGuardada() {
        var revision = ArgumentCaptor.forClass(RevisionIdentidad.class);
        verify(revisiones).save(revision.capture());
        return revision.getValue();
    }

    private EventoAuditoria eventoAuditado() {
        var evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        return evento.getValue();
    }

    @Test
    @DisplayName("HU-06 R9: con un rol distinto de ADMIN los 4 casos de uso lanzan 403 sin llamar a CuentaService")
    void rolDistintoDeAdmin() {
        for (var rol : new String[] {"USUARIO", "ARRENDADOR"}) {
            var usuario = new UsuarioAutenticado(ARRENDADOR, 31L, rol);
            assertThatThrownBy(() -> servicio.cola(usuario)).isInstanceOf(AccesoDenegadoException.class);
            assertThatThrownBy(() -> servicio.expediente(usuario, ARRENDADOR))
                    .isInstanceOf(AccesoDenegadoException.class);
            assertThatThrownBy(() -> servicio.aprobar(usuario, ARRENDADOR, IP))
                    .isInstanceOf(AccesoDenegadoException.class);
            assertThatThrownBy(() -> servicio.rechazar(usuario, ARRENDADOR, MOTIVO, IP))
                    .isInstanceOf(AccesoDenegadoException.class);
        }
        verifyNoInteractions(cuentas, revisiones, auditoria);
    }

    @Test
    @DisplayName("HU-06 R20: la cola es la que devuelve CuentaService para el admin de la sesión")
    void colaDelegaEnCuentas() {
        var fila = new ArrendadorEnRevisionDTO(ARRENDADOR, "Ana", "Canchas SAC", "RUC", AHORA);
        when(cuentas.arrendadoresEnRevision(ADMIN)).thenReturn(List.of(fila));

        assertThat(servicio.cola(ADMIN)).containsExactly(fila);
        verifyNoInteractions(revisiones, auditoria);
    }

    @Test
    @DisplayName("HU-06 R27, R29: aprobar guarda la revisión con el admin de la sesión y responde APROBADA, "
            + "VERIFICADA y la fecha")
    void aprobarGuardaLaRevision() {
        when(cuentas.aprobarIdentidad(ADMIN, ARRENDADOR)).thenReturn("VERIFICADA");
        guardadoConId();

        var respuesta = servicio.aprobar(ADMIN, ARRENDADOR, IP);

        assertThat(respuesta).isEqualTo(new RevisionIdentidadDTO("APROBADA", "VERIFICADA", AHORA));
        var revision = revisionGuardada();
        assertThat(revision.getArrendadorId()).isEqualTo(ARRENDADOR);
        assertThat(revision.getAdminId()).isEqualTo(1L);
        assertThat(revision.getDecision()).isEqualTo(DecisionIdentidad.APROBADA);
        assertThat(revision.getMotivo()).isNull();
        assertThat(revision.getFecha()).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("HU-06 R31, R33: rechazar guarda la revisión con el motivo y responde RECHAZADA, RECHAZADA y la fecha")
    void rechazarGuardaLaRevision() {
        when(cuentas.rechazarIdentidad(ADMIN, ARRENDADOR, MOTIVO)).thenReturn("RECHAZADA");
        guardadoConId();

        var respuesta = servicio.rechazar(ADMIN, ARRENDADOR, MOTIVO, IP);

        assertThat(respuesta).isEqualTo(new RevisionIdentidadDTO("RECHAZADA", "RECHAZADA", AHORA));
        var revision = revisionGuardada();
        assertThat(revision.getAdminId()).isEqualTo(1L);
        assertThat(revision.getDecision()).isEqualTo(DecisionIdentidad.RECHAZADA);
        assertThat(revision.getMotivo()).isEqualTo(MOTIVO);
        assertThat(revision.getFecha()).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("HU-06 R42, R43: aprobar audita IDENTIDAD_APROBADA sobre el perfil con decision y revision, sin "
            + "datos del arrendador")
    void aprobarAudita() {
        when(cuentas.aprobarIdentidad(ADMIN, ARRENDADOR)).thenReturn("VERIFICADA");
        guardadoConId();

        servicio.aprobar(ADMIN, ARRENDADOR, IP);

        assertThat(eventoAuditado()).isEqualTo(new EventoAuditoria(1L, "IDENTIDAD_APROBADA", "perfil_arrendador",
                ARRENDADOR, IP, "decision=APROBADA;revision=31"));
    }

    @Test
    @DisplayName("HU-06 R42, R43: rechazar audita IDENTIDAD_RECHAZADA con decision y revision, sin el motivo")
    void rechazarAuditaSinMotivo() {
        when(cuentas.rechazarIdentidad(ADMIN, ARRENDADOR, MOTIVO)).thenReturn("RECHAZADA");
        guardadoConId();

        servicio.rechazar(ADMIN, ARRENDADOR, MOTIVO, IP);

        var evento = eventoAuditado();
        assertThat(evento).isEqualTo(new EventoAuditoria(1L, "IDENTIDAD_RECHAZADA", "perfil_arrendador",
                ARRENDADOR, IP, "decision=RECHAZADA;revision=31"));
        assertThat(evento.detalle()).doesNotContain("44556677").doesNotContain("borroso");
    }

    @Test
    @DisplayName("HU-06 R37, R38, R40: si CuentaService rechaza la decisión no se guarda la revisión ni se audita")
    void excepcionDeCuentasSinRevisionNiAuditoria() {
        when(cuentas.aprobarIdentidad(ADMIN, ARRENDADOR)).thenThrow(new ReglaNegocioException("ya revisada"));
        when(cuentas.rechazarIdentidad(ADMIN, 404L, MOTIVO)).thenThrow(new RecursoNoEncontradoException());

        assertThatThrownBy(() -> servicio.aprobar(ADMIN, ARRENDADOR, IP)).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> servicio.rechazar(ADMIN, 404L, MOTIVO, IP))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(revisiones, never()).save(any());
        verifyNoInteractions(auditoria);
    }
}
