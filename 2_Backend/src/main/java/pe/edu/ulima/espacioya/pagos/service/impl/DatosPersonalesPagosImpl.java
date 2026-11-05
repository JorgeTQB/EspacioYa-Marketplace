package pe.edu.ulima.espacioya.pagos.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.ulima.espacioya.cuentas.service.puertos.Impedimento;
import pe.edu.ulima.espacioya.cuentas.service.puertos.ParticipanteDatosPersonales;
import pe.edu.ulima.espacioya.cuentas.service.puertos.SeccionDatos;
import pe.edu.ulima.espacioya.cuentas.service.puertos.TitularDatos;
import pe.edu.ulima.espacioya.pagos.domain.Pago;
import pe.edu.ulima.espacioya.pagos.dto.CuentaBancariaExportadaDTO;
import pe.edu.ulima.espacioya.pagos.repository.CuentaBancariaRepository;
import pe.edu.ulima.espacioya.pagos.repository.LiquidacionRepository;
import pe.edu.ulima.espacioya.pagos.repository.PagoRepository;
import pe.edu.ulima.espacioya.pagos.service.MisLiquidacionesService;
import pe.edu.ulima.espacioya.pagos.service.VistaLiquidaciones;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservasLiquidables;

/**
 * Participante de {@code pagos} en HU-22. Solo el arrendador tiene datos aquí: liquidaciones y cuenta bancaria. Los
 * pagos del usuario los exporta {@code reservas}, que sabe cuáles son sus reservas (DT-3).
 */
@Component
@Order(2)
public class DatosPersonalesPagosImpl implements ParticipanteDatosPersonales {

    public static final String SECCION_LIQUIDACIONES = "liquidaciones";
    public static final String SECCION_CUENTA_BANCARIA = "cuentaBancaria";
    public static final String CAMPO_LIQUIDACIONES = "liquidaciones";
    public static final String CAMPO_PAGOS = "pagos";
    public static final String MENSAJE_LIQUIDACIONES =
            "Tienes liquidaciones por cobrar. Espera a que se paguen antes de eliminar tu cuenta.";
    public static final String MENSAJE_COBROS_POR_LIQUIDAR = "Tienes cobros de reservas que aún no se liquidan. "
            + "Espera a que se liquiden y se paguen antes de eliminar tu cuenta.";

    private static final String PASARELA_SIMULADA = "simulada";

    private final LiquidacionRepository liquidaciones;
    private final VistaLiquidaciones vista;
    private final MisLiquidacionesService misLiquidaciones;
    private final CuentaBancariaRepository cuentas;
    private final PagoRepository pagos;
    private final ReservasLiquidables reservas;
    private final Clock reloj;
    private final boolean incluirSimulados;

    public DatosPersonalesPagosImpl(LiquidacionRepository liquidaciones, VistaLiquidaciones vista,
                                    MisLiquidacionesService misLiquidaciones, CuentaBancariaRepository cuentas,
                                    PagoRepository pagos, ReservasLiquidables reservas, Clock reloj,
                                    @Value("${espacioya.pagos.pasarela}") String pasarela) {
        this.liquidaciones = liquidaciones;
        this.vista = vista;
        this.misLiquidaciones = misLiquidaciones;
        this.cuentas = cuentas;
        this.pagos = pagos;
        this.reservas = reservas;
        this.reloj = reloj;
        // El mismo criterio que GeneradorLiquidaciones: lo que la siguiente generación liquidaría.
        this.incluirSimulados = PASARELA_SIMULADA.equalsIgnoreCase(pasarela == null ? "" : pasarela.strip());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SeccionDatos> exportar(TitularDatos titular) {
        if (!titular.esArrendador()) {
            return List.of();
        }
        var id = titular.usuarioId();
        var suyas = vista.misLiquidaciones(liquidaciones.findByArrendadorIdOrderByGeneradaEnDescIdDesc(id));
        var cuenta = cuentas.findByArrendadorId(id)
                .map(c -> CuentaBancariaExportadaDTO.de(c, Instant.now(reloj)))
                .orElse(null);
        return List.of(new SeccionDatos(SECCION_LIQUIDACIONES, suyas),
                new SeccionDatos(SECCION_CUENTA_BANCARIA, cuenta));
    }

    /**
     * R38, R39, R44: el bloqueo de generación va primero; así una generación en curso termina antes y su resultado se
     * ve, o espera a que la eliminación confirme y ya no encuentra cobros de esta cuenta.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Impedimento> impedimentos(TitularDatos titular, Instant ahora) {
        if (!titular.esArrendador()) {
            return List.of();
        }
        var id = titular.usuarioId();
        liquidaciones.bloquearGeneracion(LiquidacionRepository.CLAVE_GENERACION);
        var impedimentos = new ArrayList<Impedimento>();
        if (misLiquidaciones.tieneLiquidacionesPendientes(id)) {
            impedimentos.add(new Impedimento(CAMPO_LIQUIDACIONES, MENSAJE_LIQUIDACIONES));
        }
        if (tieneCobrosPorLiquidar(id)) {
            impedimentos.add(new Impedimento(CAMPO_PAGOS, MENSAJE_COBROS_POR_LIQUIDAR));
        }
        return List.copyOf(impedimentos);
    }

    /** R27: la fila se borra (DT-9); nada la referencia y la liquidación pagada ya copió banco y cuenta enmascarada. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void anonimizar(TitularDatos titular) {
        if (!titular.esArrendador()) {
            return;
        }
        cuentas.findParaActualizar(titular.usuarioId()).ifPresent(cuentas::delete);
    }

    private boolean tieneCobrosPorLiquidar(Long arrendadorId) {
        var reservaIds = pagos.pagadosSinLiquidar(incluirSimulados).stream().map(Pago::getReservaId).toList();
        if (reservaIds.isEmpty()) {
            return false;
        }
        return reservas.describir(reservaIds).stream()
                .anyMatch(reserva -> arrendadorId.equals(reserva.arrendadorId()) && reserva.admitida());
    }
}
