package pe.edu.ulima.espacioya.comun.correo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;

/** Formatos de los correos (HU-25, tabla C1): fechas y horas de Lima, montos en soles con escala 2. */
public final class FormatoCorreo {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter INSTANTE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZonaHoraria.LIMA);

    private FormatoCorreo() {
    }

    public static String fecha(LocalDate fecha) {
        return FECHA.format(fecha);
    }

    /** "10:00 a 12:00"; la hora fin 24 (bloque de 23:00) se escribe "24:00". */
    public static String horario(int horaInicio, int horaFin) {
        return "%02d:00 a %02d:00".formatted(horaInicio, horaFin);
    }

    public static String monto(BigDecimal monto) {
        return "S/ " + monto.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public static String instante(Instant instante) {
        return INSTANTE.format(instante);
    }
}
