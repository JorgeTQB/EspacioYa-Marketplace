package pe.edu.ulima.espacioya.cuentas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.limite.LimitadorIntentos;
import pe.edu.ulima.espacioya.comun.seguridad.TokenAleatorio;
import pe.edu.ulima.espacioya.cuentas.domain.EstadoUsuario;
import pe.edu.ulima.espacioya.cuentas.domain.PoliticaContrasena;
import pe.edu.ulima.espacioya.cuentas.domain.Rol;
import pe.edu.ulima.espacioya.cuentas.domain.TipoToken;
import pe.edu.ulima.espacioya.cuentas.domain.Token;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.dto.RestablecimientoContrasenaDTO;
import pe.edu.ulima.espacioya.cuentas.repository.SesionRepository;
import pe.edu.ulima.espacioya.cuentas.repository.TokenRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class RecuperacionContrasenaServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-28T15:00:00Z");
    private static final String IP = "10.0.0.1";
    private static final String CORREO = "ana@correo.pe";
    private static final String HASH_ANTERIOR = "$2a$12$hashanterior";
    private static final String HASH_NUEVO = "$2a$12$hashnuevo";
    private static final String NUEVA = "Nueva12345";
    private static final String URL_FRONTEND = "http://localhost:5173";
    private static final String CLAVE = "recuperacion|" + CORREO;
    private static final long ID_USUARIO = 7L;
    private static final long ID_TOKEN = 41L;

    @Mock
    private UsuarioRepository usuarios;
    @Mock
    private TokenRepository tokens;
    @Mock
    private SesionRepository sesiones;
    @Mock
    private NotificadorCorreo notificador;
    @Mock
    private PasswordEncoder encoder;
    @Mock
    private AuditoriaService auditoria;

    private LimitadorIntentos limitador;
    private RecuperacionContrasenaService servicio;

    @BeforeEach
    void preparar() {
        var reloj = Clock.fixed(AHORA, ZoneOffset.UTC);
        limitador = new LimitadorIntentos(reloj);
        servicio = new RecuperacionContrasenaService(usuarios, tokens, sesiones, notificador, encoder, limitador,
                auditoria, new TransactionTemplate(mock(PlatformTransactionManager.class)), reloj, URL_FRONTEND);
    }

    // ── Datos ────────────────────────────────────────────────────────────

    private static Usuario usuario() {
        var usuario = Usuario.registrar(CORREO, HASH_ANTERIOR, "2026-10", AHORA.minusSeconds(3600));
        ReflectionTestUtils.setField(usuario, "id", ID_USUARIO);
        return usuario;
    }

    private static Token tokenGuardado(String token, Instant creado) {
        var guardado = Token.recuperacion(ID_USUARIO, TokenAleatorio.hash(token), creado);
        ReflectionTestUtils.setField(guardado, "id", ID_TOKEN);
        return guardado;
    }

    private void prepararEmision(Usuario usuario) {
        when(usuarios.findByCorreo(CORREO)).thenReturn(Optional.of(usuario));
        when(tokens.save(any(Token.class))).thenAnswer(invocacion -> {
            Token token = invocacion.getArgument(0);
            ReflectionTestUtils.setField(token, "id", ID_TOKEN);
            return token;
        });
    }

    private Token prepararTokenVigente(String token, Usuario usuario) {
        var guardado = tokenGuardado(token, AHORA.minus(Duration.ofMinutes(10)));
        when(tokens.findByHashTokenAndTipo(TokenAleatorio.hash(token), TipoToken.RECUPERACION))
                .thenReturn(Optional.of(guardado));
        when(usuarios.findById(ID_USUARIO)).thenReturn(Optional.of(usuario));
        return guardado;
    }

    private EventoAuditoria unicaAuditoria() {
        var captor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(captor.capture());
        return captor.getValue();
    }

    private static void sinDatosSensibles(EventoAuditoria evento, String token) {
        var detalle = String.valueOf(evento.detalle());
        assertThat(detalle).doesNotContain(CORREO).doesNotContain(NUEVA);
        if (token != null) {
            assertThat(detalle).doesNotContain(token).doesNotContain(TokenAleatorio.hash(token));
        }
    }

    // ── Solicitud ────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-04 R2, R3, R4, R5: emite un token con hash, fechas del reloj y avisa con el enlace del token")
    void emiteTokenYNotificaEnlace() {
        prepararEmision(usuario());

        servicio.solicitar("  ANA@Correo.PE ", IP);

        var guardado = ArgumentCaptor.forClass(Token.class);
        verify(tokens).save(guardado.capture());
        var enlace = ArgumentCaptor.forClass(String.class);
        verify(notificador, times(1)).notificarRecuperacion(eq(CORREO), enlace.capture());
        assertThat(enlace.getValue()).startsWith(URL_FRONTEND + "/restablecer-contrasena#token=");
        var token = enlace.getValue().substring(enlace.getValue().indexOf("#token=") + 7);
        assertThat(TokenAleatorio.tieneFormato(token)).isTrue();
        var fila = guardado.getValue();
        assertThat(fila.getUsuarioId()).isEqualTo(ID_USUARIO);
        assertThat(fila.getTipo()).isEqualTo(TipoToken.RECUPERACION);
        assertThat(fila.getCreadoEn()).isEqualTo(AHORA);
        assertThat(fila.getExpiraEn()).isEqualTo(AHORA.plus(Duration.ofMinutes(30)));
        assertThat(fila.getUsadoEn()).isNull();
        assertThat(fila.getHashToken()).isEqualTo(TokenAleatorio.hash(token)).isNotEqualTo(token);
    }

    @Test
    @DisplayName("HU-04 R4: dos solicitudes de la misma cuenta reciben tokens distintos")
    void dosSolicitudesTokensDistintos() {
        prepararEmision(usuario());

        servicio.solicitar(CORREO, IP);
        servicio.solicitar(CORREO, IP);

        var guardados = ArgumentCaptor.forClass(Token.class);
        verify(tokens, times(2)).save(guardados.capture());
        var hashes = guardados.getAllValues().stream().map(Token::getHashToken).toList();
        assertThat(hashes.get(0)).isNotEqualTo(hashes.get(1));
    }

    @Test
    @DisplayName("HU-04 R6: antes de guardar el token nuevo anula los tokens vigentes de la cuenta")
    void anulaPendientesAntesDeGuardar() {
        prepararEmision(usuario());

        servicio.solicitar(CORREO, IP);

        var orden = inOrder(tokens, notificador);
        orden.verify(tokens).anularPendientes(ID_USUARIO, TipoToken.RECUPERACION, AHORA);
        orden.verify(tokens).save(any(Token.class));
        orden.verify(notificador).notificarRecuperacion(eq(CORREO), anyString());
    }

    @Test
    @DisplayName("HU-04 R26, R30: audita RECUPERACION_SOLICITADA con la cuenta, la entidad token y su id, sin datos")
    void auditaSolicitudEmitida() {
        prepararEmision(usuario());

        servicio.solicitar(CORREO, IP);

        var evento = unicaAuditoria();
        assertThat(evento.accion()).isEqualTo(RecuperacionContrasenaService.ACCION_SOLICITADA);
        assertThat(evento.usuarioId()).isEqualTo(ID_USUARIO);
        assertThat(evento.entidad()).isEqualTo("token");
        assertThat(evento.entidadId()).isEqualTo(ID_TOKEN);
        assertThat(evento.ip()).isEqualTo(IP);
        assertThat(evento.detalle()).isNull();
    }

    @Test
    @DisplayName("HU-04 R7, R27, R30: correo desconocido → sin token ni aviso, audita CORREO_DESCONOCIDO sin el correo")
    void correoDesconocidoNoEmite() {
        when(usuarios.findByCorreo(CORREO)).thenReturn(Optional.empty());

        servicio.solicitar(CORREO, IP);

        verifyNoInteractions(tokens, notificador);
        var evento = unicaAuditoria();
        assertThat(evento.accion()).isEqualTo(RecuperacionContrasenaService.ACCION_NO_EMITIDA);
        assertThat(evento.usuarioId()).isNull();
        assertThat(evento.ip()).isEqualTo(IP);
        assertThat(evento.detalle()).isEqualTo("motivo=CORREO_DESCONOCIDO");
        sinDatosSensibles(evento, null);
    }

    @Test
    @DisplayName("HU-04 R8, R27: cuentas no habilitadas (admin, correo sin verificar, suspendida) no reciben enlace")
    void cuentaNoHabilitadaNoEmite() {
        var admin = usuario();
        ReflectionTestUtils.setField(admin, "rol", Rol.ADMIN);
        var sinVerificar = usuario();
        ReflectionTestUtils.setField(sinVerificar, "correoVerificado", false);
        var suspendida = usuario();
        ReflectionTestUtils.setField(suspendida, "estado", EstadoUsuario.SUSPENDIDA);
        when(usuarios.findByCorreo(CORREO))
                .thenReturn(Optional.of(admin), Optional.of(sinVerificar), Optional.of(suspendida));

        servicio.solicitar(CORREO, IP);
        servicio.solicitar(CORREO, IP);
        servicio.solicitar(CORREO, IP);

        verifyNoInteractions(tokens, notificador);
        var captor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria, times(3)).registrar(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(evento -> {
            assertThat(evento.accion()).isEqualTo(RecuperacionContrasenaService.ACCION_NO_EMITIDA);
            assertThat(evento.usuarioId()).isEqualTo(ID_USUARIO);
        });
        assertThat(captor.getAllValues()).extracting(EventoAuditoria::detalle).containsExactly(
                "motivo=ROL_NO_PERMITIDO", "motivo=CORREO_NO_VERIFICADO", "motivo=CUENTA_NO_ACTIVA");
    }

    @Test
    @DisplayName("HU-04 R9, R27: la 4.ª solicitud en la hora no emite token aunque la cuenta esté habilitada")
    void cuartaSolicitudNoEmite() {
        prepararEmision(usuario());

        for (int i = 0; i < 4; i++) {
            servicio.solicitar(CORREO, IP);
        }

        verify(tokens, times(3)).save(any(Token.class));
        verify(notificador, times(3)).notificarRecuperacion(eq(CORREO), anyString());
        var captor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria, times(4)).registrar(captor.capture());
        var ultimo = captor.getAllValues().get(3);
        assertThat(ultimo.accion()).isEqualTo(RecuperacionContrasenaService.ACCION_NO_EMITIDA);
        assertThat(ultimo.usuarioId()).isEqualTo(ID_USUARIO);
        assertThat(ultimo.detalle()).isEqualTo("motivo=LIMITE_SUPERADO");
    }

    @Test
    @DisplayName("HU-04 R10: un correo inexistente consume cupo, porque el límite se aplica antes de buscar la cuenta")
    void correoInexistenteConsumeCupo() {
        when(usuarios.findByCorreo(CORREO)).thenReturn(Optional.empty());

        for (int i = 0; i < 3; i++) {
            servicio.solicitar(CORREO, IP);
        }

        assertThat(limitador.superaLimite(CLAVE, 3, Duration.ofHours(1))).isTrue();
        assertThat(limitador.intentar(CLAVE, 3, Duration.ofHours(1))).isFalse();
    }

    @Test
    @DisplayName("HU-04 R11: las solicitudes que superan el límite no cuentan para él")
    void solicitudRechazadaNoCuenta() {
        when(usuarios.findByCorreo(CORREO)).thenReturn(Optional.empty());

        for (int i = 0; i < 6; i++) {
            servicio.solicitar(CORREO, IP);
        }

        assertThat(limitador.superaLimite(CLAVE, 3, Duration.ofHours(1))).isTrue();
        assertThat(limitador.superaLimite(CLAVE, 4, Duration.ofHours(1))).isFalse();
    }

    // ── Validación del enlace ────────────────────────────────────────────

    @Test
    @DisplayName("HU-04 R15: un token vigente de una cuenta habilitada se valida sin modificarlo ni auditar")
    void validaTokenVigente() {
        var token = TokenAleatorio.generar();
        var guardado = prepararTokenVigente(token, usuario());

        servicio.validar(token);

        assertThat(guardado.getUsadoEn()).isNull();
        verify(tokens, never()).marcarUsado(anyLong(), any());
        verify(tokens, never()).anularPendientes(anyLong(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("HU-04 R16: token desconocido, usado, expirado o de una cuenta no habilitada → enlace inválido")
    void validarRechazaCadaMotivo() {
        var desconocido = TokenAleatorio.generar();
        when(tokens.findByHashTokenAndTipo(TokenAleatorio.hash(desconocido), TipoToken.RECUPERACION))
                .thenReturn(Optional.empty());
        var usado = TokenAleatorio.generar();
        var filaUsada = tokenGuardado(usado, AHORA.minus(Duration.ofMinutes(5)));
        ReflectionTestUtils.setField(filaUsada, "usadoEn", AHORA.minus(Duration.ofMinutes(1)));
        when(tokens.findByHashTokenAndTipo(TokenAleatorio.hash(usado), TipoToken.RECUPERACION))
                .thenReturn(Optional.of(filaUsada));
        var expirado = TokenAleatorio.generar();
        when(tokens.findByHashTokenAndTipo(TokenAleatorio.hash(expirado), TipoToken.RECUPERACION))
                .thenReturn(Optional.of(tokenGuardado(expirado, AHORA.minus(Duration.ofMinutes(30)))));
        var deSuspendida = TokenAleatorio.generar();
        var suspendida = usuario();
        ReflectionTestUtils.setField(suspendida, "estado", EstadoUsuario.SUSPENDIDA);
        prepararTokenVigente(deSuspendida, suspendida);

        for (var token : List.of(desconocido, usado, expirado, deSuspendida)) {
            assertThatThrownBy(() -> servicio.validar(token))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage(RecuperacionContrasenaService.MENSAJE_ENLACE_INVALIDO)
                    .extracting("campo").isNull();
        }
        verify(tokens, never()).marcarUsado(anyLong(), any());
        verifyNoInteractions(auditoria, sesiones);
    }

    @Test
    @DisplayName("HU-04 R17: un token sin forma válida se rechaza sin consultar la tabla token")
    void formaInvalidaSinConsultar() {
        for (var token : new String[] {null, "", "corto", TokenAleatorio.generar() + "x", "+".repeat(43)}) {
            assertThatThrownBy(() -> servicio.validar(token))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage(RecuperacionContrasenaService.MENSAJE_ENLACE_INVALIDO);
            assertThatThrownBy(() -> servicio.restablecer(new RestablecimientoContrasenaDTO(token, NUEVA), IP))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage(RecuperacionContrasenaService.MENSAJE_ENLACE_INVALIDO);
        }

        verifyNoInteractions(tokens, usuarios, sesiones, encoder);
    }

    // ── Restablecimiento ─────────────────────────────────────────────────

    @Test
    @DisplayName("HU-04 R18, R19, R20, R21, R25: cambia el hash, usa el token, anula pendientes y revoca sesiones")
    void restableceContrasena() {
        var token = TokenAleatorio.generar();
        var usuario = usuario();
        prepararTokenVigente(token, usuario);
        when(encoder.encode(NUEVA)).thenReturn(HASH_NUEVO);
        when(tokens.marcarUsado(ID_TOKEN, AHORA)).thenReturn(1);
        when(sesiones.revocarTodas(ID_USUARIO)).thenReturn(2);

        servicio.restablecer(new RestablecimientoContrasenaDTO(token, NUEVA), IP);

        assertThat(usuario.getHashContrasena()).isEqualTo(HASH_NUEVO);
        var orden = inOrder(encoder, tokens, sesiones);
        orden.verify(encoder).encode(NUEVA);
        orden.verify(tokens).marcarUsado(ID_TOKEN, AHORA);
        orden.verify(tokens).anularPendientes(ID_USUARIO, TipoToken.RECUPERACION, AHORA);
        orden.verify(sesiones).revocarTodas(ID_USUARIO);
        verify(sesiones, never()).save(any());
    }

    @Test
    @DisplayName("HU-04 R28, R30: audita CONTRASENA_RESTABLECIDA con la cuenta y las sesiones revocadas, sin datos")
    void auditaRestablecimiento() {
        var token = TokenAleatorio.generar();
        prepararTokenVigente(token, usuario());
        when(encoder.encode(NUEVA)).thenReturn(HASH_NUEVO);
        when(tokens.marcarUsado(ID_TOKEN, AHORA)).thenReturn(1);
        when(sesiones.revocarTodas(ID_USUARIO)).thenReturn(2);

        servicio.restablecer(new RestablecimientoContrasenaDTO(token, NUEVA), IP);

        var evento = unicaAuditoria();
        assertThat(evento.accion()).isEqualTo(RecuperacionContrasenaService.ACCION_RESTABLECIDA);
        assertThat(evento.usuarioId()).isEqualTo(ID_USUARIO);
        assertThat(evento.entidad()).isEqualTo("usuario");
        assertThat(evento.entidadId()).isEqualTo(ID_USUARIO);
        assertThat(evento.ip()).isEqualTo(IP);
        assertThat(evento.detalle()).isEqualTo("sesionesRevocadas=2");
        sinDatosSensibles(evento, token);
        assertThat(evento.detalle()).doesNotContain(HASH_NUEVO);
    }

    @Test
    @DisplayName("HU-04 R22, R29: si marcarUsado no afecta filas → 400 sin cambiar la contraseña, audita TOKEN_USADO")
    void carreraDeConfirmaciones() {
        var token = TokenAleatorio.generar();
        var usuario = usuario();
        prepararTokenVigente(token, usuario);
        when(encoder.encode(NUEVA)).thenReturn(HASH_NUEVO);
        when(tokens.marcarUsado(ID_TOKEN, AHORA)).thenReturn(0);

        assertThatThrownBy(() -> servicio.restablecer(new RestablecimientoContrasenaDTO(token, NUEVA), IP))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage(RecuperacionContrasenaService.MENSAJE_ENLACE_INVALIDO);

        assertThat(usuario.getHashContrasena()).isEqualTo(HASH_ANTERIOR);
        verify(sesiones, never()).revocarTodas(anyLong());
        verify(tokens, never()).anularPendientes(anyLong(), any(), any());
        var evento = unicaAuditoria();
        assertThat(evento.accion()).isEqualTo(RecuperacionContrasenaService.ACCION_RECHAZADA);
        assertThat(evento.usuarioId()).isEqualTo(ID_USUARIO);
        assertThat(evento.detalle()).isEqualTo("motivo=TOKEN_USADO");
        sinDatosSensibles(evento, token);
    }

    @Test
    @DisplayName("HU-04 R16, R29, R30: confirmar con token desconocido audita TOKEN_DESCONOCIDO sin cuenta ni token")
    void confirmarTokenDesconocido() {
        var token = TokenAleatorio.generar();
        when(tokens.findByHashTokenAndTipo(TokenAleatorio.hash(token), TipoToken.RECUPERACION))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.restablecer(new RestablecimientoContrasenaDTO(token, NUEVA), IP))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage(RecuperacionContrasenaService.MENSAJE_ENLACE_INVALIDO);

        verifyNoInteractions(encoder, sesiones);
        var evento = unicaAuditoria();
        assertThat(evento.accion()).isEqualTo(RecuperacionContrasenaService.ACCION_RECHAZADA);
        assertThat(evento.usuarioId()).isNull();
        assertThat(evento.ip()).isEqualTo(IP);
        assertThat(evento.detalle()).isEqualTo("motivo=TOKEN_DESCONOCIDO");
        sinDatosSensibles(evento, token);
    }

    @Test
    @DisplayName("HU-04 R16, R29: confirmar con token expirado, usado o de cuenta no habilitada audita su motivo")
    void confirmarAuditaCadaMotivo() {
        var expirado = TokenAleatorio.generar();
        when(tokens.findByHashTokenAndTipo(TokenAleatorio.hash(expirado), TipoToken.RECUPERACION))
                .thenReturn(Optional.of(tokenGuardado(expirado, AHORA.minus(Duration.ofMinutes(31)))));
        var usado = TokenAleatorio.generar();
        var filaUsada = tokenGuardado(usado, AHORA.minus(Duration.ofMinutes(5)));
        ReflectionTestUtils.setField(filaUsada, "usadoEn", AHORA.minus(Duration.ofMinutes(1)));
        when(tokens.findByHashTokenAndTipo(TokenAleatorio.hash(usado), TipoToken.RECUPERACION))
                .thenReturn(Optional.of(filaUsada));
        var deAdmin = TokenAleatorio.generar();
        var admin = usuario();
        ReflectionTestUtils.setField(admin, "rol", Rol.ADMIN);
        prepararTokenVigente(deAdmin, admin);

        for (var token : List.of(expirado, usado, deAdmin)) {
            assertThatThrownBy(() -> servicio.restablecer(new RestablecimientoContrasenaDTO(token, NUEVA), IP))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage(RecuperacionContrasenaService.MENSAJE_ENLACE_INVALIDO);
        }

        verifyNoInteractions(encoder, sesiones);
        assertThat(admin.getHashContrasena()).isEqualTo(HASH_ANTERIOR);
        var captor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria, times(3)).registrar(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(evento -> {
            assertThat(evento.accion()).isEqualTo(RecuperacionContrasenaService.ACCION_RECHAZADA);
            assertThat(evento.usuarioId()).isEqualTo(ID_USUARIO);
            assertThat(evento.entidad()).isEqualTo("token");
            assertThat(evento.entidadId()).isEqualTo(ID_TOKEN);
        });
        assertThat(captor.getAllValues()).extracting(EventoAuditoria::detalle).containsExactly(
                "motivo=TOKEN_EXPIRADO", "motivo=TOKEN_USADO", "motivo=CUENTA_NO_HABILITADA");
    }

    @Test
    @DisplayName("HU-04 R23: contraseña que no cumple la política → 400 en contrasena sin marcar el token")
    void contrasenaDebilNoMarcaToken() {
        var token = TokenAleatorio.generar();
        prepararTokenVigente(token, usuario());

        for (var debil : new String[] {"SinDigitos", CORREO, "corta1"}) {
            assertThatThrownBy(() -> servicio.restablecer(new RestablecimientoContrasenaDTO(token, debil), IP))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage(PoliticaContrasena.REQUISITOS)
                    .extracting("campo").isEqualTo("contrasena");
        }

        verify(tokens, never()).marcarUsado(anyLong(), any());
        verifyNoInteractions(encoder, sesiones, auditoria);
    }

    @Test
    @DisplayName("HU-04 R24: al restablecer reinicia los fallos de inicio de sesión contados para el correo")
    void reiniciaContadorDeLogin() {
        var token = TokenAleatorio.generar();
        prepararTokenVigente(token, usuario());
        when(encoder.encode(NUEVA)).thenReturn(HASH_NUEVO);
        when(tokens.marcarUsado(ID_TOKEN, AHORA)).thenReturn(1);
        for (int i = 0; i < InicioSesionService.MAXIMO_FALLOS; i++) {
            limitador.registrar("login|" + CORREO);
        }

        servicio.restablecer(new RestablecimientoContrasenaDTO(token, NUEVA), IP);

        assertThat(limitador.superaLimite("login|" + CORREO, 1, InicioSesionService.VENTANA_FALLOS)).isFalse();
    }

    @Test
    @DisplayName("HU-04 R24: si el restablecimiento se rechaza, los fallos de inicio de sesión se conservan")
    void rechazoConservaContadorDeLogin() {
        var token = TokenAleatorio.generar();
        prepararTokenVigente(token, usuario());
        when(encoder.encode(NUEVA)).thenReturn(HASH_NUEVO);
        when(tokens.marcarUsado(ID_TOKEN, AHORA)).thenReturn(0);
        limitador.registrar("login|" + CORREO);

        assertThatThrownBy(() -> servicio.restablecer(new RestablecimientoContrasenaDTO(token, NUEVA), IP))
                .isInstanceOf(ReglaNegocioException.class);

        assertThat(limitador.superaLimite("login|" + CORREO, 1, InicioSesionService.VENTANA_FALLOS)).isTrue();
    }
}
