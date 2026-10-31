package pe.edu.ulima.espacioya.cuentas.service;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.comun.almacenamiento.ArchivosPrivados;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.comun.transaccion.DespuesDelCommit;
import pe.edu.ulima.espacioya.cuentas.domain.DocumentoIdentidad;
import pe.edu.ulima.espacioya.cuentas.domain.EstadoIdentidad;
import pe.edu.ulima.espacioya.cuentas.domain.EstadoUsuario;
import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.Rol;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.dto.ArrendadorEnRevisionDTO;
import pe.edu.ulima.espacioya.cuentas.dto.BusquedaCuentasDTO;
import pe.edu.ulima.espacioya.cuentas.dto.CambioEstadoCuentaDTO;
import pe.edu.ulima.espacioya.cuentas.dto.CuentaResumenDTO;
import pe.edu.ulima.espacioya.cuentas.dto.DetalleCuentaDTO;
import pe.edu.ulima.espacioya.cuentas.dto.DocumentoIdentidadDTO;
import pe.edu.ulima.espacioya.cuentas.dto.ExpedienteIdentidadDTO;
import pe.edu.ulima.espacioya.cuentas.dto.HabilitacionArrendadorDTO;
import pe.edu.ulima.espacioya.cuentas.repository.DocumentoIdentidadRepository;
import pe.edu.ulima.espacioya.cuentas.repository.NombreCuenta;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository.EnRevision;
import pe.edu.ulima.espacioya.cuentas.repository.SesionRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;

@Service
public class CuentaServiceImpl implements CuentaService {

    public static final String MENSAJE_YA_REVISADA = "Este arrendador ya no está en revisión. Actualiza la cola.";
    public static final String MENSAJE_NO_GESTIONABLE = "Esta cuenta no se puede suspender ni reactivar.";
    public static final String MENSAJE_YA_SUSPENDIDA = "Esta cuenta ya está suspendida. Actualiza la página.";
    public static final String MENSAJE_YA_ACTIVA = "Esta cuenta ya está activa. Actualiza la página.";
    public static final int MAXIMO_RESULTADOS_BUSQUEDA = 20;

    private final UsuarioRepository usuarios;
    private final PerfilArrendadorRepository perfiles;
    private final DocumentoIdentidadRepository documentos;
    private final ArchivosPrivados archivos;
    private final NotificadorCorreo notificador;
    private final SesionRepository sesiones;

    public CuentaServiceImpl(UsuarioRepository usuarios, PerfilArrendadorRepository perfiles,
                             DocumentoIdentidadRepository documentos, ArchivosPrivados archivos,
                             NotificadorCorreo notificador, SesionRepository sesiones) {
        this.usuarios = usuarios;
        this.perfiles = perfiles;
        this.documentos = documentos;
        this.archivos = archivos;
        this.notificador = notificador;
        this.sesiones = sesiones;
    }

