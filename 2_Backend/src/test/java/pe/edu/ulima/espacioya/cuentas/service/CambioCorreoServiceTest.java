package pe.edu.ulima.espacioya.cuentas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.DemasiadosIntentosException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.limite.LimitadorIntentos;
import pe.edu.ulima.espacioya.comun.seguridad.TokenAleatorio;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.domain.EstadoUsuario;
import pe.edu.ulima.espacioya.cuentas.domain.MotivoRechazoCambioCorreo;
import pe.edu.ulima.espacioya.cuentas.domain.TipoToken;
import pe.edu.ulima.espacioya.cuentas.domain.Token;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.dto.SolicitudCambioCorreoDTO;
import pe.edu.ulima.espacioya.cuentas.repository.TokenRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;
import pe.edu.ulima.espacioya.cuentas.service.ReautenticacionPerfil.Operacion;

@ExtendWith(MockitoExtension.class)
class CambioCorreoServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-04T15:00:00Z");
    private static final String IP = "10.20.2.1";
    private static final long ID = 7L;
    private static final long ID_TOKEN = 41L;
    private static final String ACTUAL = "ana@correo.pe";
    private static final String NUEVO = "juana.nueva@correo.pe";
    private static final String NUEVO_ENMASCARADO = "j***@correo.pe";
    private static final String CONTRASENA = "Segura123!";
    private static final String URL = "http://localhost:5173";
    private static final UsuarioAutenticado TITULAR = new UsuarioAutenticado(ID, 70L, "USUARIO");

    @Mock
    private UsuarioRepository usuarios;
    @Mock
    private TokenRepository tokens;
    @Mock
    private ReautenticacionPerfil reautenticacion;
    @Mock
    private NotificadorCorreo notificador;
    @Mock
    private AuditoriaService auditoria;

    private LimitadorIntentos limitador;
    private PlatformTransactionManager transacciones;
    private CambioCorreoService servicio;

    @BeforeEach
    void preparar() {
        var reloj = Clock.fixed(AHORA, ZoneOffset.UTC);
        limitador = new LimitadorIntentos(reloj);
        // Un estado real: confirmar marca rollback-only cuando el correo ya tiene cuenta (R33).
        transacciones = mock(PlatformTransactionManager.class);
        lenient().when(transacciones.getTransaction(any()))
                .thenAnswer(invocacion -> new SimpleTransactionStatus());
        servicio = new CambioCorreoService(usuarios, tokens, reautenticacion, notificador, limitador, auditoria,
                new TransactionTemplate(transacciones), reloj, URL + "/");
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void limpiar() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private static void confirmarTransaccion() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    private static Usuario cuenta() {
        var cuenta = Usuario.registrar(ACTUAL, "$2a$12$hash", "2026-10", AHORA.minusSeconds(3600));
        ReflectionTestUtils.setField(cuenta, "id", ID);
        return cuenta;
    }

    private static SolicitudCambioCorreoDTO solicitud(String correo) {
        return new SolicitudCambioCorreoDTO(correo, CONTRASENA);
    }

    private Usuario prepararSolicitud(boolean enUso) {
        var cuenta = cuenta();
        when(usuarios.findById(ID)).thenReturn(Optional.of(cuenta));
        when(usuarios.findParaActualizar(ID)).thenReturn(Optional.of(cuenta));
        when(usuarios.findByCorreo(NUEVO)).thenReturn(enUso ? Optional.of(otraCuenta()) : Optional.empty());
        if (!enUso) {
            when(tokens.save(any(Token.class))).thenAnswer(invocacion -> {
                Token token = invocacion.getArgument(0);
                ReflectionTestUtils.setField(token, "id", ID_TOKEN);
                return token;
            });
        }
        return cuenta;
    }

    private static Usuario otraCuenta() {
        var otra = Usuario.registrar(NUEVO, "$2a$12$otro", "2026-10", AHORA);
        ReflectionTestUtils.setField(otra, "id", 99L);
        return otra;
    }

    private List<EventoAuditoria> auditorias() {
        var captor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria, atLeastOnce()).registrar(captor.capture());
        return captor.getAllValues();
    }

    private String enlaceEnviado() {
        var enlace = ArgumentCaptor.forClass(String.class);
        verify(notificador).notificarCambioCorreo(eq(NUEVO), enlace.capture());
        return enlace.getValue();
    }

    // ── Solicitud ────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-20 R2: un ADMIN recibe 403 sin comprobar la contraseña ni leer la cuenta")
    void adminRecibe403() {
        var admin = new UsuarioAutenticado(ID, 70L, "ADMIN");

        assertThatThrownBy(() -> servicio.solicitar(admin, solicitud(NUEVO), IP))
                .isInstanceOf(AccesoDenegadoException.class);
        verifyNoInteractions(reautenticacion, usuarios, tokens, notificador, auditoria);
    }

    @Test
    @DisplayName("HU-20 R19, R20: si la reautenticación falla no se lee ni modifica nada")
    void reautenticacionFalla() {
        doThrow(new ReglaNegocioException("contrasena", ReautenticacionPerfil.MENSAJE_CONTRASENA_INCORRECTA))
                .when(reautenticacion).exigir(TITULAR, "Mala1234", Operacion.CORREO, IP);
        doThrow(new DemasiadosIntentosException()).when(reautenticacion)
                .exigir(TITULAR, CONTRASENA, Operacion.CORREO, IP);

        assertThatThrownBy(() -> servicio.solicitar(TITULAR, new SolicitudCambioCorreoDTO(NUEVO, "Mala1234"), IP))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> servicio.solicitar(TITULAR, solicitud(NUEVO), IP))
                .isInstanceOf(DemasiadosIntentosException.class);
        verifyNoInteractions(usuarios, tokens, notificador);
    }

    @Test
    @DisplayName("HU-20 R25: guarda el correo normalizado como pendiente, anula los tokens y crea uno de 30 min")
    void solicitaCambio() {
        var cuenta = prepararSolicitud(false);

        servicio.solicitar(TITULAR, solicitud("  Juana.Nueva@Correo.PE "), IP);

        assertThat(cuenta.getCorreo()).isEqualTo(ACTUAL);
        assertThat(cuenta.getCorreoPendiente()).isEqualTo(NUEVO);
        var orden = inOrder(reautenticacion, usuarios, tokens);
        orden.verify(reautenticacion).exigir(TITULAR, CONTRASENA, Operacion.CORREO, IP);
        orden.verify(usuarios).findParaActualizar(ID);
        orden.verify(tokens).anularPendientes(ID, TipoToken.CAMBIO_CORREO, AHORA);
        var guardado = ArgumentCaptor.forClass(Token.class);
        orden.verify(tokens).save(guardado.capture());
        assertThat(guardado.getValue().getTipo()).isEqualTo(TipoToken.CAMBIO_CORREO);
        assertThat(guardado.getValue().getExpiraEn()).isEqualTo(AHORA.plus(Duration.ofMinutes(30)));
        assertThat(guardado.getValue().getHashToken()).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("HU-20 R26, R27: tras el commit envía el enlace al correo nuevo y el aviso enmascarado al actual")
    void enviaCorreosTrasCommit() {
        prepararSolicitud(false);

        servicio.solicitar(TITULAR, solicitud(NUEVO), IP);

        verifyNoInteractions(notificador);
        confirmarTransaccion();
        var enlace = enlaceEnviado();
        assertThat(enlace).startsWith(URL + "/confirmar-correo#token=");
        var token = enlace.substring((URL + "/confirmar-correo#token=").length());
        assertThat(TokenAleatorio.tieneFormato(token)).isTrue();
        var guardado = ArgumentCaptor.forClass(Token.class);
        verify(tokens).save(guardado.capture());
        assertThat(guardado.getValue().getHashToken()).isEqualTo(TokenAleatorio.hash(token));
        verify(notificador).notificarAvisoCambioCorreo(ACTUAL, NUEVO_ENMASCARADO);
    }

    @Test
    @DisplayName("HU-20 R28: si el envío del enlace falla, el aviso al correo actual igual se envía y no hay error")
    void falloDelEnvioNoAfecta() {
        prepararSolicitud(false);
        doThrow(new IllegalStateException("smtp caído")).when(notificador).notificarCambioCorreo(anyString(),
                anyString());

        servicio.solicitar(TITULAR, solicitud(NUEVO), IP);
        confirmarTransaccion();

        verify(notificador).notificarAvisoCambioCorreo(ACTUAL, NUEVO_ENMASCARADO);
    }

    @Test
    @DisplayName("HU-20 R28: si la transacción se revierte no se envía ningún correo")
    void rollbackNoEnvia() {
        prepararSolicitud(false);
        doThrow(new IllegalStateException("BD caída")).when(auditoria).registrar(any());

        assertThatThrownBy(() -> servicio.solicitar(TITULAR, solicitud(NUEVO), IP))
                .isInstanceOf(IllegalStateException.class);

        // Sin commit no corre ninguna sincronización: ningún correo sale.
        verifyNoInteractions(notificador);
    }

    @Test
    @DisplayName("HU-20 R29, R49: correo de otra cuenta → mismo pendiente y aviso, sin token ni enlace, auditado")
    void correoEnUso() {
        var cuenta = prepararSolicitud(true);

        servicio.solicitar(TITULAR, solicitud(NUEVO), IP);
        confirmarTransaccion();

        assertThat(cuenta.getCorreoPendiente()).isEqualTo(NUEVO);
        verify(tokens).anularPendientes(ID, TipoToken.CAMBIO_CORREO, AHORA);
        verify(tokens, never()).save(any());
        verify(notificador, never()).notificarCambioCorreo(anyString(), anyString());
        verify(notificador).notificarAvisoCambioCorreo(ACTUAL, NUEVO_ENMASCARADO);
        assertThat(auditorias()).containsExactly(new EventoAuditoria(ID, "CORREO_CAMBIO_NO_EMITIDO", "usuario", ID, IP,
                "motivo=CORREO_EN_USO;nuevo=" + NUEVO_ENMASCARADO));
    }

    @Test
    @DisplayName("HU-20 R49: la solicitud aceptada se audita con el correo nuevo enmascarado")
    void auditaSolicitud() {
        prepararSolicitud(false);

        servicio.solicitar(TITULAR, solicitud(NUEVO), IP);

        var eventos = auditorias();
        assertThat(eventos).containsExactly(new EventoAuditoria(ID, "CORREO_CAMBIO_SOLICITADO", "token", ID_TOKEN, IP,
                "nuevo=" + NUEVO_ENMASCARADO));
        assertThat(eventos.getFirst().detalle()).doesNotContain(NUEVO).doesNotContain(CONTRASENA);
    }

    @Test
    @DisplayName("HU-20 R23: pedir el mismo correo da 400 en correo sin emitir token ni enviar correos")
    void mismoCorreo() {
        when(usuarios.findById(ID)).thenReturn(Optional.of(cuenta()));

        assertThatThrownBy(() -> servicio.solicitar(TITULAR, solicitud(" ANA@correo.pe "), IP))
                .isInstanceOfSatisfying(ReglaNegocioException.class, e -> {
                    assertThat(e.getCampo()).isEqualTo("correo");
                    assertThat(e.getMessage()).isEqualTo(CambioCorreoService.MENSAJE_MISMO_CORREO);
                });
        verifyNoInteractions(tokens, notificador, auditoria);
        verify(usuarios, never()).findParaActualizar(any());
    }

    @Test
    @DisplayName("HU-20 R24: la 4.ª solicitud en la hora da 429 sin tocar el pendiente, sin token ni correos")
    void cuartaSolicitudDa429() {
        var cuenta = prepararSolicitud(false);
        for (int i = 0; i < CambioCorreoService.MAXIMO_SOLICITUDES; i++) {
            servicio.solicitar(TITULAR, solicitud(NUEVO), IP);
        }
        ReflectionTestUtils.setField(cuenta, "correoPendiente", null);
        clearInvocations(tokens, notificador);

        assertThatThrownBy(() -> servicio.solicitar(TITULAR, solicitud(NUEVO), IP))
                .isInstanceOf(DemasiadosIntentosException.class);

        assertThat(cuenta.getCorreoPendiente()).isNull();
        verifyNoInteractions(tokens, notificador);
    }

    // ── Confirmación ─────────────────────────────────────────────────────

    private Token tokenVigente(String token) {
        var guardado = Token.cambioCorreo(ID, TokenAleatorio.hash(token), AHORA.minus(Duration.ofMinutes(10)));
        ReflectionTestUtils.setField(guardado, "id", ID_TOKEN);
        when(tokens.findByHashTokenAndTipo(TokenAleatorio.hash(token), TipoToken.CAMBIO_CORREO))
                .thenReturn(Optional.of(guardado));
        return guardado;
    }

    private Usuario cuentaConPendiente() {
        var cuenta = cuenta();
        cuenta.solicitarCambioCorreo(NUEVO);
        return cuenta;
    }

    private void rechazoAuditado(MotivoRechazoCambioCorreo motivo, Long usuarioId, Long tokenId) {
        assertThat(auditorias()).containsExactly(new EventoAuditoria(usuarioId, "CORREO_CAMBIO_RECHAZADO", "token",
                tokenId, IP, "motivo=" + motivo.name()));
    }

    private void confirmacionInvalida(String token) {
        assertThatThrownBy(() -> servicio.confirmar(token, IP))
                .isInstanceOfSatisfying(ReglaNegocioException.class, e -> {
                    assertThat(e.getCampo()).isNull();
                    assertThat(e.getMessage()).isEqualTo(CambioCorreoService.MENSAJE_ENLACE_INVALIDO);
                });
    }

    @Test
    @DisplayName("HU-20 R31, R50: un token vigente cambia el correo, deja el pendiente nulo y audita enmascarado")
    void confirmaCambio() {
        var token = TokenAleatorio.generar();
        tokenVigente(token);
        var cuenta = cuentaConPendiente();
        when(usuarios.findById(ID)).thenReturn(Optional.of(cuenta));
        when(tokens.marcarUsado(ID_TOKEN, AHORA)).thenReturn(1);
        when(usuarios.findParaActualizar(ID)).thenReturn(Optional.of(cuenta));
        when(usuarios.findByCorreo(NUEVO)).thenReturn(Optional.empty());

        servicio.confirmar(token, IP);

        assertThat(cuenta.getCorreo()).isEqualTo(NUEVO);
        assertThat(cuenta.getCorreoPendiente()).isNull();
        verify(usuarios).saveAndFlush(cuenta);
        assertThat(auditorias()).containsExactly(new EventoAuditoria(ID, "CORREO_CAMBIADO", "usuario", ID, IP,
                "anterior=a***@correo.pe;nuevo=" + NUEVO_ENMASCARADO));
    }

    @Test
    @DisplayName("HU-20 R32, R50: un token mal formado o desconocido da 400 genérico y se audita TOKEN_DESCONOCIDO")
    void tokenDesconocido() {
        confirmacionInvalida("no-es-un-token");
        verifyNoInteractions(tokens);
        rechazoAuditado(MotivoRechazoCambioCorreo.TOKEN_DESCONOCIDO, null, null);
    }

    @Test
    @DisplayName("HU-20 R32: un token que no existe con tipo CAMBIO_CORREO da 400 genérico")
    void tokenDeOtroTipo() {
        var token = TokenAleatorio.generar();
        when(tokens.findByHashTokenAndTipo(TokenAleatorio.hash(token), TipoToken.CAMBIO_CORREO))
                .thenReturn(Optional.empty());

        confirmacionInvalida(token);

        rechazoAuditado(MotivoRechazoCambioCorreo.TOKEN_DESCONOCIDO, null, null);
        verify(usuarios, never()).findParaActualizar(any());
    }

    @Test
    @DisplayName("HU-20 R32, R50: un token usado da 400 genérico y se audita TOKEN_USADO")
    void tokenUsado() {
        var token = TokenAleatorio.generar();
        ReflectionTestUtils.setField(tokenVigente(token), "usadoEn", AHORA.minusSeconds(5));

        confirmacionInvalida(token);

        rechazoAuditado(MotivoRechazoCambioCorreo.TOKEN_USADO, ID, ID_TOKEN);
    }

    @Test
    @DisplayName("HU-20 R32, R50: un token vencido da 400 genérico y se audita TOKEN_EXPIRADO")
    void tokenExpirado() {
        var token = TokenAleatorio.generar();
        ReflectionTestUtils.setField(tokenVigente(token), "expiraEn", AHORA);

        confirmacionInvalida(token);

        rechazoAuditado(MotivoRechazoCambioCorreo.TOKEN_EXPIRADO, ID, ID_TOKEN);
    }

    @Test
    @DisplayName("HU-20 R32, R50: cuenta suspendida o sin correo pendiente → 400 genérico y CUENTA_NO_HABILITADA")
    void cuentaNoHabilitada() {
        var token = TokenAleatorio.generar();
        tokenVigente(token);
        var suspendida = cuentaConPendiente();
        ReflectionTestUtils.setField(suspendida, "estado", EstadoUsuario.SUSPENDIDA);
        var sinPendiente = cuenta();
        when(usuarios.findById(ID)).thenReturn(Optional.of(suspendida), Optional.of(sinPendiente));
        when(usuarios.findParaActualizar(ID)).thenReturn(Optional.of(sinPendiente));
        when(tokens.marcarUsado(ID_TOKEN, AHORA)).thenReturn(1);

        confirmacionInvalida(token);
        // La suspendida se rechaza antes de la transacción; sin pendiente, bajo el bloqueo tras marcar el token.
        verify(tokens, never()).marcarUsado(any(), any());
        confirmacionInvalida(token);

        assertThat(auditorias()).extracting(EventoAuditoria::detalle)
                .containsExactly("motivo=CUENTA_NO_HABILITADA", "motivo=CUENTA_NO_HABILITADA");
        verify(usuarios, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("HU-20 R33, R52: confirmar bloquea la cuenta antes de marcar el token (mismo orden que solicitar)")
    void bloqueaCuentaAntesDelToken() {
        var token = TokenAleatorio.generar();
        tokenVigente(token);
        var cuenta = cuentaConPendiente();
        when(usuarios.findById(ID)).thenReturn(Optional.of(cuenta));
        when(tokens.marcarUsado(ID_TOKEN, AHORA)).thenReturn(1);
        when(usuarios.findParaActualizar(ID)).thenReturn(Optional.of(cuenta));
        when(usuarios.findByCorreo(NUEVO)).thenReturn(Optional.empty());

        servicio.confirmar(token, IP);

        var orden = inOrder(usuarios, tokens);
        orden.verify(usuarios).findParaActualizar(ID);
        orden.verify(tokens).marcarUsado(ID_TOKEN, AHORA);
        orden.verify(usuarios).findParaActualizar(ID);
    }

    @Test
    @DisplayName("HU-20 R33: si otra confirmación marcó el token primero, 400 genérico sin cambiar el correo")
    void carreraDelToken() {
        var token = TokenAleatorio.generar();
        tokenVigente(token);
        var cuenta = cuentaConPendiente();
        when(usuarios.findById(ID)).thenReturn(Optional.of(cuenta));
        when(usuarios.findParaActualizar(ID)).thenReturn(Optional.of(cuenta));
        when(tokens.marcarUsado(ID_TOKEN, AHORA)).thenReturn(0);

        confirmacionInvalida(token);

        assertThat(cuenta.getCorreo()).isEqualTo(ACTUAL);
        verify(usuarios).findParaActualizar(ID);
        verify(usuarios, never()).saveAndFlush(any());
        rechazoAuditado(MotivoRechazoCambioCorreo.TOKEN_USADO, ID, ID_TOKEN);
    }

    @Test
    @DisplayName("HU-20 R33: si el correo pendiente ya es de otra cuenta, 400 genérico sin cambiar el correo")
    void correoTomadoAlConfirmar() {
        var token = TokenAleatorio.generar();
        tokenVigente(token);
        var cuenta = cuentaConPendiente();
        when(usuarios.findById(ID)).thenReturn(Optional.of(cuenta));
        when(tokens.marcarUsado(ID_TOKEN, AHORA)).thenReturn(1);
        when(usuarios.findParaActualizar(ID)).thenReturn(Optional.of(cuenta));
        when(usuarios.findByCorreo(NUEVO)).thenReturn(Optional.of(otraCuenta()));

        confirmacionInvalida(token);

        assertThat(cuenta.getCorreo()).isEqualTo(ACTUAL);
        verify(usuarios, never()).saveAndFlush(any());
        var estado = ArgumentCaptor.forClass(TransactionStatus.class);
        verify(transacciones).commit(estado.capture());
        assertThat(estado.getValue().isRollbackOnly()).isTrue();
        rechazoAuditado(MotivoRechazoCambioCorreo.CORREO_EN_USO, ID, ID_TOKEN);
    }

    @Test
    @DisplayName("HU-20 R33: la violación de uq_usuario_correo al guardar da el mismo 400 genérico, no un 500")
    void violacionDeUnicidad() {
        var token = TokenAleatorio.generar();
        tokenVigente(token);
        var cuenta = cuentaConPendiente();
        when(usuarios.findById(ID)).thenReturn(Optional.of(cuenta));
        when(tokens.marcarUsado(ID_TOKEN, AHORA)).thenReturn(1);
        when(usuarios.findParaActualizar(ID)).thenReturn(Optional.of(cuenta));
        when(usuarios.findByCorreo(NUEVO)).thenReturn(Optional.empty());
        when(usuarios.saveAndFlush(cuenta)).thenThrow(new DataIntegrityViolationException("uq_usuario_correo"));

        confirmacionInvalida(token);

        rechazoAuditado(MotivoRechazoCambioCorreo.CORREO_EN_USO, ID, ID_TOKEN);
    }
}
