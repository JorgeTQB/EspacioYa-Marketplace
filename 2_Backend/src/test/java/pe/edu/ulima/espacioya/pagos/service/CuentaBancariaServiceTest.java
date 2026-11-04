package pe.edu.ulima.espacioya.pagos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.RelojControlable;
import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.DemasiadosIntentosException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.limite.LimitadorIntentos;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.dto.HabilitacionArrendadorDTO;
import pe.edu.ulima.espacioya.cuentas.service.AvisoCambioCuentaBancaria;
import pe.edu.ulima.espacioya.cuentas.service.CuentaService;
import pe.edu.ulima.espacioya.cuentas.service.NotificadorCorreo;
import pe.edu.ulima.espacioya.pagos.domain.CuentaBancaria;
import pe.edu.ulima.espacioya.pagos.domain.EstadoCuentaBancaria;
import pe.edu.ulima.espacioya.pagos.dto.GuardarCuentaBancariaDTO;
import pe.edu.ulima.espacioya.pagos.repository.CuentaBancariaRepository;

@ExtendWith(MockitoExtension.class)
class CuentaBancariaServiceTest {

    private static final Instant INICIO = Instant.parse("2026-11-03T15:00:00Z");
    private static final long ID = 7L;
    private static final long CUENTA_ID = 41L;
    private static final UsuarioAutenticado ARRENDADOR = new UsuarioAutenticado(ID, 31L, "ARRENDADOR");
    private static final String IP = "10.0.0.9";
    private static final String CORREO = "ana@correo.pe";
    private static final String CLAVE = "cuenta-bancaria|" + ID;
    private static final String NUMERO_A = "00112233445566771111";
    private static final String NUMERO_B = "99887766554433222222";
    private static final String CONTRASENA = "Segura123!";

    @Mock
    private CuentaBancariaRepository cuentas;
    @Mock
    private CuentaService cuentasUsuario;
    @Mock
    private NotificadorCorreo notificador;
    @Mock
    private AuditoriaService auditoria;

    private RelojControlable reloj;
    private LimitadorIntentos limitador;
    private CuentaBancariaService servicio;

