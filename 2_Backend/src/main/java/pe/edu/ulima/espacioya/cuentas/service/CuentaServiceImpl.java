package pe.edu.ulima.espacioya.cuentas.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import pe.edu.ulima.espacioya.comun.almacenamiento.ArchivosPrivados;
import pe.edu.ulima.espacioya.comun.error.AccesoDenegadoException;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.cuentas.domain.DocumentoIdentidad;
import pe.edu.ulima.espacioya.cuentas.domain.EstadoIdentidad;
import pe.edu.ulima.espacioya.cuentas.domain.EstadoUsuario;
import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.Rol;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.dto.ArrendadorEnRevisionDTO;
import pe.edu.ulima.espacioya.cuentas.dto.DocumentoIdentidadDTO;
import pe.edu.ulima.espacioya.cuentas.dto.ExpedienteIdentidadDTO;
import pe.edu.ulima.espacioya.cuentas.dto.HabilitacionArrendadorDTO;
import pe.edu.ulima.espacioya.cuentas.repository.DocumentoIdentidadRepository;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository.EnRevision;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;

@Service
public class CuentaServiceImpl implements CuentaService {

    public static final String MENSAJE_YA_REVISADA = "Este arrendador ya no está en revisión. Actualiza la cola.";

    private static final Logger log = LoggerFactory.getLogger(CuentaServiceImpl.class);

    private final UsuarioRepository usuarios;
    private final PerfilArrendadorRepository perfiles;
    private final DocumentoIdentidadRepository documentos;
    private final ArchivosPrivados archivos;
    private final NotificadorCorreo notificador;

    public CuentaServiceImpl(UsuarioRepository usuarios, PerfilArrendadorRepository perfiles,
                             DocumentoIdentidadRepository documentos, ArchivosPrivados archivos,
                             NotificadorCorreo notificador) {
        this.usuarios = usuarios;
        this.perfiles = perfiles;
        this.documentos = documentos;
        this.archivos = archivos;
        this.notificador = notificador;
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
        despuesDelCommit(() -> notificador.notificarIdentidadAprobada(correo));
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
        despuesDelCommit(() -> notificador.notificarIdentidadRechazada(correo, motivoGuardado));
        return decision.perfil().getEstadoIdentidad().name();
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

    // No se avisa una decisión que hizo rollback (R40). Un fallo del envío no revierte la decisión ya confirmada.
    private static void despuesDelCommit(Runnable aviso) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    aviso.run();
                } catch (RuntimeException e) {
                    log.warn("No se pudo enviar el aviso de la decisión de identidad");
                }
            }
        });
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
