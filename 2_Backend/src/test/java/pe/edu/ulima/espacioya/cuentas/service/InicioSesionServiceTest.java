package pe.edu.ulima.espacioya.cuentas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.CredencialesInvalidasException;
import pe.edu.ulima.espacioya.comun.error.DemasiadosIntentosException;
import pe.edu.ulima.espacioya.comun.limite.LimitadorIntentos;
import pe.edu.ulima.espacioya.comun.seguridad.TokenAleatorio;
import pe.edu.ulima.espacioya.cuentas.domain.EstadoIdentidad;
import pe.edu.ulima.espacioya.cuentas.domain.EstadoUsuario;
import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.Rol;
import pe.edu.ulima.espacioya.cuentas.domain.Sesion;
import pe.edu.ulima.espacioya.cuentas.domain.TipoDocumento;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.dto.InicioSesionDTO;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;
import pe.edu.ulima.espacioya.cuentas.repository.SesionRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class InicioSesionServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-28T15:00:00Z");
    private static final String IP = "10.0.0.1";
    private static final String CORREO = "ana@correo.pe";
    private static final String CONTRASENA = "Segura123!";
    private static final String HASH = "$2a$12$hashdeprueba";
    private static final String SENUELO = "$2a$12$senuelo";
    private static final String CLAVE = "login|" + CORREO;
    private static final long ID_USUARIO = 7L;
    private static final long ID_SESION = 31L;

    @Mock
    private UsuarioRepository usuarios;
    @Mock
    private PerfilArrendadorRepository perfiles;
    @Mock
    private SesionRepository sesiones;
    @Mock
    private PasswordEncoder encoder;
    @Mock
    private AuditoriaService auditoria;

    private LimitadorIntentos limitador;
    private InicioSesionService servicio;

    @BeforeEach
    void preparar() {
        var reloj = Clock.fixed(AHORA, ZoneOffset.UTC);
        limitador = new LimitadorIntentos(reloj);
        when(encoder.encode(anyString())).thenReturn(SENUELO);
        servicio = new InicioSesionService(usuarios, perfiles, sesiones, encoder, limitador, auditoria,
                new TransactionTemplate(mock(PlatformTransactionManager.class)), reloj);
    }

    private static InicioSesionDTO solicitud(String correo, String contrasena) {
        return new InicioSesionDTO(correo, contrasena);
    }

    private static Usuario usuario() {
        var usuario = Usuario.registrar(CORREO, HASH, "2026-10", AHORA.minusSeconds(3600));
        ReflectionTestUtils.setField(usuario, "id", ID_USUARIO);
        return usuario;
    }

    private static Usuario arrendador() {
        var usuario = Usuario.registrarArrendador(CORREO, HASH, "Ana", "987654321", "2026-10",
                AHORA.minusSeconds(3600));
        ReflectionTestUtils.setField(usuario, "id", ID_USUARIO);
        return usuario;
    }

    private static PerfilArrendador perfil(EstadoIdentidad estado) {
        var perfil = PerfilArrendador.crear(ID_USUARIO, TipoDocumento.RUC, "20100070970", "b".repeat(64),
                "Canchas SAC", "Av. Prado 1");
        ReflectionTestUtils.setField(perfil, "estadoIdentidad", estado);
        return perfil;
    }

    private void prepararLoginCorrecto(Usuario usuario) {
        when(usuarios.findByCorreo(CORREO)).thenReturn(Optional.of(usuario));
        when(encoder.matches(CONTRASENA, HASH)).thenReturn(true);
        when(sesiones.save(any(Sesion.class))).thenAnswer(invocacion -> {
            Sesion sesion = invocacion.getArgument(0);
            ReflectionTestUtils.setField(sesion, "id", ID_SESION);
            return sesion;
        });
    }

    private EventoAuditoria unicoEvento() {
        var eventos = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(eventos.capture());
        return eventos.getValue();
    }

    private void fallar(int veces) {
        for (int i = 0; i < veces; i++) {
            limitador.registrar(CLAVE);
        }
    }

    // ── Éxito ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-03 R1, R2, R3, R5, R6: login correcto de USUARIO guarda la sesión con el hash del token")
    void loginCorrectoDeUsuario() {
        prepararLoginCorrecto(usuario());

        var respuesta = servicio.iniciar(solicitud("  ANA@Correo.PE ", CONTRASENA), IP);

        assertThat(respuesta.token()).hasSize(43);
        assertThat(respuesta.rol()).isEqualTo("USUARIO");
        assertThat(respuesta.aviso()).isNull();
        var sesion = ArgumentCaptor.forClass(Sesion.class);
        verify(sesiones).save(sesion.capture());
        assertThat(sesion.getValue().getUsuarioId()).isEqualTo(ID_USUARIO);
        assertThat(sesion.getValue().getHashToken()).isEqualTo(TokenAleatorio.hash(respuesta.token()));
        assertThat(sesion.getValue().getCreadaEn()).isEqualTo(AHORA);
        assertThat(sesion.getValue().getExpiraEn()).isEqualTo(AHORA.plus(Duration.ofMinutes(30)));
        assertThat(sesion.getValue().isRevocada()).isFalse();
    }

    @Test
    @DisplayName("HU-03 R4: login de ARRENDADOR con identidad NO_INICIADA devuelve el aviso de identidad")
    void arrendadorNoIniciadaRecibeAviso() {
        prepararLoginCorrecto(arrendador());
        when(perfiles.findById(ID_USUARIO)).thenReturn(Optional.of(perfil(EstadoIdentidad.NO_INICIADA)));

        var respuesta = servicio.iniciar(solicitud(CORREO, CONTRASENA), IP);

        assertThat(respuesta.rol()).isEqualTo("ARRENDADOR");
        assertThat(respuesta.aviso()).isEqualTo("Para publicar tus espacios, primero debes verificar tu identidad.");
    }

    @Test
    @DisplayName("HU-03 R5: login de ARRENDADOR con identidad distinta de NO_INICIADA devuelve aviso nulo")
    void arrendadorVerificadoSinAviso() {
        prepararLoginCorrecto(arrendador());
        when(perfiles.findById(ID_USUARIO)).thenReturn(Optional.of(perfil(EstadoIdentidad.VERIFICADA)));

        var respuesta = servicio.iniciar(solicitud(CORREO, CONTRASENA), IP);

        assertThat(respuesta.aviso()).isNull();
    }

    @Test
    @DisplayName("HU-03 R30, R34: login correcto audita LOGIN_EXITOSO con la sesión, la IP y sin detalle")
    void auditaLoginExitoso() {
        prepararLoginCorrecto(usuario());

        var respuesta = servicio.iniciar(solicitud(CORREO, CONTRASENA), IP);

        var evento = unicoEvento();
        assertThat(evento.accion()).isEqualTo("LOGIN_EXITOSO");
        assertThat(evento.usuarioId()).isEqualTo(ID_USUARIO);
        assertThat(evento.entidad()).isEqualTo("sesion");
        assertThat(evento.entidadId()).isEqualTo(ID_SESION);
        assertThat(evento.ip()).isEqualTo(IP);
        assertThat(evento.detalle()).isNull();
        assertThat(evento.toString()).doesNotContain(respuesta.token()).doesNotContain(CORREO);
    }

    @Test
    @DisplayName("HU-03 R20: un login correcto reinicia a cero los fallos del correo")
    void loginCorrectoReiniciaFallos() {
        prepararLoginCorrecto(usuario());
        fallar(4);

        servicio.iniciar(solicitud(CORREO, CONTRASENA), IP);

        assertThat(limitador.superaLimite(CLAVE, 1, Duration.ofMinutes(15))).isFalse();
    }

    // ── Rechazos con 401 ─────────────────────────────────────────────────

    private void esperaRechazo(String motivo, Long usuarioId) {
        assertThatThrownBy(() -> servicio.iniciar(solicitud(CORREO, CONTRASENA), IP))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage("Correo o contraseña incorrectos.");
        verify(sesiones, never()).save(any());
        var evento = unicoEvento();
        assertThat(evento.accion()).isEqualTo("LOGIN_FALLIDO");
        assertThat(evento.usuarioId()).isEqualTo(usuarioId);
        assertThat(evento.ip()).isEqualTo(IP);
        assertThat(evento.detalle()).isEqualTo("motivo=" + motivo);
        assertThat(limitador.superaLimite(CLAVE, 1, Duration.ofMinutes(15))).isTrue();
    }

    @Test
    @DisplayName("HU-03 R9, R17, R31: contraseña incorrecta lanza 401, cuenta el fallo y audita CONTRASENA_INCORRECTA")
    void contrasenaIncorrecta() {
        when(usuarios.findByCorreo(CORREO)).thenReturn(Optional.of(usuario()));
        when(encoder.matches(CONTRASENA, HASH)).thenReturn(false);

        esperaRechazo("CONTRASENA_INCORRECTA", ID_USUARIO);
    }

    @Test
    @DisplayName("HU-03 R10, R17, R31: correo inexistente lanza el mismo 401, cuenta el fallo y audita sin usuario")
    void correoDesconocido() {
        when(usuarios.findByCorreo(CORREO)).thenReturn(Optional.empty());

        esperaRechazo("CORREO_DESCONOCIDO", null);
    }

    @Test
    @DisplayName("HU-03 R14: con correo inexistente compara la contraseña una vez contra el hash señuelo")
    void comparaContraHashSenuelo() {
        when(usuarios.findByCorreo(CORREO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.iniciar(solicitud(CORREO, CONTRASENA), IP))
                .isInstanceOf(CredencialesInvalidasException.class);

        verify(encoder, times(1)).matches(CONTRASENA, SENUELO);
        verify(encoder, times(1)).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("HU-03 R11, R31: correo no verificado con contraseña correcta lanza 401 y audita CORREO_NO_VERIFICADO")
    void correoNoVerificado() {
        var usuario = usuario();
        ReflectionTestUtils.setField(usuario, "correoVerificado", false);
        when(usuarios.findByCorreo(CORREO)).thenReturn(Optional.of(usuario));
        when(encoder.matches(CONTRASENA, HASH)).thenReturn(true);

        esperaRechazo("CORREO_NO_VERIFICADO", ID_USUARIO);
    }

    @Test
    @DisplayName("HU-03 R12, R31: cuenta SUSPENDIDA con contraseña correcta lanza 401 y audita CUENTA_NO_ACTIVA")
    void cuentaNoActiva() {
        var usuario = usuario();
        ReflectionTestUtils.setField(usuario, "estado", EstadoUsuario.SUSPENDIDA);
        when(usuarios.findByCorreo(CORREO)).thenReturn(Optional.of(usuario));
        when(encoder.matches(CONTRASENA, HASH)).thenReturn(true);

        esperaRechazo("CUENTA_NO_ACTIVA", ID_USUARIO);
    }

    @Test
    @DisplayName("HU-03 R13, R31: cuenta ADMIN con contraseña correcta lanza 401 y audita ROL_NO_PERMITIDO")
    void adminNoPermitido() {
        var usuario = usuario();
        ReflectionTestUtils.setField(usuario, "rol", Rol.ADMIN);
        when(usuarios.findByCorreo(CORREO)).thenReturn(Optional.of(usuario));
        when(encoder.matches(CONTRASENA, HASH)).thenReturn(true);

        esperaRechazo("ROL_NO_PERMITIDO", ID_USUARIO);
    }

    @Test
    @DisplayName("HU-03 R34: el detalle de los fallos solo lleva el motivo, nunca el correo ni la contraseña")
    void detalleSinDatosSensibles() {
        when(usuarios.findByCorreo(CORREO)).thenReturn(Optional.of(usuario()));
        when(encoder.matches(CONTRASENA, HASH)).thenReturn(false);

        assertThatThrownBy(() -> servicio.iniciar(solicitud(CORREO, CONTRASENA), IP))
                .isInstanceOf(CredencialesInvalidasException.class);

        var evento = unicoEvento();
        assertThat(evento.detalle()).matches("^motivo=[A-Z_]+$");
        assertThat(evento.toString()).doesNotContain(CORREO).doesNotContain(CONTRASENA);
    }

    // ── Bloqueo ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-03 R16, R32: con 5 fallos responde 429 sin comparar la contraseña y audita LOGIN_BLOQUEADO")
    void bloqueadoNoComparaContrasena() {
        fallar(5);
        when(usuarios.findByCorreo(CORREO)).thenReturn(Optional.of(usuario()));

        assertThatThrownBy(() -> servicio.iniciar(solicitud(CORREO, CONTRASENA), IP))
                .isInstanceOf(DemasiadosIntentosException.class)
                .hasMessage("Hiciste demasiados intentos. Espera un momento e inténtalo de nuevo.");

        verify(encoder, never()).matches(anyString(), anyString());
        verify(sesiones, never()).save(any());
        var evento = unicoEvento();
        assertThat(evento.accion()).isEqualTo("LOGIN_BLOQUEADO");
        assertThat(evento.usuarioId()).isEqualTo(ID_USUARIO);
        assertThat(evento.ip()).isEqualTo(IP);
        assertThat(evento.detalle()).isNull();
    }

    @Test
    @DisplayName("HU-03 R32: el bloqueo de un correo inexistente se audita con usuario nulo")
    void bloqueadoCorreoInexistente() {
        fallar(5);
        when(usuarios.findByCorreo(CORREO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.iniciar(solicitud(CORREO, CONTRASENA), IP))
                .isInstanceOf(DemasiadosIntentosException.class);

        assertThat(unicoEvento().usuarioId()).isNull();
    }

    @Test
    @DisplayName("HU-03 R18: una solicitud respondida con 429 no cuenta como fallo")
    void bloqueadoNoRegistraFallo() {
        var limitadorEspia = mock(LimitadorIntentos.class);
        when(limitadorEspia.superaLimite(eq(CLAVE), eq(5), eq(Duration.ofMinutes(15)))).thenReturn(true);
        var conEspia = new InicioSesionService(usuarios, perfiles, sesiones, encoder, limitadorEspia, auditoria,
                new TransactionTemplate(mock(PlatformTransactionManager.class)), Clock.fixed(AHORA, ZoneOffset.UTC));

        assertThatThrownBy(() -> conEspia.iniciar(solicitud(CORREO, CONTRASENA), IP))
                .isInstanceOf(DemasiadosIntentosException.class);

        verify(limitadorEspia, never()).registrar(anyString());
        verify(limitadorEspia, never()).limpiar(anyString());
    }

    @Test
    @DisplayName("HU-03 R16: con 4 fallos el login correcto se procesa con normalidad")
    void cuatroFallosNoBloquean() {
        fallar(4);
        prepararLoginCorrecto(usuario());

        var respuesta = servicio.iniciar(solicitud(CORREO, CONTRASENA), IP);

        assertThat(respuesta.rol()).isEqualTo("USUARIO");
        verify(encoder).matches(CONTRASENA, HASH);
        assertThat(unicoEvento().accion()).isEqualTo("LOGIN_EXITOSO");
    }
}
