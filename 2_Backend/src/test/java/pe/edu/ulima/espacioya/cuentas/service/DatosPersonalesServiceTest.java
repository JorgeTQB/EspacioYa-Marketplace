package pe.edu.ulima.espacioya.cuentas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.comun.almacenamiento.ArchivosPrivados;
import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.DemasiadosIntentosException;
import pe.edu.ulima.espacioya.comun.error.ErrorCampoDTO;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.error.ValidacionException;
import pe.edu.ulima.espacioya.comun.limite.LimitadorIntentos;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.domain.DocumentoIdentidad;
import pe.edu.ulima.espacioya.cuentas.domain.EstadoIdentidad;
import pe.edu.ulima.espacioya.cuentas.domain.EstadoUsuario;
import pe.edu.ulima.espacioya.cuentas.domain.Mfa;
import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.TipoDocumento;
import pe.edu.ulima.espacioya.cuentas.domain.TipoDocumentoIdentidad;
import pe.edu.ulima.espacioya.cuentas.domain.TipoToken;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.repository.CodigoRespaldoRepository;
import pe.edu.ulima.espacioya.cuentas.repository.DesafioMfaRepository;
import pe.edu.ulima.espacioya.cuentas.repository.DocumentoIdentidadRepository;
import pe.edu.ulima.espacioya.cuentas.repository.MfaRepository;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;
import pe.edu.ulima.espacioya.cuentas.repository.SesionRepository;
import pe.edu.ulima.espacioya.cuentas.repository.TokenRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;
import pe.edu.ulima.espacioya.cuentas.service.puertos.Impedimento;
import pe.edu.ulima.espacioya.cuentas.service.puertos.ParticipanteDatosPersonales;
import pe.edu.ulima.espacioya.cuentas.service.puertos.SeccionDatos;
import pe.edu.ulima.espacioya.cuentas.service.puertos.TitularDatos;

