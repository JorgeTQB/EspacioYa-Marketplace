package pe.edu.ulima.espacioya.busqueda.service;

import pe.edu.ulima.espacioya.busqueda.domain.Resena;
import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.comun.auditoria.EventoAuditoria;

/**
 * ★ Decorator, rol <b>ConcreteDecorator</b> (HU-24, R49, R50): registra {@code RESENA_CREADA} <b>después</b> de que
 * la envuelta vuelve. Va dentro de {@link PublicacionTransaccional}: si la auditoría falla, la reseña se revierte
 * (R51); si la envuelta lanza, no registra nada (R24).
 */
public final class PublicacionAuditada extends PublicacionResenaDecorador {

    private static final String ENTIDAD_RESENA = "resena";

    private final AuditoriaService auditoria;

    public PublicacionAuditada(PublicacionResena envuelta, AuditoriaService auditoria) {
        super(envuelta);
        this.auditoria = auditoria;
    }

    @Override
    public Resena publicar(SolicitudResena solicitud) {
        var resena = super.publicar(solicitud);
        // Sin el comentario: el detalle de R23 solo lleva ids y la calificación.
        var detalle = "reservaId=" + resena.getReservaId() + ";espacioId=" + resena.getEspacioId() + ";calificacion="
                + resena.getCalificacion();
        auditoria.registrar(new EventoAuditoria(solicitud.usuarioId(), ResenaService.ACCION_RESENA_CREADA,
                ENTIDAD_RESENA, resena.getId(), solicitud.ip(), detalle));
        return resena;
    }
}
