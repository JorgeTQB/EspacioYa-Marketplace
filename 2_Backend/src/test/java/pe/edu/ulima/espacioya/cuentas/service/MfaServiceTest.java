package pe.edu.ulima.espacioya.cuentas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
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
import pe.edu.ulima.espacioya.comun.cifrado.HuellaCampos;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.DemasiadosIntentosException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.limite.LimitadorIntentos;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.domain.Base32;
import pe.edu.ulima.espacioya.cuentas.domain.CodigoRespaldo;
import pe.edu.ulima.espacioya.cuentas.domain.Mfa;
import pe.edu.ulima.espacioya.cuentas.domain.Totp;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.dto.CodigoMfaDTO;
import pe.edu.ulima.espacioya.cuentas.dto.DesactivacionMfaDTO;
import pe.edu.ulima.espacioya.cuentas.repository.CodigoRespaldoRepository;
import pe.edu.ulima.espacioya.cuentas.repository.MfaRepository;
import pe.edu.ulima.espacioya.cuentas.repository.SesionRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class MfaServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-03T15:00:10Z");
    private static final String IP = "10.17.0.1";
    private static final String SECRETO = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
    private static final String HASH = "$2a$12$hash";
    private static final String CONTRASENA = "Segura123!";
    private static final String CODIGO_MALO = "000001";
    private static final long ID_USUARIO = 7L;
    private static final long ID_SESION = 31L;
    private static final UsuarioAutenticado USUARIO = new UsuarioAutenticado(ID_USUARIO, ID_SESION, "USUARIO");
    private static final UsuarioAutenticado ADMIN = new UsuarioAutenticado(ID_USUARIO, ID_SESION, "ADMIN");

    @Mock
    private UsuarioRepository usuarios;
    @Mock
    private MfaRepository mfas;
    @Mock
    private CodigoRespaldoRepository codigos;
    @Mock
    private SesionRepository sesiones;
    @Mock
    private PasswordEncoder encoder;
    @Mock
    private AuditoriaService auditoria;

    private LimitadorIntentos limitador;
    private GeneradorMfa generador;
    private MfaService servicio;

    @BeforeEach
    void preparar() {
        var reloj = Clock.fixed(AHORA, ZoneOffset.UTC);
        limitador = new LimitadorIntentos(reloj);
        generador = new GeneradorMfa(new HuellaCampos(GeneradorMfaTest.LLAVE_HUELLA), codigos);
        var verificador = new VerificadorMfa(limitador, codigos, generador, auditoria);
        servicio = new MfaService(usuarios, mfas, codigos, sesiones, verificador, generador, encoder, auditoria,
                new TransactionTemplate(mock(PlatformTransactionManager.class)), reloj);
        lenient().when(codigos.save(any(CodigoRespaldo.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    private static Usuario cuenta() {
        var usuario = Usuario.registrar("ana@correo.pe", HASH, "2026-10", AHORA.minusSeconds(7200));
        ReflectionTestUtils.setField(usuario, "id", ID_USUARIO);
        return usuario;
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

    private void prepararBloqueos(Mfa mfa) {
        when(usuarios.findParaActualizar(ID_USUARIO)).thenReturn(Optional.of(cuenta()));
        when(mfas.findParaActualizar(ID_USUARIO)).thenReturn(Optional.ofNullable(mfa));
    }

    private List<EventoAuditoria> eventos() {
        var eventos = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria, atLeast(0)).registrar(eventos.capture());
        return eventos.getAllValues();
    }

    private boolean hayFallos() {
        return limitador.superaLimite("mfa|" + ID_USUARIO, 1, Duration.ofMinutes(15));
    }

    private void fallarUsuario(int veces) {
        for (int i = 0; i < veces; i++) {
            limitador.registrar("mfa|" + ID_USUARIO);
        }
    }

    // ── Estado ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-17 R39: sin MFA el estado es inactivo, 0 códigos restantes y no desactivable")
    void estadoSinMfa() {
        when(mfas.findById(ID_USUARIO)).thenReturn(Optional.empty());

        var estado = servicio.estado(USUARIO);

        assertThat(estado.activo()).isFalse();
        assertThat(estado.codigosRespaldoRestantes()).isZero();
        assertThat(estado.desactivable()).isFalse();
        verify(codigos, never()).countByUsuarioIdAndUsadoEnIsNull(anyLong());
    }

    @Test
    @DisplayName("HU-17 R39: con una configuración pendiente el estado sigue inactivo")
    void estadoPendiente() {
        when(mfas.findById(ID_USUARIO)).thenReturn(Optional.of(pendiente()));

        assertThat(servicio.estado(USUARIO).activo()).isFalse();
    }

    @Test
    @DisplayName("HU-17 R39: con MFA activo un usuario final ve los restantes y puede desactivarlo")
    void estadoActivoUsuarioFinal() {
        when(mfas.findById(ID_USUARIO)).thenReturn(Optional.of(activo()));
        when(codigos.countByUsuarioIdAndUsadoEnIsNull(ID_USUARIO)).thenReturn(3L);

        var estado = servicio.estado(USUARIO);

        assertThat(estado.activo()).isTrue();
        assertThat(estado.codigosRespaldoRestantes()).isEqualTo(3);
        assertThat(estado.desactivable()).isTrue();
    }

    @Test
    @DisplayName("HU-17 R39: con MFA activo un admin no puede desactivarlo (desactivable = false)")
    void estadoActivoAdmin() {
        when(mfas.findById(ID_USUARIO)).thenReturn(Optional.of(activo()));
        when(codigos.countByUsuarioIdAndUsadoEnIsNull(ID_USUARIO)).thenReturn(5L);

        var estado = servicio.estado(ADMIN);

        assertThat(estado.activo()).isTrue();
        assertThat(estado.desactivable()).isFalse();
    }

    // ── Configuración ────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-17 R24, R29: sin MFA, iniciarConfiguracion guarda un pendiente y devuelve secreto y uri")
    void iniciarConfiguracionCrea() {
        prepararBloqueos(null);

        var configuracion = servicio.iniciarConfiguracion(USUARIO);

        var guardado = ArgumentCaptor.forClass(Mfa.class);
        verify(mfas).save(guardado.capture());
        assertThat(guardado.getValue().getUsuarioId()).isEqualTo(ID_USUARIO);
        assertThat(guardado.getValue().estaActivo()).isFalse();
        assertThat(guardado.getValue().getSecreto()).isEqualTo(configuracion.secreto()).matches("^[A-Z2-7]{32}$");
        assertThat(configuracion.uri()).startsWith("otpauth://totp/EspacioYa:ana%40correo.pe?secret="
                + configuracion.secreto());
    }

    @Test
    @DisplayName("HU-17 R24: con una configuración pendiente, iniciarConfiguracion reemplaza su secreto")
    void iniciarConfiguracionReemplaza() {
        var pendiente = pendiente();
        prepararBloqueos(pendiente);

        var configuracion = servicio.iniciarConfiguracion(USUARIO);

        assertThat(pendiente.getSecreto()).isEqualTo(configuracion.secreto()).isNotEqualTo(SECRETO);
        verify(mfas, never()).save(any());
    }

    @Test
    @DisplayName("HU-17 R30: con MFA activo, iniciarConfiguracion responde 400 MFA ya activo sin cambiar el secreto")
    void iniciarConfiguracionYaActivo() {
        var mfa = activo();
        prepararBloqueos(mfa);

        assertThatThrownBy(() -> servicio.iniciarConfiguracion(USUARIO))
                .isInstanceOfSatisfying(ReglaNegocioException.class, ex -> {
                    assertThat(ex.getCampo()).isNull();
                    assertThat(ex.getMessage()).isEqualTo("La verificación en dos pasos ya está activa en tu cuenta.");
                });
        assertThat(mfa.getSecreto()).isEqualTo(SECRETO);
        verify(mfas, never()).save(any());
    }

    // ── Activación ───────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-17 R31, R32, R36: un TOTP aceptado del pendiente activa MFA y devuelve 5 códigos")
    void activarConTotp() {
        var mfa = pendiente();
        prepararBloqueos(mfa);

        var respuesta = servicio.activar(USUARIO, new CodigoMfaDTO(codigoActual()), IP);

        assertThat(mfa.estaActivo()).isTrue();
        assertThat(mfa.getActivadoEn()).isEqualTo(AHORA);
        assertThat(respuesta.codigos()).hasSize(5).doesNotHaveDuplicates()
                .allSatisfy(codigo -> assertThat(codigo).matches("^[A-Z2-7]{5}-[A-Z2-7]{5}$"));
        verify(codigos, times(5)).save(any(CodigoRespaldo.class));
    }

    @Test
    @DisplayName("HU-17 R35: al activar revoca las demás sesiones de la cuenta, no la de la solicitud, al final")
    void activarRevocaOtras() {
        prepararBloqueos(pendiente());

        servicio.activar(USUARIO, new CodigoMfaDTO(codigoActual()), IP);

        var orden = inOrder(codigos, sesiones);
        orden.verify(codigos, times(5)).save(any(CodigoRespaldo.class));
        orden.verify(sesiones).revocarOtras(ID_USUARIO, ID_SESION);
        verify(sesiones, never()).revocarTodas(anyLong());
    }

    @Test
    @DisplayName("HU-17 R48, R61: activar audita MFA_ACTIVADO contexto=CUENTA y reinicia los fallos")
    void activarAuditaYLimpia() {
        fallarUsuario(3);
        prepararBloqueos(pendiente());

        servicio.activar(USUARIO, new CodigoMfaDTO(codigoActual()), IP);

        assertThat(eventos()).containsExactly(
                new EventoAuditoria(ID_USUARIO, "MFA_ACTIVADO", "usuario", ID_USUARIO, IP, "contexto=CUENTA"));
        assertThat(hayFallos()).isFalse();
    }

    @Test
    @DisplayName("HU-17 R33, R49: sin configuración pendiente (sin fila) activar responde 400 sin contar fallo")
    void activarSinFila() {
        prepararBloqueos(null);

        assertThatThrownBy(() -> servicio.activar(USUARIO, new CodigoMfaDTO(codigoActual()), IP))
                .isInstanceOfSatisfying(ReglaNegocioException.class, ex -> {
                    assertThat(ex.getCampo()).isNull();
                    assertThat(ex.getMessage()).isEqualTo("Primero genera tu clave de configuración.");
                });
        assertThat(hayFallos()).isFalse();
        verify(codigos, never()).save(any());
    }

    @Test
    @DisplayName("HU-17 R33: con MFA ya activo, activar responde el 400 de sin configuración pendiente")
    void activarYaActivo() {
        prepararBloqueos(activo());

        assertThatThrownBy(() -> servicio.activar(USUARIO, new CodigoMfaDTO(codigoActual()), IP))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Primero genera tu clave de configuración.");
        assertThat(hayFallos()).isFalse();
    }

    @Test
    @DisplayName("HU-17 R34, R45, R64: un código incorrecto responde 400 en el campo codigo, no activa y cuenta el "
            + "fallo con contexto=ACTIVACION")
    void activarCodigoIncorrecto() {
        var mfa = pendiente();
        prepararBloqueos(mfa);

        assertThatThrownBy(() -> servicio.activar(USUARIO, new CodigoMfaDTO(CODIGO_MALO), IP))
                .isInstanceOfSatisfying(ReglaNegocioException.class, ex -> {
                    assertThat(ex.getCampo()).isEqualTo("codigo");
                    assertThat(ex.getMessage()).isEqualTo("El código no es válido o ya se usó.");
                });
        assertThat(mfa.estaActivo()).isFalse();
        assertThat(hayFallos()).isTrue();
        assertThat(eventos()).containsExactly(new EventoAuditoria(ID_USUARIO, "MFA_CODIGO_FALLIDO", "usuario",
                ID_USUARIO, IP, "contexto=ACTIVACION"));
        verify(sesiones, never()).revocarOtras(anyLong(), anyLong());
    }

    @Test
    @DisplayName("HU-17 R46: con 10 fallos en 15 min activar responde 429 sin comprobar el código")
    void activarBloqueado() {
        fallarUsuario(10);
        var mfa = pendiente();
        prepararBloqueos(mfa);

        assertThatThrownBy(() -> servicio.activar(USUARIO, new CodigoMfaDTO(codigoActual()), IP))
                .isInstanceOf(DemasiadosIntentosException.class);
        assertThat(mfa.estaActivo()).isFalse();
        assertThat(mfa.getUltimoPaso()).isNull();
        assertThat(eventos()).extracting(EventoAuditoria::accion).containsExactly("MFA_BLOQUEADO");
    }

    // ── Desactivación ────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-17 R40, R62: contraseña y TOTP correctos borran el MFA y sus códigos y auditan MFA_DESACTIVADO")
    void desactivarConTotp() {
        var mfa = activo();
        prepararBloqueos(mfa);
        when(encoder.matches(CONTRASENA, HASH)).thenReturn(true);

        servicio.desactivar(USUARIO, new DesactivacionMfaDTO(CONTRASENA, codigoActual()), IP);

        verify(codigos).borrarDeUsuario(ID_USUARIO);
        verify(mfas).delete(mfa);
        assertThat(eventos()).containsExactly(
                new EventoAuditoria(ID_USUARIO, "MFA_DESACTIVADO", "usuario", ID_USUARIO, IP, null));
    }

    @Test
    @DisplayName("HU-17 R40, R48: contraseña y código de respaldo correctos también desactivan y reinician los fallos")
    void desactivarConRespaldo() {
        fallarUsuario(2);
        var mfa = activo();
        prepararBloqueos(mfa);
        when(encoder.matches(CONTRASENA, HASH)).thenReturn(true);
        when(codigos.findByUsuarioIdAndHashCodigoAndUsadoEnIsNull(ID_USUARIO, generador.huella("ABCDEFGHIJ")))
                .thenReturn(Optional.of(CodigoRespaldo.nuevo(ID_USUARIO, generador.huella("ABCDEFGHIJ"), AHORA)));

        servicio.desactivar(USUARIO, new DesactivacionMfaDTO(CONTRASENA, "abcde-fghij"), IP);

        verify(mfas).delete(mfa);
        assertThat(hayFallos()).isFalse();
        assertThat(eventos()).extracting(EventoAuditoria::accion)
                .containsExactly("MFA_CODIGO_RESPALDO_USADO", "MFA_DESACTIVADO");
    }

    @Test
    @DisplayName("HU-17 R41, R45: contraseña incorrecta con código correcto o contraseña correcta con código "
            + "incorrecto → el mismo 400, sin desactivar")
    void desactivarRechazadaMismoMensaje() {
        prepararBloqueos(activo());
        when(encoder.matches(CONTRASENA, HASH)).thenReturn(false);

        assertThatThrownBy(() -> servicio.desactivar(USUARIO, new DesactivacionMfaDTO(CONTRASENA, codigoActual()), IP))
                .isInstanceOfSatisfying(ReglaNegocioException.class, ex -> {
                    assertThat(ex.getCampo()).isNull();
                    assertThat(ex.getMessage()).isEqualTo("La contraseña o el código no son correctos.");
                });

        prepararBloqueos(activo());
        when(encoder.matches(CONTRASENA, HASH)).thenReturn(true);
        assertThatThrownBy(() -> servicio.desactivar(USUARIO, new DesactivacionMfaDTO(CONTRASENA, CODIGO_MALO), IP))
                .isInstanceOfSatisfying(ReglaNegocioException.class, ex -> {
                    assertThat(ex.getCampo()).isNull();
                    assertThat(ex.getMessage()).isEqualTo("La contraseña o el código no son correctos.");
                });

        verify(mfas, never()).delete(any());
        verify(codigos, never()).borrarDeUsuario(anyLong());
        assertThat(eventos()).extracting(EventoAuditoria::detalle)
                .containsExactly("contexto=DESACTIVACION", "contexto=DESACTIVACION");
    }

    @Test
    @DisplayName("HU-17 R41: con la contraseña incorrecta igual se comprueba el código (no se revela cuál falló)")
    void desactivarCompruebaAmbos() {
        var mfa = activo();
        prepararBloqueos(mfa);
        when(encoder.matches(CONTRASENA, HASH)).thenReturn(false);

        assertThatThrownBy(() -> servicio.desactivar(USUARIO, new DesactivacionMfaDTO(CONTRASENA, codigoActual()), IP))
                .isInstanceOf(ReglaNegocioException.class);

        verify(encoder).matches(CONTRASENA, HASH);
        assertThat(mfa.getUltimoPaso()).isEqualTo(Totp.pasoDe(AHORA));
    }

    @Test
    @DisplayName("HU-17 R42: un admin que desactiva recibe 403 sin comprobar la contraseña ni el código")
    void desactivarAdmin() {
        assertThatThrownBy(() -> servicio.desactivar(ADMIN, new DesactivacionMfaDTO(CONTRASENA, codigoActual()), IP))
                .isInstanceOf(AccesoDenegadoException.class);

        verifyNoInteractions(encoder, mfas, codigos, usuarios, auditoria);
    }

    @Test
    @DisplayName("HU-17 R43, R49: sin MFA activo desactivar responde 400 MFA no activo sin contar fallo")
    void desactivarSinMfa() {
        prepararBloqueos(pendiente());

        assertThatThrownBy(() -> servicio.desactivar(USUARIO, new DesactivacionMfaDTO(CONTRASENA, codigoActual()), IP))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("La verificación en dos pasos no está activa en tu cuenta.");
        assertThat(hayFallos()).isFalse();
        verify(encoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("HU-17 R46: con 10 fallos desactivar responde 429 sin comprobar la contraseña ni el código")
    void desactivarBloqueado() {
        fallarUsuario(10);
        prepararBloqueos(activo());

        assertThatThrownBy(() -> servicio.desactivar(USUARIO, new DesactivacionMfaDTO(CONTRASENA, codigoActual()), IP))
                .isInstanceOf(DemasiadosIntentosException.class);
        verify(encoder, never()).matches(anyString(), anyString());
        verify(mfas, never()).delete(any());
    }
}
