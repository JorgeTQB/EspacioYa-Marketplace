package pe.edu.ulima.espacioya.espacios.service;

import java.text.Collator;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.dto.HabilitacionArrendadorDTO;
import pe.edu.ulima.espacioya.cuentas.service.CuentaService;
import pe.edu.ulima.espacioya.espacios.domain.Espacio;
import pe.edu.ulima.espacioya.espacios.domain.FotoEspacio;
import pe.edu.ulima.espacioya.espacios.domain.Zona;
import pe.edu.ulima.espacioya.espacios.dto.EspacioDTO;
import pe.edu.ulima.espacioya.espacios.dto.EspacioResumenDTO;
import pe.edu.ulima.espacioya.espacios.dto.FichaEspacioDTO;
import pe.edu.ulima.espacioya.espacios.dto.FotoEspacioDTO;
import pe.edu.ulima.espacioya.espacios.dto.ZonaDTO;
import pe.edu.ulima.espacioya.espacios.repository.EspacioRepository;
import pe.edu.ulima.espacioya.espacios.repository.FotoEspacioRepository;
import pe.edu.ulima.espacioya.espacios.repository.ZonaRepository;

/**
 * Ficha, publicación y consulta de los espacios del arrendador (HU-07). Cada operación comprueba, en este orden,
 * rol, correo verificado, propiedad y estado (R53); la propiedad se resuelve en la consulta, filtrando por el
 * arrendador de la sesión, así que un espacio ajeno responde igual que uno inexistente (R40).
 */
@Service
public class EspacioService {

    public static final String ACCION_CREACION = "CREACION_ESPACIO";
    public static final String ACCION_EDICION = "EDICION_ESPACIO";
    public static final String ACCION_PUBLICACION = "PUBLICACION_ESPACIO";
    public static final String ENTIDAD_ESPACIO = "espacio";
    public static final String RUTA_FOTOS = "/api/fotos/";

    public static final String MENSAJE_ZONA = FichaEspacioDTO.M_ZONA;
    // Mismo texto que el aviso de HU-02 (PerfilArrendador.AVISO_IDENTIDAD): espacios no importa el dominio de cuentas.
    public static final String MENSAJE_IDENTIDAD_NO_VERIFICADA =
            "Para publicar tus espacios, primero debes verificar tu identidad.";
    public static final String MENSAJE_SOLO_BORRADOR = "Solo puedes publicar un espacio que está en borrador.";
    public static final String MENSAJE_SIN_FOTOS = "Agrega al menos una foto antes de publicar.";
    public static final String MENSAJE_ESTADO_NO_EDITABLE = "Este espacio ya no se puede editar.";

    private static final String CAMPO_ZONA = "zona";

    private final EspacioRepository espacios;
    private final FotoEspacioRepository fotos;
    private final ZonaRepository zonas;
    private final CuentaService cuentas;
    private final AuditoriaService auditoria;
    private final Clock reloj;

