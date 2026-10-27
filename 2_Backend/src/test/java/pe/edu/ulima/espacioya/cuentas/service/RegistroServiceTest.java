package pe.edu.ulima.espacioya.cuentas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.cuentas.domain.PoliticaContrasena;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.dto.RegistroUsuarioDTO;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class RegistroServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-27T15:00:00Z");
    private static final String IP = "10.0.0.1";
    private static final String VERSION = "2026-10";
    private static final String HASH = "$2a$12$hashdeprueba";

    @Mock
    private UsuarioRepository usuarios;
    @Mock
    private PasswordEncoder encoder;
    @Mock
    private NotificadorCorreo notificador;
    @Mock
    private AuditoriaService auditoria;

    private PlatformTransactionManager gestorTransacciones;
    private RegistroService servicio;

    @BeforeEach
    void preparar() {
        gestorTransacciones = mock(PlatformTransactionManager.class);
        servicio = new RegistroService(usuarios, encoder, new PoliticaPrivacidad(VERSION), notificador, auditoria,
                new TransactionTemplate(gestorTransacciones), Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private static RegistroUsuarioDTO solicitud(String correo, String contrasena, String version) {
        return new RegistroUsuarioDTO(correo, contrasena, true, version);
    }

    private static Usuario existente(long id) {
        var usuario = Usuario.registrar("ana@correo.pe", "$2a$12$otro", VERSION, AHORA.minusSeconds(3600));
        ReflectionTestUtils.setField(usuario, "id", id);
        return usuario;
    }

    private void prepararRegistroNuevo() {
        when(encoder.encode("Segura123!")).thenReturn(HASH);
        when(usuarios.findByCorreo("ana@correo.pe")).thenReturn(Optional.empty());
        when(usuarios.saveAndFlush(any(Usuario.class))).thenAnswer(invocacion -> {
            Usuario usuario = invocacion.getArgument(0);
            ReflectionTestUtils.setField(usuario, "id", 7L);
            return usuario;
        });
    }

    @Test
    @DisplayName("HU-01 R1, R5, R19: guarda un USUARIO ACTIVA verificado, con el correo normalizado y el hash")
    void guardaUsuarioNuevo() {
        prepararRegistroNuevo();

        servicio.registrar(solicitud("Ana@Correo.PE", "Segura123!", VERSION), IP);

        var guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).saveAndFlush(guardado.capture());
        assertThat(guardado.getValue().getCorreo()).isEqualTo("ana@correo.pe");
        assertThat(guardado.getValue().getHashContrasena()).isEqualTo(HASH);
        assertThat(guardado.getValue().isCorreoVerificado()).isTrue();
    }

    @Test
    @DisplayName("HU-01 R3: invoca una sola vez al notificador con el correo normalizado")
    void notificaUnaVezConCorreoNormalizado() {
        prepararRegistroNuevo();

        servicio.registrar(solicitud("ANA@correo.pe", "Segura123!", VERSION), IP);

        verify(notificador, times(1)).notificarVerificacion("ana@correo.pe");
    }

    @Test
    @DisplayName("HU-01 R7: rechaza una versión de la política distinta de la vigente sin tocar el repositorio")
    void rechazaVersionNoVigente() {
        assertThatThrownBy(() -> servicio.registrar(solicitud("ana@correo.pe", "Segura123!", "2025-01"), IP))
                .isInstanceOf(ReglaNegocioException.class)
                .hasFieldOrPropertyWithValue("campo", "versionPolitica");

        verifyNoInteractions(usuarios, notificador, auditoria);
    }

    @Test
    @DisplayName("HU-01 R8: guarda la versión aceptada y la fecha del consentimiento tomada del reloj")
    void guardaConsentimiento() {
        prepararRegistroNuevo();

        servicio.registrar(solicitud("ana@correo.pe", "Segura123!", VERSION), IP);

        var guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).saveAndFlush(guardado.capture());
        assertThat(guardado.getValue().getVersionPolitica()).isEqualTo(VERSION);
        assertThat(guardado.getValue().getFechaConsentimiento()).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("HU-01 R9: audita REGISTRO_USUARIO con el id nuevo, la IP y la versión aceptada")
    void auditaRegistro() {
        prepararRegistroNuevo();

        servicio.registrar(solicitud("ana@correo.pe", "Segura123!", VERSION), IP);

        var evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        assertThat(evento.getValue().accion()).isEqualTo("REGISTRO_USUARIO");
        assertThat(evento.getValue().usuarioId()).isEqualTo(7L);
        assertThat(evento.getValue().ip()).isEqualTo(IP);
        assertThat(evento.getValue().detalle()).isEqualTo("versionPolitica=2026-10");
    }

    @Test
    @DisplayName("HU-01 R14, R15: con correo existente no guarda, no notifica y termina sin error")
    void correoExistenteNoGuardaNiNotifica() {
        when(encoder.encode("Segura123!")).thenReturn(HASH);
        when(usuarios.findByCorreo("ana@correo.pe")).thenReturn(Optional.of(existente(3L)));

        servicio.registrar(solicitud("Ana@correo.pe", "Segura123!", VERSION), IP);

        verify(usuarios, never()).save(any());
        verify(usuarios, never()).saveAndFlush(any());
        verifyNoInteractions(notificador);
    }

    @Test
    @DisplayName("HU-01 R14: calcula el hash también cuando el correo ya existe")
    void calculaHashConCorreoExistente() {
        when(encoder.encode("Segura123!")).thenReturn(HASH);
        when(usuarios.findByCorreo("ana@correo.pe")).thenReturn(Optional.of(existente(3L)));

        servicio.registrar(solicitud("ana@correo.pe", "Segura123!", VERSION), IP);

        verify(encoder).encode("Segura123!");
    }

    @Test
    @DisplayName("HU-01 R16: audita REGISTRO_CORREO_EXISTENTE con el id de la cuenta existente y la IP")
    void auditaCorreoExistente() {
        when(encoder.encode("Segura123!")).thenReturn(HASH);
        when(usuarios.findByCorreo("ana@correo.pe")).thenReturn(Optional.of(existente(3L)));

        servicio.registrar(solicitud("ana@correo.pe", "Segura123!", VERSION), IP);

        var evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        assertThat(evento.getValue().accion()).isEqualTo("REGISTRO_CORREO_EXISTENTE");
        assertThat(evento.getValue().usuarioId()).isEqualTo(3L);
        assertThat(evento.getValue().ip()).isEqualTo(IP);
    }

    @Test
    @DisplayName("HU-01 R10, R17: con una contraseña débil rechaza antes de consultar el correo")
    void contrasenaDebilNoConsultaElCorreo() {
        assertThatThrownBy(() -> servicio.registrar(solicitud("ana@correo.pe", "corta1", VERSION), IP))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage(PoliticaContrasena.REQUISITOS)
                .hasFieldOrPropertyWithValue("campo", "contrasena");

        verifyNoInteractions(usuarios, encoder, notificador, auditoria);
    }

    @Test
    @DisplayName("HU-01 R17: con una versión inválida rechaza antes de consultar el correo")
    void versionInvalidaNoConsultaElCorreo() {
        assertThatThrownBy(() -> servicio.registrar(solicitud("ana@correo.pe", "Segura123!", null), IP))
                .isInstanceOf(ReglaNegocioException.class);

        verifyNoInteractions(usuarios, encoder);
    }

    @Test
    @DisplayName("HU-01 R18: si otra solicitud gana el correo único, sigue el camino del correo existente")
    void carreraPorCorreoUnico() {
        when(encoder.encode("Segura123!")).thenReturn(HASH);
        when(usuarios.findByCorreo("ana@correo.pe")).thenReturn(Optional.empty(), Optional.of(existente(9L)));
        when(usuarios.saveAndFlush(any(Usuario.class)))
                .thenThrow(new DataIntegrityViolationException("uq_usuario_correo"));

        servicio.registrar(solicitud("ana@correo.pe", "Segura123!", VERSION), IP);

        var evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        assertThat(evento.getValue().accion()).isEqualTo("REGISTRO_CORREO_EXISTENTE");
        assertThat(evento.getValue().usuarioId()).isEqualTo(9L);
        verifyNoInteractions(notificador);
        verify(gestorTransacciones).rollback(any());
    }

    @Test
    @DisplayName("HU-01 R25: si el notificador falla, la excepción se propaga y la transacción se revierte")
    void falloDelNotificadorRevierte() {
        prepararRegistroNuevo();
        doThrow(new IllegalStateException("servidor de correo caído"))
                .when(notificador).notificarVerificacion(anyString());

        assertThatThrownBy(() -> servicio.registrar(solicitud("ana@correo.pe", "Segura123!", VERSION), IP))
                .isInstanceOf(IllegalStateException.class);

        verify(gestorTransacciones).rollback(any());
        verify(gestorTransacciones, never()).commit(any());
    }
}
