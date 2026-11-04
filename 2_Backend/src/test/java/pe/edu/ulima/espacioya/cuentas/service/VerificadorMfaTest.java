package pe.edu.ulima.espacioya.cuentas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pe.edu.ulima.espacioya.RelojControlable;
import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.cifrado.HuellaCampos;
import pe.edu.ulima.espacioya.comun.limite.LimitadorIntentos;
import pe.edu.ulima.espacioya.cuentas.domain.Base32;
import pe.edu.ulima.espacioya.cuentas.domain.CodigoRespaldo;
import pe.edu.ulima.espacioya.cuentas.domain.ContextoMfa;
import pe.edu.ulima.espacioya.cuentas.domain.FactorMfa;
import pe.edu.ulima.espacioya.cuentas.domain.Mfa;
import pe.edu.ulima.espacioya.cuentas.domain.Totp;
import pe.edu.ulima.espacioya.cuentas.repository.CodigoRespaldoRepository;

@ExtendWith(MockitoExtension.class)
class VerificadorMfaTest {

    private static final Instant AHORA = Instant.parse("2026-11-03T15:00:10Z");
    private static final String SECRETO = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
    private static final String IP = "10.17.0.1";
    private static final long ID_USUARIO = 7L;
    private static final String RESPALDO = "ABCDE-FGHIJ";

    @Mock
    private CodigoRespaldoRepository codigos;
    @Mock
    private AuditoriaService auditoria;

    private RelojControlable reloj;
    private LimitadorIntentos limitador;
    private GeneradorMfa generador;
    private VerificadorMfa verificador;

    @BeforeEach
    void preparar() {
        reloj = new RelojControlable(AHORA);
        limitador = new LimitadorIntentos(reloj);
        generador = new GeneradorMfa(new HuellaCampos(GeneradorMfaTest.LLAVE_HUELLA), codigos);
        verificador = new VerificadorMfa(limitador, codigos, generador, auditoria);
    }

    private static Mfa activo() {
        var mfa = Mfa.pendiente(ID_USUARIO, SECRETO, AHORA.minusSeconds(3600));
        mfa.activar(AHORA.minusSeconds(3500));
        return mfa;
    }

    private static String codigoActual() {
        return Totp.codigo(Base32.decodificar(SECRETO), Totp.pasoDe(AHORA));
    }

    private EventoAuditoria unicoEvento() {
        var evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        return evento.getValue();
    }

    private void fallar(int veces) {
        for (int i = 0; i < veces; i++) {
            verificador.registrarFallo(ID_USUARIO, ContextoMfa.INICIO_SESION, IP);
        }
    }

    @Test
    @DisplayName("HU-17 R14: un código TOTP aceptado devuelve TOTP y guarda su paso en ultimo_paso")
    void totpAceptadoRegistraPaso() {
        var mfa = activo();

        var factor = verificador.comprobar(mfa, codigoActual(), true, ContextoMfa.INICIO_SESION, IP, AHORA);

        assertThat(factor).contains(FactorMfa.TOTP);
        assertThat(mfa.getUltimoPaso()).isEqualTo(Totp.pasoDe(AHORA));
        verify(codigos, never()).findByUsuarioIdAndHashCodigoAndUsadoEnIsNull(anyLong(), anyString());
    }

    @Test
    @DisplayName("HU-17 R15: el mismo código TOTP enviado dos veces solo se acepta la primera")
    void totpNoSeReutiliza() {
        var mfa = activo();
        var codigo = codigoActual();

        assertThat(verificador.comprobar(mfa, codigo, false, ContextoMfa.ACTIVACION, IP, AHORA)).isPresent();
        assertThat(verificador.comprobar(mfa, codigo, false, ContextoMfa.ACTIVACION, IP, AHORA)).isEmpty();
    }