    @BeforeEach
    void preparar() {
        reloj = new RelojControlable(INICIO);
        limitador = new LimitadorIntentos(reloj);
        servicio = new CuentaBancariaService(cuentas, cuentasUsuario, notificador, limitador, auditoria,
                new TransactionTemplate(mock(PlatformTransactionManager.class)), reloj);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void limpiar() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private static GuardarCuentaBancariaDTO solicitud(String banco, String numero) {
        return new GuardarCuentaBancariaDTO(banco, numero, true, CONTRASENA);
    }

    private void identidad(boolean verificada) {
        when(cuentasUsuario.habilitacionArrendador(ARRENDADOR))
                .thenReturn(new HabilitacionArrendadorDTO(true, verificada));
    }

    private void contrasenaCorrecta() {
        when(cuentasUsuario.confirmarContrasena(ARRENDADOR, CONTRASENA)).thenReturn(true);
    }

    private void sinCuentaVigente() {
        when(cuentas.findParaActualizar(ID)).thenReturn(Optional.empty());
        when(cuentasUsuario.razonSocialDe(ID)).thenReturn("Canchas SAC");
        when(cuentas.saveAndFlush(any(CuentaBancaria.class))).thenAnswer(invocacion -> {
            CuentaBancaria cuenta = invocacion.getArgument(0);
            ReflectionTestUtils.setField(cuenta, "id", CUENTA_ID);
            return cuenta;
        });
    }

    private CuentaBancaria conCuentaVigente(String banco, String numero) {
        var cuenta = CuentaBancaria.registrar(ID, banco, numero, "Canchas SAC", INICIO.minus(Duration.ofDays(30)));
        ReflectionTestUtils.setField(cuenta, "id", CUENTA_ID);
        when(cuentas.findParaActualizar(ID)).thenReturn(Optional.of(cuenta));
        return cuenta;
    }

    private static void confirmarTransaccion() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    private EventoAuditoria eventoAuditado() {
        var captor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(captor.capture());
        return captor.getValue();
    }

    // ── Rol (R2) ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-18 R2: consultar con otro rol lanza 403 sin leer nada")
    void consultarOtroRol() {
        var usuario = new UsuarioAutenticado(9L, 1L, "USUARIO");

        assertThatThrownBy(() -> servicio.consultar(usuario)).isInstanceOf(AccesoDenegadoException.class);

        verifyNoInteractions(cuentas, cuentasUsuario);
    }

    @Test
    @DisplayName("HU-18 R2: guardar con otro rol lanza 403 sin leer, escribir ni comprobar la contraseña")
    void guardarOtroRol() {
        var admin = new UsuarioAutenticado(1L, 1L, "ADMIN");

        assertThatThrownBy(() -> servicio.guardar(admin, solicitud("Banco", NUMERO_A), IP))
                .isInstanceOf(AccesoDenegadoException.class);

        verifyNoInteractions(cuentas, cuentasUsuario, notificador, auditoria);
    }

    // ── Consulta (R5, R18, R19) ──────────────────────────────────────────

    @Test
    @DisplayName("HU-18 R5: sin cuenta, consultar responde la identidad y la cuenta nula")
    void consultarSinCuenta() {
        identidad(true);
        when(cuentas.findByArrendadorId(ID)).thenReturn(Optional.empty());

        var respuesta = servicio.consultar(ARRENDADOR);

        assertThat(respuesta.identidadVerificada()).isTrue();
        assertThat(respuesta.cuenta()).isNull();
    }

    @Test
    @DisplayName("HU-18 R5, R19: con cuenta, consultar devuelve banco, número enmascarado y titular, nunca el número")
    void consultarConCuenta() {
        identidad(false);
        var cuenta = CuentaBancaria.registrar(ID, "Banco Andino", NUMERO_A, "Canchas SAC", INICIO);
        when(cuentas.findByArrendadorId(ID)).thenReturn(Optional.of(cuenta));

        var respuesta = servicio.consultar(ARRENDADOR);

        assertThat(respuesta.identidadVerificada()).isFalse();
        assertThat(respuesta.cuenta().banco()).isEqualTo("Banco Andino");
        assertThat(respuesta.cuenta().numeroEnmascarado()).isEqualTo("****1111");
        assertThat(respuesta.cuenta().titular()).isEqualTo("Canchas SAC");
        assertThat(respuesta.toString()).doesNotContain(NUMERO_A);
    }

    @Test
    @DisplayName("HU-18 R18: consultar responde RETENIDA con retenidaHasta antes de 72 h y DISPONIBLE después, sin escribir")
    void consultarRetenidaYDisponible() {
        identidad(true);
        var cuenta = CuentaBancaria.registrar(ID, "Banco Andino", NUMERO_A, "Canchas SAC", INICIO);
        cuenta.cambiar("Otro Banco", NUMERO_B, "Canchas SAC", INICIO);
        when(cuentas.findByArrendadorId(ID)).thenReturn(Optional.of(cuenta));
        var limite = INICIO.plus(Duration.ofHours(72));

        reloj.avanzar(Duration.ofHours(71));
        var antes = servicio.consultar(ARRENDADOR).cuenta();
        reloj.avanzar(Duration.ofHours(1));
        var despues = servicio.consultar(ARRENDADOR).cuenta();

        assertThat(antes.estado()).isEqualTo(EstadoCuentaBancaria.RETENIDA);
        assertThat(antes.retenidaHasta()).isEqualTo(limite);
        assertThat(despues.estado()).isEqualTo(EstadoCuentaBancaria.DISPONIBLE);
        assertThat(despues.retenidaHasta()).isNull();
        verify(cuentas, never()).save(any());
        verify(cuentas, never()).saveAndFlush(any());
        verifyNoInteractions(auditoria, notificador);
    }

    // ── Identidad (R3) ───────────────────────────────────────────────────

    @Test
    @DisplayName("HU-18 R3: sin identidad verificada guardar responde 400 sin comprobar la contraseña ni contar fallos")
    void sinIdentidadVerificada() {
        identidad(false);

        assertThatThrownBy(() -> servicio.guardar(ARRENDADOR, solicitud("Banco", NUMERO_A), IP))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Para registrar una cuenta bancaria, primero debes verificar tu identidad.")
                .satisfies(e -> assertThat(((ReglaNegocioException) e).getCampo()).isNull());

        verify(cuentasUsuario, never()).confirmarContrasena(any(), any());
        verifyNoInteractions(cuentas, notificador, auditoria);
        assertThat(limitador.superaLimite(CLAVE, 1, Duration.ofMinutes(15))).isFalse();
    }

    // ── Contraseña (R7, R8, R9) ──────────────────────────────────────────

    @Test
    @DisplayName("HU-18 R7: una contraseña incorrecta responde 400 en el campo contrasena, cuenta el fallo, audita y no escribe")
    void contrasenaIncorrecta() {
        identidad(true);
        when(cuentasUsuario.confirmarContrasena(ARRENDADOR, CONTRASENA)).thenReturn(false);

        assertThatThrownBy(() -> servicio.guardar(ARRENDADOR, solicitud("Banco", NUMERO_A), IP))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("La contraseña no es correcta.")
                .satisfies(e -> assertThat(((ReglaNegocioException) e).getCampo()).isEqualTo("contrasena"));

        var evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("CUENTA_BANCARIA_CONTRASENA_FALLIDA");
        assertThat(evento.usuarioId()).isEqualTo(ID);
        assertThat(evento.entidad()).isEqualTo("usuario");
        assertThat(evento.entidadId()).isEqualTo(ID);
        assertThat(evento.ip()).isEqualTo(IP);
        assertThat(String.valueOf(evento.detalle())).doesNotContain(CONTRASENA).doesNotContain(NUMERO_A);
        verifyNoInteractions(cuentas, notificador);
        verify(cuentasUsuario, never()).bloquearCuenta(any());
    }

    @Test
    @DisplayName("HU-18 R8: tras 5 contraseñas incorrectas responde 429 sin comprobar la contraseña ni escribir")
    void cincoFallosBloquean() {
        identidad(true);
        when(cuentasUsuario.confirmarContrasena(ARRENDADOR, CONTRASENA)).thenReturn(false);
        for (int i = 0; i < CuentaBancariaService.MAXIMO_FALLOS; i++) {
            assertThatThrownBy(() -> servicio.guardar(ARRENDADOR, solicitud("Banco", NUMERO_A), IP))
                    .isInstanceOf(ReglaNegocioException.class);
        }

        assertThatThrownBy(() -> servicio.guardar(ARRENDADOR, solicitud("Banco", NUMERO_A), IP))
                .isInstanceOf(DemasiadosIntentosException.class);

        verify(cuentasUsuario, org.mockito.Mockito.times(5)).confirmarContrasena(ARRENDADOR, CONTRASENA);
        verifyNoInteractions(cuentas, notificador);
    }

    @Test
    @DisplayName("HU-18 R8: vencida la ventana de 15 minutos el arrendador puede intentar de nuevo")
    void ventanaVence() {
        identidad(true);
        when(cuentasUsuario.confirmarContrasena(ARRENDADOR, CONTRASENA)).thenReturn(false, false, false, false,
                false, true);
        for (int i = 0; i < CuentaBancariaService.MAXIMO_FALLOS; i++) {
            assertThatThrownBy(() -> servicio.guardar(ARRENDADOR, solicitud("Banco", NUMERO_A), IP))
                    .isInstanceOf(ReglaNegocioException.class);
        }
        reloj.avanzar(CuentaBancariaService.VENTANA_FALLOS.plusSeconds(1));
        sinCuentaVigente();

        var respuesta = servicio.guardar(ARRENDADOR, solicitud("Banco", NUMERO_A), IP);

        assertThat(respuesta.numeroEnmascarado()).isEqualTo("****1111");
    }

    @Test
    @DisplayName("HU-18 R9: una contraseña correcta reinicia el contador de fallos")
    void aciertoReiniciaElContador() {
        identidad(true);
        when(cuentasUsuario.confirmarContrasena(ARRENDADOR, CONTRASENA)).thenReturn(false, false, false, false,
                true);
        sinCuentaVigente();
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> servicio.guardar(ARRENDADOR, solicitud("Banco", NUMERO_A), IP))
                    .isInstanceOf(ReglaNegocioException.class);
        }