    @Override
    @Transactional(readOnly = true)
    public HabilitacionArrendadorDTO habilitacionArrendador(UsuarioAutenticado usuario) {
        if (!Rol.ARRENDADOR.name().equals(usuario.rol())) {
            throw new AccesoDenegadoException();
        }
        var cuenta = usuarios.findById(usuario.usuarioId())
                .orElseThrow(() -> new IllegalStateException("La cuenta de una sesión vigente no existe"));
        var perfil = perfiles.findById(usuario.usuarioId())
                .orElseThrow(() -> new IllegalStateException("El arrendador no tiene perfil"));
        return new HabilitacionArrendadorDTO(cuenta.isCorreoVerificado(),
                perfil.getEstadoIdentidad() == EstadoIdentidad.VERIFICADA);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ArrendadorEnRevisionDTO> arrendadoresEnRevision(UsuarioAutenticado admin) {
        exigirAdmin(admin);
        var cola = perfiles.findEnRevision();
        var ids = cola.stream().map(EnRevision::arrendadorId).toList();
        Map<Long, Usuario> cuentas = usuarios.findAllById(ids).stream()
                .collect(Collectors.toMap(Usuario::getId, Function.identity()));
        Map<Long, PerfilArrendador> perfilesPorId = perfiles.findAllById(ids).stream()
                .collect(Collectors.toMap(PerfilArrendador::getUsuarioId, Function.identity()));
        return cola.stream()
                .filter(fila -> cuentas.containsKey(fila.arrendadorId())
                        && perfilesPorId.containsKey(fila.arrendadorId()))
                .map(fila -> aDto(fila, cuentas.get(fila.arrendadorId()), perfilesPorId.get(fila.arrendadorId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ExpedienteIdentidadDTO expedienteIdentidad(UsuarioAutenticado admin, Long arrendadorId) {
        exigirAdmin(admin);
        var cuenta = arrendadorActivo(arrendadorId);
        var perfil = perfiles.findById(arrendadorId)
                .filter(encontrado -> encontrado.getEstadoIdentidad() == EstadoIdentidad.EN_REVISION)
                .orElseThrow(RecursoNoEncontradoException::new);
        var vigentes = documentos.findByArrendadorIdOrderByTipoAsc(arrendadorId);
        var enviadoEn = vigentes.stream().map(DocumentoIdentidad::getSubidoEn)
                .min(Comparator.naturalOrder()).orElse(null);
        // Las URL se firman solo aquí, al abrir el expediente: vencen 5 minutos después (R24, R25).
        return new ExpedienteIdentidadDTO(arrendadorId, cuenta.getNombre(), cuenta.getCorreo(), cuenta.getTelefono(),
                perfil.getTipoDocumento().name(), perfil.getNumeroDocumento(), perfil.getRazonSocial(),
                perfil.getDireccionFiscal(), perfil.getEstadoIdentidad().name(), enviadoEn,
                vigentes.stream().map(this::aDto).toList());
    }

    @Override
    @Transactional
    public String aprobarIdentidad(UsuarioAutenticado admin, Long arrendadorId) {
        exigirAdmin(admin);
        var decision = paraDecidir(arrendadorId);
        decision.perfil().aprobarIdentidad();
        var correo = decision.cuenta().getCorreo();
        DespuesDelCommit.ejecutar(() -> notificador.notificarIdentidadAprobada(correo));
        return decision.perfil().getEstadoIdentidad().name();
    }

    @Override
    @Transactional
    public String rechazarIdentidad(UsuarioAutenticado admin, Long arrendadorId, String motivo) {
        exigirAdmin(admin);
        var decision = paraDecidir(arrendadorId);
        decision.perfil().rechazarIdentidad(motivo);
        var correo = decision.cuenta().getCorreo();
        var motivoGuardado = decision.perfil().getMotivoRechazo();
        DespuesDelCommit.ejecutar(() -> notificador.notificarIdentidadRechazada(correo, motivoGuardado));
        return decision.perfil().getEstadoIdentidad().name();
    }

    @Override
    @Transactional(readOnly = true)
    public BusquedaCuentasDTO buscarCuentas(UsuarioAutenticado admin, String textoRecortado) {
        exigirAdmin(admin);
        var patron = "%" + escaparComodines(textoRecortado.toLowerCase(Locale.ROOT)) + "%";
        // Se pide una de más para saber si hay más sin un count (DT-5).
        var encontradas = usuarios.buscar(patron, Limit.of(MAXIMO_RESULTADOS_BUSQUEDA + 1));
        var hayMas = encontradas.size() > MAXIMO_RESULTADOS_BUSQUEDA;
        var cuentas = encontradas.stream().limit(MAXIMO_RESULTADOS_BUSQUEDA)
                .map(cuenta -> new CuentaResumenDTO(cuenta.getId(), cuenta.getNombre(), cuenta.getCorreo(),
                        cuenta.getRol().name(), cuenta.getEstado().name()))
                .toList();
        return new BusquedaCuentasDTO(cuentas, hayMas);
    }

    @Override
    @Transactional(readOnly = true)
    public DetalleCuentaDTO detalleCuenta(UsuarioAutenticado admin, Long cuentaId) {
        exigirAdmin(admin);
        var cuenta = usuarios.findById(cuentaId).orElseThrow(RecursoNoEncontradoException::new);
        String estadoIdentidad = null;
        if (cuenta.getRol() == Rol.ARRENDADOR) {
            estadoIdentidad = perfiles.findById(cuentaId).map(perfil -> perfil.getEstadoIdentidad().name())
                    .orElse(null);
        }
        return new DetalleCuentaDTO(cuenta.getId(), cuenta.getNombre(), cuenta.getCorreo(), cuenta.getTelefono(),
                cuenta.getRol().name(), cuenta.getEstado().name(), cuenta.isCorreoVerificado(), estadoIdentidad,
                cuenta.puedeSuspenderse(), cuenta.puedeReactivarse());
    }

    @Override
    @Transactional
    public CambioEstadoCuentaDTO suspenderCuenta(UsuarioAutenticado admin, Long cuentaId, String motivo) {
        exigirAdmin(admin);
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Una suspensión exige un motivo");
        }
        var cuenta = paraCambiarEstado(cuentaId);
        if (!cuenta.puedeSuspenderse()) {
            throw new ReglaNegocioException(MENSAJE_YA_SUSPENDIDA);
        }
        cuenta.suspender();
        var estado = cuenta.getEstado().name();
        var correo = cuenta.getCorreo();
        var motivoRecortado = motivo.strip();
        // revocarTodas escribe antes el estado (flush) y deja la cuenta detached (clear): no se vuelve a usar.
        var revocadas = sesiones.revocarTodas(cuentaId);
        DespuesDelCommit.ejecutar(() -> notificador.notificarCuentaSuspendida(correo, motivoRecortado));
        return new CambioEstadoCuentaDTO(estado, revocadas);
    }

    @Override
    @Transactional
    public CambioEstadoCuentaDTO reactivarCuenta(UsuarioAutenticado admin, Long cuentaId) {
        exigirAdmin(admin);
        var cuenta = paraCambiarEstado(cuentaId);
        if (!cuenta.puedeReactivarse()) {
            throw new ReglaNegocioException(MENSAJE_YA_ACTIVA);
        }
        cuenta.reactivar();
        var correo = cuenta.getCorreo();
        DespuesDelCommit.ejecutar(() -> notificador.notificarCuentaReactivada(correo));
        return new CambioEstadoCuentaDTO(cuenta.getEstado().name(), 0);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean cuentaActiva(Long usuarioId) {
        if (usuarioId == null) {
            return false;
        }
        return usuarios.findById(usuarioId).map(cuenta -> cuenta.getEstado() == EstadoUsuario.ACTIVA).orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> arrendadoresNoActivos() {
        return Set.copyOf(usuarios.findIdsArrendadoresNoActivos());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void bloquearCuenta(Long usuarioId) {
        usuarios.findParaActualizar(usuarioId).orElseThrow(RecursoNoEncontradoException::new);
    }

    @Override
    @Transactional(readOnly = true)
    public String correoDe(Long usuarioId) {
        return usuarios.findById(usuarioId).map(Usuario::getCorreo).orElseThrow(RecursoNoEncontradoException::new);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> nombres(Collection<Long> usuarioIds) {
        if (usuarioIds.isEmpty()) {
            return Map.of();
        }
        return usuarios.nombresDe(Set.copyOf(usuarioIds)).stream()
                .filter(cuenta -> !cuenta.nombre().isBlank())
                .collect(Collectors.toMap(NombreCuenta::id, NombreCuenta::nombre));
    }

    // El bloqueo de la fila serializa dos admins sobre la misma cuenta: el segundo espera y ve el estado nuevo (R38).
    // Orden fijo: existe (404) → gestionable (400) → estado (400, lo comprueba cada llamador).
    private Usuario paraCambiarEstado(Long cuentaId) {
        var cuenta = usuarios.findParaActualizar(cuentaId).orElseThrow(RecursoNoEncontradoException::new);
        if (!cuenta.esGestionablePorAdmin()) {
            throw new ReglaNegocioException(MENSAJE_NO_GESTIONABLE);
        }
        return cuenta;
    }

    // El texto se compara literal (R4). La barra se escapa primero para no duplicar los escapes de % y _.
    static String escaparComodines(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    // Defensa en profundidad (HU-06, R41): administracion ya validó el rol, pero ningún otro llamador lo salta.
    private static void exigirAdmin(UsuarioAutenticado admin) {
        if (admin == null || !Rol.ADMIN.name().equals(admin.rol())) {
            throw new AccesoDenegadoException();
        }
    }

    private Usuario arrendadorActivo(Long arrendadorId) {
        return usuarios.findById(arrendadorId)
                .filter(cuenta -> cuenta.getRol() == Rol.ARRENDADOR && cuenta.getEstado() == EstadoUsuario.ACTIVA)
                .orElseThrow(RecursoNoEncontradoException::new);
    }

    // El bloqueo de la fila del perfil serializa dos decisiones simultáneas: la segunda espera y ve el estado
    // nuevo (R39).
    private Decision paraDecidir(Long arrendadorId) {
        var perfil = perfiles.findParaActualizar(arrendadorId).orElseThrow(RecursoNoEncontradoException::new);
        var cuenta = arrendadorActivo(arrendadorId);
        if (perfil.getEstadoIdentidad() != EstadoIdentidad.EN_REVISION) {
            throw new ReglaNegocioException(MENSAJE_YA_REVISADA);
        }
        return new Decision(cuenta, perfil);
    }

    private static ArrendadorEnRevisionDTO aDto(EnRevision fila, Usuario cuenta, PerfilArrendador perfil) {
        return new ArrendadorEnRevisionDTO(fila.arrendadorId(), cuenta.getNombre(), perfil.getRazonSocial(),
                perfil.getTipoDocumento().name(), fila.enviadoEn());
    }

    private DocumentoIdentidadDTO aDto(DocumentoIdentidad documento) {
        var url = archivos.urlFirmada(documento.getRutaArchivo(), documento.getTipoMime());
        return new DocumentoIdentidadDTO(documento.getId(), documento.getTipo().name(), documento.getTipoMime(),
                documento.getTamano(), documento.getSubidoEn(), url.url(), url.expiraEn());
    }

    private record Decision(Usuario cuenta, PerfilArrendador perfil) {
    }
}