@ExtendWith(MockitoExtension.class)
class DatosPersonalesServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-04T15:00:00Z");
    private static final Instant REGISTRO = Instant.parse("2026-10-20T13:00:00Z");
    private static final String IP = "10.22.0.1";
    private static final long ID = 41L;
    private static final String CONTRASENA = "Segura123!";
    private static final String HASH_NUEVO = "$2a$12$nuevo";
    private static final String FOTO = "perfiles/11111111-1111-1111-1111-111111111111";
    private static final String DNI_FRENTE = "identidad/22222222-2222-2222-2222-222222222222";
    private static final String DNI_DORSO = "identidad/33333333-3333-3333-3333-333333333333";
    private static final UsuarioAutenticado USUARIO = new UsuarioAutenticado(ID, 70L, "USUARIO");
    private static final UsuarioAutenticado ARRENDADOR = new UsuarioAutenticado(ID, 70L, "ARRENDADOR");
    private static final UsuarioAutenticado ADMIN = new UsuarioAutenticado(ID, 70L, "ADMIN");
    private static final TitularDatos TITULAR_USUARIO = new TitularDatos(ID, "USUARIO");
    private static final TitularDatos TITULAR_ARRENDADOR = new TitularDatos(ID, "ARRENDADOR");

    @Mock
    private UsuarioRepository usuarios;
    @Mock
    private PerfilArrendadorRepository perfiles;
    @Mock
    private DocumentoIdentidadRepository documentos;
    @Mock
    private TokenRepository tokens;
    @Mock
    private SesionRepository sesiones;
    @Mock
    private MfaRepository mfas;
    @Mock
    private CodigoRespaldoRepository codigos;
    @Mock
    private DesafioMfaRepository desafios;
    @Mock
    private ParticipanteDatosPersonales reservas;
    @Mock
    private ParticipanteDatosPersonales pagos;
    @Mock
    private CuentaService cuentas;
    @Mock
    private PasswordEncoder encoder;
    @Mock
    private ArchivosPrivados archivos;
    @Mock
    private AuditoriaService auditoria;
    @Mock
    private PlatformTransactionManager transaccion;

    private LimitadorIntentos limitador;
    private DatosPersonalesService servicio;

    @BeforeEach
    void preparar() {
        var reloj = Clock.fixed(AHORA, ZoneOffset.UTC);
        limitador = new LimitadorIntentos(reloj);
        var reautenticacion = new ReautenticacionPerfil(cuentas, limitador, auditoria);
        servicio = new DatosPersonalesService(usuarios, perfiles, documentos, tokens, sesiones, mfas, codigos,
                desafios, List.of(reservas, pagos), reautenticacion, encoder, archivos, auditoria,
                new TransactionTemplate(transaccion), reloj);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void limpiar() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private static void confirmarTransaccion() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    private static void revertirTransaccion() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    }

    private static Usuario usuario() {
        var cuenta = Usuario.registrar("leo@correo.pe", "$2a$12$viejo", "2026-10", REGISTRO);
        ReflectionTestUtils.setField(cuenta, "id", ID);
        cuenta.actualizarDatos("Leo Paz", "912345678");
        cuenta.solicitarCambioCorreo("leo.nuevo@correo.pe");
        cuenta.cambiarFoto(FOTO, "image/jpeg");
        return cuenta;
    }

    private static Usuario arrendador() {
        var cuenta = Usuario.registrarArrendador("ana@correo.pe", "$2a$12$viejo", "Ana Ríos", "987654321",
                "2026-10", REGISTRO);
        ReflectionTestUtils.setField(cuenta, "id", ID);
        return cuenta;
    }

    private static PerfilArrendador perfil() {
        var perfil = PerfilArrendador.crear(ID, TipoDocumento.RUC, "20100075678", "a".repeat(64), "Ana Ríos EIRL",
                "Av. Arequipa 123");
        ReflectionTestUtils.setField(perfil, "estadoIdentidad", EstadoIdentidad.VERIFICADA);
        return perfil;
    }

    private static DocumentoIdentidad documento(TipoDocumentoIdentidad tipo, String ruta) {
        return DocumentoIdentidad.registrar(ID, tipo, ruta, "image/png", 100, REGISTRO);
    }

    private void contrasenaCorrecta(UsuarioAutenticado sesion) {
        when(cuentas.confirmarContrasena(sesion, CONTRASENA)).thenReturn(true);
    }

    private void paraExportar(Usuario cuenta, PerfilArrendador perfil, boolean mfaActivo) {
        when(usuarios.findById(ID)).thenReturn(Optional.of(cuenta));
        if (perfil != null) {
            when(perfiles.findById(ID)).thenReturn(Optional.of(perfil));
        }
        when(mfas.existsByUsuarioIdAndActivoTrue(ID)).thenReturn(mfaActivo);
    }

    private void paraEliminar(Usuario cuenta, TitularDatos titular) {
        when(encoder.encode(anyString())).thenReturn(HASH_NUEVO);
        when(usuarios.findParaActualizar(ID)).thenReturn(Optional.of(cuenta));
        when(reservas.impedimentos(eq(titular), any())).thenReturn(List.of());
        when(pagos.impedimentos(eq(titular), any())).thenReturn(List.of());
    }

    private Usuario eliminarUsuario() {
        var cuenta = usuario();
        contrasenaCorrecta(USUARIO);
        paraEliminar(cuenta, TITULAR_USUARIO);
        servicio.eliminar(USUARIO, CONTRASENA, IP);
        return cuenta;
    }

    private PerfilArrendador eliminarArrendador() {
        var perfil = perfil();
        contrasenaCorrecta(ARRENDADOR);
        paraEliminar(arrendador(), TITULAR_ARRENDADOR);
        when(perfiles.findParaActualizar(ID)).thenReturn(Optional.of(perfil));
        when(documentos.findByArrendadorIdOrderByTipoAsc(ID)).thenReturn(List.of(
                documento(TipoDocumentoIdentidad.IDENTIDAD, DNI_FRENTE),
                documento(TipoDocumentoIdentidad.PROPIEDAD, DNI_DORSO)));
        servicio.eliminar(ARRENDADOR, CONTRASENA, IP);
        return perfil;
    }

    private EventoAuditoria auditado(String accion) {
        var eventos = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria, atLeastOnce()).registrar(eventos.capture());
        return eventos.getAllValues().stream().filter(e -> e.accion().equals(accion)).findFirst().orElseThrow();
    }

    // ---- Exportación ----

    @Test
    @DisplayName("HU-22 R1: el usuario con contraseña correcta recibe generadaEn, perfil y las secciones reservas y "
            + "pagos")
    void exportaUsuario() {
        contrasenaCorrecta(USUARIO);
        paraExportar(usuario(), null, false);
        when(reservas.exportar(TITULAR_USUARIO)).thenReturn(List.of(
                new SeccionDatos("reservas", List.of("r1")), new SeccionDatos("pagos", List.of("p1"))));
        when(pagos.exportar(TITULAR_USUARIO)).thenReturn(List.of());

        var exportacion = servicio.exportar(USUARIO, CONTRASENA, IP);

        assertThat(exportacion.generadaEn()).isEqualTo(AHORA);
        assertThat(exportacion.perfil()).isNotNull();
        assertThat(exportacion.perfil().facturacion()).isNull();
        assertThat(exportacion.secciones()).containsExactly(Map.entry("reservas", List.of("r1")),
                Map.entry("pagos", List.of("p1")));
    }

    @Test
    @DisplayName("HU-22 R2: el arrendador recibe reservas, liquidaciones y cuentaBancaria (null si no tiene) en el "
            + "orden de los participantes")
    void exportaArrendador() {
        contrasenaCorrecta(ARRENDADOR);
        paraExportar(arrendador(), perfil(), false);
        when(reservas.exportar(TITULAR_ARRENDADOR)).thenReturn(List.of(new SeccionDatos("reservas", List.of())));
        when(pagos.exportar(TITULAR_ARRENDADOR)).thenReturn(List.of(new SeccionDatos("liquidaciones", List.of()),
                new SeccionDatos("cuentaBancaria", null)));

        var exportacion = servicio.exportar(ARRENDADOR, CONTRASENA, IP);

        assertThat(exportacion.secciones().keySet()).containsExactly("reservas", "liquidaciones", "cuentaBancaria");
        assertThat(exportacion.secciones().get("cuentaBancaria")).isNull();
        assertThat(exportacion.secciones()).containsKey("cuentaBancaria");
    }

    @Test
    @DisplayName("HU-22 R3: el bloque perfil trae rol, correo, correo pendiente, nombre, teléfono, foto, MFA y "
            + "consentimiento de la cuenta")
    void perfilExportado() {
        contrasenaCorrecta(USUARIO);
        paraExportar(usuario(), null, true);
        when(reservas.exportar(TITULAR_USUARIO)).thenReturn(List.of());
        when(pagos.exportar(TITULAR_USUARIO)).thenReturn(List.of());

        var perfil = servicio.exportar(USUARIO, CONTRASENA, IP).perfil();

        assertThat(perfil.rol()).isEqualTo("USUARIO");
        assertThat(perfil.correo()).isEqualTo("leo@correo.pe");
        assertThat(perfil.correoPendiente()).isEqualTo("leo.nuevo@correo.pe");
        assertThat(perfil.nombre()).isEqualTo("Leo Paz");
        assertThat(perfil.telefono()).isEqualTo("912345678");
        assertThat(perfil.tieneFoto()).isTrue();
        assertThat(perfil.mfaActivo()).isTrue();
        assertThat(perfil.consentimiento().versionPolitica()).isEqualTo("2026-10");
        assertThat(perfil.consentimiento().fechaConsentimiento()).isEqualTo(REGISTRO);
    }

    @Test
    @DisplayName("HU-22 R4: el arrendador exporta su facturación con el documento enmascarado")
    void facturacionEnmascarada() {
        contrasenaCorrecta(ARRENDADOR);
        paraExportar(arrendador(), perfil(), false);
        when(reservas.exportar(TITULAR_ARRENDADOR)).thenReturn(List.of());
        when(pagos.exportar(TITULAR_ARRENDADOR)).thenReturn(List.of());

        var facturacion = servicio.exportar(ARRENDADOR, CONTRASENA, IP).perfil().facturacion();

        assertThat(facturacion.tipoDocumento()).isEqualTo("RUC");
        assertThat(facturacion.numeroDocumento()).isEqualTo("****5678");
        assertThat(facturacion.razonSocial()).isEqualTo("Ana Ríos EIRL");
        assertThat(facturacion.direccionFiscal()).isEqualTo("Av. Arequipa 123");
        assertThat(facturacion.estadoIdentidad()).isEqualTo("VERIFICADA");
    }

    @Test
    @DisplayName("HU-22 R14: la exportación audita DATOS_EXPORTADOS con las claves de las secciones y sin valores")
    void auditaExportacion() {
        contrasenaCorrecta(USUARIO);
        paraExportar(usuario(), null, false);
        when(reservas.exportar(TITULAR_USUARIO)).thenReturn(List.of(
                new SeccionDatos("reservas", List.of()), new SeccionDatos("pagos", List.of())));
        when(pagos.exportar(TITULAR_USUARIO)).thenReturn(List.of());

        servicio.exportar(USUARIO, CONTRASENA, IP);

        assertThat(auditado("DATOS_EXPORTADOS")).isEqualTo(new EventoAuditoria(ID, "DATOS_EXPORTADOS", "usuario", ID,
                IP, "secciones=reservas,pagos"));
    }

    @Test
    @DisplayName("HU-22 R45: una sección repetida entre participantes es un error interno y no se audita")
    void seccionRepetida() {
        contrasenaCorrecta(USUARIO);
        paraExportar(usuario(), null, false);
        when(reservas.exportar(TITULAR_USUARIO)).thenReturn(List.of(new SeccionDatos("reservas", List.of())));
        when(pagos.exportar(TITULAR_USUARIO)).thenReturn(List.of(new SeccionDatos("reservas", List.of())));

        assertThatThrownBy(() -> servicio.exportar(USUARIO, CONTRASENA, IP)).isInstanceOf(IllegalStateException.class);
        verify(auditoria, never()).registrar(any());
    }

    // ---- Reautenticación ----

    @Test
    @DisplayName("HU-22 R15: sin contraseña, exportar y eliminar dan 400 en contrasena sin leer ni cambiar nada")
    void sinContrasena() {
        assertThatThrownBy(() -> servicio.exportar(USUARIO, " ", IP))
                .isInstanceOfSatisfying(ReglaNegocioException.class, e -> {
                    assertThat(e.getCampo()).isEqualTo("contrasena");
                    assertThat(e.getMessage()).isEqualTo("Ingresa tu contraseña para confirmar.");
                });
        assertThatThrownBy(() -> servicio.eliminar(ARRENDADOR, null, IP))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.getCampo()).isEqualTo("contrasena"));

        verifyNoInteractions(usuarios, reservas, pagos, transaccion, encoder, sesiones);
    }

    @Test
    @DisplayName("HU-22 R16: una contraseña incorrecta da 400 en contrasena sin abrir la transacción")
    void contrasenaIncorrecta() {
        when(cuentas.confirmarContrasena(USUARIO, "Mala123!")).thenReturn(false);

        assertThatThrownBy(() -> servicio.exportar(USUARIO, "Mala123!", IP))
                .isInstanceOfSatisfying(ReglaNegocioException.class, e -> {
                    assertThat(e.getCampo()).isEqualTo("contrasena");
                    assertThat(e.getMessage()).isEqualTo("La contraseña no es correcta.");
                });

        verifyNoInteractions(usuarios, reservas, pagos, transaccion);
        assertThat(auditado("PERFIL_CONTRASENA_FALLIDA").detalle()).isEqualTo("operacion=EXPORTACION");
    }

    @Test
    @DisplayName("HU-22 R18: con 5 fallos en el cupo, exportar y eliminar dan 429 sin comprobar la contraseña")
    void cupoAgotado() {
        for (int i = 0; i < ReautenticacionPerfil.MAXIMO_FALLOS; i++) {
            limitador.registrar("perfil|" + ID);
        }

        assertThatThrownBy(() -> servicio.exportar(USUARIO, CONTRASENA, IP))
                .isInstanceOf(DemasiadosIntentosException.class);
        assertThatThrownBy(() -> servicio.eliminar(USUARIO, CONTRASENA, IP))
                .isInstanceOf(DemasiadosIntentosException.class);

        verifyNoInteractions(cuentas, usuarios, reservas, pagos, transaccion, encoder);
    }

    @Test
    @DisplayName("HU-22 R19: el ADMIN recibe 403 en exportar y eliminar sin comprobar la contraseña ni leer datos")
    void adminRechazado() {
        assertThatThrownBy(() -> servicio.exportar(ADMIN, CONTRASENA, IP)).isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> servicio.eliminar(ADMIN, CONTRASENA, IP)).isInstanceOf(AccesoDenegadoException.class);

        verifyNoInteractions(cuentas, usuarios, perfiles, mfas, reservas, pagos, transaccion, encoder, auditoria);
    }

    @Test
    @DisplayName("HU-22 R20: con contraseña incorrecta la eliminación no consulta impedimentos")
    void contrasenaIncorrectaNoRevelaImpedimentos() {
        when(cuentas.confirmarContrasena(USUARIO, "Mala123!")).thenReturn(false);

        assertThatThrownBy(() -> servicio.eliminar(USUARIO, "Mala123!", IP))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.getCampo()).isEqualTo("contrasena"));

        verifyNoInteractions(reservas, pagos, usuarios, transaccion);
        assertThat(auditado("PERFIL_CONTRASENA_FALLIDA").detalle()).isEqualTo("operacion=ELIMINACION");
    }

    // ---- Eliminación ----

    @Test
    @DisplayName("HU-22 R21: sin impedimentos, la cuenta queda ELIMINADA y la transacción se confirma")
    void eliminaCuenta() {
        var cuenta = eliminarUsuario();

        assertThat(cuenta.getEstado()).isEqualTo(EstadoUsuario.ELIMINADA);
        verify(transaccion).commit(any());
    }

    @Test
    @DisplayName("HU-22 R22: la eliminación pone el marcador de correo y deja nulos nombre, teléfono, correo "
            + "pendiente y foto")
    void anonimizaDatosDeUsuario() {
        var cuenta = eliminarUsuario();

        assertThat(cuenta.getCorreo()).isEqualTo("eliminada-41@espacioya.invalid");
        assertThat(cuenta.getNombre()).isNull();
        assertThat(cuenta.getTelefono()).isNull();
        assertThat(cuenta.getCorreoPendiente()).isNull();
        assertThat(cuenta.getFotoRuta()).isNull();
        assertThat(cuenta.getFotoTipoMime()).isNull();
    }

    @Test
    @DisplayName("HU-22 R23: el hash nuevo es bcrypt de un secreto aleatorio calculado antes de abrir la transacción")
    void hashFueraDeLaTransaccion() {
        var cuenta = eliminarUsuario();

        assertThat(cuenta.getHashContrasena()).isEqualTo(HASH_NUEVO);
        var secreto = ArgumentCaptor.forClass(String.class);
        var orden = inOrder(encoder, transaccion);
        orden.verify(encoder).encode(secreto.capture());
        orden.verify(transaccion).getTransaction(any());
        assertThat(secreto.getValue()).hasSize(43).isNotEqualTo(CONTRASENA);
    }

    @Test
    @DisplayName("HU-22 R24: la foto se borra de ArchivosPrivados solo después del commit")
    void fotoTrasElCommit() {
        eliminarUsuario();

        verify(archivos, never()).eliminar(anyString());
        confirmarTransaccion();
        verify(archivos).eliminar(FOTO);
    }

    @Test
    @DisplayName("HU-22 R24: con rollback la foto no se borra")
    void fotoNoSeBorraConRollback() {
        contrasenaCorrecta(USUARIO);
        paraEliminar(usuario(), TITULAR_USUARIO);
        when(sesiones.revocarTodas(ID)).thenThrow(new IllegalStateException("falla"));

        assertThatThrownBy(() -> servicio.eliminar(USUARIO, CONTRASENA, IP)).isInstanceOf(IllegalStateException.class);
        revertirTransaccion();

        verify(transaccion).rollback(any());
        verify(archivos, never()).eliminar(anyString());
    }

    @Test
    @DisplayName("HU-22 R25: el perfil del arrendador se anonimiza bajo su bloqueo")
    void anonimizaPerfil() {
        var perfil = eliminarArrendador();

        assertThat(perfil.getNumeroDocumento()).isEqualTo("Cuenta eliminada");
        assertThat(perfil.getRazonSocial()).isEqualTo("Cuenta eliminada");
        assertThat(perfil.getDireccionFiscal()).isEqualTo("Cuenta eliminada");
        assertThat(perfil.getHashDocumento()).isEqualTo("eliminada-41");
        verify(perfiles).findParaActualizar(ID);
    }

    @Test
    @DisplayName("HU-22 R26: los documentos de identidad se borran y sus archivos solo tras el commit")
    void borraDocumentos() {
        eliminarArrendador();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DocumentoIdentidad>> borrados = ArgumentCaptor.forClass(List.class);
        verify(documentos).deleteAllInBatch(borrados.capture());
        assertThat(borrados.getValue()).extracting(DocumentoIdentidad::getRutaArchivo)
                .containsExactly(DNI_FRENTE, DNI_DORSO);
        verify(archivos, never()).eliminar(anyString());
        confirmarTransaccion();
        verify(archivos).eliminar(DNI_FRENTE);
        verify(archivos).eliminar(DNI_DORSO);
    }

    @Test
    @DisplayName("HU-22 R28: revocarTodas es el último paso de la transacción")
    void revocaSesionesAlFinal() {
        eliminarUsuario();

        var orden = inOrder(tokens, codigos, desafios, reservas, pagos, auditoria, sesiones, transaccion);
        orden.verify(tokens, atLeastOnce()).anularPendientes(eq(ID), any(), eq(AHORA));
        orden.verify(codigos).borrarDeUsuario(ID);
        orden.verify(desafios).borrarDeUsuario(ID);
        orden.verify(reservas).anonimizar(TITULAR_USUARIO);
        orden.verify(pagos).anonimizar(TITULAR_USUARIO);
        orden.verify(auditoria).registrar(any());
        orden.verify(sesiones).revocarTodas(ID);
        orden.verify(transaccion).commit(any());
    }

    @Test
    @DisplayName("HU-22 R29: se anulan los tokens pendientes de todos los tipos")
    void anulaTokens() {
        eliminarUsuario();

        for (var tipo : TipoToken.values()) {
            verify(tokens).anularPendientes(ID, tipo, AHORA);
        }
        assertThat(TipoToken.values()).contains(TipoToken.RECUPERACION, TipoToken.CAMBIO_CORREO);
    }

    @Test
    @DisplayName("HU-22 R30: se borran los códigos de respaldo, el MFA y los desafíos MFA")
    void borraMfa() {
        var mfa = Mfa.pendiente(ID, "JBSWY3DPEHPK3PXP", REGISTRO);
        when(mfas.findById(ID)).thenReturn(Optional.of(mfa));

        eliminarUsuario();

        var orden = inOrder(codigos, mfas, desafios);
        orden.verify(codigos).borrarDeUsuario(ID);
        orden.verify(mfas).delete(mfa);
        orden.verify(desafios).borrarDeUsuario(ID);
    }

    @Test
    @DisplayName("HU-22 R34: la eliminación audita CUENTA_ELIMINADA con el rol en el detalle")
    void auditaEliminacion() {
        eliminarArrendador();

        assertThat(auditado("CUENTA_ELIMINADA")).isEqualTo(new EventoAuditoria(ID, "CUENTA_ELIMINADA", "usuario", ID,
                IP, "rol=ARRENDADOR"));
    }

    @Test
    @DisplayName("HU-22 R35: si un participante falla al anonimizar, la excepción se propaga, se revierte y no se "
            + "audita CUENTA_ELIMINADA")
    void participanteQueFallaRevierte() {
        contrasenaCorrecta(USUARIO);
        paraEliminar(usuario(), TITULAR_USUARIO);
        doThrow(new IllegalStateException("falla")).when(pagos).anonimizar(TITULAR_USUARIO);

        assertThatThrownBy(() -> servicio.eliminar(USUARIO, CONTRASENA, IP)).isInstanceOf(IllegalStateException.class);

        verify(transaccion).rollback(any());
        verify(transaccion, never()).commit(any());
        verify(auditoria, never()).registrar(any());
        verify(sesiones, never()).revocarTodas(any());
    }

    @Test
    @DisplayName("HU-22 R40: los impedimentos de todos los participantes vuelven juntos, en orden, sin anonimizar")
    void impedimentosJuntos() {
        var cuenta = arrendador();
        contrasenaCorrecta(ARRENDADOR);
        when(encoder.encode(anyString())).thenReturn(HASH_NUEVO);
        when(usuarios.findParaActualizar(ID)).thenReturn(Optional.of(cuenta));
        when(reservas.impedimentos(TITULAR_ARRENDADOR, AHORA))
                .thenReturn(List.of(new Impedimento("reservas", "R")));
        when(pagos.impedimentos(TITULAR_ARRENDADOR, AHORA))
                .thenReturn(List.of(new Impedimento("liquidaciones", "L"), new Impedimento("pagos", "P")));

        assertThatThrownBy(() -> servicio.eliminar(ARRENDADOR, CONTRASENA, IP))
                .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.getErrores()).containsExactly(
                        new ErrorCampoDTO("reservas", "R"), new ErrorCampoDTO("liquidaciones", "L"),
                        new ErrorCampoDTO("pagos", "P")));

        assertThat(cuenta.getEstado()).isEqualTo(EstadoUsuario.ACTIVA);
        assertThat(cuenta.getCorreo()).isEqualTo("ana@correo.pe");
        verify(reservas, never()).anonimizar(any());
        verify(pagos, never()).anonimizar(any());
        verify(auditoria, never()).registrar(any());
        verify(sesiones, never()).revocarTodas(any());
        verify(transaccion).rollback(any());
    }

    @Test
    @DisplayName("HU-22 R42: los impedimentos se leen después de bloquear la fila de usuario y dentro de la "
            + "transacción")
    void impedimentosBajoElBloqueo() {
        eliminarUsuario();

        var orden = inOrder(transaccion, usuarios, reservas, pagos);
        orden.verify(transaccion).getTransaction(any());
        orden.verify(usuarios).findParaActualizar(ID);
        orden.verify(reservas).impedimentos(TITULAR_USUARIO, AHORA);
        orden.verify(pagos).impedimentos(TITULAR_USUARIO, AHORA);
        orden.verify(reservas).anonimizar(TITULAR_USUARIO);
        orden.verify(transaccion).commit(any());
    }

    @Test
    @DisplayName("HU-22 R45: exportar, impedimentos y anonimizar se piden a todos los participantes")
    void todosLosParticipantes() {
        contrasenaCorrecta(USUARIO);
        paraExportar(usuario(), null, false);
        when(reservas.exportar(TITULAR_USUARIO)).thenReturn(List.of());
        when(pagos.exportar(TITULAR_USUARIO)).thenReturn(List.of(new SeccionDatos("otra", List.of())));

        assertThat(servicio.exportar(USUARIO, CONTRASENA, IP).secciones()).containsOnlyKeys("otra");
        eliminarUsuario();

        verify(reservas).exportar(TITULAR_USUARIO);
        verify(pagos).exportar(TITULAR_USUARIO);
        verify(reservas).impedimentos(TITULAR_USUARIO, AHORA);
        verify(pagos).impedimentos(TITULAR_USUARIO, AHORA);
        verify(reservas).anonimizar(TITULAR_USUARIO);
        verify(pagos).anonimizar(TITULAR_USUARIO);
    }
}
