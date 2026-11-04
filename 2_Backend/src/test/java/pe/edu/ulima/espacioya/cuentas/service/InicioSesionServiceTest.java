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
import pe.edu.ulima.espacioya.comun.seguridad.IpsPermitidasAdmin;
import pe.edu.ulima.espacioya.comun.seguridad.TokenAleatorio;
import pe.edu.ulima.espacioya.cuentas.domain.DesafioMfa;
import pe.edu.ulima.espacioya.cuentas.domain.EstadoIdentidad;
import pe.edu.ulima.espacioya.cuentas.domain.EstadoUsuario;
import pe.edu.ulima.espacioya.cuentas.domain.PasoMfa;
import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.Rol;
import pe.edu.ulima.espacioya.cuentas.domain.Sesion;
import pe.edu.ulima.espacioya.cuentas.domain.TipoDocumento;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.dto.DesafioMfaDTO;
import pe.edu.ulima.espacioya.cuentas.dto.InicioSesionDTO;
import pe.edu.ulima.espacioya.cuentas.dto.SesionIniciadaDTO;
import pe.edu.ulima.espacioya.cuentas.repository.DesafioMfaRepository;
import pe.edu.ulima.espacioya.cuentas.repository.MfaRepository;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;
import pe.edu.ulima.espacioya.cuentas.repository.SesionRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class InicioSesionServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-28T15:00:00Z");
    private static final String IP = "10.0.0.1";
    private static final String IP_ADMIN = "127.0.0.1";
    private static final IpsPermitidasAdmin IPS_ADMIN = new IpsPermitidasAdmin(IP_ADMIN + ",::1");
    private static final String CORREO = "ana@correo.pe";
    private static final String CONTRASENA = "Segura123!";
    private static final String HASH = "$2a$12$hashdeprueba";
    private static final String SENUELO = "$2a$12$senuelo";
    private static final String CLAVE = "login|" + CORREO;
    private static final long ID_USUARIO = 7L;
    private static final long ID_SESION = 31L;
    private static final long ID_DESAFIO = 55L;

    @Mock
    private UsuarioRepository usuarios;
    @Mock
    private PerfilArrendadorRepository perfiles;
    @Mock
    private SesionRepository sesiones;
    @Mock
    private MfaRepository mfas;
    @Mock
    private DesafioMfaRepository desafios;
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
        servicio = servicio(limitador, IPS_ADMIN);
    }

    private InicioSesionService servicio(LimitadorIntentos limitadorUsado, IpsPermitidasAdmin ips) {
        var reloj = Clock.fixed(AHORA, ZoneOffset.UTC);
        var emisor = new EmisorSesiones(sesiones, perfiles, auditoria, reloj);
        return new InicioSesionService(usuarios, mfas, desafios, emisor, encoder, limitadorUsado, auditoria,
                new TransactionTemplate(mock(PlatformTransactionManager.class)), reloj, ips);
    }

    private SesionIniciadaDTO iniciarSesion(String correo, String ip) {
        return (SesionIniciadaDTO) servicio.iniciar(solicitud(correo, CONTRASENA), ip);
    }

    private DesafioMfaDTO iniciarConDesafio(String ip) {
        return (DesafioMfaDTO) servicio.iniciar(solicitud(CORREO, CONTRASENA), ip);
    }

    private void prepararDesafio() {
        when(desafios.save(any(DesafioMfa.class))).thenAnswer(invocacion -> {
            DesafioMfa desafio = invocacion.getArgument(0);
            ReflectionTestUtils.setField(desafio, "id", ID_DESAFIO);
            return desafio;
        });
    }

    private static Usuario admin() {
        var usuario = usuario();
        ReflectionTestUtils.setField(usuario, "rol", Rol.ADMIN);
        return usuario;
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

        var respuesta = iniciarSesion("  ANA@Correo.PE ", IP);

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

        var respuesta = iniciarSesion(CORREO, IP);

        assertThat(respuesta.rol()).isEqualTo("ARRENDADOR");
        assertThat(respuesta.aviso()).isEqualTo("Para publicar tus espacios, primero debes verificar tu identidad.");
    }

    @Test
    @DisplayName("HU-03 R5: login de ARRENDADOR con identidad distinta de NO_INICIADA devuelve aviso nulo")
    void arrendadorVerificadoSinAviso() {
        prepararLoginCorrecto(arrendador());
        when(perfiles.findById(ID_USUARIO)).thenReturn(Optional.of(perfil(EstadoIdentidad.VERIFICADA)));

        var respuesta = iniciarSesion(CORREO, IP);

        assertThat(respuesta.aviso()).isNull();
    }

    @Test
    @DisplayName("HU-03 R30, R34: login correcto audita LOGIN_EXITOSO con la sesión, la IP y sin detalle")
    void auditaLoginExitoso() {
        prepararLoginCorrecto(usuario());

        var respuesta = iniciarSesion(CORREO, IP);

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

    // HU-06 reemplaza HU-03 R13: el admin ya no se rechaza por su rol, sino por la IP desde la que llega.
    @Test
    @DisplayName("HU-06 R2, R3, R4: cuenta ADMIN con contraseña correcta desde una IP no permitida lanza 401, "
            + "cuenta el fallo y audita IP_NO_PERMITIDA")
    void adminNoPermitido() {
        var usuario = usuario();
        ReflectionTestUtils.setField(usuario, "rol", Rol.ADMIN);
        when(usuarios.findByCorreo(CORREO)).thenReturn(Optional.of(usuario));
        when(encoder.matches(CONTRASENA, HASH)).thenReturn(true);

        esperaRechazo("IP_NO_PERMITIDA", ID_USUARIO);
    }

    // HU-17 reemplaza HU-06 R1: el admin sin MFA recibe un desafío de configuración en vez de la sesión.
    @Test
    @DisplayName("HU-06 R1, HU-17 R2, R4: cuenta ADMIN sin MFA desde una IP permitida recibe un desafío "
            + "CONFIGURACION y ninguna sesión")
    void adminDesdeIpPermitida() {
        when(usuarios.findByCorreo(CORREO)).thenReturn(Optional.of(admin()));
        when(encoder.matches(CONTRASENA, HASH)).thenReturn(true);
        prepararDesafio();

        var respuesta = iniciarConDesafio(IP_ADMIN);

        assertThat(respuesta.paso()).isEqualTo(PasoMfa.CONFIGURACION);
        assertThat(respuesta.desafio()).hasSize(43);
        verify(sesiones, never()).save(any());
        assertThat(unicoEvento().accion()).isEqualTo("LOGIN_MFA_PENDIENTE");
    }

    @Test
    @DisplayName("HU-06 R2, R3: un ADMIN con contraseña incorrecta audita CONTRASENA_INCORRECTA aunque la IP no "
            + "esté permitida")
    void adminContrasenaIncorrectaAntesQueIp() {
        var usuario = usuario();
        ReflectionTestUtils.setField(usuario, "rol", Rol.ADMIN);
        when(usuarios.findByCorreo(CORREO)).thenReturn(Optional.of(usuario));
        when(encoder.matches(CONTRASENA, HASH)).thenReturn(false);

        esperaRechazo("CONTRASENA_INCORRECTA", ID_USUARIO);
    }

    @Test
    @DisplayName("HU-06 R5: con la lista de IPs vacía un ADMIN no inicia sesión ni desde 127.0.0.1")
    void adminConListaVacia() {
        var usuario = usuario();
        ReflectionTestUtils.setField(usuario, "rol", Rol.ADMIN);
        when(usuarios.findByCorreo(CORREO)).thenReturn(Optional.of(usuario));
        when(encoder.matches(CONTRASENA, HASH)).thenReturn(true);
        var sinIps = servicio(limitador, new IpsPermitidasAdmin(""));

        assertThatThrownBy(() -> sinIps.iniciar(solicitud(CORREO, CONTRASENA), IP_ADMIN))
                .isInstanceOf(CredencialesInvalidasException.class);

        verify(sesiones, never()).save(any());
        assertThat(unicoEvento().detalle()).isEqualTo("motivo=IP_NO_PERMITIDA");
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
        var conEspia = servicio(limitadorEspia, IPS_ADMIN);

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

        var respuesta = iniciarSesion(CORREO, IP);

        assertThat(respuesta.rol()).isEqualTo("USUARIO");
        verify(encoder).matches(CONTRASENA, HASH);
        assertThat(unicoEvento().accion()).isEqualTo("LOGIN_EXITOSO");
    }

    // ── MFA (HU-17) ──────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-17 R1, R4: credenciales aceptadas de una cuenta con MFA activo responden un desafío CODIGO "
            + "sin crear sesión")
    void conMfaActivoDevuelveDesafioCodigo() {
        when(usuarios.findByCorreo(CORREO)).thenReturn(Optional.of(usuario()));
        when(encoder.matches(CONTRASENA, HASH)).thenReturn(true);
        when(mfas.existsByUsuarioIdAndActivoTrue(ID_USUARIO)).thenReturn(true);
        prepararDesafio();

        var respuesta = iniciarConDesafio(IP);

        assertThat(respuesta.paso()).isEqualTo(PasoMfa.CODIGO);
        assertThat(respuesta.desafio()).matches("^[A-Za-z0-9_-]{43}$");
        verify(sesiones, never()).save(any());
    }

    @Test
    @DisplayName("HU-17 R2: un admin con configuración pendiente (sin MFA activo) recibe un desafío CONFIGURACION")
    void adminConPendienteDevuelveConfiguracion() {
        when(usuarios.findByCorreo(CORREO)).thenReturn(Optional.of(admin()));
        when(encoder.matches(CONTRASENA, HASH)).thenReturn(true);
        when(mfas.existsByUsuarioIdAndActivoTrue(ID_USUARIO)).thenReturn(false);
        prepararDesafio();

        assertThat(iniciarConDesafio(IP_ADMIN).paso()).isEqualTo(PasoMfa.CONFIGURACION);
    }

    @Test
    @DisplayName("HU-17 R1: un admin con MFA activo recibe un desafío CODIGO")
    void adminConMfaActivoDevuelveCodigo() {
        when(usuarios.findByCorreo(CORREO)).thenReturn(Optional.of(admin()));
        when(encoder.matches(CONTRASENA, HASH)).thenReturn(true);
        when(mfas.existsByUsuarioIdAndActivoTrue(ID_USUARIO)).thenReturn(true);
        prepararDesafio();

        assertThat(iniciarConDesafio(IP_ADMIN).paso()).isEqualTo(PasoMfa.CODIGO);
    }

    @Test
    @DisplayName("HU-17 R3: un usuario final sin MFA activo (con o sin configuración pendiente) recibe la sesión "
            + "de HU-03 sin desafío")
    void usuarioFinalSinMfaRecibeSesion() {
        prepararLoginCorrecto(usuario());
        when(mfas.existsByUsuarioIdAndActivoTrue(ID_USUARIO)).thenReturn(false);

        var respuesta = iniciarSesion(CORREO, IP);

        assertThat(respuesta.token()).hasSize(43);
        assertThat(respuesta.rol()).isEqualTo("USUARIO");
        verify(sesiones).save(any(Sesion.class));
        verify(desafios, never()).save(any());
    }

    @Test
    @DisplayName("HU-17 R5, R6: el desafío se guarda con el usuario, el paso, +5 min, 0 fallos, abierto y solo el "
            + "SHA-256 del token")
    void guardaDesafioConHash() {
        when(usuarios.findByCorreo(CORREO)).thenReturn(Optional.of(usuario()));
        when(encoder.matches(CONTRASENA, HASH)).thenReturn(true);
        when(mfas.existsByUsuarioIdAndActivoTrue(ID_USUARIO)).thenReturn(true);
        prepararDesafio();

        var respuesta = iniciarConDesafio(IP);

        var guardado = ArgumentCaptor.forClass(DesafioMfa.class);
        verify(desafios).save(guardado.capture());
        var desafio = guardado.getValue();
        assertThat(desafio.getUsuarioId()).isEqualTo(ID_USUARIO);
        assertThat(desafio.getPaso()).isEqualTo(PasoMfa.CODIGO);
        assertThat(desafio.getCreadoEn()).isEqualTo(AHORA);
        assertThat(desafio.getExpiraEn()).isEqualTo(AHORA.plus(Duration.ofMinutes(5)));
        assertThat(desafio.getIntentosFallidos()).isZero();
        assertThat(desafio.getCerradoEn()).isNull();
        assertThat(desafio.getHashToken()).isEqualTo(TokenAleatorio.hash(respuesta.desafio())).hasSize(64)
                .doesNotContain(respuesta.desafio());
    }

    @Test
    @DisplayName("HU-17 R7: dos inicios de sesión de la misma cuenta reciben desafíos distintos de 43 caracteres")
    void desafiosDistintos() {
        when(usuarios.findByCorreo(CORREO)).thenReturn(Optional.of(usuario()));
        when(encoder.matches(CONTRASENA, HASH)).thenReturn(true);
        when(mfas.existsByUsuarioIdAndActivoTrue(ID_USUARIO)).thenReturn(true);
        prepararDesafio();

        var primero = iniciarConDesafio(IP).desafio();
        var segundo = iniciarConDesafio(IP).desafio();

        assertThat(primero).hasSize(43);
        assertThat(segundo).hasSize(43).isNotEqualTo(primero);
    }

    @Test
    @DisplayName("HU-17 R8: con MFA activo, una contraseña incorrecta responde el 401 de HU-03 R9 sin crear desafío")
    void contrasenaIncorrectaConMfaSinDesafio() {
        when(usuarios.findByCorreo(CORREO)).thenReturn(Optional.of(usuario()));
        when(encoder.matches(CONTRASENA, HASH)).thenReturn(false);

        esperaRechazo("CONTRASENA_INCORRECTA", ID_USUARIO);
        verify(desafios, never()).save(any());
        verify(mfas, never()).existsByUsuarioIdAndActivoTrue(any());
    }

    @Test
    @DisplayName("HU-17 R8: con MFA activo, una cuenta suspendida responde el 401 de HU-03 sin crear desafío")
    void cuentaSuspendidaConMfaSinDesafio() {
        var usuario = usuario();
        ReflectionTestUtils.setField(usuario, "estado", EstadoUsuario.SUSPENDIDA);
        when(usuarios.findByCorreo(CORREO)).thenReturn(Optional.of(usuario));
        when(encoder.matches(CONTRASENA, HASH)).thenReturn(true);

        esperaRechazo("CUENTA_NO_ACTIVA", ID_USUARIO);
        verify(desafios, never()).save(any());
    }

    @Test
    @DisplayName("HU-17 R9: un admin con MFA activo desde una IP no permitida recibe el 401 de HU-06 R2 sin desafío")
    void adminConMfaDesdeIpNoPermitida() {
        when(usuarios.findByCorreo(CORREO)).thenReturn(Optional.of(admin()));
        when(encoder.matches(CONTRASENA, HASH)).thenReturn(true);

        esperaRechazo("IP_NO_PERMITIDA", ID_USUARIO);
        verify(desafios, never()).save(any());
    }

    @Test
    @DisplayName("HU-17 R59, R66: el desafío audita LOGIN_MFA_PENDIENTE con desafio_mfa, su id, la IP y el paso, "
            + "sin el token")
    void auditaLoginMfaPendiente() {
        when(usuarios.findByCorreo(CORREO)).thenReturn(Optional.of(admin()));
        when(encoder.matches(CONTRASENA, HASH)).thenReturn(true);
        prepararDesafio();

        var respuesta = iniciarConDesafio(IP_ADMIN);

        var evento = unicoEvento();
        assertThat(evento.accion()).isEqualTo("LOGIN_MFA_PENDIENTE");
        assertThat(evento.usuarioId()).isEqualTo(ID_USUARIO);
        assertThat(evento.entidad()).isEqualTo("desafio_mfa");
        assertThat(evento.entidadId()).isEqualTo(ID_DESAFIO);
        assertThat(evento.ip()).isEqualTo(IP_ADMIN);
        assertThat(evento.detalle()).isEqualTo("paso=CONFIGURACION");
        assertThat(evento.toString()).doesNotContain(respuesta.desafio());
    }
}
