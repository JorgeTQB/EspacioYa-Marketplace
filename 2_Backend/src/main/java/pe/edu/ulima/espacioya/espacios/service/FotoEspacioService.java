package pe.edu.ulima.espacioya.espacios.service;

import java.time.Clock;
import java.time.Instant;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.comun.almacenamiento.ArchivoRecibido;
import pe.edu.ulima.espacioya.comun.almacenamiento.ArchivoValidado;
import pe.edu.ulima.espacioya.comun.almacenamiento.ArchivosPublicos;
import pe.edu.ulima.espacioya.comun.almacenamiento.MotivoRechazoArchivo;
import pe.edu.ulima.espacioya.comun.almacenamiento.TipoArchivo;
import pe.edu.ulima.espacioya.comun.almacenamiento.ValidadorArchivo;
import pe.edu.ulima.espacioya.comun.almacenamiento.ValidadorArchivo.ResultadoValidacion;
import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.espacios.domain.Espacio;
import pe.edu.ulima.espacioya.espacios.domain.EstadoEspacio;
import pe.edu.ulima.espacioya.espacios.domain.FotoEspacio;
import pe.edu.ulima.espacioya.espacios.dto.EspacioDTO;
import pe.edu.ulima.espacioya.espacios.dto.FotoServida;
import pe.edu.ulima.espacioya.espacios.repository.EspacioRepository;
import pe.edu.ulima.espacioya.espacios.repository.FotoEspacioRepository;

/**
 * Fotos de los espacios (HU-07). {@code agregar} y {@code eliminar} no son transaccionales a propósito, como el
 * envío de documentos de HU-05: los rechazos se auditan aunque no haya cambios, el archivo nuevo se borra si la
 * transacción falla y el archivo quitado se borra solo después del commit.
 */
@Service
public class FotoEspacioService {

    public static final String ACCION_FOTO_AGREGADA = "FOTO_ESPACIO_AGREGADA";
    public static final String ACCION_FOTO_ELIMINADA = "FOTO_ESPACIO_ELIMINADA";
    public static final String ACCION_FOTO_RECHAZADA = "FOTO_ESPACIO_RECHAZADA";
    public static final String CARPETA = "espacios";
    public static final String CAMPO_FOTO = "foto";
    public static final int MAXIMO_FOTOS = FotoEspacio.ORDEN_MAXIMO;

    public static final String MENSAJE_MAXIMO_FOTOS = "Ya tienes 5 fotos. Quita una para subir otra.";
    public static final String MENSAJE_FALTA_FOTO = "Elige una foto.";
    public static final String MENSAJE_TIPO_FOTO = "La foto debe ser JPG o PNG.";

    private static final Pattern UUID_MINUSCULAS =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private static final Logger log = LoggerFactory.getLogger(FotoEspacioService.class);

    private final EspacioService espacioService;
    private final EspacioRepository espacios;
    private final FotoEspacioRepository fotos;
    private final ValidadorArchivo validador;
    private final ArchivosPublicos archivos;
    private final AuditoriaService auditoria;
    private final TransactionTemplate transacciones;
    private final Clock reloj;

    public FotoEspacioService(EspacioService espacioService, EspacioRepository espacios, FotoEspacioRepository fotos,
                              ValidadorArchivo validador, ArchivosPublicos archivos, AuditoriaService auditoria,
                              TransactionTemplate transacciones, Clock reloj) {
        this.espacioService = espacioService;
        this.espacios = espacios;
        this.fotos = fotos;
        this.validador = validador;
        this.archivos = archivos;
        this.auditoria = auditoria;
        this.transacciones = transacciones;
        this.reloj = reloj;
    }

    public EspacioDTO agregar(UsuarioAutenticado usuario, Long espacioId, ArchivoRecibido foto, String ip) {
        // Rol, correo, propiedad, estado y cupo se comprueban antes de mirar el contenido de la foto (R44).
        var arrendadorId = espacioService.arrendadorHabilitado(usuario);
        exigirEditable(espacios.findByIdAndArrendadorId(espacioId, arrendadorId)
                .orElseThrow(RecursoNoEncontradoException::new));
        if (fotos.countByEspacioId(espacioId) >= MAXIMO_FOTOS) {
            auditarRechazo(arrendadorId, espacioId, ip, "fotos=MAXIMO");
            throw new CupoAgotadoException();
        }

        var aceptada = validar(arrendadorId, espacioId, foto, ip);
        var clave = archivos.guardar(CARPETA, aceptada.contenido());
        try {
            transacciones.executeWithoutResult(estado -> registrar(arrendadorId, espacioId, clave, aceptada, ip));
        } catch (RuntimeException e) {
            eliminarSinFallar(clave);
            if (e instanceof CupoAgotadoException) {
                auditarRechazo(arrendadorId, espacioId, ip, "fotos=MAXIMO");
            }
            throw e;
        }
        return espacioService.consultar(usuario, espacioId);
    }

    public EspacioDTO eliminar(UsuarioAutenticado usuario, Long espacioId, Long fotoId, String ip) {
        var arrendadorId = espacioService.arrendadorHabilitado(usuario);
        exigirEditable(espacios.findByIdAndArrendadorId(espacioId, arrendadorId)
                .orElseThrow(RecursoNoEncontradoException::new));
        var clave = transacciones.execute(estado -> quitar(arrendadorId, espacioId, fotoId, ip));
        if (clave != null) {
            eliminarSinFallar(clave);
        }
        return espacioService.consultar(usuario, espacioId);
    }

