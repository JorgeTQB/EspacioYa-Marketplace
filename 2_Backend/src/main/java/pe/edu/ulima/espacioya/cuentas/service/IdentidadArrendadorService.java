package pe.edu.ulima.espacioya.cuentas.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.comun.almacenamiento.ArchivoRecibido;
import pe.edu.ulima.espacioya.comun.almacenamiento.ArchivoValidado;
import pe.edu.ulima.espacioya.comun.almacenamiento.ArchivosPrivados;
import pe.edu.ulima.espacioya.comun.almacenamiento.MotivoRechazoArchivo;
import pe.edu.ulima.espacioya.comun.almacenamiento.ValidadorArchivo;
import pe.edu.ulima.espacioya.comun.almacenamiento.ValidadorArchivo.ResultadoValidacion;
import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.ErrorCampoDTO;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.error.ValidacionException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.domain.DocumentoIdentidad;
import pe.edu.ulima.espacioya.cuentas.domain.EstadoIdentidad;
import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.Rol;
import pe.edu.ulima.espacioya.cuentas.domain.TipoDocumentoIdentidad;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.dto.DocumentoIdentidadDTO;
import pe.edu.ulima.espacioya.cuentas.dto.IdentidadDTO;
import pe.edu.ulima.espacioya.cuentas.repository.DocumentoIdentidadRepository;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;

/**
 * Envío y consulta de los documentos de identidad del arrendador (HU-05). {@code enviar} no es
 * transaccional a propósito: los rechazos se auditan aunque no haya cambios, y los archivos reemplazados se
 * borran solo después del commit para no perder los vigentes si la transacción falla.
 */
@Service
public class IdentidadArrendadorService {

    public static final String ACCION_CARGA_DOCUMENTO = "CARGA_DOCUMENTO_IDENTIDAD";
    public static final String ACCION_CARGA_RECHAZADA = "CARGA_DOCUMENTO_RECHAZADA";
    public static final String CARPETA = "identidad";
    public static final String CAMPO_IDENTIDAD = "documentoIdentidad";
    public static final String CAMPO_PROPIEDAD = "documentoPropiedad";
    public static final String MENSAJE_FALTA_IDENTIDAD = "Sube tu documento de identidad (DNI o RUC).";
    public static final String MENSAJE_FALTA_PROPIEDAD =
            "Sube el documento que acredita la propiedad o autorización del espacio.";
    public static final String MENSAJE_ESTADO_NO_PERMITIDO =
            "Ya enviaste tus documentos o tu identidad ya está verificada.";

    private static final String ENTIDAD_DOCUMENTO = "documento_identidad";
    private static final String ENTIDAD_PERFIL = "perfil_arrendador";
    private static final Logger log = LoggerFactory.getLogger(IdentidadArrendadorService.class);

    private final UsuarioRepository usuarios;
    private final PerfilArrendadorRepository perfiles;
    private final DocumentoIdentidadRepository documentos;
    private final ValidadorArchivo validador;
    private final ArchivosPrivados archivos;
    private final AuditoriaService auditoria;
    private final TransactionTemplate transacciones;
    private final Clock reloj;

    public IdentidadArrendadorService(UsuarioRepository usuarios, PerfilArrendadorRepository perfiles,
                                      DocumentoIdentidadRepository documentos, ValidadorArchivo validador,
                                      ArchivosPrivados archivos, AuditoriaService auditoria,
                                      TransactionTemplate transacciones, Clock reloj) {
        this.usuarios = usuarios;
        this.perfiles = perfiles;
        this.documentos = documentos;
        this.validador = validador;
        this.archivos = archivos;
        this.auditoria = auditoria;
        this.transacciones = transacciones;
        this.reloj = reloj;
    }

    public IdentidadDTO enviar(UsuarioAutenticado usuario, ArchivoRecibido identidad, ArchivoRecibido propiedad,
                               String ip) {
        // Rol, correo y estado se comprueban antes de mirar el contenido de los archivos (R20).
        var cuenta = cuentaArrendador(usuario);
        if (!cuenta.isCorreoVerificado()) {
            throw new AccesoDenegadoException();
        }
        var perfil = perfil(usuario.usuarioId());
        if (!perfil.puedeEnviarDocumentos()) {
            auditarRechazo(usuario, ip, "estado=" + perfil.getEstadoIdentidad());
            throw new EstadoNoPermitidoException(perfil.getEstadoIdentidad());
        }

        var aceptados = validar(usuario, identidad, propiedad, ip);
        var claveIdentidad = archivos.guardar(CARPETA, aceptados.get(0).contenido());
        String clavePropiedad;
        try {
            clavePropiedad = archivos.guardar(CARPETA, aceptados.get(1).contenido());
        } catch (RuntimeException e) {
            eliminarSinFallar(List.of(claveIdentidad));
            throw e;
        }

        List<String> reemplazados;
        try {
            reemplazados = transacciones.execute(estado -> registrar(usuario, ip,
                    new Nuevo(TipoDocumentoIdentidad.IDENTIDAD, claveIdentidad, aceptados.get(0)),
                    new Nuevo(TipoDocumentoIdentidad.PROPIEDAD, clavePropiedad, aceptados.get(1))));
        } catch (RuntimeException e) {
            eliminarSinFallar(List.of(claveIdentidad, clavePropiedad));
            if (e instanceof EstadoNoPermitidoException noPermitido) {
                auditarRechazo(usuario, ip, "estado=" + noPermitido.estado);
            }
            throw e;
        }
        eliminarSinFallar(reemplazados == null ? List.of() : reemplazados);
        return consultar(usuario);
    }

