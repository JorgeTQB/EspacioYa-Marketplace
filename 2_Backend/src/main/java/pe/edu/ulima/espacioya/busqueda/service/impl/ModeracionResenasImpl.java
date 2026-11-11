package pe.edu.ulima.espacioya.busqueda.service.impl;

import static pe.edu.ulima.espacioya.comun.seguridad.RolesSesion.exigirAdmin;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.busqueda.domain.Resena;
import pe.edu.ulima.espacioya.busqueda.repository.ResenaRepository;
import pe.edu.ulima.espacioya.busqueda.service.ModeracionResenas;
import pe.edu.ulima.espacioya.busqueda.service.ResenaModerada;
import pe.edu.ulima.espacioya.busqueda.service.ResenaReportable;
import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;
import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;
import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.espacios.service.CatalogoEspaciosService;

/**
 * Moderación de reseñas para {@code administracion} (HU-27). Las consultas públicas de HU-24 ya filtran
 * {@code VISIBLE}: ocultarla la saca de la ficha y del promedio sin tocarlas (R37).
 */
@Service
public class ModeracionResenasImpl implements ModeracionResenas {

    public static final String ACCION_OCULTACION = "OCULTACION_RESENA";
    public static final String MENSAJE_NO_OCULTABLE = "Esta reseña ya está oculta.";

    private static final String ENTIDAD_RESENA = "resena";

    private final ResenaRepository resenas;
    private final CatalogoEspaciosService catalogo;
    private final AuditoriaService auditoria;

    public ModeracionResenasImpl(ResenaRepository resenas, CatalogoEspaciosService catalogo,
                                 AuditoriaService auditoria) {
        this.resenas = resenas;
        this.catalogo = catalogo;
        this.auditoria = auditoria;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ResenaReportable> reportable(Long resenaId) {
        if (resenaId == null) {
            return Optional.empty();
        }
        return resenas.findById(resenaId)
                .filter(Resena::estaVisible)
                .filter(resena -> catalogo.esVisible(resena.getEspacioId()))
                .map(resena -> new ResenaReportable(resena.getId(), resena.getUsuarioId(), resena.getEspacioId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, ResenaModerada> describir(Collection<Long> resenaIds) {
        if (resenaIds.isEmpty()) {
            return Map.of();
        }
        return resenas.findAllById(Set.copyOf(resenaIds)).stream()
                .map(ModeracionResenasImpl::moderada)
                .collect(Collectors.toMap(ResenaModerada::id, Function.identity()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void ocultar(UsuarioAutenticado admin, Long resenaId, String ip) {
        exigirAdmin(admin);
        var resena = resenas.findParaActualizar(resenaId).orElseThrow(RecursoNoEncontradoException::new);
        if (!resena.estaVisible()) {
            throw new ReglaNegocioException(MENSAJE_NO_OCULTABLE);
        }
        resena.ocultar();
        auditoria.registrar(new EventoAuditoria(admin.usuarioId(), ACCION_OCULTACION, ENTIDAD_RESENA, resena.getId(),
                ip, "estado=VISIBLE->OCULTA;actor=ADMIN;espacioId=" + resena.getEspacioId()));
    }

    private static ResenaModerada moderada(Resena resena) {
        return new ResenaModerada(resena.getId(), resena.getEspacioId(), resena.getCalificacion(),
                resena.getComentario(), LocalDate.ofInstant(resena.getCreadoEn(), ZonaHoraria.LIMA),
                resena.getEstado().name());
    }
}
