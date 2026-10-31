package pe.edu.ulima.espacioya.pagos.service;

/** Servicio público de pagos para otros módulos ({@code docs/architecture.md}: "reservas usa PagoService"). */
public interface PagoService {

    /**
     * Deja solicitado el reembolso de una cancelación contra el pago {@code PAGADO} de la reserva, en la transacción
     * del llamador ({@code MANDATORY}). Lo llama {@code reservas} una sola vez por reserva, con la reserva ya
     * {@code CANCELADA}, sus bloques liberados y {@code monto > 0} (HU-15 D6, D7).
     * <p>
     * HU-15 solo busca el pago, valida el monto y audita {@code REEMBOLSO_SOLICITADO}: no llama a la pasarela ni
     * cambia el estado del pago. HU-16 reemplaza el cuerpo sin cambiar la firma ni la propagación: crea la fila de
     * {@code reembolso} y ejecuta el Command contra {@code PasarelaPago.reembolsar} después del commit. Un fallo de la
     * pasarela no debe lanzar aquí (va a {@code REEMBOLSO_FALLIDO}); cualquier excepción revierte la cancelación.
     *
     * @throws IllegalStateException si la reserva no tiene pago {@code PAGADO}.
     * @throws IllegalArgumentException si el monto no es mayor que 0 o supera el pago.
     */
    void reembolsarCancelacion(ReembolsoPorCancelacion solicitud);
}
