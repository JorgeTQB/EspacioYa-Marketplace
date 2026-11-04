package pe.edu.ulima.espacioya.cuentas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.cifrado.HuellaCampos;
import pe.edu.ulima.espacioya.comun.error.DemasiadosIntentosException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.error.VerificacionExpiradaException;
import pe.edu.ulima.espacioya.comun.limite.LimitadorIntentos;
import pe.edu.ulima.espacioya.comun.seguridad.TokenAleatorio;
import pe.edu.ulima.espacioya.cuentas.domain.Base32;
import pe.edu.ulima.espacioya.cuentas.domain.CodigoRespaldo;
import pe.edu.ulima.espacioya.cuentas.domain.DesafioMfa;
import pe.edu.ulima.espacioya.cuentas.domain.EstadoUsuario;
import pe.edu.ulima.espacioya.cuentas.domain.Mfa;
import pe.edu.ulima.espacioya.cuentas.domain.PasoMfa;
import pe.edu.ulima.espacioya.cuentas.domain.Sesion;
import pe.edu.ulima.espacioya.cuentas.domain.Totp;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.dto.DesafioDTO;
import pe.edu.ulima.espacioya.cuentas.dto.VerificacionMfaDTO;
import pe.edu.ulima.espacioya.cuentas.repository.CodigoRespaldoRepository;
import pe.edu.ulima.espacioya.cuentas.repository.DesafioMfaRepository;
import pe.edu.ulima.espacioya.cuentas.repository.MfaRepository;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;
import pe.edu.ulima.espacioya.cuentas.repository.SesionRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class VerificacionMfaServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-03T15:00:10Z");
    private static final String IP = "10.17.0.1";
    private static final String SECRETO = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
    private static final String DESAFIO = "DesafioDePruebaDeCuarentaYTresCaracteres_ab";
    private static final String HASH_DESAFIO = TokenAleatorio.hash(DESAFIO);
    private static final String CODIGO_MALO = "000001";
    private static final long ID_USUARIO = 7L;
    private static final long ID_DESAFIO = 55L;
    private static final long ID_SESION = 31L;

    @Mock
    private DesafioMfaRepository desafios;
    @Mock
    private UsuarioRepository usuarios;
    @Mock
    private MfaRepository mfas;
    @Mock
    private SesionRepository sesiones;
    @Mock
    private CodigoRespaldoRepository codigos;
    @Mock
    private PerfilArrendadorRepository perfiles;
    @Mock
    private AuditoriaService auditoria;

    private LimitadorIntentos limitador;
    private GeneradorMfa generador;
    private VerificacionMfaService servicio;

    @BeforeEach
    void preparar() {
        var reloj = Clock.fixed(AHORA, ZoneOffset.UTC);
        limitador = new LimitadorIntentos(reloj);
        generador = new GeneradorMfa(new HuellaCampos(GeneradorMfaTest.LLAVE_HUELLA), codigos);
        var verificador = new VerificadorMfa(limitador, codigos, generador, auditoria);
        var emisor = new EmisorSesiones(sesiones, perfiles, auditoria, reloj);
        servicio = new VerificacionMfaService(desafios, usuarios, mfas, sesiones, verificador, generador, emisor,
                auditoria, new TransactionTemplate(mock(PlatformTransactionManager.class)), reloj);
        lenient().when(sesiones.save(any(Sesion.class))).thenAnswer(invocacion -> {
            Sesion sesion = invocacion.getArgument(0);
            ReflectionTestUtils.setField(sesion, "id", ID_SESION);
            return sesion;
        });
        lenient().when(codigos.save(any(CodigoRespaldo.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    // ── Datos ────────────────────────────────────────────────────────────

    private static Usuario usuario() {
        var usuario = Usuario.registrar("ana@correo.pe", "$2a$12$hash", "2026-10", AHORA.minusSeconds(7200));
        ReflectionTestUtils.setField(usuario, "id", ID_USUARIO);
        return usuario;
    }

    private static Usuario admin() {
        var usuario = Usuario.registrarAdmin("admin@espacioya.pe", "$2a$12$hash", "2026-10", AHORA.minusSeconds(7200));
        ReflectionTestUtils.setField(usuario, "id", ID_USUARIO);
        return usuario;
    }

    private static DesafioMfa desafio(PasoMfa paso, Instant creado) {
        var desafio = DesafioMfa.crear(ID_USUARIO, HASH_DESAFIO, paso, creado);
        ReflectionTestUtils.setField(desafio, "id", ID_DESAFIO);
        return desafio;
    }

    private static Mfa activo() {
        var mfa = Mfa.pendiente(ID_USUARIO, SECRETO, AHORA.minusSeconds(3600));
        mfa.activar(AHORA.minusSeconds(3500));
        return mfa;
    }

    private static Mfa pendiente() {
        return Mfa.pendiente(ID_USUARIO, SECRETO, AHORA.minusSeconds(60));
    }

    private static String codigoActual() {
        return Totp.codigo(Base32.decodificar(SECRETO), Totp.pasoDe(AHORA));
    }

    private void prepararBloqueos(DesafioMfa desafio, Usuario cuenta, Mfa mfa) {
        when(desafios.findParaActualizar(HASH_DESAFIO)).thenReturn(Optional.of(desafio));
        when(usuarios.findParaActualizar(ID_USUARIO)).thenReturn(Optional.of(cuenta));
        when(mfas.findParaActualizar(ID_USUARIO)).thenReturn(Optional.ofNullable(mfa));
    }

    private static VerificacionMfaDTO solicitud(String codigo) {
        return new VerificacionMfaDTO(DESAFIO, codigo);
    }

    private List<EventoAuditoria> eventos() {
        var eventos = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria, atLeast(0)).registrar(eventos.capture());
        return eventos.getAllValues();
    }

    private EventoAuditoria evento(String accion) {
        return eventos().stream().filter(evento -> evento.accion().equals(accion)).findFirst().orElseThrow();
    }

    private void fallarUsuario(int veces) {
        for (int i = 0; i < veces; i++) {
            limitador.registrar("mfa|" + ID_USUARIO);
        }
    }

    // ── Paso CODIGO: éxito ───────────────────────────────────────────────

    @Test
    @DisplayName("HU-17 R10, R12: desafío CODIGO + TOTP aceptado → sesión con token, rol, aviso y codigosRespaldo nulo")
    void totpCreaSesion() {
        prepararBloqueos(desafio(PasoMfa.CODIGO, AHORA), usuario(), activo());

        var respuesta = servicio.verificar(solicitud(codigoActual()), IP);

        assertThat(respuesta.token()).hasSize(43);
        assertThat(respuesta.rol()).isEqualTo("USUARIO");
        assertThat(respuesta.aviso()).isNull();
        assertThat(respuesta.codigosRespaldo()).isNull();
        var sesion = ArgumentCaptor.forClass(Sesion.class);
        verify(sesiones).save(sesion.capture());
        assertThat(sesion.getValue().getHashToken()).isEqualTo(TokenAleatorio.hash(respuesta.token()));
        assertThat(sesion.getValue().getExpiraEn()).isEqualTo(AHORA.plus(Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("HU-17 R11, R16: desafío CODIGO + respaldo aceptado → sesión y el respaldo queda usado")
    void respaldoCreaSesion() {
        var guardado = CodigoRespaldo.nuevo(ID_USUARIO, generador.huella("ABCDEFGHIJ"), AHORA.minusSeconds(3500));
        prepararBloqueos(desafio(PasoMfa.CODIGO, AHORA), usuario(), activo());
        when(codigos.findByUsuarioIdAndHashCodigoAndUsadoEnIsNull(ID_USUARIO, generador.huella("ABCDEFGHIJ")))
                .thenReturn(Optional.of(guardado));
        when(codigos.countByUsuarioIdAndUsadoEnIsNull(ID_USUARIO)).thenReturn(4L);

        var respuesta = servicio.verificar(solicitud("abcde-fghij"), IP);

        assertThat(respuesta.token()).hasSize(43);
        assertThat(respuesta.codigosRespaldo()).isNull();
        assertThat(guardado.estaUsado()).isTrue();
        verify(sesiones).save(any(Sesion.class));
    }

    @Test
    @DisplayName("HU-17 R13: al aceptar el código el desafío queda cerrado con el reloj del sistema")
    void cierraDesafio() {
        var desafio = desafio(PasoMfa.CODIGO, AHORA);
        prepararBloqueos(desafio, usuario(), activo());

        servicio.verificar(solicitud(codigoActual()), IP);

        assertThat(desafio.getCerradoEn()).isEqualTo(AHORA);
        assertThatThrownBy(() -> servicio.verificar(solicitud(codigoActual()), IP))
                .isInstanceOf(VerificacionExpiradaException.class);
        verify(sesiones, times(1)).save(any(Sesion.class));
    }

    @Test
    @DisplayName("HU-17 R14: el TOTP aceptado guarda su paso en ultimo_paso")
    void guardaUltimoPaso() {
        var mfa = activo();
        prepararBloqueos(desafio(PasoMfa.CODIGO, AHORA), usuario(), mfa);

        servicio.verificar(solicitud(codigoActual()), IP);

        assertThat(mfa.getUltimoPaso()).isEqualTo(Totp.pasoDe(AHORA));
    }

    @Test
    @DisplayName("HU-17 R60: la sesión nacida de un TOTP audita LOGIN_EXITOSO con factor=TOTP")
    void auditaFactorTotp() {
        prepararBloqueos(desafio(PasoMfa.CODIGO, AHORA), usuario(), activo());

        servicio.verificar(solicitud(codigoActual()), IP);

        assertThat(evento("LOGIN_EXITOSO"))
                .isEqualTo(new EventoAuditoria(ID_USUARIO, "LOGIN_EXITOSO", "sesion", ID_SESION, IP, "factor=TOTP"));
    }

    @Test
    @DisplayName("HU-17 R60: la sesión nacida de un respaldo audita LOGIN_EXITOSO con factor=RESPALDO")
    void auditaFactorRespaldo() {
        prepararBloqueos(desafio(PasoMfa.CODIGO, AHORA), usuario(), activo());
        when(codigos.findByUsuarioIdAndHashCodigoAndUsadoEnIsNull(ID_USUARIO, generador.huella("ABCDEFGHIJ")))
                .thenReturn(Optional.of(CodigoRespaldo.nuevo(ID_USUARIO, generador.huella("ABCDEFGHIJ"), AHORA)));

        servicio.verificar(solicitud("ABCDE-FGHIJ"), IP);

        assertThat(evento("LOGIN_EXITOSO").detalle()).isEqualTo("factor=RESPALDO");
    }

    @Test
    @DisplayName("HU-17 R48: aceptar un código reinicia a cero los fallos MFA de la cuenta")
    void aciertoReiniciaFallos() {
        fallarUsuario(9);
        prepararBloqueos(desafio(PasoMfa.CODIGO, AHORA), usuario(), activo());

        servicio.verificar(solicitud(codigoActual()), IP);

        assertThat(limitador.superaLimite("mfa|" + ID_USUARIO, 1, Duration.ofMinutes(15))).isFalse();
    }

    // ── Desafío no vigente ───────────────────────────────────────────────

    private void esperaExpirado() {
        assertThatThrownBy(() -> servicio.verificar(solicitud(codigoActual()), IP))
                .isInstanceOf(VerificacionExpiradaException.class)
                .hasMessage("Tu verificación expiró o no es válida. Inicia sesión de nuevo.");
        verify(sesiones, never()).save(any());
        assertThat(limitador.superaLimite("mfa|" + ID_USUARIO, 1, Duration.ofMinutes(15))).isFalse();
    }

    @Test
    @DisplayName("HU-17 R18: un desafío inexistente responde verificación expirada sin contar fallo")
    void desafioInexistente() {
        when(desafios.findParaActualizar(HASH_DESAFIO)).thenReturn(Optional.empty());

        esperaExpirado();
        verify(usuarios, never()).findParaActualizar(anyLong());
    }

    @Test
    @DisplayName("HU-17 R18: un desafío de hace 5 minutos ya no es vigente")
    void desafioVencido() {
        var desafio = desafio(PasoMfa.CODIGO, AHORA.minus(Duration.ofMinutes(5)));
        prepararBloqueos(desafio, usuario(), activo());

        esperaExpirado();
        assertThat(desafio.getIntentosFallidos()).isZero();
    }

    @Test
    @DisplayName("HU-17 R18: un desafío cerrado no es vigente")
    void desafioCerrado() {
        var desafio = desafio(PasoMfa.CODIGO, AHORA);
        desafio.cerrar(AHORA);
        prepararBloqueos(desafio, usuario(), activo());

        esperaExpirado();
    }

    @Test
    @DisplayName("HU-17 R18: el desafío de una cuenta suspendida o sin correo verificado no es vigente")
    void cuentaNoHabilitada() {
        var suspendida = usuario();
        ReflectionTestUtils.setField(suspendida, "estado", EstadoUsuario.SUSPENDIDA);
        prepararBloqueos(desafio(PasoMfa.CODIGO, AHORA), suspendida, activo());
        esperaExpirado();

        var sinVerificar = usuario();
        ReflectionTestUtils.setField(sinVerificar, "correoVerificado", false);
        when(usuarios.findParaActualizar(ID_USUARIO)).thenReturn(Optional.of(sinVerificar));
        esperaExpirado();
    }

    @Test
    @DisplayName("HU-17 R18: un desafío CODIGO de una cuenta sin MFA activo no es vigente")
    void codigoSinMfaActivo() {
        prepararBloqueos(desafio(PasoMfa.CODIGO, AHORA), usuario(), pendiente());

        esperaExpirado();
    }

    @Test
    @DisplayName("HU-17 R18: un desafío CONFIGURACION de una cuenta que ya tiene MFA activo no es vigente")
    void configuracionConMfaActivo() {
        prepararBloqueos(desafio(PasoMfa.CONFIGURACION, AHORA), admin(), activo());

        esperaExpirado();
    }

    @Test
    @DisplayName("HU-17 R18: un desafío CODIGO no sirve en POST /api/sesiones/mfa/configuracion")
    void codigoEnConfiguracion() {
        prepararBloqueos(desafio(PasoMfa.CODIGO, AHORA), usuario(), activo());

        assertThatThrownBy(() -> servicio.configurar(new DesafioDTO(DESAFIO), IP))
                .isInstanceOf(VerificacionExpiradaException.class);
        verify(mfas, never()).save(any());
    }

    // ── Códigos incorrectos ──────────────────────────────────────────────

    @Test
    @DisplayName("HU-17 R19, R20, R45, R64: un código incorrecto responde 400 en el campo codigo, suma 1 al desafío "
            + "y audita MFA_CODIGO_FALLIDO")
    void codigoIncorrecto() {
        var desafio = desafio(PasoMfa.CODIGO, AHORA);
        prepararBloqueos(desafio, usuario(), activo());

        assertThatThrownBy(() -> servicio.verificar(solicitud(CODIGO_MALO), IP))
                .isInstanceOfSatisfying(ReglaNegocioException.class, ex -> {
                    assertThat(ex.getCampo()).isEqualTo("codigo");
                    assertThat(ex.getMessage()).isEqualTo("El código no es válido o ya se usó.");
                });

        assertThat(desafio.getIntentosFallidos()).isEqualTo(1);
        assertThat(desafio.getCerradoEn()).isNull();
        verify(sesiones, never()).save(any());
        assertThat(evento("MFA_CODIGO_FALLIDO")).isEqualTo(new EventoAuditoria(ID_USUARIO, "MFA_CODIGO_FALLIDO",
                "usuario", ID_USUARIO, IP, "contexto=INICIO_SESION"));
        assertThat(limitador.superaLimite("mfa|" + ID_USUARIO, 1, Duration.ofMinutes(15))).isTrue();
    }

    @Test
    @DisplayName("HU-17 R21: el 5.º código incorrecto cierra el desafío y responde verificación expirada")
    void quintoFalloCierraDesafio() {
        var desafio = desafio(PasoMfa.CODIGO, AHORA);
        ReflectionTestUtils.setField(desafio, "intentosFallidos", 4);
        prepararBloqueos(desafio, usuario(), activo());

        assertThatThrownBy(() -> servicio.verificar(solicitud(CODIGO_MALO), IP))
                .isInstanceOf(VerificacionExpiradaException.class);

        assertThat(desafio.getIntentosFallidos()).isEqualTo(5);
        assertThat(desafio.getCerradoEn()).isEqualTo(AHORA);
        verify(sesiones, never()).save(any());
    }

    @Test
    @DisplayName("HU-17 R20, DT-11: el fallo queda guardado aunque la respuesta sea un error (la transacción no "
            + "lanza la excepción)")
    void falloSeGuardaAunqueResponda400() {
        var desafio = desafio(PasoMfa.CODIGO, AHORA);
        prepararBloqueos(desafio, usuario(), activo());
        var transacciones = mock(PlatformTransactionManager.class);
        var conTransaccionReal = new VerificacionMfaService(desafios, usuarios, mfas, sesiones,
                new VerificadorMfa(limitador, codigos, generador, auditoria), generador,
                new EmisorSesiones(sesiones, perfiles, auditoria, Clock.fixed(AHORA, ZoneOffset.UTC)), auditoria,
                new TransactionTemplate(transacciones), Clock.fixed(AHORA, ZoneOffset.UTC));

        assertThatThrownBy(() -> conTransaccionReal.verificar(solicitud(CODIGO_MALO), IP))
                .isInstanceOf(ReglaNegocioException.class);

        verify(transacciones).commit(any());
        verify(transacciones, never()).rollback(any());
    }

    // ── Límite por cuenta ────────────────────────────────────────────────

    @Test
    @DisplayName("HU-17 R46, R49: con 10 fallos en 15 min responde 429 sin comprobar el código ni sumar al desafío")
    void bloqueadoNoComprueba() {
        fallarUsuario(10);
        var desafio = desafio(PasoMfa.CODIGO, AHORA);
        var mfa = activo();
        prepararBloqueos(desafio, usuario(), mfa);

        assertThatThrownBy(() -> servicio.verificar(solicitud(codigoActual()), IP))
                .isInstanceOf(DemasiadosIntentosException.class);

        assertThat(desafio.getIntentosFallidos()).isZero();
        assertThat(desafio.getCerradoEn()).isNull();
        assertThat(mfa.getUltimoPaso()).isNull();
        verify(sesiones, never()).save(any());
        assertThat(evento("MFA_BLOQUEADO").usuarioId()).isEqualTo(ID_USUARIO);
        assertThat(eventos()).extracting(EventoAuditoria::accion).doesNotContain("MFA_CODIGO_FALLIDO");
    }

    // ── Paso CONFIGURACION (admin) ───────────────────────────────────────

    @Test
    @DisplayName("HU-17 R23, R24: configurar con un desafío CONFIGURACION crea la configuración pendiente y devuelve "
            + "secreto y uri")
    void configurarCreaPendiente() {
        prepararBloqueos(desafio(PasoMfa.CONFIGURACION, AHORA), admin(), null);

        var configuracion = servicio.configurar(new DesafioDTO(DESAFIO), IP);

        var guardado = ArgumentCaptor.forClass(Mfa.class);
        verify(mfas).save(guardado.capture());
        assertThat(guardado.getValue().estaActivo()).isFalse();
        assertThat(guardado.getValue().getSecreto()).isEqualTo(configuracion.secreto()).hasSize(32);
        assertThat(configuracion.uri()).isEqualTo("otpauth://totp/EspacioYa:admin%40espacioya.pe?secret="
                + configuracion.secreto() + "&issuer=EspacioYa&algorithm=SHA1&digits=6&period=30");
    }

    @Test
    @DisplayName("HU-17 R24: configurar otra vez reemplaza el secreto de la configuración pendiente")
    void configurarReemplazaPendiente() {
        var pendiente = pendiente();
        prepararBloqueos(desafio(PasoMfa.CONFIGURACION, AHORA), admin(), pendiente);

        var configuracion = servicio.configurar(new DesafioDTO(DESAFIO), IP);

        assertThat(pendiente.getSecreto()).isEqualTo(configuracion.secreto()).isNotEqualTo(SECRETO);
        verify(mfas, never()).save(any());
    }

    @Test
    @DisplayName("HU-17 R25, R26, R36: desafío CONFIGURACION + TOTP del pendiente activa MFA y devuelve la sesión con "
            + "5 códigos")
    void configuracionActivaYDevuelveCodigos() {
        var mfa = pendiente();
        var desafio = desafio(PasoMfa.CONFIGURACION, AHORA);
        prepararBloqueos(desafio, admin(), mfa);

        var respuesta = servicio.verificar(solicitud(codigoActual()), IP);

        assertThat(mfa.estaActivo()).isTrue();
        assertThat(mfa.getActivadoEn()).isEqualTo(AHORA);
        assertThat(desafio.getCerradoEn()).isEqualTo(AHORA);
        assertThat(respuesta.rol()).isEqualTo("ADMIN");
        assertThat(respuesta.token()).hasSize(43);
        assertThat(respuesta.codigosRespaldo()).hasSize(5).doesNotHaveDuplicates()
                .allSatisfy(codigo -> assertThat(codigo).matches("^[A-Z2-7]{5}-[A-Z2-7]{5}$"));
        verify(codigos, times(5)).save(any(CodigoRespaldo.class));
    }

    @Test
    @DisplayName("HU-17 R35: al activar en el inicio de sesión revoca las sesiones previas antes de crear la nueva")
    void configuracionRevocaAntesDeEmitir() {
        prepararBloqueos(desafio(PasoMfa.CONFIGURACION, AHORA), admin(), pendiente());

        servicio.verificar(solicitud(codigoActual()), IP);

        var orden = inOrder(codigos, sesiones);
        orden.verify(codigos, times(5)).save(any(CodigoRespaldo.class));
        orden.verify(sesiones).revocarTodas(ID_USUARIO);
        orden.verify(sesiones).save(any(Sesion.class));
    }

    @Test
    @DisplayName("HU-17 R61: al activar en el inicio de sesión audita MFA_ACTIVADO con contexto=INICIO_SESION")
    void auditaMfaActivadoEnInicio() {
        prepararBloqueos(desafio(PasoMfa.CONFIGURACION, AHORA), admin(), pendiente());

        servicio.verificar(solicitud(codigoActual()), IP);

        assertThat(evento("MFA_ACTIVADO")).isEqualTo(new EventoAuditoria(ID_USUARIO, "MFA_ACTIVADO", "usuario",
                ID_USUARIO, IP, "contexto=INICIO_SESION"));
        assertThat(evento("LOGIN_EXITOSO").detalle()).isEqualTo("factor=TOTP");
    }

    @Test
    @DisplayName("HU-17 R27: desafío CONFIGURACION sin configuración pendiente → código inválido, cuenta el fallo")
    void configuracionSinPendiente() {
        var desafio = desafio(PasoMfa.CONFIGURACION, AHORA);
        prepararBloqueos(desafio, admin(), null);

        assertThatThrownBy(() -> servicio.verificar(solicitud(codigoActual()), IP))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        ex -> assertThat(ex.getCampo()).isEqualTo("codigo"));

        assertThat(desafio.getIntentosFallidos()).isEqualTo(1);
        verify(sesiones, never()).save(any());
    }

    @Test
    @DisplayName("HU-17 R27: en el paso CONFIGURACION un código de respaldo tampoco sirve y no activa MFA")
    void configuracionConRespaldo() {
        var mfa = pendiente();
        prepararBloqueos(desafio(PasoMfa.CONFIGURACION, AHORA), admin(), mfa);

        assertThatThrownBy(() -> servicio.verificar(solicitud("ABCDE-FGHIJ"), IP))
                .isInstanceOf(ReglaNegocioException.class);

        assertThat(mfa.estaActivo()).isFalse();
        verify(codigos, never()).save(any());
        verify(sesiones, never()).revocarTodas(anyLong());
    }
}
