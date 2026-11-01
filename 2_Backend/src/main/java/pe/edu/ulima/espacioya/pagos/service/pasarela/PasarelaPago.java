package pe.edu.ulima.espacioya.pagos.service.pasarela;

import java.util.Optional;

/**
 * Lo que {@code pagos} necesita de una pasarela, sin tipos del proveedor (Adapter, RNF-ARQ-08). Las implementaciones
 * solo traducen: las reglas viven en {@code ReglasPago}. Las fallas de red o de la API lanzan
 * {@link pe.edu.ulima.espacioya.comun.error.ServicioExternoException}.
 */
public interface PasarelaPago {

    CheckoutCreado crearCheckout(SolicitudCheckout solicitud);

    void cerrarCheckout(String idCheckout);

    /**
     * Id del pago notificado, ya con la firma verificada. Vacío si la notificación no es de un pago.
     *
     * @throws FirmaInvalidaException si falta el id o la firma no es válida.
     */
    Optional<String> idNotificado(NotificacionRecibida notificacion);

    PagoConsultado consultarPago(String idTransaccion);

    /**
     * Reembolsa {@code monto} de la transacción {@code idTransaccion} con la clave de idempotencia dada (HU-16). Solo
     * devuelve si la pasarela aprobó el reembolso.
     *
     * @throws pe.edu.ulima.espacioya.comun.error.ServicioExternoException ante cualquier falla, rechazo o respuesta
     *         incompleta (R12, R13).
     */
    ReembolsoRealizado reembolsar(SolicitudReembolsoPasarela solicitud);
}
