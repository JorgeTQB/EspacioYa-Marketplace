package pe.edu.ulima.espacioya.cuentas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import pe.edu.ulima.espacioya.comun.almacenamiento.ArchivosPrivados;
import pe.edu.ulima.espacioya.comun.almacenamiento.UrlFirmada;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.domain.DocumentoIdentidad;
import pe.edu.ulima.espacioya.cuentas.domain.EstadoIdentidad;
import pe.edu.ulima.espacioya.cuentas.domain.EstadoUsuario;
import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.Rol;
import pe.edu.ulima.espacioya.cuentas.domain.TipoDocumento;
import pe.edu.ulima.espacioya.cuentas.domain.TipoDocumentoIdentidad;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.dto.ArrendadorEnRevisionDTO;
import pe.edu.ulima.espacioya.cuentas.dto.HabilitacionArrendadorDTO;
import pe.edu.ulima.espacioya.cuentas.repository.DocumentoIdentidadRepository;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository.EnRevision;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class CuentaServiceImplTest {

    private static final long ID = 7L;
    private static final Instant AHORA = Instant.parse("2026-10-28T15:00:00Z");
    private static final String CORREO = "ana@correo.pe";
    private static final UsuarioAutenticado ARRENDADOR = new UsuarioAutenticado(ID, 31L, "ARRENDADOR");
    private static final UsuarioAutenticado ADMIN = new UsuarioAutenticado(1L, 99L, "ADMIN");

    @Mock
    private UsuarioRepository usuarios;
    @Mock
    private PerfilArrendadorRepository perfiles;
    @Mock
    private DocumentoIdentidadRepository documentos;
    @Mock
    private ArchivosPrivados archivos;
    @Mock
    private NotificadorCorreo notificador;

    private CuentaServiceImpl servicio;

    @BeforeEach
    void preparar() {
        servicio = new CuentaServiceImpl(usuarios, perfiles, documentos, archivos, notificador);
    }

    @AfterEach
    void limpiarSincronizacion() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static Usuario arrendador(long id, EstadoUsuario estado) {
        var usuario = Usuario.registrarArrendador("arrendador" + id + "@correo.pe", "$2a$12$hash", "Ana " + id,
                "987654321", "2026-10", AHORA);
        ReflectionTestUtils.setField(usuario, "id", id);
        ReflectionTestUtils.setField(usuario, "estado", estado);
        return usuario;
    }

    private static PerfilArrendador perfil(long id, EstadoIdentidad estado) {
        var perfil = PerfilArrendador.crear(id, TipoDocumento.RUC, "20100070970", "c".repeat(64), "Canchas " + id,
                "Av. Prado " + id);
        ReflectionTestUtils.setField(perfil, "estadoIdentidad", estado);
        return perfil;
    }

    private void dado(boolean correoVerificado, EstadoIdentidad estado) {
        var usuario = Usuario.registrarArrendador(CORREO, "$2a$12$hash", "Ana", "987654321", "2026-10", AHORA);
        ReflectionTestUtils.setField(usuario, "id", ID);
        ReflectionTestUtils.setField(usuario, "correoVerificado", correoVerificado);
        when(usuarios.findById(ID)).thenReturn(Optional.of(usuario));
        when(perfiles.findById(ID)).thenReturn(Optional.of(perfil(ID, estado)));
    }

    // ── HU-07: habilitación del arrendador (sin cambios) ─────────────────

    @Test
    @DisplayName("HU-07 R41: una cuenta USUARIO o ADMIN recibe AccesoDenegado sin consultar repositorios")
    void rolDistintoDeArrendador() {
        for (var rol : new String[] {"USUARIO", "ADMIN"}) {
            var usuario = new UsuarioAutenticado(ID, 31L, rol);
            assertThatThrownBy(() -> servicio.habilitacionArrendador(usuario))
                    .isInstanceOf(AccesoDenegadoException.class);
        }
        verifyNoInteractions(usuarios, perfiles);
    }

    @Test
    @DisplayName("HU-07 R42: informa si el correo del arrendador está verificado")
    void correoVerificado() {
        dado(false, EstadoIdentidad.VERIFICADA);
        assertThat(servicio.habilitacionArrendador(ARRENDADOR))
                .isEqualTo(new HabilitacionArrendadorDTO(false, true));
    }

    @Test
    @DisplayName("HU-07 R15: solo VERIFICADA cuenta como identidad verificada")
    void identidadVerificadaSoloEnVerificada() {
        for (var estado : EstadoIdentidad.values()) {
            dado(true, estado);
            assertThat(servicio.habilitacionArrendador(ARRENDADOR))
                    .isEqualTo(new HabilitacionArrendadorDTO(true, estado == EstadoIdentidad.VERIFICADA));
        }
    }

    // ── HU-06: rol ADMIN en cada método ──────────────────────────────────

    @Test
    @DisplayName("HU-06 R41: con rol USUARIO o ARRENDADOR los 4 métodos del admin lanzan AccesoDenegado sin "
            + "consultar repositorios")
    void rolDistintoDeAdmin() {
        for (var rol : new String[] {"USUARIO", "ARRENDADOR"}) {
            var usuario = new UsuarioAutenticado(ID, 31L, rol);
            assertThatThrownBy(() -> servicio.arrendadoresEnRevision(usuario))
                    .isInstanceOf(AccesoDenegadoException.class);
            assertThatThrownBy(() -> servicio.expedienteIdentidad(usuario, ID))
                    .isInstanceOf(AccesoDenegadoException.class);
            assertThatThrownBy(() -> servicio.aprobarIdentidad(usuario, ID))
                    .isInstanceOf(AccesoDenegadoException.class);
            assertThatThrownBy(() -> servicio.rechazarIdentidad(usuario, ID, "Ilegible"))
                    .isInstanceOf(AccesoDenegadoException.class);
        }
        verifyNoInteractions(usuarios, perfiles, documentos, archivos, notificador);
    }

    // ── Cola y expediente ────────────────────────────────────────────────

    @Test
    @DisplayName("HU-06 R20, R21: la cola tiene un elemento por arrendador con sus 5 campos en el orden de la consulta")
    void colaEnElOrdenDeLaConsulta() {
        var primero = AHORA.minusSeconds(7200);
        var segundo = AHORA.minusSeconds(60);
        when(perfiles.findEnRevision()).thenReturn(List.of(new EnRevision(9L, primero), new EnRevision(4L, segundo)));
        when(usuarios.findAllById(List.of(9L, 4L)))
                .thenReturn(List.of(arrendador(4L, EstadoUsuario.ACTIVA), arrendador(9L, EstadoUsuario.ACTIVA)));
        when(perfiles.findAllById(List.of(9L, 4L)))
                .thenReturn(List.of(perfil(4L, EstadoIdentidad.EN_REVISION), perfil(9L, EstadoIdentidad.EN_REVISION)));

        var cola = servicio.arrendadoresEnRevision(ADMIN);

        assertThat(cola).containsExactly(
                new ArrendadorEnRevisionDTO(9L, "Ana 9", "Canchas 9", "RUC", primero),
                new ArrendadorEnRevisionDTO(4L, "Ana 4", "Canchas 4", "RUC", segundo));
        verifyNoInteractions(archivos);
    }

    private void dadoEnCola(EstadoUsuario estadoCuenta, EstadoIdentidad estadoIdentidad) {
        when(usuarios.findById(ID)).thenReturn(Optional.of(arrendador(ID, estadoCuenta)));
        if (estadoCuenta == EstadoUsuario.ACTIVA) {
            when(perfiles.findById(ID)).thenReturn(Optional.of(perfil(ID, estadoIdentidad)));
        }
    }

    @Test
    @DisplayName("HU-06 R23, R24: el expediente trae los datos completos y firma una URL por documento vigente")
    void expedienteFirmaLosDocumentos() {
        dadoEnCola(EstadoUsuario.ACTIVA, EstadoIdentidad.EN_REVISION);
        var enviado = AHORA.minusSeconds(3600);
        var identidad = DocumentoIdentidad.registrar(ID, TipoDocumentoIdentidad.IDENTIDAD,
                "identidad/0f8fad5b-d9cb-469f-a165-70867728950e", "application/pdf", 1000, enviado);
        var propiedad = DocumentoIdentidad.registrar(ID, TipoDocumentoIdentidad.PROPIEDAD,
                "identidad/7c9e6679-7425-40de-944b-e07fc1f90ae7", "image/png", 2000, enviado);
        when(documentos.findByArrendadorIdOrderByTipoAsc(ID)).thenReturn(List.of(identidad, propiedad));
        var expira = AHORA.plusSeconds(300);
        when(archivos.urlFirmada(identidad.getRutaArchivo(), "application/pdf"))
                .thenReturn(new UrlFirmada("/api/archivos?firma=1", expira));
        when(archivos.urlFirmada(propiedad.getRutaArchivo(), "image/png"))
                .thenReturn(new UrlFirmada("/api/archivos?firma=2", expira));

        var expediente = servicio.expedienteIdentidad(ADMIN, ID);

        assertThat(expediente.arrendadorId()).isEqualTo(ID);
        assertThat(expediente.nombre()).isEqualTo("Ana 7");
        assertThat(expediente.correo()).isEqualTo("arrendador7@correo.pe");
        assertThat(expediente.telefono()).isEqualTo("987654321");
        assertThat(expediente.tipoDocumento()).isEqualTo("RUC");
        assertThat(expediente.numeroDocumento()).isEqualTo("20100070970");
        assertThat(expediente.razonSocial()).isEqualTo("Canchas 7");
        assertThat(expediente.direccionFiscal()).isEqualTo("Av. Prado 7");
        assertThat(expediente.estadoIdentidad()).isEqualTo("EN_REVISION");
        assertThat(expediente.enviadoEn()).isEqualTo(enviado);
        assertThat(expediente.documentos()).extracting(d -> d.tipo() + " " + d.url() + " " + d.urlExpiraEn())
                .containsExactly("IDENTIDAD /api/archivos?firma=1 " + expira,
                        "PROPIEDAD /api/archivos?firma=2 " + expira);
        assertThat(expediente.toString()).doesNotContain("20100070970").doesNotContain("arrendador7@correo.pe");
    }

    @Test
    @DisplayName("HU-06 R25: el expediente de un arrendador fuera de la cola responde 404 sin firmar ninguna URL")
    void expedienteFueraDeLaColaDa404() {
        for (var estado : new EstadoIdentidad[] {EstadoIdentidad.NO_INICIADA, EstadoIdentidad.VERIFICADA,
                EstadoIdentidad.RECHAZADA}) {
            dadoEnCola(EstadoUsuario.ACTIVA, estado);
            assertThatThrownBy(() -> servicio.expedienteIdentidad(ADMIN, ID))
                    .isInstanceOf(RecursoNoEncontradoException.class);
        }
        dadoEnCola(EstadoUsuario.SUSPENDIDA, EstadoIdentidad.EN_REVISION);
        assertThatThrownBy(() -> servicio.expedienteIdentidad(ADMIN, ID))
                .isInstanceOf(RecursoNoEncontradoException.class);
        var usuario = Usuario.registrar(CORREO, "$2a$12$hash", "2026-10", AHORA);
        when(usuarios.findById(8L)).thenReturn(Optional.of(usuario));
        assertThatThrownBy(() -> servicio.expedienteIdentidad(ADMIN, 8L))
                .isInstanceOf(RecursoNoEncontradoException.class);
        when(usuarios.findById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.expedienteIdentidad(ADMIN, 404L))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verifyNoInteractions(archivos, documentos);
    }

    // ── Decisiones ───────────────────────────────────────────────────────

    private PerfilArrendador dadoParaDecidir(EstadoUsuario estadoCuenta, EstadoIdentidad estadoIdentidad) {
        var perfil = perfil(ID, estadoIdentidad);
        when(perfiles.findParaActualizar(ID)).thenReturn(Optional.of(perfil));
        when(usuarios.findById(ID)).thenReturn(Optional.of(arrendador(ID, estadoCuenta)));
        return perfil;
    }

    private static void confirmar() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    private static void revertir() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    }

    @Test
    @DisplayName("HU-06 R28, R30: aprobar deja VERIFICADA sin motivo y avisa una sola vez, solo tras el commit")
    void aprobarAvisaTrasElCommit() {
        TransactionSynchronizationManager.initSynchronization();
        var perfil = dadoParaDecidir(EstadoUsuario.ACTIVA, EstadoIdentidad.EN_REVISION);

        var estado = servicio.aprobarIdentidad(ADMIN, ID);

        assertThat(estado).isEqualTo("VERIFICADA");
        assertThat(perfil.getEstadoIdentidad()).isEqualTo(EstadoIdentidad.VERIFICADA);
        assertThat(perfil.getMotivoRechazo()).isNull();
        verifyNoInteractions(notificador);
        confirmar();
        verify(notificador).notificarIdentidadAprobada("arrendador7@correo.pe");
        verify(notificador, never()).notificarIdentidadRechazada(anyString(), anyString());
    }

    @Test
    @DisplayName("HU-06 R32, R34: rechazar deja RECHAZADA con el motivo recortado y lo avisa solo tras el commit")
    void rechazarAvisaTrasElCommit() {
        TransactionSynchronizationManager.initSynchronization();
        var perfil = dadoParaDecidir(EstadoUsuario.ACTIVA, EstadoIdentidad.EN_REVISION);

        var estado = servicio.rechazarIdentidad(ADMIN, ID, "  El DNI está borroso.  ");

        assertThat(estado).isEqualTo("RECHAZADA");
        assertThat(perfil.getEstadoIdentidad()).isEqualTo(EstadoIdentidad.RECHAZADA);
        assertThat(perfil.getMotivoRechazo()).isEqualTo("El DNI está borroso.");
        verifyNoInteractions(notificador);
        confirmar();
        verify(notificador).notificarIdentidadRechazada("arrendador7@correo.pe", "El DNI está borroso.");
    }

    @Test
    @DisplayName("HU-06 R40: si la transacción se revierte no se invoca el notificador")
    void rollbackNoAvisa() {
        TransactionSynchronizationManager.initSynchronization();
        dadoParaDecidir(EstadoUsuario.ACTIVA, EstadoIdentidad.EN_REVISION);
        servicio.aprobarIdentidad(ADMIN, ID);
        var otro = perfil(8L, EstadoIdentidad.EN_REVISION);
        when(perfiles.findParaActualizar(8L)).thenReturn(Optional.of(otro));
        when(usuarios.findById(8L)).thenReturn(Optional.of(arrendador(8L, EstadoUsuario.ACTIVA)));
        servicio.rechazarIdentidad(ADMIN, 8L, "Ilegible");

        revertir();

        verifyNoInteractions(notificador);
    }

    @Test
    @DisplayName("HU-06 R30: un fallo del envío tras el commit no se propaga ni revierte la decisión")
    void falloDelEnvioNoSePropaga() {
        TransactionSynchronizationManager.initSynchronization();
        var perfil = dadoParaDecidir(EstadoUsuario.ACTIVA, EstadoIdentidad.EN_REVISION);
        doThrow(new IllegalStateException("servidor de correo caído")).when(notificador)
                .notificarIdentidadAprobada(any());
        servicio.aprobarIdentidad(ADMIN, ID);

        confirmar();

        assertThat(perfil.getEstadoIdentidad()).isEqualTo(EstadoIdentidad.VERIFICADA);
    }

    @Test
    @DisplayName("HU-06 R37, R40: una decisión sobre un arrendador inexistente, no ARRENDADOR o no ACTIVA → 404 sin "
            + "cambios ni aviso")
    void decisionSobreArrendadorNoValidoDa404() {
        TransactionSynchronizationManager.initSynchronization();
        when(perfiles.findParaActualizar(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.aprobarIdentidad(ADMIN, 404L))
                .isInstanceOf(RecursoNoEncontradoException.class);

        var suspendido = dadoParaDecidir(EstadoUsuario.SUSPENDIDA, EstadoIdentidad.EN_REVISION);
        assertThatThrownBy(() -> servicio.rechazarIdentidad(ADMIN, ID, "Ilegible"))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(suspendido.getEstadoIdentidad()).isEqualTo(EstadoIdentidad.EN_REVISION);

        var deUsuario = perfil(8L, EstadoIdentidad.EN_REVISION);
        var usuario = Usuario.registrar(CORREO, "$2a$12$hash", "2026-10", AHORA);
        ReflectionTestUtils.setField(usuario, "rol", Rol.USUARIO);
        when(perfiles.findParaActualizar(8L)).thenReturn(Optional.of(deUsuario));
        when(usuarios.findById(8L)).thenReturn(Optional.of(usuario));
        assertThatThrownBy(() -> servicio.aprobarIdentidad(ADMIN, 8L))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(deUsuario.getEstadoIdentidad()).isEqualTo(EstadoIdentidad.EN_REVISION);

        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        verifyNoInteractions(notificador);
    }

    @Test
    @DisplayName("HU-06 R38, R40: una decisión sobre una identidad que no está EN_REVISION → 400 ya revisada sin "
            + "cambios ni aviso")
    void decisionYaTomadaDa400() {
        TransactionSynchronizationManager.initSynchronization();
        for (var estado : new EstadoIdentidad[] {EstadoIdentidad.NO_INICIADA, EstadoIdentidad.VERIFICADA,
                EstadoIdentidad.RECHAZADA}) {
            var perfil = dadoParaDecidir(EstadoUsuario.ACTIVA, estado);

            assertThatThrownBy(() -> servicio.aprobarIdentidad(ADMIN, ID))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage("Este arrendador ya no está en revisión. Actualiza la cola.");
            assertThatThrownBy(() -> servicio.rechazarIdentidad(ADMIN, ID, "Ilegible"))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage("Este arrendador ya no está en revisión. Actualiza la cola.");
            assertThat(perfil.getEstadoIdentidad()).isEqualTo(estado);
        }
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        verifyNoInteractions(notificador);
    }
}
