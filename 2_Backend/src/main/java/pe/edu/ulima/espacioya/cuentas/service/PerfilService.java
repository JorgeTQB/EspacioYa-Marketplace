package pe.edu.ulima.espacioya.cuentas.service;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.comun.almacenamiento.ArchivoRecibido;
import pe.edu.ulima.espacioya.comun.almacenamiento.ArchivoValidado;
import pe.edu.ulima.espacioya.comun.almacenamiento.ArchivosPrivados;
import pe.edu.ulima.espacioya.comun.almacenamiento.MotivoRechazoArchivo;
import pe.edu.ulima.espacioya.comun.almacenamiento.TipoArchivo;
import pe.edu.ulima.espacioya.comun.almacenamiento.ValidadorArchivo;
import pe.edu.ulima.espacioya.comun.almacenamiento.ValidadorArchivo.ResultadoValidacion;
import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.ErrorCampoDTO;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.error.ValidacionException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.comun.transaccion.DespuesDelCommit;
import pe.edu.ulima.espacioya.cuentas.domain.Rol;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.dto.DatosPerfilDTO;
import pe.edu.ulima.espacioya.cuentas.dto.PerfilDTO;
import pe.edu.ulima.espacioya.cuentas.dto.RegistroArrendadorDTO;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;

/**
 * Perfil del titular de la sesión (HU-20): consulta, nombre y teléfono, y foto. Ninguna operación exige contraseña
 * (D5). {@code guardarFoto} y {@code quitarFoto} no son transaccionales a propósito, como las fotos de HU-07: el archivo
 * nuevo se borra si la transacción falla y el anterior solo después del commit (R15).
 */
@Service
public class PerfilService {

    public static final String ACCION_ACTUALIZADO = "PERFIL_ACTUALIZADO";
    public static final String ACCION_FOTO_ACTUALIZADA = "FOTO_PERFIL_ACTUALIZADA";
    public static final String ACCION_FOTO_ELIMINADA = "FOTO_PERFIL_ELIMINADA";
    public static final String CARPETA = "perfiles";
    public static final String CAMPO_FOTO = "foto";

    public static final String MENSAJE_FALTA_FOTO = "Elige una foto.";
    public static final String MENSAJE_TIPO_FOTO = "La foto debe ser JPG o PNG.";

    static final String ENTIDAD_USUARIO = "usuario";
    private static final Logger log = LoggerFactory.getLogger(PerfilService.class);

    private final UsuarioRepository usuarios;
    private final PerfilArrendadorRepository perfiles;
    private final ValidadorArchivo validador;
    private final ArchivosPrivados archivos;
    private final AuditoriaService auditoria;
    private final TransactionTemplate transacciones;

    public PerfilService(UsuarioRepository usuarios, PerfilArrendadorRepository perfiles, ValidadorArchivo validador,
                         ArchivosPrivados archivos, AuditoriaService auditoria, TransactionTemplate transacciones) {
        this.usuarios = usuarios;
        this.perfiles = perfiles;
        this.validador = validador;
        this.archivos = archivos;
        this.auditoria = auditoria;
        this.transacciones = transacciones;
    }

    public record FotoPerfil(byte[] contenido, String tipoMime) {
    }

    @Transactional(readOnly = true)
    public PerfilDTO consultar(UsuarioAutenticado usuario) {
        exigirTitular(usuario);
        var cuenta = cuenta(usuario);
        var perfil = cuenta.getRol() == Rol.ARRENDADOR
                ? perfiles.findById(cuenta.getId())
                        .orElseThrow(() -> new IllegalStateException("El arrendador no tiene perfil"))
                : null;
        return PerfilDTO.de(cuenta, perfil);
    }

    public PerfilDTO actualizarDatos(UsuarioAutenticado usuario, DatosPerfilDTO datos, String ip) {
        exigirTitular(usuario);
        if (Rol.ARRENDADOR.name().equals(usuario.rol())) {
            exigirObligatorios(datos);
        }
        var id = usuario.usuarioId();
        transacciones.executeWithoutResult(estado -> {
            var cuenta = usuarios.findParaActualizar(id)
                    .orElseThrow(() -> new IllegalStateException("La cuenta de una sesión vigente no existe"));
            var campos = cuenta.actualizarDatos(datos.nombre(), datos.telefono());
            // Solo los nombres de los campos, nunca sus valores (R46, R47).
            if (!campos.isEmpty()) {
                auditar(id, ACCION_ACTUALIZADO, ip, "campos=" + String.join(",", campos));
            }
        });
        return consultar(usuario);
    }