    @Transactional(readOnly = true)
    public IdentidadDTO consultar(UsuarioAutenticado usuario) {
        var cuenta = cuentaArrendador(usuario);
        var perfil = perfil(usuario.usuarioId());
        var estado = perfil.getEstadoIdentidad();
        var vigentes = documentos.findByArrendadorIdOrderByTipoAsc(usuario.usuarioId()).stream()
                .map(this::aDto)
                .toList();
        return new IdentidadDTO(estado.name(), estado == EstadoIdentidad.RECHAZADA ? perfil.getMotivoRechazo() : null,
                perfil.puedeEnviarDocumentos() && cuenta.isCorreoVerificado(), vigentes);
    }

    private Usuario cuentaArrendador(UsuarioAutenticado usuario) {
        if (!Rol.ARRENDADOR.name().equals(usuario.rol())) {
            throw new AccesoDenegadoException();
        }
        return usuarios.findById(usuario.usuarioId())
                .orElseThrow(() -> new IllegalStateException("La cuenta de una sesión vigente no existe"));
    }

    private PerfilArrendador perfil(Long usuarioId) {
        return perfiles.findById(usuarioId)
                .orElseThrow(() -> new IllegalStateException("El arrendador no tiene perfil"));
    }

    private List<ArchivoValidado> validar(UsuarioAutenticado usuario, ArchivoRecibido identidad,
                                          ArchivoRecibido propiedad, String ip) {
        var aceptados = new ArrayList<ArchivoValidado>();
        var errores = new ArrayList<ErrorCampoDTO>();
        var detalle = new ArrayList<String>();
        for (var parte : List.of(new Parte(CAMPO_IDENTIDAD, MENSAJE_FALTA_IDENTIDAD, identidad),
                new Parte(CAMPO_PROPIEDAD, MENSAJE_FALTA_PROPIEDAD, propiedad))) {
            switch (validador.validar(parte.archivo())) {
                case ResultadoValidacion.Aceptado aceptado -> aceptados.add(aceptado.archivo());
                case ResultadoValidacion.Rechazado rechazado -> {
                    var motivo = rechazado.motivo();
                    var mensaje = motivo == MotivoRechazoArchivo.VACIO ? parte.mensajeVacio() : motivo.mensaje();
                    errores.add(new ErrorCampoDTO(parte.campo(), mensaje));
                    detalle.add(parte.campo() + "=" + motivo.name());
                }
            }
        }
        if (!errores.isEmpty()) {
            auditarRechazo(usuario, ip, String.join(";", detalle));
            throw new ValidacionException(errores);
        }
        return aceptados;
    }

    private List<String> registrar(UsuarioAutenticado usuario, String ip, Nuevo... nuevos) {
        var arrendadorId = usuario.usuarioId();
        var perfil = perfiles.findParaActualizar(arrendadorId)
                .orElseThrow(() -> new IllegalStateException("El arrendador no tiene perfil"));
        if (!perfil.puedeEnviarDocumentos()) {
            throw new EstadoNoPermitidoException(perfil.getEstadoIdentidad());
        }
        var anteriores = documentos.findByArrendadorIdOrderByTipoAsc(arrendadorId);
        documentos.deleteAll(anteriores);
        documentos.flush();
        var ahora = Instant.now(reloj);
        for (var nuevo : nuevos) {
            var archivo = nuevo.archivo();
            var documento = documentos.save(DocumentoIdentidad.registrar(arrendadorId, nuevo.tipo(), nuevo.clave(),
                    archivo.tipo().mime(), Math.toIntExact(archivo.tamanoRecibido()), ahora));
            // Solo metadatos: ni contenido, ni nombre original, ni clave de almacenamiento, ni número de documento.
            auditoria.registrar(new EventoAuditoria(arrendadorId, ACCION_CARGA_DOCUMENTO, ENTIDAD_DOCUMENTO,
                    documento.getId(), ip, "tipo=" + nuevo.tipo() + ";mime=" + archivo.tipo().mime()
                            + ";tamano=" + archivo.tamanoRecibido()));
        }
        perfil.enviarDocumentos();
        return anteriores.stream().map(DocumentoIdentidad::getRutaArchivo).toList();
    }

    private DocumentoIdentidadDTO aDto(DocumentoIdentidad documento) {
        var url = archivos.urlFirmada(documento.getRutaArchivo(), documento.getTipoMime());
        return new DocumentoIdentidadDTO(documento.getId(), documento.getTipo().name(), documento.getTipoMime(),
                documento.getTamano(), documento.getSubidoEn(), url.url(), url.expiraEn());
    }

    private void auditarRechazo(UsuarioAutenticado usuario, String ip, String detalle) {
        auditoria.registrar(new EventoAuditoria(usuario.usuarioId(), ACCION_CARGA_RECHAZADA, ENTIDAD_PERFIL,
                usuario.usuarioId(), ip, detalle));
    }

    // Si falla, queda un archivo huérfano cifrado: se avisa en el log sin la clave.
    private void eliminarSinFallar(List<String> claves) {
        for (var clave : claves) {
            try {
                archivos.eliminar(clave);
            } catch (RuntimeException e) {
                log.warn("No se pudo eliminar un documento de identidad almacenado", e);
            }
        }
    }

    private record Parte(String campo, String mensajeVacio, ArchivoRecibido archivo) {
    }

    private record Nuevo(TipoDocumentoIdentidad tipo, String clave, ArchivoValidado archivo) {
    }

    private static final class EstadoNoPermitidoException extends ReglaNegocioException {

        private final transient EstadoIdentidad estado;

        EstadoNoPermitidoException(EstadoIdentidad estado) {
            super(MENSAJE_ESTADO_NO_PERMITIDO);
            this.estado = estado;
        }
    }
}
