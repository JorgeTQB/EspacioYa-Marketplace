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
import java.util.stream.LongStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
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
import pe.edu.ulima.espacioya.cuentas.dto.CambioEstadoCuentaDTO;
import pe.edu.ulima.espacioya.cuentas.dto.CuentaResumenDTO;
import pe.edu.ulima.espacioya.cuentas.dto.DetalleCuentaDTO;
import pe.edu.ulima.espacioya.cuentas.dto.HabilitacionArrendadorDTO;
import pe.edu.ulima.espacioya.cuentas.repository.DocumentoIdentidadRepository;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository.EnRevision;
import pe.edu.ulima.espacioya.cuentas.repository.SesionRepository;
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
    @Mock
    private SesionRepository sesiones;

    private CuentaServiceImpl servicio;

    @BeforeEach
    void preparar() {
        servicio = new CuentaServiceImpl(usuarios, perfiles, documentos, archivos, notificador, sesiones);
    }

    @AfterEach
    void limpiarSincronizacion() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("HU-12 R28: bloquearCuenta toma el bloqueo de la fila por id")
    void bloquearCuentaBloqueaLaFila() {
        when(usuarios.findParaActualizar(ID))
                .thenReturn(Optional.of(Usuario.registrar(CORREO, "hash", "2026-10", AHORA)));

        servicio.bloquearCuenta(ID);

        verify(usuarios).findParaActualizar(ID);
    }

    @Test
    @DisplayName("HU-12 R28: bloquearCuenta de una cuenta inexistente lanza 404")
    void bloquearCuentaInexistente() {
        when(usuarios.findParaActualizar(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.bloquearCuenta(ID)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("HU-13 R48, R49: correoDe devuelve el correo de la cuenta, sin sesión ni rol")
    void correoDeDevuelveElCorreo() {
        when(usuarios.findById(ID)).thenReturn(Optional.of(Usuario.registrar(CORREO, "hash", "2026-10", AHORA)));

        assertThat(servicio.correoDe(ID)).isEqualTo(CORREO);
    }

    @Test
    @DisplayName("HU-13 R48: correoDe de una cuenta inexistente lanza 404")
    void correoDeInexistente() {
        when(usuarios.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.correoDe(ID)).isInstanceOf(RecursoNoEncontradoException.class);
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

    // ── HU-09: suspensión y reactivación ─────────────────────────────────

    private static Usuario cuenta(long id, Rol rol, EstadoUsuario estado) {
        var usuario = Usuario.registrar("cuenta" + id + "@correo.pe", "$2a$12$hash", "2026-10", AHORA);
        ReflectionTestUtils.setField(usuario, "id", id);
        ReflectionTestUtils.setField(usuario, "rol", rol);
        ReflectionTestUtils.setField(usuario, "estado", estado);
        return usuario;
    }

    private Usuario dadoParaCambiar(Rol rol, EstadoUsuario estado) {
        var usuario = cuenta(ID, rol, estado);
        when(usuarios.findParaActualizar(ID)).thenReturn(Optional.of(usuario));
        return usuario;
    }

    @Test
    @DisplayName("HU-09 R40: con rol USUARIO, ARRENDADOR o sin sesión los 4 métodos del admin lanzan AccesoDenegado "
            + "sin consultar repositorios")
    void rolDistintoDeAdminEnMetodosDeSuspension() {
        var usuarioRol = new UsuarioAutenticado(ID, 31L, "USUARIO");
        var arrendadorRol = new UsuarioAutenticado(ID, 31L, "ARRENDADOR");
        for (var usuario : new UsuarioAutenticado[] {usuarioRol, arrendadorRol, null}) {
            assertThatThrownBy(() -> servicio.buscarCuentas(usuario, "ana"))
                    .isInstanceOf(AccesoDenegadoException.class);
            assertThatThrownBy(() -> servicio.detalleCuenta(usuario, ID))
                    .isInstanceOf(AccesoDenegadoException.class);
            assertThatThrownBy(() -> servicio.suspenderCuenta(usuario, ID, "Fraude"))
                    .isInstanceOf(AccesoDenegadoException.class);
            assertThatThrownBy(() -> servicio.reactivarCuenta(usuario, ID))
                    .isInstanceOf(AccesoDenegadoException.class);
        }
        verifyNoInteractions(usuarios, perfiles, documentos, archivos, notificador, sesiones);
    }

    @Test
    @DisplayName("HU-09 R4: el patrón de búsqueda va en minúsculas, con %, _ y \\ escapados, y pide 21 filas")
    void patronEscapadoYLimite() {
        when(usuarios.buscar(anyString(), any(Limit.class))).thenReturn(List.of());

        servicio.buscarCuentas(ADMIN, "A%b_C\\");

        verify(usuarios).buscar("%a\\%b\\_c\\\\%", Limit.of(21));
        assertThat(CuentaServiceImpl.escaparComodines("50%_\\x")).isEqualTo("50\\%\\_\\\\x");
    }

    @Test
    @DisplayName("HU-09 R1, R2, R3: con 21 coincidencias devuelve las 20 primeras con sus 5 campos y hayMas")
    void veintiunaCoincidenciasDanVeinteYHayMas() {
        var filas = LongStream.rangeClosed(1, 21)
                .mapToObj(id -> cuenta(id, Rol.USUARIO, EstadoUsuario.ACTIVA)).toList();
        when(usuarios.buscar(anyString(), any(Limit.class))).thenReturn(filas);

        var resultado = servicio.buscarCuentas(ADMIN, "cuenta");

        assertThat(resultado.hayMas()).isTrue();
        assertThat(resultado.cuentas()).hasSize(20);
        assertThat(resultado.cuentas().getFirst())
                .isEqualTo(new CuentaResumenDTO(1L, null, "cuenta1@correo.pe", "USUARIO", "ACTIVA"));
        assertThat(resultado.cuentas().getLast().id()).isEqualTo(20L);
    }

    @Test
    @DisplayName("HU-09 R3: con 20 coincidencias o menos hayMas es false")
    void veinteCoincidenciasSinHayMas() {
        var filas = LongStream.rangeClosed(1, 20)
                .mapToObj(id -> cuenta(id, Rol.ARRENDADOR, EstadoUsuario.SUSPENDIDA)).toList();
        when(usuarios.buscar(anyString(), any(Limit.class))).thenReturn(filas);

        var resultado = servicio.buscarCuentas(ADMIN, "cuenta");

        assertThat(resultado.hayMas()).isFalse();
        assertThat(resultado.cuentas()).hasSize(20);
    }

    @Test
    @DisplayName("HU-09 R12: el detalle de una cuenta inexistente lanza RecursoNoEncontrado")
    void detalleInexistenteDa404() {
        when(usuarios.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.detalleCuenta(ADMIN, 404L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("HU-09 R7, R8, R9: el detalle de un ARRENDADOR ACTIVA trae su identidad y puede suspenderse")
    void detalleDeArrendador() {
        when(usuarios.findById(ID)).thenReturn(Optional.of(arrendador(ID, EstadoUsuario.ACTIVA)));
        when(perfiles.findById(ID)).thenReturn(Optional.of(perfil(ID, EstadoIdentidad.EN_REVISION)));

        var detalle = servicio.detalleCuenta(ADMIN, ID);

        assertThat(detalle).isEqualTo(new DetalleCuentaDTO(ID, "Ana 7", "arrendador7@correo.pe", "987654321",
                "ARRENDADOR", "ACTIVA", true, "EN_REVISION", true, false));
        assertThat(detalle.toString()).doesNotContain("20100070970").doesNotContain("Canchas");
    }

    @Test
    @DisplayName("HU-09 R8, R9: un USUARIO no tiene identidad; banderas según rol y estado")
    void detalleSinIdentidadYBanderas() {
        when(usuarios.findById(ID)).thenReturn(Optional.of(cuenta(ID, Rol.USUARIO, EstadoUsuario.SUSPENDIDA)));
        when(usuarios.findById(8L)).thenReturn(Optional.of(cuenta(8L, Rol.ADMIN, EstadoUsuario.ACTIVA)));
        when(usuarios.findById(9L)).thenReturn(Optional.of(cuenta(9L, Rol.USUARIO, EstadoUsuario.ELIMINADA)));

        var suspendida = servicio.detalleCuenta(ADMIN, ID);
        var admin = servicio.detalleCuenta(ADMIN, 8L);
        var eliminada = servicio.detalleCuenta(ADMIN, 9L);

        assertThat(suspendida.estadoIdentidad()).isNull();
        assertThat(suspendida.puedeSuspender()).isFalse();
        assertThat(suspendida.puedeReactivar()).isTrue();
        assertThat(admin.puedeSuspender()).isFalse();
        assertThat(admin.puedeReactivar()).isFalse();
        assertThat(eliminada.puedeSuspender()).isFalse();
        assertThat(eliminada.puedeReactivar()).isFalse();
        verifyNoInteractions(perfiles);
    }

    @Test
    @DisplayName("HU-09 R15, R16, R18: suspender bloquea, cambia el estado, revoca después y avisa solo tras el commit")
    void suspenderRevocaYAvisaTrasElCommit() {
        TransactionSynchronizationManager.initSynchronization();
        var usuario = dadoParaCambiar(Rol.ARRENDADOR, EstadoUsuario.ACTIVA);
        when(sesiones.revocarTodas(ID)).thenAnswer(invocacion -> {
            assertThat(usuario.getEstado()).isEqualTo(EstadoUsuario.SUSPENDIDA);
            return 2;
        });

        var cambio = servicio.suspenderCuenta(ADMIN, ID, "  Publicó datos falsos.  ");

        assertThat(cambio).isEqualTo(new CambioEstadoCuentaDTO("SUSPENDIDA", 2));
        assertThat(usuario.getEstado()).isEqualTo(EstadoUsuario.SUSPENDIDA);
        verify(sesiones).revocarTodas(ID);
        verifyNoInteractions(notificador);
        confirmar();
        verify(notificador).notificarCuentaSuspendida("cuenta7@correo.pe", "Publicó datos falsos.");
        verify(notificador, never()).notificarCuentaReactivada(anyString());
    }

    @Test
    @DisplayName("HU-09 R30, R32: reactivar deja ACTIVA, no revoca sesiones y avisa solo tras el commit")
    void reactivarAvisaTrasElCommit() {
        TransactionSynchronizationManager.initSynchronization();
        var usuario = dadoParaCambiar(Rol.USUARIO, EstadoUsuario.SUSPENDIDA);

        var cambio = servicio.reactivarCuenta(ADMIN, ID);

        assertThat(cambio).isEqualTo(new CambioEstadoCuentaDTO("ACTIVA", 0));
        assertThat(usuario.getEstado()).isEqualTo(EstadoUsuario.ACTIVA);
        verifyNoInteractions(sesiones, notificador);
        confirmar();
        verify(notificador).notificarCuentaReactivada("cuenta7@correo.pe");
        verify(notificador, never()).notificarCuentaSuspendida(anyString(), anyString());
    }

    @Test
    @DisplayName("HU-09 R39: si la transacción se revierte no se invoca el notificador")
    void suspensionYReactivacionRevertidasNoAvisan() {
        TransactionSynchronizationManager.initSynchronization();
        dadoParaCambiar(Rol.ARRENDADOR, EstadoUsuario.ACTIVA);
        servicio.suspenderCuenta(ADMIN, ID, "Fraude");
        when(usuarios.findParaActualizar(8L)).thenReturn(Optional.of(cuenta(8L, Rol.USUARIO,
                EstadoUsuario.SUSPENDIDA)));
        servicio.reactivarCuenta(ADMIN, 8L);

        revertir();

        verifyNoInteractions(notificador);
    }

    @Test
    @DisplayName("HU-09 R34, R39: suspender o reactivar una cuenta inexistente → 404 sin revocar ni avisar")
    void cambioSobreCuentaInexistenteDa404() {
        TransactionSynchronizationManager.initSynchronization();
        when(usuarios.findParaActualizar(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.suspenderCuenta(ADMIN, 404L, "Fraude"))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.reactivarCuenta(ADMIN, 404L))
                .isInstanceOf(RecursoNoEncontradoException.class);

        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        verifyNoInteractions(sesiones, notificador);
    }

    @Test
    @DisplayName("HU-09 R35, R39: suspender o reactivar una cuenta ADMIN o ELIMINADA → 400 no gestionable sin cambios")
    void cambioSobreCuentaNoGestionableDa400() {
        TransactionSynchronizationManager.initSynchronization();
        for (var rolYEstado : new Object[][] {{Rol.ADMIN, EstadoUsuario.ACTIVA}, {Rol.ADMIN, EstadoUsuario.SUSPENDIDA},
                {Rol.USUARIO, EstadoUsuario.ELIMINADA}, {Rol.ARRENDADOR, EstadoUsuario.ELIMINADA}}) {
            var usuario = dadoParaCambiar((Rol) rolYEstado[0], (EstadoUsuario) rolYEstado[1]);

            assertThatThrownBy(() -> servicio.suspenderCuenta(ADMIN, ID, "Fraude"))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage("Esta cuenta no se puede suspender ni reactivar.");
            assertThatThrownBy(() -> servicio.reactivarCuenta(ADMIN, ID))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage("Esta cuenta no se puede suspender ni reactivar.");
            assertThat(usuario.getEstado()).isEqualTo(rolYEstado[1]);
        }
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        verifyNoInteractions(sesiones, notificador);
    }

    @Test
    @DisplayName("HU-09 R36, R37, R39: suspender una SUSPENDIDA o reactivar una ACTIVA → 400 sin cambios ni aviso")
    void cambioSobreEstadoIgualDa400() {
        TransactionSynchronizationManager.initSynchronization();
        var suspendida = dadoParaCambiar(Rol.USUARIO, EstadoUsuario.SUSPENDIDA);
        assertThatThrownBy(() -> servicio.suspenderCuenta(ADMIN, ID, "Fraude"))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Esta cuenta ya está suspendida. Actualiza la página.");
        assertThat(suspendida.getEstado()).isEqualTo(EstadoUsuario.SUSPENDIDA);

        var activa = dadoParaCambiar(Rol.ARRENDADOR, EstadoUsuario.ACTIVA);
        assertThatThrownBy(() -> servicio.reactivarCuenta(ADMIN, ID))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Esta cuenta ya está activa. Actualiza la página.");
        assertThat(activa.getEstado()).isEqualTo(EstadoUsuario.ACTIVA);

        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        verifyNoInteractions(sesiones, notificador);
    }

    @Test
    @DisplayName("HU-09 R19, R39: suspender sin motivo o con motivo en blanco lanza sin consultar ni avisar")
    void suspenderSinMotivoLanza() {
        assertThatThrownBy(() -> servicio.suspenderCuenta(ADMIN, ID, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.suspenderCuenta(ADMIN, ID, "   "))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(usuarios, sesiones, notificador);
    }

    @Test
    @DisplayName("HU-09 R27: cuentaActiva es true solo para una cuenta existente ACTIVA, sin pedir rol")
    void cuentaActivaSoloSiExisteYEstaActiva() {
        when(usuarios.findById(1L)).thenReturn(Optional.of(cuenta(1L, Rol.ARRENDADOR, EstadoUsuario.ACTIVA)));
        when(usuarios.findById(2L)).thenReturn(Optional.of(cuenta(2L, Rol.ARRENDADOR, EstadoUsuario.SUSPENDIDA)));
        when(usuarios.findById(3L)).thenReturn(Optional.of(cuenta(3L, Rol.USUARIO, EstadoUsuario.ELIMINADA)));
        when(usuarios.findById(404L)).thenReturn(Optional.empty());

        assertThat(servicio.cuentaActiva(1L)).isTrue();
        assertThat(servicio.cuentaActiva(2L)).isFalse();
        assertThat(servicio.cuentaActiva(3L)).isFalse();
        assertThat(servicio.cuentaActiva(404L)).isFalse();
        assertThat(servicio.cuentaActiva(null)).isFalse();
    }

    @Test
    @DisplayName("HU-09 R28: arrendadoresNoActivos devuelve los ids de la consulta como conjunto, sin pedir rol")
    void arrendadoresNoActivosComoConjunto() {
        when(usuarios.findIdsArrendadoresNoActivos()).thenReturn(List.of(4L, 9L));

        assertThat(servicio.arrendadoresNoActivos()).containsExactlyInAnyOrder(4L, 9L);
    }
}
