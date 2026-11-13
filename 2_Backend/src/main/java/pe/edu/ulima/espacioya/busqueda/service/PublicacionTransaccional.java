package pe.edu.ulima.espacioya.busqueda.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.busqueda.domain.Resena;
import pe.edu.ulima.espacioya.comun.error.ReglaNegocioException;

/**
 * ★ Decorator, rol <b>ConcreteDecorator</b> (HU-24, R47, R48, DT-6, DT-21): ejecuta la envuelta <b>dentro</b> de una
 * transacción y traduce la carrera por {@code uq_resena_reserva} a <i>ya reseñada</i>. Es un {@link TransactionTemplate}
 * y no {@code @Transactional}: con él la violación llegaría al confirmar y no se podría traducir.
 */
public final class PublicacionTransaccional extends PublicacionResenaDecorador {

    static final String RESTRICCION_UNA_POR_RESERVA = "uq_resena_reserva";

    private final TransactionTemplate transacciones;

    public PublicacionTransaccional(PublicacionResena envuelta, TransactionTemplate transacciones) {
        super(envuelta);
        this.transacciones = transacciones;
    }

    @Override
    public Resena publicar(SolicitudResena solicitud) {
        try {
            return transacciones.execute(estado -> super.publicar(solicitud));
        } catch (DataIntegrityViolationException conflicto) {
            if (esReservaYaResenada(conflicto)) {
                throw new ReglaNegocioException(ResenaService.MENSAJE_YA_RESENADA);
            }
            throw conflicto;
        }
    }

    // Por el nombre de la restricción: otro fallo de integridad no debe disfrazarse de reseña repetida (R14).
    private static boolean esReservaYaResenada(DataIntegrityViolationException conflicto) {
        var mensaje = conflicto.getMostSpecificCause().getMessage();
        return mensaje != null && mensaje.contains(RESTRICCION_UNA_POR_RESERVA);
    }
}