    @Test
    @DisplayName("HU-17 R11, R16, R17, R63: un respaldo en minúsculas con guion se acepta, se marca usado y se audita "
            + "con los restantes")
    void respaldoNormalizadoAceptado() {
        var guardado = CodigoRespaldo.nuevo(ID_USUARIO, generador.huella("ABCDEFGHIJ"), AHORA.minusSeconds(3500));
        when(codigos.findByUsuarioIdAndHashCodigoAndUsadoEnIsNull(ID_USUARIO, generador.huella("ABCDEFGHIJ")))
                .thenReturn(Optional.of(guardado));
        when(codigos.countByUsuarioIdAndUsadoEnIsNull(ID_USUARIO)).thenReturn(4L);

        var factor = verificador.comprobar(activo(), "abcde-fghij", true, ContextoMfa.INICIO_SESION, IP, AHORA);

        assertThat(factor).contains(FactorMfa.RESPALDO);
        assertThat(guardado.getUsadoEn()).isEqualTo(AHORA);
        verify(codigos).flush();
        assertThat(unicoEvento()).isEqualTo(new EventoAuditoria(ID_USUARIO, "MFA_CODIGO_RESPALDO_USADO", "usuario",
                ID_USUARIO, IP, "contexto=INICIO_SESION;restantes=4"));
    }

    @Test
    @DisplayName("HU-17 R17: ABCDE FGHIJ con espacio busca la misma huella")
    void respaldoConEspacio() {
        when(codigos.findByUsuarioIdAndHashCodigoAndUsadoEnIsNull(ID_USUARIO, generador.huella("ABCDEFGHIJ")))
                .thenReturn(Optional.empty());

        assertThat(verificador.comprobar(activo(), "ABCDE FGHIJ", true, ContextoMfa.DESACTIVACION, IP, AHORA))
                .isEmpty();
        verify(codigos).findByUsuarioIdAndHashCodigoAndUsadoEnIsNull(ID_USUARIO, generador.huella("ABCDEFGHIJ"));
    }

    @Test
    @DisplayName("HU-17 R63: el respaldo aceptado en una desactivación audita contexto=DESACTIVACION")
    void respaldoEnDesactivacion() {
        var guardado = CodigoRespaldo.nuevo(ID_USUARIO, generador.huella("ABCDEFGHIJ"), AHORA);
        when(codigos.findByUsuarioIdAndHashCodigoAndUsadoEnIsNull(ID_USUARIO, generador.huella("ABCDEFGHIJ")))
                .thenReturn(Optional.of(guardado));
        when(codigos.countByUsuarioIdAndUsadoEnIsNull(ID_USUARIO)).thenReturn(0L);

        verificador.comprobar(activo(), RESPALDO, true, ContextoMfa.DESACTIVACION, IP, AHORA);

        assertThat(unicoEvento().detalle()).isEqualTo("contexto=DESACTIVACION;restantes=0");
    }

    @Test
    @DisplayName("HU-17 R27: con admiteRespaldo = false un código de respaldo no se acepta ni se busca")
    void respaldoNoAdmitido() {
        assertThat(verificador.comprobar(activo(), RESPALDO, false, ContextoMfa.INICIO_SESION, IP, AHORA)).isEmpty();

        verify(codigos, never()).findByUsuarioIdAndHashCodigoAndUsadoEnIsNull(anyLong(), anyString());
    }

    @Test
    @DisplayName("HU-17 R27: con una configuración pendiente un código de respaldo no se acepta")
    void respaldoSinMfaActivo() {
        var pendiente = Mfa.pendiente(ID_USUARIO, SECRETO, AHORA);

        assertThat(verificador.comprobar(pendiente, RESPALDO, true, ContextoMfa.ACTIVACION, IP, AHORA)).isEmpty();

        verify(codigos, never()).findByUsuarioIdAndHashCodigoAndUsadoEnIsNull(anyLong(), anyString());
    }

    @Test
    @DisplayName("HU-17 R16: un respaldo ya usado (sin fila con usado_en nulo) no se acepta")
    void respaldoUsadoNoAceptado() {
        when(codigos.findByUsuarioIdAndHashCodigoAndUsadoEnIsNull(ID_USUARIO, generador.huella("ABCDEFGHIJ")))
                .thenReturn(Optional.empty());

        assertThat(verificador.comprobar(activo(), RESPALDO, true, ContextoMfa.INICIO_SESION, IP, AHORA)).isEmpty();
        verify(auditoria, never()).registrar(any());
    }

