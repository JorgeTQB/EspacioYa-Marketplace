package pe.edu.ulima.espacioya.cuentas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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

import pe.edu.ulima.espacioya.comun.almacenamiento.ArchivoRecibido;
import pe.edu.ulima.espacioya.comun.almacenamiento.ArchivosDePrueba;
import pe.edu.ulima.espacioya.comun.almacenamiento.ArchivosPrivados;
import pe.edu.ulima.espacioya.comun.almacenamiento.ValidadorArchivo;
import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.ErrorCampoDTO;
import pe.edu.ulima.espacioya.comun.error.ErrorDTO;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.error.ValidacionException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.domain.EstadoIdentidad;
import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.TipoDocumento;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.dto.DatosPerfilDTO;
import pe.edu.ulima.espacioya.cuentas.dto.RegistroArrendadorDTO;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class PerfilServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-04T15:00:00Z");
    private static final String IP = "10.20.1.1";
    private static final long ID = 7L;
    private static final UsuarioAutenticado USUARIO = new UsuarioAutenticado(ID, 70L, "USUARIO");
    private static final UsuarioAutenticado ARRENDADOR = new UsuarioAutenticado(ID, 70L, "ARRENDADOR");
    private static final UsuarioAutenticado ADMIN = new UsuarioAutenticado(ID, 70L, "ADMIN");
    private static final String CLAVE_NUEVA = "perfiles/22222222-2222-2222-2222-222222222222";
    private static final String CLAVE_ANTERIOR = "perfiles/11111111-1111-1111-1111-111111111111";

    @Mock
    private UsuarioRepository usuarios;
    @Mock
    private PerfilArrendadorRepository perfiles;
    @Mock
    private ArchivosPrivados archivos;
    @Mock
    private AuditoriaService auditoria;

    private PerfilService servicio;

    @BeforeEach
    void preparar() {
        servicio = new PerfilService(usuarios, perfiles, new ValidadorArchivo(), archivos, auditoria,
                new TransactionTemplate(mock(PlatformTransactionManager.class)));
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void limpiar() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private static void confirmarTransaccion() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    private static Usuario usuario() {
        var cuenta = Usuario.registrar("ana@correo.pe", "$2a$12$hash", "2026-10", AHORA);
        ReflectionTestUtils.setField(cuenta, "id", ID);
        return cuenta;
    }

    private static Usuario arrendador() {
        var cuenta = Usuario.registrarArrendador("ana@correo.pe", "$2a$12$hash", "Ana Pérez", "987654321",
                "2026-10", AHORA);
        ReflectionTestUtils.setField(cuenta, "id", ID);
        return cuenta;
    }

    private static PerfilArrendador perfil() {
        var perfil = PerfilArrendador.crear(ID, TipoDocumento.RUC, "20100070970", "a".repeat(64), "Canchas SAC",
                "Av. Lima 123");
        ReflectionTestUtils.setField(perfil, "estadoIdentidad", EstadoIdentidad.VERIFICADA);
        return perfil;
    }

    private void cuenta(Usuario cuenta) {
        when(usuarios.findById(ID)).thenReturn(Optional.of(cuenta));
    }

    private void cuentaBloqueada(Usuario cuenta) {
        when(usuarios.findParaActualizar(ID)).thenReturn(Optional.of(cuenta));
    }

    private EventoAuditoria unicaAuditoria() {
        var captor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(captor.capture());
        return captor.getValue();
    }

    // ── Acceso ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-20 R2: un ADMIN recibe 403 en todas las operaciones del perfil sin leer ni escribir nada")
    void adminRecibe403() {
        var foto = new ArchivoRecibido(ArchivosDePrueba.png(10, 10), 100);

        assertThatThrownBy(() -> servicio.consultar(ADMIN)).isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> servicio.actualizarDatos(ADMIN, new DatosPerfilDTO("Admin", null), IP))
                .isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> servicio.guardarFoto(ADMIN, foto, IP)).isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> servicio.foto(ADMIN)).isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> servicio.quitarFoto(ADMIN, IP)).isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> servicio.consultar(null)).isInstanceOf(AccesoDenegadoException.class);
        verifyNoInteractions(usuarios, perfiles, archivos, auditoria);
    }

    // ── Consulta ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-20 R5, R6: un ARRENDADOR consulta su perfil con la facturación y el número enmascarado")
    void consultaArrendador() {
        var cuenta = arrendador();
        cuenta.solicitarCambioCorreo("nuevo@correo.pe");
        cuenta(cuenta);
        when(perfiles.findById(ID)).thenReturn(Optional.of(perfil()));

        var dto = servicio.consultar(ARRENDADOR);

        assertThat(dto.rol()).isEqualTo("ARRENDADOR");
        assertThat(dto.nombre()).isEqualTo("Ana Pérez");
        assertThat(dto.telefono()).isEqualTo("987654321");
        assertThat(dto.correo()).isEqualTo("ana@correo.pe");
        assertThat(dto.correoPendiente()).isEqualTo("nuevo@correo.pe");
        assertThat(dto.tieneFoto()).isFalse();
        assertThat(dto.facturacion().tipoDocumento()).isEqualTo("RUC");
        assertThat(dto.facturacion().numeroDocumentoEnmascarado()).isEqualTo("****0970");
        assertThat(dto.facturacion().razonSocial()).isEqualTo("Canchas SAC");
        assertThat(dto.facturacion().direccionFiscal()).isEqualTo("Av. Lima 123");
        assertThat(dto.facturacion().estadoIdentidad()).isEqualTo("VERIFICADA");
        assertThat(dto.toString()).doesNotContain("20100070970").doesNotContain("a".repeat(64))
                .doesNotContain("$2a$12$hash");
    }

    @Test
    @DisplayName("HU-20 R5, R4: un USUARIO consulta su perfil sin facturación y por el id de la sesión")
    void consultaUsuario() {
        var cuenta = usuario();
        cuenta.cambiarFoto(CLAVE_ANTERIOR, "image/png");
        cuenta(cuenta);

        var dto = servicio.consultar(USUARIO);

        assertThat(dto.rol()).isEqualTo("USUARIO");
        assertThat(dto.nombre()).isNull();
        assertThat(dto.correoPendiente()).isNull();
        assertThat(dto.tieneFoto()).isTrue();
        assertThat(dto.facturacion()).isNull();
        assertThat(dto.toString()).doesNotContain(CLAVE_ANTERIOR);
        verify(usuarios).findById(ID);
        verifyNoInteractions(perfiles);
    }

    // ── Datos básicos ────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-20 R8, R47: guarda nombre y teléfono bajo bloqueo y audita solo los nombres de campo")
    void actualizaDatos() {
        var cuenta = usuario();
        cuentaBloqueada(cuenta);
        cuenta(cuenta);

        var dto = servicio.actualizarDatos(USUARIO, new DatosPerfilDTO("  Ana Secreta ", "912345678"), IP);

        assertThat(dto.nombre()).isEqualTo("Ana Secreta");
        assertThat(dto.telefono()).isEqualTo("912345678");
        var evento = unicaAuditoria();
        assertThat(evento).isEqualTo(new EventoAuditoria(ID, "PERFIL_ACTUALIZADO", "usuario", ID, IP,
                "campos=nombre,telefono"));
        assertThat(evento.detalle()).doesNotContain("Ana").doesNotContain("912345678");
    }

    @Test
    @DisplayName("HU-20 R47: si no cambia ningún dato no audita")
    void sinCambiosNoAudita() {
        var cuenta = arrendador();
        cuentaBloqueada(cuenta);
        cuenta(cuenta);
        when(perfiles.findById(ID)).thenReturn(Optional.of(perfil()));

        servicio.actualizarDatos(ARRENDADOR, new DatosPerfilDTO("Ana Pérez", "987654321"), IP);

        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("HU-20 R10: un ARRENDADOR con nombre en blanco y sin teléfono recibe un error por campo sin guardar")
    void arrendadorConObligatorios() {
        assertThatThrownBy(() -> servicio.actualizarDatos(ARRENDADOR, new DatosPerfilDTO("  ", null), IP))
                .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.getErrores()).containsExactly(
                        new ErrorCampoDTO("nombre", RegistroArrendadorDTO.MENSAJE_NOMBRE),
                        new ErrorCampoDTO("telefono", RegistroArrendadorDTO.MENSAJE_TELEFONO)));
        assertThatThrownBy(() -> servicio.actualizarDatos(ARRENDADOR, new DatosPerfilDTO("Ana", ""), IP))
                .isInstanceOfSatisfying(ValidacionException.class, e -> assertThat(e.getErrores())
                        .extracting(ErrorCampoDTO::campo).containsExactly("telefono"));
        verifyNoInteractions(usuarios, auditoria);
    }

    @Test
    @DisplayName("HU-20 R11: un USUARIO que deja nombre y teléfono en blanco los guarda nulos")
    void usuarioBorraDatos() {
        var cuenta = usuario();
        cuenta.actualizarDatos("Ana", "987654321");
        cuentaBloqueada(cuenta);
        cuenta(cuenta);

        var dto = servicio.actualizarDatos(USUARIO, new DatosPerfilDTO("", null), IP);

        assertThat(dto.nombre()).isNull();
        assertThat(dto.telefono()).isNull();
        assertThat(cuenta.getNombre()).isNull();
        assertThat(cuenta.getTelefono()).isNull();
    }

    @Test
    @DisplayName("HU-20 R12: guardar datos básicos no pide contraseña ni toca correo, foto ni facturación")
    void datosNoTocanLoDemas() {
        var cuenta = arrendador();
        cuenta.cambiarFoto(CLAVE_ANTERIOR, "image/png");
        cuentaBloqueada(cuenta);
        cuenta(cuenta);
        var perfil = perfil();
        when(perfiles.findById(ID)).thenReturn(Optional.of(perfil));

        servicio.actualizarDatos(ARRENDADOR, new DatosPerfilDTO("Ana María", "912345678"), IP);

        assertThat(cuenta.getCorreo()).isEqualTo("ana@correo.pe");
        assertThat(cuenta.getCorreoPendiente()).isNull();
        assertThat(cuenta.getFotoRuta()).isEqualTo(CLAVE_ANTERIOR);
        assertThat(perfil.getRazonSocial()).isEqualTo("Canchas SAC");
        verify(perfiles, never()).findParaActualizar(any());
        verify(perfiles, never()).save(any());
        verifyNoInteractions(archivos);
    }

    // ── Foto ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("HU-20 R13, R48: guarda la foto re-codificada y sin EXIF en perfiles, la registra y la audita")
    void guardaFoto() {
        var cuenta = usuario();
        cuentaBloqueada(cuenta);
        cuenta(cuenta);
        var jpeg = ArchivosDePrueba.jpegConExif();
        when(archivos.guardar(eq("perfiles"), any())).thenReturn(CLAVE_NUEVA);

        var dto = servicio.guardarFoto(USUARIO, new ArchivoRecibido(jpeg, jpeg.length), IP);

        var guardado = ArgumentCaptor.forClass(byte[].class);
        verify(archivos).guardar(eq("perfiles"), guardado.capture());
        assertThat(ArchivosDePrueba.contiene(guardado.getValue(), ArchivosDePrueba.SECRETO_EXIF)).isFalse();
        assertThat(dto.tieneFoto()).isTrue();
        assertThat(cuenta.getFotoRuta()).isEqualTo(CLAVE_NUEVA);
        assertThat(cuenta.getFotoTipoMime()).isEqualTo("image/jpeg");
        assertThat(unicaAuditoria()).isEqualTo(new EventoAuditoria(ID, "FOTO_PERFIL_ACTUALIZADA", "usuario", ID, IP,
                "mime=image/jpeg;tamano=" + jpeg.length));
    }

    @Test
    @DisplayName("HU-20 R14: una foto vacía, enorme, ilegible, PDF o de otro tipo da 400 en foto sin guardar nada")
    void rechazaFotos() {
        var casos = new Object[][] {
            {new ArchivoRecibido(null, 0), PerfilService.MENSAJE_FALTA_FOTO},
            {new ArchivoRecibido(null, ValidadorArchivo.TAMANO_MAXIMO + 1), ErrorDTO.MENSAJE_ARCHIVO_DEMASIADO_GRANDE},
            {new ArchivoRecibido(ArchivosDePrueba.pngIlegible(), 40),
                "No pudimos leer la imagen. Revisa que no esté dañada y vuelve a subirla."},
            {new ArchivoRecibido(ArchivosDePrueba.pdf(200), 200), PerfilService.MENSAJE_TIPO_FOTO},
            {new ArchivoRecibido(ArchivosDePrueba.texto(), 40), PerfilService.MENSAJE_TIPO_FOTO},
        };
        for (var caso : casos) {
            assertThatThrownBy(() -> servicio.guardarFoto(USUARIO, (ArchivoRecibido) caso[0], IP))
                    .isInstanceOfSatisfying(ReglaNegocioException.class, e -> {
                        assertThat(e.getCampo()).isEqualTo("foto");
                        assertThat(e.getMessage()).isEqualTo(caso[1]);
                    });
        }
        verifyNoInteractions(archivos, usuarios, auditoria);
    }

    @Test
    @DisplayName("HU-20 R15: al reemplazar, el archivo anterior se borra solo después del commit")
    void reemplazoBorraAnteriorTrasCommit() {
        var cuenta = usuario();
        cuenta.cambiarFoto(CLAVE_ANTERIOR, "image/png");
        cuentaBloqueada(cuenta);
        cuenta(cuenta);
        when(archivos.guardar(eq("perfiles"), any())).thenReturn(CLAVE_NUEVA);
        var png = ArchivosDePrueba.png(20, 20);

        servicio.guardarFoto(USUARIO, new ArchivoRecibido(png, png.length), IP);

        verify(archivos, never()).eliminar(anyString());
        confirmarTransaccion();
        verify(archivos).eliminar(CLAVE_ANTERIOR);
        verify(archivos, never()).eliminar(CLAVE_NUEVA);
    }

    @Test
    @DisplayName("HU-20 R15: si la transacción que registra la foto falla, se borra el archivo nuevo y queda el anterior")
    void falloBorraNueva() {
        var cuenta = usuario();
        cuenta.cambiarFoto(CLAVE_ANTERIOR, "image/png");
        cuentaBloqueada(cuenta);
        when(archivos.guardar(eq("perfiles"), any())).thenReturn(CLAVE_NUEVA);
        doThrow(new IllegalStateException("BD caída")).when(auditoria).registrar(any());
        var png = ArchivosDePrueba.png(20, 20);

        assertThatThrownBy(() -> servicio.guardarFoto(USUARIO, new ArchivoRecibido(png, png.length), IP))
                .isInstanceOf(IllegalStateException.class);

        verify(archivos).eliminar(CLAVE_NUEVA);
        verify(archivos, never()).eliminar(CLAVE_ANTERIOR);
    }

    @Test
    @DisplayName("HU-20 R16: el titular obtiene los bytes y el tipo de su foto; sin foto, 404")
    void verFoto() {
        var cuenta = usuario();
        cuenta(cuenta);

        assertThatThrownBy(() -> servicio.foto(USUARIO)).isInstanceOf(RecursoNoEncontradoException.class);

        cuenta.cambiarFoto(CLAVE_ANTERIOR, "image/png");
        var bytes = ArchivosDePrueba.png(5, 5);
        when(archivos.leer(CLAVE_ANTERIOR)).thenReturn(Optional.of(bytes));

        var foto = servicio.foto(USUARIO);

        assertThat(foto.contenido()).isEqualTo(bytes);
        assertThat(foto.tipoMime()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("HU-20 R16: si el archivo ya no está en el almacenamiento, 404")
    void fotoSinArchivo() {
        var cuenta = usuario();
        cuenta.cambiarFoto(CLAVE_ANTERIOR, "image/png");
        cuenta(cuenta);
        when(archivos.leer(CLAVE_ANTERIOR)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.foto(USUARIO)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("HU-20 R15, R17, R48: quitar la foto la deja sin foto, la audita y borra el archivo tras el commit")
    void quitaFoto() {
        var cuenta = usuario();
        cuenta.cambiarFoto(CLAVE_ANTERIOR, "image/png");
        cuentaBloqueada(cuenta);
        cuenta(cuenta);

        var dto = servicio.quitarFoto(USUARIO, IP);

        assertThat(dto.tieneFoto()).isFalse();
        assertThat(unicaAuditoria()).isEqualTo(new EventoAuditoria(ID, "FOTO_PERFIL_ELIMINADA", "usuario", ID, IP,
                null));
        verify(archivos, never()).eliminar(anyString());
        confirmarTransaccion();
        verify(archivos).eliminar(CLAVE_ANTERIOR);
    }

    @Test
    @DisplayName("HU-20 R17: quitar sin foto previa responde igual y no audita ni borra nada")
    void quitarSinFoto() {
        var cuenta = usuario();
        cuentaBloqueada(cuenta);
        cuenta(cuenta);

        var dto = servicio.quitarFoto(USUARIO, IP);

        assertThat(dto.tieneFoto()).isFalse();
        confirmarTransaccion();
        verifyNoInteractions(auditoria, archivos);
    }
}
