package pe.edu.ulima.espacioya.busqueda.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.busqueda.domain.Resena;
import pe.edu.ulima.espacioya.busqueda.dto.ResenaExportadaDTO;
import pe.edu.ulima.espacioya.busqueda.repository.ResenaDAO;
import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;
import pe.edu.ulima.espacioya.cuentas.service.puertos.Impedimento;
import pe.edu.ulima.espacioya.cuentas.service.puertos.ParticipanteDatosPersonales;
import pe.edu.ulima.espacioya.cuentas.service.puertos.SeccionDatos;
import pe.edu.ulima.espacioya.cuentas.service.puertos.TitularDatos;
import pe.edu.ulima.espacioya.espacios.service.CatalogoEspaciosService;

/**
 * Participante de {@code busqueda} en HU-22 (HU-24 P11). Solo el rol {@code USUARIO} escribe reseñas. Al eliminar la
 * cuenta se borra el texto libre y se conserva la calificación, para no alterar el promedio de un espacio por un hecho
 * ajeno a él. {@code CUENTA_ELIMINADA} ya lo audita.
 */
@Component
@Order(3)
public class DatosPersonalesResenasImpl implements ParticipanteDatosPersonales {

    public static final String SECCION_RESENAS = "resenas";

    // ★ DAO (HU-24 reapertura, DT-27): BusinessObject de ResenaDAO, sin ResenaRepository (R53).
    private final ResenaDAO resenas;
    private final CatalogoEspaciosService catalogo;

    public DatosPersonalesResenasImpl(ResenaDAO resenas, CatalogoEspaciosService catalogo) {
        this.resenas = resenas;
        this.catalogo = catalogo;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SeccionDatos> exportar(TitularDatos titular) {
        if (!titular.esUsuario()) {
            return List.of();
        }
        var suyas = resenas.delUsuario(titular.usuarioId());
        var nombres = catalogo.nombres(suyas.stream().map(Resena::getEspacioId).distinct().toList());
        var exportadas = suyas.stream().map(resena -> exportada(resena, nombres)).toList();
        return List.of(new SeccionDatos(SECCION_RESENAS, exportadas));
    }

    /** Ninguna: una reseña no impide eliminar la cuenta (R41). */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Impedimento> impedimentos(TitularDatos titular, Instant ahora) {
        return List.of();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void anonimizar(TitularDatos titular) {
        resenas.anonimizarComentarios(titular.usuarioId());
    }

    private static ResenaExportadaDTO exportada(Resena resena, Map<Long, String> nombres) {
        return new ResenaExportadaDTO(resena.getReservaId(), nombres.get(resena.getEspacioId()),
                resena.getCalificacion(), resena.getComentario(), resena.getEstado().name(),
                LocalDate.ofInstant(resena.getCreadoEn(), ZonaHoraria.LIMA));
    }
}
