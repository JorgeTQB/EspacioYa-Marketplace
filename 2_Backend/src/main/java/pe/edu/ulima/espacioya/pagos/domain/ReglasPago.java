package pe.edu.ulima.espacioya.pagos.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;

/** Reglas puras de HU-13: cuándo se puede pagar y cómo se clasifica un pago aprobado. Sin Spring ni pasarela. */
public final class ReglasPago {

    public static final int MAXIMO_RECHAZOS = 3;
    public static final Duration VENTANA_RECHAZOS = Duration.ofMinutes(30);
    public static final String MONEDA = "PEN";

    /** Inicio de {@code id_transaccion_pasarela} de la pasarela simulada: ningún dinero se movió (RS-5, HU-19 D2). */
    public static final String PREFIJO_SIMULADO = "sim-";

    private static final Pattern ID_BIEN_ESCRITO = Pattern.compile("[0-9]{1,19}");

    private ReglasPago() {
    }

    /** {@code null} si está disponible; el orden es el de R18. */
    public static MotivoNoDisponible motivoNoDisponible(boolean vigente, Instant expiraEn, boolean espacioVisible,
                                                        long rechazosRecientes, Instant ahora, Duration margen) {
        if (!vigente) {
            return MotivoNoDisponible.NO_VIGENTE;
        }
        if (!ahora.isBefore(expiraEn.minus(margen))) {
            return MotivoNoDisponible.ULTIMO_TRAMO;
        }
        if (!espacioVisible) {
            return MotivoNoDisponible.ESPACIO_NO_VISIBLE;
        }
        if (intentosAgotados(rechazosRecientes)) {
            return MotivoNoDisponible.INTENTOS_AGOTADOS;
        }
        return null;
    }

    public static boolean intentosAgotados(long rechazosRecientes) {
        return rechazosRecientes >= MAXIMO_RECHAZOS;
    }

    /** Un rechazo cuenta si {@code creado_en} es igual o posterior a este instante. */
    public static Instant inicioVentanaRechazos(Instant ahora) {
        return ahora.minus(VENTANA_RECHAZOS);
    }

    /** {@code MONTO_DISTINTO}, {@code PAGADO_TARDIO} o {@code PAGADO}, en ese orden. 241.5 es igual a 241.50. */
    public static EstadoPago estadoDeAprobado(BigDecimal monto, String moneda, BigDecimal montoReserva,
                                              boolean vigente) {
        if (!MONEDA.equals(moneda) || monto.compareTo(montoReserva) != 0) {
            return EstadoPago.MONTO_DISTINTO;
        }
        return vigente ? EstadoPago.PAGADO : EstadoPago.PAGADO_TARDIO;
    }

    /** {@code null} si el texto no es un id bien escrito (ver {@link #idBienEscrito}). */
    public static Long idDeReserva(String texto) {
        return idBienEscrito(texto);
    }

    /** {@code null} si el texto no es un id bien escrito: 1 a 19 dígitos, sin desbordar un long y mayor que 0. */
    public static Long idBienEscrito(String texto) {
        if (texto == null || !ID_BIEN_ESCRITO.matcher(texto).matches()) {
            return null;
        }
        try {
            var id = Long.parseLong(texto);
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
