package pe.edu.ulima.espacioya.pagos.service.comprobante;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Escribe el comprobante de pago como PDF 1.4 de una página A4 con Helvetica, sin librerías (HU-28 D1). Sin {@code /Info}
 * ni fechas de creación: la salida es determinista. Muestra de la tarjeta solo los últimos 4 dígitos (R14, R15).
 */
@Component
public class GeneradorComprobantePdf {

    private static final Charset WIN_ANSI = Charset.forName("windows-1252");
    private static final Pattern CUATRO_DIGITOS = Pattern.compile("[0-9]{4}");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int MARGEN = 56;

    public byte[] generar(DatosComprobante datos) {
        var contenido = contenido(datos);
        var salida = new ByteArrayOutputStream();
        var posiciones = new ArrayList<Integer>();

        escribir(salida, "%PDF-1.4\n");
        objeto(salida, posiciones, "<< /Type /Catalog /Pages 2 0 R >>");
        objeto(salida, posiciones, "<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        objeto(salida, posiciones, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                + "/Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>");
        objeto(salida, posiciones, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica "
                + "/Encoding /WinAnsiEncoding >>");
        posiciones.add(salida.size());
        escribir(salida, "5 0 obj\n<< /Length " + contenido.length + " >>\nstream\n");
        salida.writeBytes(contenido);
        escribir(salida, "\nendstream\nendobj\n");

        var inicioXref = salida.size();
        escribir(salida, "xref\n0 " + (posiciones.size() + 1) + "\n0000000000 65535 f \n");
        for (var posicion : posiciones) {
            escribir(salida, String.format("%010d 00000 n \n", posicion));
        }
        escribir(salida, "trailer\n<< /Size " + (posiciones.size() + 1) + " /Root 1 0 R >>\nstartxref\n"
                + inicioXref + "\n%%EOF");
        return salida.toByteArray();
    }

    // Las líneas, en el orden de design.md § 4.
    private static byte[] contenido(DatosComprobante d) {
        var lineas = new ArrayList<Linea>();
        lineas.add(new Linea("EspacioYa · Comprobante de pago", 18, 40));
        lineas.add(new Linea("N.º " + d.numero(), 12, 28));
        lineas.add(new Linea("Fecha de pago: " + DIA.format(d.fechaPago()), 12, 20));
        lineas.add(new Linea("Espacio: " + d.espacio(), 12, 20));
        lineas.add(new Linea("Reserva: " + DIA.format(d.fechaReserva()) + ", " + hora(d.horaInicio()) + " a "
                + hora(d.horaFin()), 12, 20));
        lineas.add(new Linea("Monto pagado: " + soles(d.monto()), 12, 20));
        lineas.add(new Linea("Estado: " + estado(d.estado()), 12, 20));
        if (d.montoReembolsado() != null) {
            lineas.add(new Linea("Monto reembolsado: " + soles(d.montoReembolsado()), 12, 20));
        }
        lineas.add(new Linea(d.ultimos4() != null && CUATRO_DIGITOS.matcher(d.ultimos4()).matches()
                ? "Tarjeta: terminada en " + d.ultimos4() : "Tarjeta: no informada", 12, 20));
        if (d.simulado()) {
            lineas.add(new Linea("Pago simulado: no se cobró dinero real.", 12, 20));
        }

        var flujo = new ByteArrayOutputStream();
        var y = 842 - MARGEN;
        for (var linea : lineas) {
            y -= linea.avance;
            escribir(flujo, "BT /F1 " + linea.tamano + " Tf " + MARGEN + " " + y + " Td (");
            flujo.writeBytes(escapado(linea.texto));
            escribir(flujo, ") Tj ET\n");
        }
        return flujo.toByteArray();
    }

    private static String hora(int hora) {
        return String.format("%02d:00", hora);
    }

    private static String soles(BigDecimal monto) {
        return "S/ " + monto.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String estado(String estado) {
        return switch (estado) {
            case "PAGADO" -> "Pagado";
            case "REEMBOLSADO" -> "Reembolsado";
            default -> estado;
        };
    }

    // windows-1252; lo no representable sale como '?'. Se escapan \ ( ) y se descartan los caracteres de control.
    private static byte[] escapado(String texto) {
        var codificador = WIN_ANSI.newEncoder();
        var salida = new ByteArrayOutputStream();
        texto.codePoints().forEach(punto -> {
            if (punto < 0x20 || punto == 0x7f) {
                return;
            }
            var caracter = new String(Character.toChars(punto));
            if (punto == '\\' || punto == '(' || punto == ')') {
                salida.write('\\');
            }
            if (codificador.canEncode(caracter)) {
                salida.writeBytes(caracter.getBytes(WIN_ANSI));
            } else {
                salida.write('?');
            }
        });
        return salida.toByteArray();
    }

    private static void objeto(ByteArrayOutputStream salida, List<Integer> posiciones, String cuerpo) {
        posiciones.add(salida.size());
        escribir(salida, (posiciones.size()) + " 0 obj\n" + cuerpo + "\nendobj\n");
    }

    private static void escribir(ByteArrayOutputStream salida, String texto) {
        salida.writeBytes(texto.getBytes(StandardCharsets.ISO_8859_1));
    }

    private record Linea(String texto, int tamano, int avance) {
    }
}