    public PerfilDTO guardarFoto(UsuarioAutenticado usuario, ArchivoRecibido foto, String ip) {
        exigirTitular(usuario);
        var aceptada = validar(foto);
        var id = usuario.usuarioId();
        var clave = archivos.guardar(CARPETA, aceptada.contenido());
        try {
            transacciones.executeWithoutResult(estado -> {
                var cuenta = usuarios.findParaActualizar(id)
                        .orElseThrow(() -> new IllegalStateException("La cuenta de una sesión vigente no existe"));
                var mime = aceptada.tipo().mime();
                var anterior = cuenta.cambiarFoto(clave, mime);
                auditar(id, ACCION_FOTO_ACTUALIZADA, ip, "mime=" + mime + ";tamano=" + aceptada.tamanoRecibido());
                if (anterior != null) {
                    DespuesDelCommit.ejecutar(() -> eliminarSinFallar(anterior));
                }
            });
        } catch (RuntimeException e) {
            eliminarSinFallar(clave);
            throw e;
        }
        return consultar(usuario);
    }

    @Transactional(readOnly = true)
    public FotoPerfil foto(UsuarioAutenticado usuario) {
        exigirTitular(usuario);
        var cuenta = cuenta(usuario);
        if (!cuenta.tieneFoto()) {
            throw new RecursoNoEncontradoException();
        }
        var contenido = archivos.leer(cuenta.getFotoRuta()).orElseThrow(RecursoNoEncontradoException::new);
        return new FotoPerfil(contenido, cuenta.getFotoTipoMime());
    }

    public PerfilDTO quitarFoto(UsuarioAutenticado usuario, String ip) {
        exigirTitular(usuario);
        var id = usuario.usuarioId();
        transacciones.executeWithoutResult(estado -> {
            var cuenta = usuarios.findParaActualizar(id)
                    .orElseThrow(() -> new IllegalStateException("La cuenta de una sesión vigente no existe"));
            var anterior = cuenta.quitarFoto();
            if (anterior != null) {
                auditar(id, ACCION_FOTO_ELIMINADA, ip, null);
                DespuesDelCommit.ejecutar(() -> eliminarSinFallar(anterior));
            }
        });
        return consultar(usuario);
    }

    /** Solo {@code USUARIO} y {@code ARRENDADOR} tienen perfil editable; el admin recibe 403 sin leer nada (D7). */
    static void exigirTitular(UsuarioAutenticado usuario) {
        if (usuario == null || !(Rol.USUARIO.name().equals(usuario.rol())
                || Rol.ARRENDADOR.name().equals(usuario.rol()))) {
            throw new AccesoDenegadoException();
        }
    }

    // D8: el arrendador nació con nombre y teléfono (HU-02) y los conserva; el usuario puede dejarlos vacíos.
    private static void exigirObligatorios(DatosPerfilDTO datos) {
        var errores = new ArrayList<ErrorCampoDTO>();
        if (datos.nombre() == null || datos.nombre().isBlank()) {
            errores.add(new ErrorCampoDTO("nombre", RegistroArrendadorDTO.MENSAJE_NOMBRE));
        }
        if (datos.telefono() == null || datos.telefono().isBlank()) {
            errores.add(new ErrorCampoDTO("telefono", RegistroArrendadorDTO.MENSAJE_TELEFONO));
        }
        if (!errores.isEmpty()) {
            throw new ValidacionException(errores);
        }
    }

    private Usuario cuenta(UsuarioAutenticado usuario) {
        return usuarios.findById(usuario.usuarioId())
                .orElseThrow(() -> new IllegalStateException("La cuenta de una sesión vigente no existe"));
    }

    private ArchivoValidado validar(ArchivoRecibido foto) {
        var resultado = validador.validar(foto);
        // ValidadorArchivo acepta PDF para los documentos de HU-05; una foto solo puede ser imagen.
        if (resultado instanceof ResultadoValidacion.Aceptado aceptado
                && aceptado.archivo().tipo() != TipoArchivo.PDF) {
            return aceptado.archivo();
        }
        var motivo = resultado instanceof ResultadoValidacion.Rechazado rechazado
                ? rechazado.motivo()
                : MotivoRechazoArchivo.TIPO_NO_PERMITIDO;
        throw new ReglaNegocioException(CAMPO_FOTO, mensaje(motivo));
    }

    private static String mensaje(MotivoRechazoArchivo motivo) {
        return switch (motivo) {
            case VACIO -> MENSAJE_FALTA_FOTO;
            case TIPO_NO_PERMITIDO -> MENSAJE_TIPO_FOTO;
            case TAMANO_EXCEDIDO, IMAGEN_ILEGIBLE -> motivo.mensaje();
        };
    }

    private void auditar(Long usuarioId, String accion, String ip, String detalle) {
        auditoria.registrar(new EventoAuditoria(usuarioId, accion, ENTIDAD_USUARIO, usuarioId, ip, detalle));
    }

    // Si falla, queda un archivo huérfano: se avisa en el log sin la clave.
    private void eliminarSinFallar(String clave) {
        try {
            archivos.eliminar(clave);
        } catch (RuntimeException e) {
            log.warn("No se pudo eliminar una foto de perfil almacenada");
        }
    }
}