    /**
     * Foto lista para servir: de un espacio publicado, a cualquiera; si no, solo a su dueño ({@code usuario} es
     * {@code null} sin sesión). Cualquier otro caso lanza el mismo 404, sin revelar si la foto existe (R55).
     */
    @Transactional(readOnly = true)
    public FotoServida ver(UsuarioAutenticado usuario, String uuid) {
        // La forma se valida antes de formar la clave: sin consultas inútiles ni path traversal.
        if (uuid == null || !UUID_MINUSCULAS.matcher(uuid).matches()) {
            throw new RecursoNoEncontradoException();
        }
        var foto = fotos.findByRutaArchivo(CARPETA + "/" + uuid).orElseThrow(RecursoNoEncontradoException::new);
        var espacio = espacios.findById(foto.getEspacioId()).orElseThrow(RecursoNoEncontradoException::new);
        var publica = espacio.getEstado() == EstadoEspacio.PUBLICADO;
        if (!publica && (usuario == null || !usuario.usuarioId().equals(espacio.getArrendadorId()))) {
            throw new RecursoNoEncontradoException();
        }
        var contenido = archivos.leer(foto.getRutaArchivo()).orElseThrow(RecursoNoEncontradoException::new);
        return new FotoServida(contenido, foto.getTipoMime(), publica);
    }

    private ArchivoValidado validar(Long arrendadorId, Long espacioId, ArchivoRecibido foto, String ip) {
        var resultado = validador.validar(foto);
        // ValidadorArchivo acepta PDF para los documentos de HU-05; una foto solo puede ser imagen (DT-5).
        if (resultado instanceof ResultadoValidacion.Aceptado aceptado
                && aceptado.archivo().tipo() != TipoArchivo.PDF) {
            return aceptado.archivo();
        }
        var motivo = resultado instanceof ResultadoValidacion.Rechazado rechazado
                ? rechazado.motivo()
                : MotivoRechazoArchivo.TIPO_NO_PERMITIDO;
        auditarRechazo(arrendadorId, espacioId, ip, "foto=" + motivo.name());
        throw new ReglaNegocioException(CAMPO_FOTO, mensaje(motivo));
    }

    private static String mensaje(MotivoRechazoArchivo motivo) {
        return switch (motivo) {
            case VACIO -> MENSAJE_FALTA_FOTO;
            case TIPO_NO_PERMITIDO -> MENSAJE_TIPO_FOTO;
            case TAMANO_EXCEDIDO, IMAGEN_ILEGIBLE -> motivo.mensaje();
        };
    }

    private void registrar(Long arrendadorId, Long espacioId, String clave, ArchivoValidado foto, String ip) {
        var espacio = espacioParaActualizar(espacioId, arrendadorId);
        var actuales = fotos.countByEspacioId(espacioId);
        if (actuales >= MAXIMO_FOTOS) {
            throw new CupoAgotadoException();
        }
        var orden = Math.toIntExact(actuales) + 1;
        var mime = foto.tipo().mime();
        var nueva = fotos.save(FotoEspacio.registrar(espacioId, clave, mime, orden));
        espacio.tocar(Instant.now(reloj));
        // Solo metadatos: ni contenido, ni nombre original, ni clave de almacenamiento (R51).
        auditar(arrendadorId, ACCION_FOTO_AGREGADA, espacioId, ip, "foto=" + nueva.getId() + ";orden=" + orden
                + ";mime=" + mime + ";tamano=" + foto.tamanoRecibido());
    }

    private String quitar(Long arrendadorId, Long espacioId, Long fotoId, String ip) {
        var espacio = espacioParaActualizar(espacioId, arrendadorId);
        var foto = fotos.findByIdAndEspacioId(fotoId, espacioId).orElseThrow(RecursoNoEncontradoException::new);
        var orden = foto.getOrden();
        fotos.delete(foto);
        // uq_foto_espacio_orden es diferida: renumerar dentro de la transacción no choca con el orden de los UPDATE.
        for (var siguiente : fotos.findByEspacioIdOrderByOrdenAsc(espacioId)) {
            if (siguiente.getOrden() > orden) {
                siguiente.moverA(siguiente.getOrden() - 1);
            }
        }
        espacio.tocar(Instant.now(reloj));
        auditar(arrendadorId, ACCION_FOTO_ELIMINADA, espacioId, ip, "foto=" + fotoId + ";orden=" + orden);
        return foto.getRutaArchivo();
    }

    private Espacio espacioParaActualizar(Long espacioId, Long arrendadorId) {
        return exigirEditable(espacios.findParaActualizar(espacioId, arrendadorId)
                .orElseThrow(RecursoNoEncontradoException::new));
    }

    private static Espacio exigirEditable(Espacio espacio) {
        if (!espacio.esEditable()) {
            throw new ReglaNegocioException(EspacioService.MENSAJE_ESTADO_NO_EDITABLE);
        }
        return espacio;
    }

    private void auditarRechazo(Long arrendadorId, Long espacioId, String ip, String detalle) {
        auditar(arrendadorId, ACCION_FOTO_RECHAZADA, espacioId, ip, detalle);
    }

    private void auditar(Long arrendadorId, String accion, Long espacioId, String ip, String detalle) {
        auditoria.registrar(new EventoAuditoria(arrendadorId, accion, EspacioService.ENTIDAD_ESPACIO, espacioId, ip,
                detalle));
    }

    // Si falla, queda un archivo huérfano: se avisa en el log sin la clave.
    private void eliminarSinFallar(String clave) {
        try {
            archivos.eliminar(clave);
        } catch (RuntimeException e) {
            log.warn("No se pudo eliminar una foto de espacio almacenada", e);
        }
    }

    private static final class CupoAgotadoException extends ReglaNegocioException {

        CupoAgotadoException() {
            super(MENSAJE_MAXIMO_FOTOS);
        }
    }
}
