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
import pe.edu.ulima.espacioya.comun.cifrado.HuellaCampos;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.cuentas.domain.EstadoIdentidad;
import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.PoliticaContrasena;
import pe.edu.ulima.espacioya.cuentas.domain.Rol;
import pe.edu.ulima.espacioya.cuentas.domain.TipoDocumento;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.domain.ValidadorDocumento;
import pe.edu.ulima.espacioya.cuentas.dto.RegistroArrendadorDTO;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class RegistroArrendadorServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-27T15:00:00Z");
    private static final String IP = "10.0.0.2";
    private static final String VERSION = "2026-10";
    private static final String HASH = "$2a$12$hashdeprueba";
    private static final String CONTRASENA = "Segura123!";
    private static final String RUC = "20100070970";
    private static final String RAZON = "Canchas del Sur SAC";
    private static final String DIRECCION = "Av. Javier Prado 4600";
    private static final String TELEFONO = "987654321";
    private static final String LLAVE_HUELLA = "ZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXp7fH1+f4CBgoM=";

    @Mock
    private UsuarioRepository usuarios;
    @Mock
    private PerfilArrendadorRepository perfiles;
    @Mock
    private PasswordEncoder encoder;
    @Mock
    private NotificadorCorreo notificador;
    @Mock
    private AuditoriaService auditoria;

    private final HuellaCampos huella = new HuellaCampos(LLAVE_HUELLA);
    private PlatformTransactionManager gestorTransacciones;
    private RegistroArrendadorService servicio;

    @BeforeEach
    void preparar() {
        gestorTransacciones = mock(PlatformTransactionManager.class);
        servicio = new RegistroArrendadorService(usuarios, perfiles, encoder, new PoliticaPrivacidad(VERSION),
                notificador, auditoria, huella, new TransactionTemplate(gestorTransacciones),
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private static RegistroArrendadorDTO solicitud(String correo, String contrasena, String version, String tipo,
                                                   String numero) {
        return new RegistroArrendadorDTO(correo, contrasena, true, version, " Ana Pérez ", TELEFONO, tipo, numero,
                RAZON, DIRECCION);
    }

    private static RegistroArrendadorDTO valida(String correo) {
        return solicitud(correo, CONTRASENA, VERSION, "RUC", RUC);
    }

    private static Usuario existente(long id) {
        var usuario = Usuario.registrar("ana@correo.pe", "$2a$12$otro", VERSION, AHORA.minusSeconds(3600));
        ReflectionTestUtils.setField(usuario, "id", id);
        return usuario;
    }

    private static PerfilArrendador perfilDe(long titular) {
        return PerfilArrendador.crear(titular, TipoDocumento.RUC, RUC, "x".repeat(64), "Otra SAC", "Otra calle");
    }

    private void prepararRegistroNuevo() {
        when(encoder.encode(CONTRASENA)).thenReturn(HASH);
        when(usuarios.findByCorreo("ana@correo.pe")).thenReturn(Optional.empty());
        when(perfiles.findByHashDocumento(huella.calcular(RUC))).thenReturn(Optional.empty());
        when(usuarios.saveAndFlush(any(Usuario.class))).thenAnswer(invocacion -> {
            Usuario usuario = invocacion.getArgument(0);
            ReflectionTestUtils.setField(usuario, "id", 7L);
            return usuario;
        });
    }

    private EventoAuditoria unicoEventoAuditado() {
        var evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        return evento.getValue();
    }

    private static void sinDatosSensibles(EventoAuditoria evento) {
        assertThat(String.valueOf(evento.detalle()))
                .doesNotContain(RUC).doesNotContain(RAZON).doesNotContain(DIRECCION).doesNotContain(TELEFONO);
    }

    // ── Registro exitoso ─────────────────────────────────────────────────

    @Test
    @DisplayName("HU-02 R1, R3, R4, R7: guarda un ARRENDADOR verificado con correo normalizado y consentimiento")
    void guardaUsuarioArrendador() {
        prepararRegistroNuevo();

        servicio.registrar(valida("Ana@Correo.PE"), IP);

        var guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).saveAndFlush(guardado.capture());
        var usuario = guardado.getValue();
        assertThat(usuario.getRol()).isEqualTo(Rol.ARRENDADOR);
        assertThat(usuario.isCorreoVerificado()).isTrue();
        assertThat(usuario.getCorreo()).isEqualTo("ana@correo.pe");
        assertThat(usuario.getHashContrasena()).isEqualTo(HASH);
        assertThat(usuario.getNombre()).isEqualTo("Ana Pérez");
        assertThat(usuario.getTelefono()).isEqualTo(TELEFONO);
        assertThat(usuario.getVersionPolitica()).isEqualTo(VERSION);
        assertThat(usuario.getFechaConsentimiento()).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("HU-02 R2, R37: guarda el perfil NO_INICIADA del nuevo usuario con la huella HMAC del número")
    void guardaPerfilDelArrendador() {
        prepararRegistroNuevo();

        servicio.registrar(solicitud("ana@correo.pe", CONTRASENA, VERSION, "RUC", "  " + RUC + " "), IP);

        var guardado = ArgumentCaptor.forClass(PerfilArrendador.class);
        verify(perfiles).saveAndFlush(guardado.capture());
        var perfil = guardado.getValue();
        assertThat(perfil.getUsuarioId()).isEqualTo(7L);
        assertThat(perfil.getTipoDocumento()).isEqualTo(TipoDocumento.RUC);
        assertThat(perfil.getNumeroDocumento()).isEqualTo(RUC);
        assertThat(perfil.getHashDocumento()).isEqualTo(huella.calcular(RUC));
        assertThat(perfil.getRazonSocial()).isEqualTo(RAZON);
        assertThat(perfil.getDireccionFiscal()).isEqualTo(DIRECCION);
        assertThat(perfil.getEstadoIdentidad()).isEqualTo(EstadoIdentidad.NO_INICIADA);
        assertThat(perfil.getMotivoRechazo()).isNull();
    }

    @Test
    @DisplayName("HU-02 R6: invoca una sola vez al notificador con el correo normalizado")
    void notificaUnaVez() {
        prepararRegistroNuevo();

        servicio.registrar(valida("ANA@correo.pe"), IP);

        verify(notificador, times(1)).notificarVerificacion("ana@correo.pe");
    }

    @Test
    @DisplayName("HU-02 R32, R33: audita REGISTRO_ARRENDADOR con el id, la IP y solo la versión en el detalle")
    void auditaRegistro() {
        prepararRegistroNuevo();

        servicio.registrar(valida("ana@correo.pe"), IP);

        var evento = unicoEventoAuditado();
        assertThat(evento.accion()).isEqualTo("REGISTRO_ARRENDADOR");
        assertThat(evento.usuarioId()).isEqualTo(7L);
        assertThat(evento.entidadId()).isEqualTo(7L);
        assertThat(evento.ip()).isEqualTo(IP);
        assertThat(evento.detalle()).isEqualTo("versionPolitica=2026-10");
        sinDatosSensibles(evento);
    }

    // ── Validaciones antes de la BD ──────────────────────────────────────

    @Test
    @DisplayName("HU-02 R9, R28: rechaza una versión no vigente sin consultar repositorios ni notificar")
    void rechazaVersionNoVigente() {
        assertThatThrownBy(() -> servicio.registrar(
                solicitud("ana@correo.pe", CONTRASENA, "2025-01", "RUC", RUC), IP))
                .isInstanceOf(ReglaNegocioException.class)
                .hasFieldOrPropertyWithValue("campo", "versionPolitica");

        verifyNoInteractions(usuarios, perfiles, encoder, notificador, auditoria);
    }

    @Test
    @DisplayName("HU-02 R10, R28: rechaza una contraseña débil con los requisitos sin consultar repositorios")
    void rechazaContrasenaDebil() {
        assertThatThrownBy(() -> servicio.registrar(
                solicitud("ana@correo.pe", "corta1", VERSION, "RUC", RUC), IP))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage(PoliticaContrasena.REQUISITOS)
                .hasFieldOrPropertyWithValue("campo", "contrasena");

        verifyNoInteractions(usuarios, perfiles, encoder, notificador, auditoria);
    }

    @Test
    @DisplayName("HU-02 R17, R28: un número vacío tras normalizar se rechaza en numeroDocumento sin consultar la BD")
    void rechazaNumeroVacio() {
        assertThatThrownBy(() -> servicio.registrar(
                solicitud("ana@correo.pe", CONTRASENA, VERSION, "DNI", "   "), IP))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage(ValidadorDocumento.MENSAJE_NUMERO_FALTA)
                .hasFieldOrPropertyWithValue("campo", "numeroDocumento");

        verifyNoInteractions(usuarios, perfiles, encoder, notificador, auditoria);
    }

    @Test
    @DisplayName("HU-02 R18, R19, R20, R21, R28: un RUC o DNI inválido se rechaza con su mensaje sin consultar la BD")
    void rechazaDocumentoInvalido() {
        esperaErrorDeDocumento("DNI", "1234567", ValidadorDocumento.MENSAJE_DNI);
        esperaErrorDeDocumento("RUC", "2010007097", ValidadorDocumento.MENSAJE_RUC_LONGITUD);
        esperaErrorDeDocumento("RUC", "30000000007", ValidadorDocumento.MENSAJE_RUC_PREFIJO);
        esperaErrorDeDocumento("RUC", "20100070971", ValidadorDocumento.MENSAJE_RUC_VERIFICADOR);

        verifyNoInteractions(usuarios, perfiles, encoder, notificador, auditoria);
    }

    private void esperaErrorDeDocumento(String tipo, String numero, String mensaje) {
        assertThatThrownBy(() -> servicio.registrar(solicitud("ana@correo.pe", CONTRASENA, VERSION, tipo, numero), IP))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage(mensaje)
                .hasFieldOrPropertyWithValue("campo", "numeroDocumento");
    }

    // ── Correo existente ─────────────────────────────────────────────────

    @Test
    @DisplayName("HU-02 R22, R23: con correo existente no guarda, no notifica y termina sin error")
    void correoExistenteNoGuardaNiNotifica() {
        when(encoder.encode(CONTRASENA)).thenReturn(HASH);
        when(usuarios.findByCorreo("ana@correo.pe")).thenReturn(Optional.of(existente(3L)));

        servicio.registrar(valida("Ana@correo.pe"), IP);

        verify(usuarios, never()).save(any());
        verify(usuarios, never()).saveAndFlush(any());
        verify(perfiles, never()).saveAndFlush(any());
        verify(perfiles, never()).save(any());
        verifyNoInteractions(notificador);
        verify(encoder).encode(CONTRASENA);
    }

    @Test
    @DisplayName("HU-02 R24, R33: con correo existente audita REGISTRO_CORREO_EXISTENTE con el id existente y la IP")
    void auditaCorreoExistente() {
        when(encoder.encode(CONTRASENA)).thenReturn(HASH);
        when(usuarios.findByCorreo("ana@correo.pe")).thenReturn(Optional.of(existente(3L)));

        servicio.registrar(valida("ana@correo.pe"), IP);

        var evento = unicoEventoAuditado();
        assertThat(evento.accion()).isEqualTo("REGISTRO_CORREO_EXISTENTE");
        assertThat(evento.usuarioId()).isEqualTo(3L);
        assertThat(evento.ip()).isEqualTo(IP);
        sinDatosSensibles(evento);
    }

    // ── Documento existente ──────────────────────────────────────────────

    @Test
    @DisplayName("HU-02 R25, R26: con documento existente no guarda, no notifica y termina sin error")
    void documentoExistenteNoGuardaNiNotifica() {
        when(encoder.encode(CONTRASENA)).thenReturn(HASH);
        when(usuarios.findByCorreo("nuevo@correo.pe")).thenReturn(Optional.empty());
        when(perfiles.findByHashDocumento(huella.calcular(RUC))).thenReturn(Optional.of(perfilDe(4L)));

        servicio.registrar(valida("nuevo@correo.pe"), IP);

        verify(usuarios, never()).saveAndFlush(any());
        verify(perfiles, never()).saveAndFlush(any());
        verifyNoInteractions(notificador);
    }

    @Test
    @DisplayName("HU-02 R27, R33: con documento existente audita REGISTRO_DOCUMENTO_EXISTENTE con el id del titular")
    void auditaDocumentoExistente() {
        when(encoder.encode(CONTRASENA)).thenReturn(HASH);
        when(usuarios.findByCorreo("nuevo@correo.pe")).thenReturn(Optional.empty());
        when(perfiles.findByHashDocumento(huella.calcular(RUC))).thenReturn(Optional.of(perfilDe(4L)));

        servicio.registrar(valida("nuevo@correo.pe"), IP);

        var evento = unicoEventoAuditado();
        assertThat(evento.accion()).isEqualTo("REGISTRO_DOCUMENTO_EXISTENTE");
        assertThat(evento.usuarioId()).isEqualTo(4L);
        assertThat(evento.ip()).isEqualTo(IP);
        sinDatosSensibles(evento);
    }

    // ── Carreras ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-02 R29: si otra solicitud gana el correo único, sigue el camino del correo existente")
    void carreraPorCorreo() {
        when(encoder.encode(CONTRASENA)).thenReturn(HASH);
        when(usuarios.findByCorreo("ana@correo.pe")).thenReturn(Optional.empty(), Optional.of(existente(9L)));
        when(perfiles.findByHashDocumento(huella.calcular(RUC))).thenReturn(Optional.empty());
        when(usuarios.saveAndFlush(any(Usuario.class)))
                .thenThrow(new DataIntegrityViolationException("uq_usuario_correo"));

        servicio.registrar(valida("ana@correo.pe"), IP);

        var evento = unicoEventoAuditado();
        assertThat(evento.accion()).isEqualTo("REGISTRO_CORREO_EXISTENTE");
        assertThat(evento.usuarioId()).isEqualTo(9L);
        verifyNoInteractions(notificador);
        verify(gestorTransacciones).rollback(any());
    }

    @Test
    @DisplayName("HU-02 R30: si otra solicitud gana el documento único, sigue el camino del documento existente")
    void carreraPorDocumento() {
        prepararRegistroNuevo();
        when(usuarios.findByCorreo("ana@correo.pe")).thenReturn(Optional.empty(), Optional.empty());
        when(perfiles.findByHashDocumento(huella.calcular(RUC)))
                .thenReturn(Optional.empty(), Optional.of(perfilDe(11L)));
        when(perfiles.saveAndFlush(any(PerfilArrendador.class)))
                .thenThrow(new DataIntegrityViolationException("uq_perfil_arrendador_hash_documento"));

        servicio.registrar(valida("ana@correo.pe"), IP);

        var evento = unicoEventoAuditado();
        assertThat(evento.accion()).isEqualTo("REGISTRO_DOCUMENTO_EXISTENTE");
        assertThat(evento.usuarioId()).isEqualTo(11L);
        verifyNoInteractions(notificador);
        verify(gestorTransacciones).rollback(any());
    }

    // ── Errores ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-02 R44: si el notificador falla, la excepción se propaga y la transacción se revierte")
    void falloDelNotificadorRevierte() {
        prepararRegistroNuevo();
        doThrow(new IllegalStateException("servidor de correo caído"))
                .when(notificador).notificarVerificacion(anyString());

        assertThatThrownBy(() -> servicio.registrar(valida("ana@correo.pe"), IP))
                .isInstanceOf(IllegalStateException.class);

        verify(gestorTransacciones).rollback(any());
        verify(gestorTransacciones, never()).commit(any());
    }
}
