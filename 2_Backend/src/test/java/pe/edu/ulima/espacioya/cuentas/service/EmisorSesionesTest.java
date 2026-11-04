package pe.edu.ulima.espacioya.cuentas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import org.springframework.test.util.ReflectionTestUtils;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.seguridad.TokenAleatorio;
import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.Sesion;
import pe.edu.ulima.espacioya.cuentas.domain.TipoDocumento;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;
import pe.edu.ulima.espacioya.cuentas.repository.SesionRepository;

@ExtendWith(MockitoExtension.class)
class EmisorSesionesTest {

    private static final Instant AHORA = Instant.parse("2026-11-03T15:00:00Z");
    private static final String IP = "10.17.0.1";
    private static final long ID_USUARIO = 7L;
    private static final long ID_SESION = 31L;

    @Mock
    private SesionRepository sesiones;
    @Mock
    private PerfilArrendadorRepository perfiles;
    @Mock
    private AuditoriaService auditoria;

    private EmisorSesiones emisor;

    @BeforeEach
    void preparar() {
        emisor = new EmisorSesiones(sesiones, perfiles, auditoria, Clock.fixed(AHORA, ZoneOffset.UTC));
        when(sesiones.save(any(Sesion.class))).thenAnswer(invocacion -> {
            Sesion sesion = invocacion.getArgument(0);
            ReflectionTestUtils.setField(sesion, "id", ID_SESION);
            return sesion;
        });
    }

    private static Usuario usuario() {
        var usuario = Usuario.registrar("ana@correo.pe", "$2a$12$hash", "2026-10", AHORA.minusSeconds(3600));
        ReflectionTestUtils.setField(usuario, "id", ID_USUARIO);
        return usuario;
    }

    @Test
    @DisplayName("HU-17 R12: emitir guarda la sesión de 30 min con el SHA-256 del token devuelto")
    void guardaSesionConHash() {
        var respuesta = emisor.emitir(usuario(), IP, null);

        var sesion = ArgumentCaptor.forClass(Sesion.class);
        verify(sesiones).save(sesion.capture());
        assertThat(respuesta.token()).hasSize(43);
        assertThat(respuesta.rol()).isEqualTo("USUARIO");
        assertThat(respuesta.aviso()).isNull();
        assertThat(sesion.getValue().getUsuarioId()).isEqualTo(ID_USUARIO);
        assertThat(sesion.getValue().getHashToken()).isEqualTo(TokenAleatorio.hash(respuesta.token()));
        assertThat(sesion.getValue().getCreadaEn()).isEqualTo(AHORA);
        assertThat(sesion.getValue().getExpiraEn()).isEqualTo(AHORA.plus(Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("HU-17 R60: emitir audita LOGIN_EXITOSO con la sesión, la IP y el detalle recibido, sin el token")
    void auditaConDetalle() {
        var respuesta = emisor.emitir(usuario(), IP, "factor=TOTP");

        var evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        assertThat(evento.getValue())
                .isEqualTo(new EventoAuditoria(ID_USUARIO, "LOGIN_EXITOSO", "sesion", ID_SESION, IP, "factor=TOTP"));
        assertThat(evento.getValue().toString()).doesNotContain(respuesta.token());
    }

    @Test
    @DisplayName("HU-17 R10: el arrendador recibe el aviso de su identidad como en HU-03 R4")
    void avisoDelArrendador() {
        var arrendador = Usuario.registrarArrendador("ana@correo.pe", "$2a$12$hash", "Ana", "987654321", "2026-10",
                AHORA.minusSeconds(3600));
        ReflectionTestUtils.setField(arrendador, "id", ID_USUARIO);
        when(perfiles.findById(ID_USUARIO)).thenReturn(Optional.of(PerfilArrendador.crear(ID_USUARIO,
                TipoDocumento.RUC, "20100070970", "b".repeat(64), "Canchas SAC", "Av. Prado 1")));

        var respuesta = emisor.emitir(arrendador, IP, "factor=RESPALDO");

        assertThat(respuesta.rol()).isEqualTo("ARRENDADOR");
        assertThat(respuesta.aviso()).isEqualTo(PerfilArrendador.AVISO_IDENTIDAD);
    }
}
