package pe.edu.ulima.espacioya.cuentas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.DemasiadosIntentosException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.limite.LimitadorIntentos;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.service.ReautenticacionPerfil.Operacion;

@ExtendWith(MockitoExtension.class)
class ReautenticacionPerfilTest {

    private static final Instant AHORA = Instant.parse("2026-11-04T15:00:00Z");
    private static final String IP = "10.20.0.1";
    private static final String CONTRASENA = "Segura123!";
    private static final UsuarioAutenticado TITULAR = new UsuarioAutenticado(7L, 70L, "ARRENDADOR");
    private static final String CLAVE = "perfil|7";

    @Mock
    private CuentaService cuentas;
    @Mock
    private AuditoriaService auditoria;

    private LimitadorIntentos limitador;
    private ReautenticacionPerfil reautenticacion;

    @BeforeEach
    void preparar() {
        limitador = new LimitadorIntentos(Clock.fixed(AHORA, ZoneOffset.UTC));
        reautenticacion = new ReautenticacionPerfil(cuentas, limitador, auditoria);
    }

    private void fallar(int veces) {
        for (int i = 0; i < veces; i++) {
            limitador.registrar(CLAVE);
        }
    }

    @Test
    @DisplayName("HU-20 R19: una contraseña incorrecta da 400 en contrasena, cuenta el fallo y lo audita con la "
            + "operación")
    void contrasenaIncorrecta() {
        when(cuentas.confirmarContrasena(TITULAR, "Mala123!")).thenReturn(false);

        assertThatThrownBy(() -> reautenticacion.exigir(TITULAR, "Mala123!", Operacion.FACTURACION, IP))
                .isInstanceOfSatisfying(ReglaNegocioException.class, e -> {
                    assertThat(e.getCampo()).isEqualTo("contrasena");
                    assertThat(e.getMessage()).isEqualTo(ReautenticacionPerfil.MENSAJE_CONTRASENA_INCORRECTA);
                });

        var evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        assertThat(evento.getValue()).isEqualTo(new EventoAuditoria(7L, "PERFIL_CONTRASENA_FALLIDA", "usuario", 7L, IP,
                "operacion=FACTURACION"));
        assertThat(evento.getValue().detalle()).doesNotContain("Mala123!");
        fallar(3);
        assertThat(limitador.superaLimite(CLAVE, 4, ReautenticacionPerfil.VENTANA_FALLOS)).isTrue();
    }

    @Test
    @DisplayName("HU-20 R20: con 5 fallos en la ventana responde 429 sin comprobar la contraseña")
    void cincoFallosDan429() {
        fallar(ReautenticacionPerfil.MAXIMO_FALLOS);

        assertThatThrownBy(() -> reautenticacion.exigir(TITULAR, CONTRASENA, Operacion.CORREO, IP))
                .isInstanceOf(DemasiadosIntentosException.class);
        verifyNoInteractions(cuentas, auditoria);
    }

    @Test
    @DisplayName("HU-20 R20: con 4 fallos todavía se comprueba la contraseña")
    void cuatroFallosTodaviaComprueba() {
        fallar(ReautenticacionPerfil.MAXIMO_FALLOS - 1);
        when(cuentas.confirmarContrasena(TITULAR, CONTRASENA)).thenReturn(true);

        reautenticacion.exigir(TITULAR, CONTRASENA, Operacion.CORREO, IP);

        verify(cuentas).confirmarContrasena(TITULAR, CONTRASENA);
    }

    @Test
    @DisplayName("HU-20 R21: una contraseña correcta reinicia el contador de perfil|<id>")
    void aciertoLimpia() {
        fallar(4);
        when(cuentas.confirmarContrasena(TITULAR, CONTRASENA)).thenReturn(true);

        reautenticacion.exigir(TITULAR, CONTRASENA, Operacion.CORREO, IP);

        fallar(4);
        assertThat(limitador.superaLimite(CLAVE, 5, ReautenticacionPerfil.VENTANA_FALLOS)).isFalse();
        verify(auditoria, never()).registrar(any());
    }

    @Test
    @DisplayName("HU-20 R18: sin contraseña da 400 en contrasena sin comprobarla ni contar un fallo")
    void sinContrasena() {
        assertThatThrownBy(() -> reautenticacion.exigir(TITULAR, " ", Operacion.CORREO, IP))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.getCampo()).isEqualTo("contrasena"));

        verifyNoInteractions(cuentas, auditoria);
        fallar(4);
        assertThat(limitador.superaLimite(CLAVE, 5, ReautenticacionPerfil.VENTANA_FALLOS)).isFalse();
    }

    @Test
    @DisplayName("HU-20 R42: registrarFallo cuenta un fallo en perfil|<id> sin auditar")
    void registrarFalloCuenta() {
        reautenticacion.registrarFallo(TITULAR);
        fallar(4);

        assertThat(limitador.superaLimite(CLAVE, 5, ReautenticacionPerfil.VENTANA_FALLOS)).isTrue();
        verifyNoInteractions(auditoria);
    }
}
