package pe.edu.ulima.espacioya.cuentas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

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
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.cifrado.HuellaCampos;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.DemasiadosIntentosException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.domain.EstadoIdentidad;
import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.TipoDocumento;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.domain.ValidadorDocumento;
import pe.edu.ulima.espacioya.cuentas.dto.ActualizacionFacturacionDTO;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;
import pe.edu.ulima.espacioya.cuentas.service.ReautenticacionPerfil.Operacion;

@ExtendWith(MockitoExtension.class)
class FacturacionServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-04T15:00:00Z");
    private static final String IP = "10.20.3.1";
    private static final long ID = 7L;
    private static final String CONTRASENA = "Segura123!";
    private static final String RUC = "20100070970";
    private static final String RUC_NUEVO = "20131312955";
    private static final String HASH = "a".repeat(64);
    private static final String HASH_NUEVO = "b".repeat(64);
    private static final String RAZON = "Canchas SAC";
    private static final String DIRECCION = "Av. Lima 123";
    private static final UsuarioAutenticado ARRENDADOR = new UsuarioAutenticado(ID, 70L, "ARRENDADOR");

    @Mock
    private UsuarioRepository usuarios;
    @Mock
    private PerfilArrendadorRepository perfiles;
    @Mock
    private ReautenticacionPerfil reautenticacion;
    @Mock
    private HuellaCampos huella;
    @Mock
    private AuditoriaService auditoria;

    private FacturacionService servicio;

    @BeforeEach
    void preparar() {
        servicio = new FacturacionService(usuarios, perfiles, reautenticacion, huella, auditoria,
                new TransactionTemplate(mock(PlatformTransactionManager.class)));
    }

    private static ActualizacionFacturacionDTO solicitud(String tipo, String numero, String razon, String direccion) {
        return new ActualizacionFacturacionDTO(tipo, numero, razon, direccion, CONTRASENA);
    }

    private static PerfilArrendador perfil(EstadoIdentidad estado) {
        var perfil = PerfilArrendador.crear(ID, TipoDocumento.RUC, RUC, HASH, RAZON, DIRECCION);
        ReflectionTestUtils.setField(perfil, "estadoIdentidad", estado);
        return perfil;
    }

    private PerfilArrendador bloqueado(EstadoIdentidad estado) {
        var cuenta = Usuario.registrarArrendador("ana@correo.pe", "$2a$12$hash", "Ana", "987654321", "2026-10",
                AHORA);
        ReflectionTestUtils.setField(cuenta, "id", ID);
        when(usuarios.findParaActualizar(ID)).thenReturn(Optional.of(cuenta));
        var perfil = perfil(estado);
        when(perfiles.findParaActualizar(ID)).thenReturn(Optional.of(perfil));
        // Sin número nuevo el servicio lee el tipo actual antes de la contraseña (R35).
        lenient().when(perfiles.findById(ID)).thenReturn(Optional.of(perfil));
        return perfil;
    }

    private EventoAuditoria unicaAuditoria() {
        var captor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(captor.capture());
        return captor.getValue();
    }

    private static void reglaSinCampo(Runnable accion, String mensaje) {
        assertThatThrownBy(accion::run).isInstanceOfSatisfying(ReglaNegocioException.class, e -> {
            assertThat(e.getCampo()).isNull();
            assertThat(e.getMessage()).isEqualTo(mensaje);
        });
    }

    // ── Acceso y validación ──────────────────────────────────────────────

    @Test
    @DisplayName("HU-20 R3: un USUARIO o un ADMIN recibe 403 sin comprobar la contraseña ni contar un fallo")
    void otroRolRecibe403() {
        for (var rol : new String[] {"USUARIO", "ADMIN"}) {
            var sesion = new UsuarioAutenticado(ID, 70L, rol);
            assertThatThrownBy(() -> servicio.actualizar(sesion, solicitud("RUC", RUC_NUEVO, RAZON, DIRECCION), IP))
                    .isInstanceOf(AccesoDenegadoException.class);
        }
        verifyNoInteractions(reautenticacion, usuarios, perfiles, auditoria, huella);
    }

    @Test
    @DisplayName("HU-20 R35: un número que no cumple ValidadorDocumento da 400 en numeroDocumento sin la contraseña")
    void numeroInvalido() {
        assertThatThrownBy(() -> servicio.actualizar(ARRENDADOR, solicitud("RUC", "20100070971", RAZON, DIRECCION),
                IP)).isInstanceOfSatisfying(ReglaNegocioException.class, e -> {
                    assertThat(e.getCampo()).isEqualTo("numeroDocumento");
                    assertThat(e.getMessage()).isEqualTo(ValidadorDocumento.MENSAJE_RUC_VERIFICADOR);
                });
        assertThatThrownBy(() -> servicio.actualizar(ARRENDADOR, solicitud("DNI", "1234", RAZON, DIRECCION), IP))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.getMessage()).isEqualTo(ValidadorDocumento.MENSAJE_DNI));
        verifyNoInteractions(reautenticacion, auditoria);
    }

    @Test
    @DisplayName("HU-20 R35: cambiar el tipo sin enviar número da 400 en numeroDocumento sin la contraseña")
    void tipoSinNumero() {
        when(perfiles.findById(ID)).thenReturn(Optional.of(perfil(EstadoIdentidad.VERIFICADA)));

        assertThatThrownBy(() -> servicio.actualizar(ARRENDADOR, solicitud("DNI", "  ", RAZON, DIRECCION), IP))
                .isInstanceOfSatisfying(ReglaNegocioException.class, e -> {
                    assertThat(e.getCampo()).isEqualTo("numeroDocumento");
                    assertThat(e.getMessage()).isEqualTo(ValidadorDocumento.MENSAJE_NUMERO_FALTA);
                });
        verifyNoInteractions(reautenticacion);
    }

    @Test
    @DisplayName("HU-20 R19, R20: si la reautenticación falla no se bloquea ni modifica nada")
    void reautenticacionFalla() {
        doThrow(new DemasiadosIntentosException()).when(reautenticacion)
                .exigir(ARRENDADOR, CONTRASENA, Operacion.FACTURACION, IP);

        assertThatThrownBy(() -> servicio.actualizar(ARRENDADOR, solicitud("RUC", RUC_NUEVO, RAZON, DIRECCION), IP))
                .isInstanceOf(DemasiadosIntentosException.class);
        verifyNoInteractions(usuarios, huella, auditoria);
        verify(perfiles, never()).findParaActualizar(any());
    }

    // ── Cambios ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-20 R36, R37, R51, R52: VERIFICADA con otro RUC guarda el número con su huella, pasa a EN_REVISION "
            + "y audita; bloquea usuario antes que perfil")
    void cambiaDocumentoVerificado() {
        var perfil = bloqueado(EstadoIdentidad.VERIFICADA);
        when(huella.calcular(RUC_NUEVO)).thenReturn(HASH_NUEVO);
        when(perfiles.findByHashDocumento(HASH_NUEVO)).thenReturn(Optional.empty());

        servicio.actualizar(ARRENDADOR, solicitud("RUC", " " + RUC_NUEVO + " ", RAZON, DIRECCION), IP);

        assertThat(perfil.getNumeroDocumento()).isEqualTo(RUC_NUEVO);
        assertThat(perfil.getHashDocumento()).isEqualTo(HASH_NUEVO);
        assertThat(perfil.getEstadoIdentidad()).isEqualTo(EstadoIdentidad.EN_REVISION);
        var orden = inOrder(reautenticacion, usuarios, perfiles);
        orden.verify(reautenticacion).exigir(ARRENDADOR, CONTRASENA, Operacion.FACTURACION, IP);
        orden.verify(usuarios).findParaActualizar(ID);
        orden.verify(perfiles).findParaActualizar(ID);
        orden.verify(perfiles).saveAndFlush(perfil);
        var evento = unicaAuditoria();
        assertThat(evento).isEqualTo(new EventoAuditoria(ID, "FACTURACION_ACTUALIZADA", "perfil_arrendador", ID, IP,
                "campos=numeroDocumento;identidad=VERIFICADA->EN_REVISION"));
        assertThat(evento.detalle()).doesNotContain(RUC_NUEVO).doesNotContain(RAZON);
    }

    @Test
    @DisplayName("HU-20 R38: VERIFICADA con otra razón social pasa a EN_REVISION")
    void cambiaRazonSocialVerificada() {
        var perfil = bloqueado(EstadoIdentidad.VERIFICADA);

        servicio.actualizar(ARRENDADOR, solicitud("RUC", null, "Otra Razón SAC", DIRECCION), IP);

        assertThat(perfil.getRazonSocial()).isEqualTo("Otra Razón SAC");
        assertThat(perfil.getEstadoIdentidad()).isEqualTo(EstadoIdentidad.EN_REVISION);
        assertThat(unicaAuditoria().detalle()).isEqualTo("campos=razonSocial;identidad=VERIFICADA->EN_REVISION");
        verifyNoInteractions(huella);
    }

    @Test
    @DisplayName("HU-20 R39: VERIFICADA que cambia solo la dirección sigue VERIFICADA")
    void cambiaSoloDireccion() {
        var perfil = bloqueado(EstadoIdentidad.VERIFICADA);

        servicio.actualizar(ARRENDADOR, solicitud("RUC", "", RAZON, "Jr. Nuevo 456"), IP);

        assertThat(perfil.getDireccionFiscal()).isEqualTo("Jr. Nuevo 456");
        assertThat(perfil.getEstadoIdentidad()).isEqualTo(EstadoIdentidad.VERIFICADA);
        assertThat(unicaAuditoria().detalle()).isEqualTo("campos=direccionFiscal;identidad=VERIFICADA->VERIFICADA");
    }

    @Test
    @DisplayName("HU-20 R40: NO_INICIADA y RECHAZADA cambian el documento sin cambiar el estado ni el motivo")
    void noIniciadaYRechazada() {
        when(huella.calcular(RUC_NUEVO)).thenReturn(HASH_NUEVO);
        when(perfiles.findByHashDocumento(HASH_NUEVO)).thenReturn(Optional.empty());
        var perfil = bloqueado(EstadoIdentidad.RECHAZADA);
        ReflectionTestUtils.setField(perfil, "motivoRechazo", "Ilegible");

        servicio.actualizar(ARRENDADOR, solicitud("RUC", RUC_NUEVO, RAZON, DIRECCION), IP);

        assertThat(perfil.getEstadoIdentidad()).isEqualTo(EstadoIdentidad.RECHAZADA);
        assertThat(perfil.getMotivoRechazo()).isEqualTo("Ilegible");
        assertThat(perfil.getNumeroDocumento()).isEqualTo(RUC_NUEVO);
        assertThat(unicaAuditoria().detalle()).isEqualTo("campos=numeroDocumento;identidad=RECHAZADA->RECHAZADA");
    }

    @Test
    @DisplayName("HU-20 R41: EN_REVISION con otro documento o razón social da 400 sin modificar nada")
    void enRevision() {
        var perfil = bloqueado(EstadoIdentidad.EN_REVISION);
        when(huella.calcular(RUC_NUEVO)).thenReturn(HASH_NUEVO);

        reglaSinCampo(() -> servicio.actualizar(ARRENDADOR, solicitud("RUC", RUC_NUEVO, RAZON, DIRECCION), IP),
                FacturacionService.MENSAJE_IDENTIDAD_EN_REVISION);
        reglaSinCampo(() -> servicio.actualizar(ARRENDADOR, solicitud("RUC", null, "Otra SAC", DIRECCION), IP),
                FacturacionService.MENSAJE_IDENTIDAD_EN_REVISION);

        assertThat(perfil.getNumeroDocumento()).isEqualTo(RUC);
        assertThat(perfil.getRazonSocial()).isEqualTo(RAZON);
        verify(perfiles, never()).saveAndFlush(any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("HU-20 R42, R51: un documento de otro arrendador da 400 genérico, cuenta un fallo y se audita sin "
            + "el número")
    void documentoDeOtro() {
        var perfil = bloqueado(EstadoIdentidad.VERIFICADA);
        when(huella.calcular(RUC_NUEVO)).thenReturn(HASH_NUEVO);
        var otro = PerfilArrendador.crear(99L, TipoDocumento.RUC, RUC_NUEVO, HASH_NUEVO, "Otro", "Otra");
        when(perfiles.findByHashDocumento(HASH_NUEVO)).thenReturn(Optional.of(otro));

        reglaSinCampo(() -> servicio.actualizar(ARRENDADOR, solicitud("RUC", RUC_NUEVO, RAZON, DIRECCION), IP),
                FacturacionService.MENSAJE_DOCUMENTO_NO_DISPONIBLE);

        assertThat(perfil.getNumeroDocumento()).isEqualTo(RUC);
        assertThat(perfil.getEstadoIdentidad()).isEqualTo(EstadoIdentidad.VERIFICADA);
        verify(perfiles, never()).saveAndFlush(any());
        verify(reautenticacion).registrarFallo(ARRENDADOR);
        var evento = unicaAuditoria();
        assertThat(evento).isEqualTo(new EventoAuditoria(ID, "FACTURACION_DOCUMENTO_RECHAZADO", "perfil_arrendador",
                ID, IP, "motivo=DOCUMENTO_EN_USO"));
    }

    @Test
    @DisplayName("HU-20 R42: la violación de uq_perfil_arrendador_hash_documento da el mismo 400 genérico")
    void carreraDelDocumento() {
        bloqueado(EstadoIdentidad.NO_INICIADA);
        when(huella.calcular(RUC_NUEVO)).thenReturn(HASH_NUEVO);
        when(perfiles.findByHashDocumento(HASH_NUEVO)).thenReturn(Optional.empty());
        when(perfiles.saveAndFlush(any())).thenThrow(
                new DataIntegrityViolationException("uq_perfil_arrendador_hash_documento"));

        reglaSinCampo(() -> servicio.actualizar(ARRENDADOR, solicitud("RUC", RUC_NUEVO, RAZON, DIRECCION), IP),
                FacturacionService.MENSAJE_DOCUMENTO_NO_DISPONIBLE);

        verify(reautenticacion).registrarFallo(ARRENDADOR);
        assertThat(unicaAuditoria().accion()).isEqualTo("FACTURACION_DOCUMENTO_RECHAZADO");
    }

    @Test
    @DisplayName("HU-20 R43: sin cambios no escribe, no cambia la identidad ni audita")
    void sinCambios() {
        var perfil = bloqueado(EstadoIdentidad.VERIFICADA);
        when(huella.calcular(RUC)).thenReturn(HASH);

        servicio.actualizar(ARRENDADOR, solicitud("RUC", RUC, " " + RAZON + " ", DIRECCION), IP);

        assertThat(perfil.getEstadoIdentidad()).isEqualTo(EstadoIdentidad.VERIFICADA);
        verify(perfiles, never()).saveAndFlush(any());
        verify(perfiles, never()).findByHashDocumento(any());
        verifyNoInteractions(auditoria);
    }
}