        servicio.guardar(ARRENDADOR, solicitud("Banco", NUMERO_A), IP);

        assertThat(limitador.superaLimite(CLAVE, 1, CuentaBancariaService.VENTANA_FALLOS)).isFalse();
    }

    // ── Registro (R11, R17, R23) ─────────────────────────────────────────

    @Test
    @DisplayName("HU-18 R11, R17: sin cuenta vigente guarda con la razón social como titular, sin retención, DISPONIBLE")
    void registra() {
        identidad(true);
        contrasenaCorrecta();
        sinCuentaVigente();

        var respuesta = servicio.guardar(ARRENDADOR, solicitud(" Banco Andino ", NUMERO_A), IP);

        var captor = ArgumentCaptor.forClass(CuentaBancaria.class);
        verify(cuentas).saveAndFlush(captor.capture());
        var guardada = captor.getValue();
        assertThat(guardada.getArrendadorId()).isEqualTo(ID);
        assertThat(guardada.getBanco()).isEqualTo("Banco Andino");
        assertThat(guardada.getTitular()).isEqualTo("Canchas SAC");
        assertThat(guardada.getRetenerHasta()).isNull();
        assertThat(respuesta.estado()).isEqualTo(EstadoCuentaBancaria.DISPONIBLE);
        assertThat(respuesta.retenidaHasta()).isNull();
        assertThat(respuesta.numeroEnmascarado()).isEqualTo("****1111");
        assertThat(respuesta.titular()).isEqualTo("Canchas SAC");
        assertThat(respuesta.toString()).doesNotContain(NUMERO_A);
        verify(cuentasUsuario).bloquearCuenta(ID);
    }

    @Test
    @DisplayName("HU-18 R23, R22: el registro audita CUENTA_BANCARIA_REGISTRADA con banco y número enmascarado")
    void auditaElRegistro() {
        identidad(true);
        contrasenaCorrecta();
        sinCuentaVigente();

        servicio.guardar(ARRENDADOR, solicitud("Banco Andino", NUMERO_A), IP);

        var evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("CUENTA_BANCARIA_REGISTRADA");
        assertThat(evento.usuarioId()).isEqualTo(ID);
        assertThat(evento.entidad()).isEqualTo("cuenta_bancaria");
        assertThat(evento.entidadId()).isEqualTo(CUENTA_ID);
        assertThat(evento.ip()).isEqualTo(IP);
        assertThat(evento.detalle()).isEqualTo("banco=Banco Andino;cuenta=****1111")
                .doesNotContain(NUMERO_A).doesNotContain(CONTRASENA);
    }

    @Test
    @DisplayName("HU-18 R14: el registro de la primera cuenta no envía correo")
    void registroSinCorreo() {
        identidad(true);
        contrasenaCorrecta();
        sinCuentaVigente();

        servicio.guardar(ARRENDADOR, solicitud("Banco Andino", NUMERO_A), IP);
        confirmarTransaccion();

        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        verifyNoInteractions(notificador);
        verify(cuentasUsuario, never()).correoDe(any());
    }

    // ── Cambio (R12, R13, R24) ───────────────────────────────────────────

    @Test
    @DisplayName("HU-18 R12: con cuenta vigente distinta reemplaza los datos, retiene 72 h y responde RETENIDA")
    void cambia() {
        identidad(true);
        contrasenaCorrecta();
        var cuenta = conCuentaVigente("Banco Andino", NUMERO_A);
        when(cuentasUsuario.correoDe(ID)).thenReturn(CORREO);

        var respuesta = servicio.guardar(ARRENDADOR, solicitud("Otro Banco", NUMERO_B), IP);

        assertThat(cuenta.getBanco()).isEqualTo("Otro Banco");
        assertThat(cuenta.enmascarado()).isEqualTo("****2222");
        assertThat(cuenta.getTitular()).isEqualTo("Canchas SAC");
        assertThat(cuenta.getRetenerHasta()).isEqualTo(INICIO.plus(Duration.ofHours(72)));
        assertThat(respuesta.estado()).isEqualTo(EstadoCuentaBancaria.RETENIDA);
        assertThat(respuesta.retenidaHasta()).isEqualTo(INICIO.plus(Duration.ofHours(72)));
        assertThat(respuesta.numeroEnmascarado()).isEqualTo("****2222");
        verify(cuentas).saveAndFlush(cuenta);
        verify(cuentasUsuario, never()).razonSocialDe(any());
    }

    @Test
    @DisplayName("HU-18 R24, R22: el cambio audita CUENTA_BANCARIA_CAMBIADA con lo anterior y lo nuevo enmascarado")
    void auditaElCambio() {
        identidad(true);
        contrasenaCorrecta();
        conCuentaVigente("Banco Andino", NUMERO_A);
        when(cuentasUsuario.correoDe(ID)).thenReturn(CORREO);

        servicio.guardar(ARRENDADOR, solicitud("Otro Banco", NUMERO_B), IP);

        var evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("CUENTA_BANCARIA_CAMBIADA");
        assertThat(evento.usuarioId()).isEqualTo(ID);
        assertThat(evento.entidad()).isEqualTo("cuenta_bancaria");
        assertThat(evento.entidadId()).isEqualTo(CUENTA_ID);
        assertThat(evento.ip()).isEqualTo(IP);
        assertThat(evento.detalle()).isEqualTo("anterior=Banco Andino/****1111;nueva=Otro Banco/****2222;"
                        + "retenerHasta=" + INICIO.plus(Duration.ofHours(72)))
                .doesNotContain(NUMERO_A).doesNotContain(NUMERO_B).doesNotContain(CONTRASENA);
    }

    @Test
    @DisplayName("HU-18 R13: tras el commit envía el aviso con banco, 4 últimos dígitos y fecha de retención; no antes")
    void avisaTrasElCommit() {
        identidad(true);
        contrasenaCorrecta();
        conCuentaVigente("Banco Andino", NUMERO_A);
        when(cuentasUsuario.correoDe(ID)).thenReturn(CORREO);

        servicio.guardar(ARRENDADOR, solicitud("Otro Banco", NUMERO_B), IP);

        verify(notificador, never()).notificarCambioCuentaBancaria(any(), any());
        confirmarTransaccion();
        verify(notificador).notificarCambioCuentaBancaria(CORREO, new AvisoCambioCuentaBancaria("Otro Banco",
                "****2222", INICIO.plus(Duration.ofHours(72))));
    }

    @Test
    @DisplayName("HU-18 R15: si el envío del aviso falla, el cambio sigue confirmado y no se propaga el fallo")
    void falloDelEnvioNoRevierte() {
        identidad(true);
        contrasenaCorrecta();
        conCuentaVigente("Banco Andino", NUMERO_A);
        when(cuentasUsuario.correoDe(ID)).thenReturn(CORREO);
        doThrow(new IllegalStateException("el proveedor de correo falló")).when(notificador)
                .notificarCambioCuentaBancaria(any(), any());

        var respuesta = servicio.guardar(ARRENDADOR, solicitud("Otro Banco", NUMERO_B), IP);
        confirmarTransaccion();

        assertThat(respuesta.estado()).isEqualTo(EstadoCuentaBancaria.RETENIDA);
        verify(notificador).notificarCambioCuentaBancaria(any(), any());
    }

    @Test
    @DisplayName("HU-18 R15: si la transacción se revierte no se envía el correo (no queda nada para el commit)")
    void sinCorreoConRollback() {
        identidad(true);
        contrasenaCorrecta();
        conCuentaVigente("Banco Andino", NUMERO_A);
        doThrow(new IllegalStateException("falla al auditar")).when(auditoria).registrar(any());

        assertThatThrownBy(() -> servicio.guardar(ARRENDADOR, solicitud("Otro Banco", NUMERO_B), IP))
                .isInstanceOf(IllegalStateException.class);
        confirmarTransaccion();

        verify(notificador, never()).notificarCambioCuentaBancaria(any(), any());
    }

    // ── Misma cuenta (R16) ───────────────────────────────────────────────

    @Test
    @DisplayName("HU-18 R16: la misma cuenta responde 400 ya registrada, sin retención, auditoría ni correo")
    void mismaCuenta() {
        identidad(true);
        contrasenaCorrecta();
        var cuenta = conCuentaVigente("Banco Andino", NUMERO_A);

        assertThatThrownBy(() -> servicio.guardar(ARRENDADOR, solicitud("banco andino", NUMERO_A), IP))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Esa cuenta ya es la que tienes registrada.");

        assertThat(cuenta.getRetenerHasta()).isNull();
        verify(cuentas, never()).saveAndFlush(any());
        verifyNoInteractions(auditoria, notificador);
        confirmarTransaccion();
        verify(notificador, never()).notificarCambioCuentaBancaria(any(), any());
    }

    // ── Concurrencia y orden (R25, DT-7) ─────────────────────────────────

    @Test
    @DisplayName("HU-18 R25: la escritura bloquea primero la cuenta de usuario y luego la fila de la cuenta bancaria")
    void bloqueaEnOrden() {
        identidad(true);
        contrasenaCorrecta();
        sinCuentaVigente();

        servicio.guardar(ARRENDADOR, solicitud("Banco Andino", NUMERO_A), IP);

        var orden = org.mockito.Mockito.inOrder(cuentasUsuario, cuentas);
        orden.verify(cuentasUsuario).confirmarContrasena(ARRENDADOR, CONTRASENA);
        orden.verify(cuentasUsuario).bloquearCuenta(ID);
        orden.verify(cuentas).findParaActualizar(ID);
    }

    @Test
    @DisplayName("HU-18 R3, R25: si la identidad deja de estar verificada bajo el bloqueo, rechaza sin escribir")
    void identidadRevisadaBajoElBloqueo() {
        when(cuentasUsuario.habilitacionArrendador(ARRENDADOR)).thenReturn(new HabilitacionArrendadorDTO(true, true),
                new HabilitacionArrendadorDTO(true, false));
        contrasenaCorrecta();

        assertThatThrownBy(() -> servicio.guardar(ARRENDADOR, solicitud("Banco Andino", NUMERO_A), IP))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage(CuentaBancariaService.MENSAJE_IDENTIDAD_NO_VERIFICADA);

        verify(cuentas, never()).findParaActualizar(any());
        verify(cuentas, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("HU-18 R4: la cuenta consultada y escrita sale siempre del id de la sesión")
    void usaElIdDeLaSesion() {
        identidad(true);
        contrasenaCorrecta();
        sinCuentaVigente();

        servicio.guardar(ARRENDADOR, solicitud("Banco Andino", NUMERO_A), IP);

        verify(cuentas).findParaActualizar(ID);
        verify(cuentasUsuario).razonSocialDe(ID);
    }
}
