package pe.edu.ulima.espacioya.cuentas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.Sesion;
import pe.edu.ulima.espacioya.cuentas.domain.TipoDocumento;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;
import pe.edu.ulima.espacioya.cuentas.repository.SesionRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class SesionServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-28T15:00:00Z");
    private static final String TOKEN = "tokenDePruebaDeCuarentaYTresCaracteres_abcd";
    private static final long ID_USUARIO = 7L;
    private static final long ID_SESION = 31L;
    private static final String IP = "10.0.0.1";

    @Mock
    private SesionRepository sesiones;
    @Mock
    private UsuarioRepository usuarios;
    @Mock
    private PerfilArrendadorRepository perfiles;
    @Mock
    private AuditoriaService auditoria;

    private SesionService servicio;

    @BeforeEach
    void preparar() {
        servicio = new SesionService(sesiones, usuarios, perfiles, auditoria, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private static Sesion sesionCreadaHace(Duration antiguedad) {
        var sesion = Sesion.iniciar(ID_USUARIO, TokenAleatorio.hash(TOKEN), AHORA.minus(antiguedad));
        ReflectionTestUtils.setField(sesion, "id", ID_SESION);
        return sesion;
    }

    private static Usuario arrendador() {
        var usuario = Usuario.registrarArrendador("ana@correo.pe", "$2a$12$hash", "Ana", "987654321", "2026-10",
                AHORA.minusSeconds(7200));
        ReflectionTestUtils.setField(usuario, "id", ID_USUARIO);
        return usuario;
    }

    @Test
    @DisplayName("HU-03 R22, R24: una sesión vigente devuelve el usuario y el rol guardados y renueva expira_en")
    void vigenteRenuevaYDevuelveUsuario() {
        var sesion = sesionCreadaHace(Duration.ofMinutes(20));
        when(sesiones.findByHashToken(TokenAleatorio.hash(TOKEN))).thenReturn(Optional.of(sesion));
        when(usuarios.findById(ID_USUARIO)).thenReturn(Optional.of(arrendador()));

        var resultado = servicio.validar(TOKEN);

        assertThat(resultado).contains(new UsuarioAutenticado(ID_USUARIO, ID_SESION, "ARRENDADOR"));
        assertThat(sesion.getExpiraEn()).isEqualTo(AHORA.plus(Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("HU-03 R26: una sesión revocada no se acepta")
    void revocadaVacio() {
        var sesion = sesionCreadaHace(Duration.ofMinutes(5));
        sesion.revocar();
        when(sesiones.findByHashToken(TokenAleatorio.hash(TOKEN))).thenReturn(Optional.of(sesion));

        assertThat(servicio.validar(TOKEN)).isEmpty();
        verify(usuarios, never()).findById(any());
    }

    @Test
    @DisplayName("HU-03 R27: una sesión vencida no se acepta ni se renueva")
    void vencidaVacioSinRenovar() {
        var sesion = sesionCreadaHace(Duration.ofMinutes(30));
        var expiracion = sesion.getExpiraEn();
        when(sesiones.findByHashToken(TokenAleatorio.hash(TOKEN))).thenReturn(Optional.of(sesion));

        assertThat(servicio.validar(TOKEN)).isEmpty();
        assertThat(sesion.getExpiraEn()).isEqualTo(expiracion).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("HU-03 R28: un token que no corresponde a ninguna sesión no se acepta")
    void tokenDesconocidoVacio() {
        when(sesiones.findByHashToken(TokenAleatorio.hash(TOKEN))).thenReturn(Optional.empty());

        assertThat(servicio.validar(TOKEN)).isEmpty();
    }

    @Test
    @DisplayName("HU-03 R23: actual devuelve el rol y el aviso de identidad del arrendador NO_INICIADA")
    void actualConAviso() {
        when(usuarios.findById(ID_USUARIO)).thenReturn(Optional.of(arrendador()));
        when(perfiles.findById(ID_USUARIO)).thenReturn(Optional.of(PerfilArrendador.crear(ID_USUARIO,
                TipoDocumento.RUC, "20100070970", "b".repeat(64), "Canchas SAC", "Av. Prado 1")));

        var actual = servicio.actual(new UsuarioAutenticado(ID_USUARIO, ID_SESION, "ARRENDADOR"));

        assertThat(actual.rol()).isEqualTo("ARRENDADOR");
        assertThat(actual.aviso()).isEqualTo(PerfilArrendador.AVISO_IDENTIDAD);
    }

    @Test
    @DisplayName("HU-03 R25, R29, R33: cerrar revoca solo la sesión del principal y audita CIERRE_SESION")
    void cerrarRevocaYAudita() {
        var sesion = sesionCreadaHace(Duration.ofMinutes(5));
        when(sesiones.findById(ID_SESION)).thenReturn(Optional.of(sesion));

        servicio.cerrar(new UsuarioAutenticado(ID_USUARIO, ID_SESION, "USUARIO"), IP);

        assertThat(sesion.isRevocada()).isTrue();
        verify(sesiones, never()).findAll();
        var evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        assertThat(evento.getValue()).isEqualTo(
                new EventoAuditoria(ID_USUARIO, "CIERRE_SESION", "sesion", ID_SESION, IP, null));
    }
}