    @Test
    @DisplayName("HU-17 R17: un texto que no son 10 caracteres Base32 no se busca como respaldo")
    void respaldoConFormatoInvalido() {
        for (var codigo : new String[] {"ABCDE-FGHI1", "ABCDEFGHI", "ABCDEFGHIJK", "000000"}) {
            assertThat(verificador.comprobar(activo(), codigo, true, ContextoMfa.INICIO_SESION, IP, AHORA)).isEmpty();
        }
        verify(codigos, never()).findByUsuarioIdAndHashCodigoAndUsadoEnIsNull(anyLong(), anyString());
    }

    @Test
    @DisplayName("HU-17 R45, R64: registrarFallo cuenta el fallo y audita MFA_CODIGO_FALLIDO con el contexto")
    void registrarFalloAudita() {
        verificador.registrarFallo(ID_USUARIO, ContextoMfa.ACTIVACION, IP);

        assertThat(unicoEvento()).isEqualTo(new EventoAuditoria(ID_USUARIO, "MFA_CODIGO_FALLIDO", "usuario",
                ID_USUARIO, IP, "contexto=ACTIVACION"));
        assertThat(limitador.superaLimite("mfa|" + ID_USUARIO, 1, Duration.ofMinutes(15))).isTrue();
    }

    @Test
    @DisplayName("HU-17 R46: 9 fallos no bloquean")
    void nueveFallosNoBloquean() {
        fallar(9);

        assertThat(verificador.bloqueado(ID_USUARIO, IP)).isFalse();
    }

    @Test
    @DisplayName("HU-17 R46, R65: 10 fallos en 15 min bloquean y auditan MFA_BLOQUEADO")
    void diezFallosBloquean() {
        fallar(10);
        clearInvocations(auditoria);

        assertThat(verificador.bloqueado(ID_USUARIO, IP)).isTrue();
        assertThat(unicoEvento())
                .isEqualTo(new EventoAuditoria(ID_USUARIO, "MFA_BLOQUEADO", "usuario", ID_USUARIO, IP, null));
    }

    @Test
    @DisplayName("HU-17 R47: pasados 15 minutos desde los fallos ya no bloquean")
    void falloCaducaALos15Minutos() {
        fallar(10);
        reloj.avanzar(Duration.ofMinutes(15));

        assertThat(verificador.bloqueado(ID_USUARIO, IP)).isFalse();
    }

    @Test
    @DisplayName("HU-17 R48: limpiarFallos reinicia a cero los fallos de la cuenta")
    void limpiarFallos() {
        fallar(10);

        verificador.limpiarFallos(ID_USUARIO);

        assertThat(verificador.bloqueado(ID_USUARIO, IP)).isFalse();
        assertThat(limitador.superaLimite("mfa|" + ID_USUARIO, 1, Duration.ofMinutes(15))).isFalse();
    }

    @Test
    @DisplayName("HU-17 R66: ningún detalle de auditoría contiene el código enviado")
    void detalleSinCodigo() {
        var guardado = CodigoRespaldo.nuevo(ID_USUARIO, generador.huella("ABCDEFGHIJ"), AHORA);
        when(codigos.findByUsuarioIdAndHashCodigoAndUsadoEnIsNull(ID_USUARIO, generador.huella("ABCDEFGHIJ")))
                .thenReturn(Optional.of(guardado));
        when(codigos.countByUsuarioIdAndUsadoEnIsNull(ID_USUARIO)).thenReturn(4L);

        verificador.comprobar(activo(), RESPALDO, true, ContextoMfa.INICIO_SESION, IP, AHORA);
        verificador.registrarFallo(ID_USUARIO, ContextoMfa.INICIO_SESION, IP);

        var eventos = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria, times(2)).registrar(eventos.capture());
        assertThat(eventos.getAllValues()).allSatisfy(evento -> assertThat(String.valueOf(evento.detalle()))
                .doesNotContain("ABCDE").doesNotContain("FGHIJ").doesNotContain(SECRETO));
    }
}