    public EspacioService(EspacioRepository espacios, FotoEspacioRepository fotos, ZonaRepository zonas,
                          CuentaService cuentas, AuditoriaService auditoria, Clock reloj) {
        this.espacios = espacios;
        this.fotos = fotos;
        this.zonas = zonas;
        this.cuentas = cuentas;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public List<ZonaDTO> listarZonas() {
        var orden = Collator.getInstance(Locale.of("es", "PE"));
        return zonas.findAll().stream()
                .sorted(Comparator.comparing(Zona::getNombre, orden))
                .map(zona -> new ZonaDTO(zona.getCodigo(), zona.getNombre()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EspacioResumenDTO> listar(UsuarioAutenticado usuario) {
        cuentas.habilitacionArrendador(usuario);
        var propios = espacios.findByArrendadorIdOrderByCreadoEnDesc(usuario.usuarioId());
        var nombres = nombresDeZona();
        return propios.stream()
                .map(espacio -> new EspacioResumenDTO(espacio.getId(), espacio.getNombre(), espacio.getTipo().name(),
                        espacio.getZona(), nombres.get(espacio.getZona()), espacio.getPrecioHora(),
                        espacio.getEstado().name()))
                .toList();
    }

    @Transactional(readOnly = true)
    public EspacioDTO consultar(UsuarioAutenticado usuario, Long id) {
        cuentas.habilitacionArrendador(usuario);
        var espacio = espacios.findByIdAndArrendadorId(id, usuario.usuarioId())
                .orElseThrow(RecursoNoEncontradoException::new);
        return aDto(espacio);
    }

    @Transactional
    public EspacioDTO crear(UsuarioAutenticado usuario, FichaEspacioDTO ficha, String ip) {
        var arrendadorId = arrendadorHabilitado(usuario);
        exigirZona(ficha.zona());
        var espacio = espacios.save(Espacio.crear(arrendadorId, ficha.aDatos(), Instant.now(reloj)));
        auditar(arrendadorId, ACCION_CREACION, espacio.getId(), ip, "tipo=" + espacio.getTipo()
                + ";zona=" + espacio.getZona() + ";capacidad=" + espacio.getCapacidad()
                + ";precioHora=" + espacio.getPrecioHora().toPlainString());
        return aDto(espacio);
    }

    @Transactional
    public EspacioDTO editar(UsuarioAutenticado usuario, Long id, FichaEspacioDTO ficha, String ip) {
        var arrendadorId = arrendadorHabilitado(usuario);
        var espacio = espacioParaActualizar(id, arrendadorId);
        if (!espacio.esEditable()) {
            throw new ReglaNegocioException(MENSAJE_ESTADO_NO_EDITABLE);
        }
        exigirZona(ficha.zona());
        var cambios = espacio.editar(ficha.aDatos(), Instant.now(reloj));
        if (!cambios.isEmpty()) {
            auditar(arrendadorId, ACCION_EDICION, espacio.getId(), ip, String.join(";", cambios));
        }
        return aDto(espacio);
    }

    @Transactional
    public EspacioDTO publicar(UsuarioAutenticado usuario, Long id, String ip) {
        var habilitacion = habilitacion(usuario);
        var espacio = espacioParaActualizar(id, usuario.usuarioId());
        // R16: si falla más de una regla, gana la primera de este orden.
        if (!espacio.esPublicable()) {
            throw new ReglaNegocioException(MENSAJE_SOLO_BORRADOR);
        }
        if (!habilitacion.identidadVerificada()) {
            throw new ReglaNegocioException(MENSAJE_IDENTIDAD_NO_VERIFICADA);
        }
        if (fotos.countByEspacioId(espacio.getId()) == 0) {
            throw new ReglaNegocioException(MENSAJE_SIN_FOTOS);
        }
        var anterior = espacio.getEstado();
        espacio.publicar(Instant.now(reloj));
        auditar(usuario.usuarioId(), ACCION_PUBLICACION, espacio.getId(), ip,
                "estado=" + anterior + "->" + espacio.getEstado());
        return aDto(espacio);
    }

    /** Rol ARRENDADOR y correo verificado (R41, R42); devuelve el id del arrendador de la sesión. */
    Long arrendadorHabilitado(UsuarioAutenticado usuario) {
        habilitacion(usuario);
        return usuario.usuarioId();
    }

    /** Rol ARRENDADOR (R41) y correo verificado (R42); la reutiliza {@code EstadoEspacioService} (HU-21). */
    HabilitacionArrendadorDTO habilitacion(UsuarioAutenticado usuario) {
        var habilitacion = cuentas.habilitacionArrendador(usuario);
        if (!habilitacion.correoVerificado()) {
            throw new AccesoDenegadoException();
        }
        return habilitacion;
    }

    private Espacio espacioParaActualizar(Long id, Long arrendadorId) {
        return espacios.findParaActualizar(id, arrendadorId).orElseThrow(RecursoNoEncontradoException::new);
    }

    private void exigirZona(String zona) {
        if (!zonas.existsById(zona)) {
            throw new ReglaNegocioException(CAMPO_ZONA, MENSAJE_ZONA);
        }
    }

    private Map<String, String> nombresDeZona() {
        return zonas.findAll().stream().collect(Collectors.toMap(Zona::getCodigo, Zona::getNombre));
    }

    EspacioDTO aDto(Espacio espacio) {
        var zonaNombre = zonas.findById(espacio.getZona()).map(Zona::getNombre).orElse(null);
        var fotosDto = fotos.findByEspacioIdOrderByOrdenAsc(espacio.getId()).stream()
                .map(foto -> new FotoEspacioDTO(foto.getId(), foto.getOrden(), urlDe(foto)))
                .toList();
        return new EspacioDTO(espacio.getId(), espacio.getNombre(), espacio.getTipo().name(), espacio.getDireccion(),
                espacio.getZona(), zonaNombre, espacio.getCapacidad(), espacio.getPrecioHora(),
                espacio.getDescripcion(), espacio.getEstado().name(), espacio.getCreadoEn(),
                espacio.getActualizadoEn(), fotosDto);
    }

    // R11: la URL estable es /api/fotos/<UUID>, el UUID de la clave espacios/<UUID>; nunca la clave entera (R7).
    public static String urlDe(FotoEspacio foto) {
        var ruta = foto.getRutaArchivo();
        return RUTA_FOTOS + ruta.substring(ruta.indexOf('/') + 1);
    }

    private void auditar(Long usuarioId, String accion, Long espacioId, String ip, String detalle) {
        auditoria.registrar(new EventoAuditoria(usuarioId, accion, ENTIDAD_ESPACIO, espacioId, ip, detalle));
    }
}
