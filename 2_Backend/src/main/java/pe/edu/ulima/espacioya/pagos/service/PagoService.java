package pe.edu.ulima.espacioya.pagos.service;

import java.util.Collection;
import java.util.List;

/** Servicio público de pagos para otros módulos ({@code docs/architecture.md}: "reservas usa PagoService"). */
public interface PagoService {

    /**
     * Reembolsa una cancelación contra el pago {@code PAGADO} de la reserva. Lo llama {@code reservas} una sola vez
     * por reserva, en su transacción ({@code MANDATORY}), con la reserva ya {@code CANCELADA}, sus bloques liberados y
     * {@code monto > 0} (HU-15 D6, D7).
     * <p>
     * En la transacción del llamador crea el reembolso {@code PENDIENTE} y audita {@code REEMBOLSO_SOLICITADO}; tras el
     * commit, el Command {@code SolicitudReembolso} lo pide a la pasarela y deja reembolso y pago {@code REEMBOLSADO} o
     * {@code REEMBOLSO_FALLIDO} (HU-16). Un fallo de la pasarela no lanza aquí ni revierte la cancelación; si la
     * cancelación se revierte, no se llama a la pasarela. Cualquier excepción de este método revierte la cancelación.
     *
     * @throws IllegalStateException si la reserva no tiene pago {@code PAGADO} o ya tiene un reembolso por cancelación.
     * @throws IllegalArgumentException si el monto no es mayor que 0 o supera el pago.
     */
    void reembolsarCancelacion(ReembolsoPorCancelacion solicitud);

    /**
     * Pagos de esas reservas con sus reembolsos, en orden de creación (HU-22 R6). {@code readOnly};
     * colección vacía → {@code List.of()} sin consultar. Sin sesión ni rol: {@code reservas} pasa solo las reservas
     * del titular.
     */
    List<PagoDeReserva> pagosDeReservas(Collection<Long> reservaIds);

    /**
     * {@code MANDATORY}. Toma {@code pg_advisory_xact_lock(190019)}, el mismo bloqueo que {@code GeneradorLiquidaciones},
     * hasta el fin de la transacción del llamador (HU-26 DT-6, R21): abrir un reclamo y generar liquidaciones se
     * serializan.
     */
    void bloquearGeneracionLiquidaciones();

    /** {@code MANDATORY}. {@code true} si la reserva ya está en {@code liquidacion_detalle} (HU-26 R8). */
    boolean reservaLiquidada(Long reservaId);

    /**
     * Como {@link #reembolsarCancelacion}, con origen {@code RECLAMO} (HU-26 R44, R45). {@code MANDATORY}: pago
     * {@code PAGADO}, {@code 0 < monto ≤ pago.monto}, sin otro reembolso {@code RECLAMO} del pago; crea el reembolso
     * {@code PENDIENTE}, audita {@code REEMBOLSO_SOLICITADO} ({@code origen=RECLAMO;reservaId=…;monto=…}) y entrega el
     * Command {@code AUTOMATICO} al Invoker para después del commit.
     *
     * @throws IllegalStateException si la reserva no tiene pago {@code PAGADO} o ya tiene un reembolso por reclamo.
     * @throws IllegalArgumentException si el monto no es mayor que 0 o supera el pago.
     */
    void reembolsarReclamo(ReembolsoPorReclamo solicitud);
}
