package pe.edu.ulima.espacioya.pagos.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

/**
 * Reglas puras de la liquidación (HU-19): cuándo una reserva ya se puede liquidar y cuánto es la comisión. Replica la
 * escala y el redondeo de {@code reservas.domain.PoliticaReembolso} sin importarla ({@code pagos} no depende de
 * {@code reservas}).
 */
public final class ReglasLiquidacion {

    /** RN-10: comisión fija de la plataforma. El CHECK {@code ck_liquidacion_montos} fija el mismo valor. */
    public static final BigDecimal TASA_COMISION = new BigDecimal("0.10");

    /** CA1: una reserva se liquida 48 h después de su fin. HU-26 abre reclamos solo antes de este mismo borde. */
    public static final Duration ESPERA = Duration.ofHours(48);

    private static final int ESCALA = 2;

    private ReglasLiquidacion() {
    }

    /** Borde inclusivo (S1): liquidable si {@code corte ≥ finEn + 48 h}. */
    public static boolean esLiquidable(Instant finEn, Instant corte) {
        return !corte.isBefore(finEn.plus(ESPERA));
    }

    /** 10 % del bruto total, escala 2, {@code HALF_UP} (S4). */
    public static BigDecimal comision(BigDecimal bruto) {
        return bruto.multiply(TASA_COMISION).setScale(ESCALA, RoundingMode.HALF_UP);
    }

    /** Pago de la pasarela simulada: ningún dinero se movió (HU-13 RS-5, D2). */
    public static boolean esSimulado(String idTransaccion) {
        return idTransaccion != null && idTransaccion.startsWith(ReglasPago.PREFIJO_SIMULADO);
    }
}
