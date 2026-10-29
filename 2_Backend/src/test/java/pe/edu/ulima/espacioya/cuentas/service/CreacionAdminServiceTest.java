package pe.edu.ulima.espacioya.cuentas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.cuentas.domain.EstadoUsuario;
import pe.edu.ulima.espacioya.cuentas.domain.Rol;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class CreacionAdminServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-28T15:00:00Z");
    private static final String VERSION = "2026-10";
    private static final String CORREO = "admin@espacioya.pe";
    private static final String CONTRASENA = "ClaveAdmin2026";
    private static final String HASH = "$2a$12$hashdeladmin";
    private static final long ID = 42L;

    @Mock
    private UsuarioRepository usuarios;
    @Mock
    private PasswordEncoder encoder;
    @Mock
    private AuditoriaService auditoria;

    private CreacionAdminService servicio;

    @BeforeEach
    void preparar() {
        servicio = new CreacionAdminService(usuarios, encoder, new PoliticaPrivacidad(VERSION), auditoria,
                new TransactionTemplate(mock(PlatformTransactionManager.class)), Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private void prepararCreacion() {
        when(encoder.encode(CONTRASENA)).thenReturn(HASH);
        when(usuarios.findByCorreo(CORREO)).thenReturn(Optional.empty());
        when(usuarios.saveAndFlush(any(Usuario.class))).thenAnswer(invocacion -> {
            Usuario usuario = invocacion.getArgument(0);
            ReflectionTestUtils.setField(usuario, "id", ID);
            return usuario;
        });
    }

    @Test
    @DisplayName("HU-06 R15: crea un ADMIN ACTIVA con el correo normalizado, el hash, correo verificado, la versión "
            + "vigente y la fecha del reloj")
    void creaLaCuentaAdmin() {
        prepararCreacion();

        var id = servicio.crear("  Admin@EspacioYa.PE ", CONTRASENA);

        assertThat(id).isEqualTo(ID);
        var guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).saveAndFlush(guardado.capture());
        var admin = guardado.getValue();
        assertThat(admin.getRol()).isEqualTo(Rol.ADMIN);
        assertThat(admin.getCorreo()).isEqualTo(CORREO);
        assertThat(admin.getHashContrasena()).isEqualTo(HASH);
        assertThat(admin.isCorreoVerificado()).isTrue();
        assertThat(admin.getEstado()).isEqualTo(EstadoUsuario.ACTIVA);
        assertThat(admin.getVersionPolitica()).isEqualTo(VERSION);
        assertThat(admin.getFechaConsentimiento()).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("HU-06 R16: audita CREACION_ADMIN con el id de la cuenta, la entidad usuario, sin IP ni detalle")
    void auditaLaCreacion() {
        prepararCreacion();

        servicio.crear(CORREO, CONTRASENA);

        var evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        assertThat(evento.getValue()).isEqualTo(new EventoAuditoria(ID, "CREACION_ADMIN", "usuario", ID, null, null));
    }

    @Test
    @DisplayName("HU-06 R17: una contraseña fuera de la política detiene la creación nombrando ADMIN_CONTRASENA sin "
            + "su valor")
    void contrasenaFueraDePolitica() {
        for (var invalida : new String[] {"corta1", "sinnumerosaqui", "12345678901", "", CORREO}) {
            assertThatThrownBy(() -> servicio.crear(CORREO, invalida))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ADMIN_CONTRASENA")
                    .satisfies(e -> {
                        if (!invalida.isEmpty()) {
                            assertThat(e.getMessage()).doesNotContain(invalida);
                        }
                    });
        }
        assertThatThrownBy(() -> servicio.crear(CORREO, null)).hasMessageContaining("ADMIN_CONTRASENA");
        verify(usuarios, never()).saveAndFlush(any());
        verifyNoInteractions(encoder, auditoria);
    }

    @Test
    @DisplayName("HU-06 R17: un correo vacío o sin forma de correo detiene la creación nombrando ADMIN_CORREO sin su "
            + "valor")
    void correoInvalido() {
        for (var invalido : new String[] {"", "   ", "no-es-correo", "a@b", "dos@@arrobas.pe", "con espacio@x.pe"}) {
            assertThatThrownBy(() -> servicio.crear(invalido, CONTRASENA))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ADMIN_CORREO")
                    .satisfies(e -> {
                        if (!invalido.isBlank()) {
                            assertThat(e.getMessage()).doesNotContain(invalido);
                        }
                    });
        }
        assertThatThrownBy(() -> servicio.crear(null, CONTRASENA)).hasMessageContaining("ADMIN_CORREO");
        verifyNoInteractions(usuarios, encoder, auditoria);
    }

    @Test
    @DisplayName("HU-06 R18: si ya existe una cuenta con ese correo falla sin crear ni modificar ninguna cuenta")
    void correoExistente() {
        var existente = Usuario.registrar(CORREO, "$2a$12$hashanterior", VERSION, AHORA.minusSeconds(3600));
        when(encoder.encode(CONTRASENA)).thenReturn(HASH);
        when(usuarios.findByCorreo(CORREO)).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> servicio.crear(CORREO, CONTRASENA))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_CORREO")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(CORREO));

        verify(usuarios, never()).saveAndFlush(any());
        verifyNoInteractions(auditoria);
        assertThat(existente.getRol()).isEqualTo(Rol.USUARIO);
        assertThat(existente.getHashContrasena()).isEqualTo("$2a$12$hashanterior");
    }

    @Test
    @DisplayName("HU-06 R18: si otra creación gana la restricción de correo único, falla con el mismo mensaje")
    void carreraPorElMismoCorreo() {
        when(encoder.encode(CONTRASENA)).thenReturn(HASH);
        when(usuarios.findByCorreo(CORREO)).thenReturn(Optional.empty());
        when(usuarios.saveAndFlush(any(Usuario.class))).thenThrow(new DataIntegrityViolationException("uq"));

        assertThatThrownBy(() -> servicio.crear(CORREO, CONTRASENA))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_CORREO");
        verifyNoInteractions(auditoria);
    }
}
