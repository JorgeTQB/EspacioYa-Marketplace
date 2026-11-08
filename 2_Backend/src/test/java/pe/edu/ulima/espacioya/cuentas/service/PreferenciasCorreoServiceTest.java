package pe.edu.ulima.espacioya.cuentas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.dto.PreferenciasCorreoDTO;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class PreferenciasCorreoServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-04T15:00:00Z");
    private static final String IP = "10.20.1.1";
    private static final long ID = 7L;
    private static final UsuarioAutenticado USUARIO = new UsuarioAutenticado(ID, 70L, "USUARIO");
    private static final UsuarioAutenticado ARRENDADOR = new UsuarioAutenticado(ID, 70L, "ARRENDADOR");
    private static final UsuarioAutenticado ADMIN = new UsuarioAutenticado(ID, 70L, "ADMIN");

    @Mock
    private UsuarioRepository usuarios;
    @Mock
    private AuditoriaService auditoria;

    private PreferenciasCorreoService servicio;

    @BeforeEach
    void preparar() {
        servicio = new PreferenciasCorreoService(usuarios, auditoria,
                new TransactionTemplate(mock(PlatformTransactionManager.class)));
    }

    private static Usuario cuenta() {
        var usuario = Usuario.registrar("ana@correo.pe", "$2a$12$hash", "2026-10", AHORA);
        ReflectionTestUtils.setField(usuario, "id", ID);
        return usuario;
    }

    @Test
    @DisplayName("HU-25 R1: consultar devuelve la preferencia de la cuenta de la sesión")
    void consultaLaPreferencia() {
        var cuenta = cuenta();
        cuenta.cambiarRecordatorios(false);
        when(usuarios.findById(ID)).thenReturn(Optional.of(cuenta));

        assertThat(servicio.consultar(ARRENDADOR)).isEqualTo(new PreferenciasCorreoDTO(false));
    }

    @Test
    @DisplayName("HU-25 R2, R8: actualizar guarda el valor nuevo en la cuenta de la sesión y lo audita")
    void actualizaYAudita() {
        var cuenta = cuenta();
        when(usuarios.findParaActualizar(ID)).thenReturn(Optional.of(cuenta));

        var respuesta = servicio.actualizar(USUARIO, new PreferenciasCorreoDTO(false), IP);

        assertThat(respuesta).isEqualTo(new PreferenciasCorreoDTO(false));
        assertThat(cuenta.tieneRecordatoriosActivos()).isFalse();
        var evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        assertThat(evento.getValue()).isEqualTo(new EventoAuditoria(ID, PreferenciasCorreoService.ACCION_PREFERENCIA,
                "usuario", ID, IP, "recordatoriosActivos=true->false"));
    }

    @Test
    @DisplayName("HU-25 R9: el mismo valor responde igual y no audita")
    void mismoValorSinAuditoria() {
        when(usuarios.findParaActualizar(ID)).thenReturn(Optional.of(cuenta()));

        var respuesta = servicio.actualizar(ARRENDADOR, new PreferenciasCorreoDTO(true), IP);

        assertThat(respuesta).isEqualTo(new PreferenciasCorreoDTO(true));
        verify(auditoria, never()).registrar(any());
    }

    @Test
    @DisplayName("HU-25 R5: el admin recibe 403 sin leer ni cambiar ninguna preferencia")
    void adminNoPuede() {
        assertThatThrownBy(() -> servicio.consultar(ADMIN)).isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> servicio.actualizar(ADMIN, new PreferenciasCorreoDTO(false), IP))
                .isInstanceOf(AccesoDenegadoException.class);
        verifyNoInteractions(usuarios, auditoria);
    }
}
