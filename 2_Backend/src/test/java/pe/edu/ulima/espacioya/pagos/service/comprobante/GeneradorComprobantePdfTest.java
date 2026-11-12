package pe.edu.ulima.espacioya.pagos.service.comprobante;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class GeneradorComprobantePdfTest {

    private static final Charset WIN_ANSI = Charset.forName("windows-1252");

    private final GeneradorComprobantePdf generador = new GeneradorComprobantePdf();

    private static DatosComprobante pagado(String ultimos4) {
        return new DatosComprobante(87L, LocalDate.parse("2026-11-04"), "Cancha Los Olivos",
                LocalDate.parse("2026-11-10"), 14, 17, new BigDecimal("241.5"), "PAGADO", null, ultimos4, false);
    }

    private String texto(DatosComprobante datos) {
        return new String(generador.generar(datos), WIN_ANSI);
    }

    @Test
    @DisplayName("HU-28 R11: el PDF empieza con %PDF-, termina con %%EOF y su xref apunta a cada objeto")
    void estructuraValida() {
        var bytes = generador.generar(pagado("4242"));
        var texto = new String(bytes, StandardCharsets.ISO_8859_1);

        assertThat(texto).startsWith("%PDF-1.4").endsWith("%%EOF").doesNotContain("/Info");
        var inicioXref = Integer.parseInt(texto.substring(texto.indexOf("startxref\n") + 10,
                texto.indexOf("\n%%EOF")));
        assertThat(texto.substring(inicioXref)).startsWith("xref\n0 6\n");
        for (var n = 1; n <= 5; n++) {
            var entrada = texto.substring(inicioXref).lines().skip(2 + n).findFirst().orElseThrow();
            var posicion = Integer.parseInt(entrada.substring(0, 10));
            assertThat(texto.substring(posicion)).startsWith(n + " 0 obj");
        }
        var largo = Integer.parseInt(texto.substring(texto.indexOf("/Length ") + 8, texto.indexOf(" >>\nstream")));
        var inicio = texto.indexOf("stream\n") + 7;
        assertThat(texto.substring(inicio, inicio + largo)).endsWith(") Tj ET\n");
    }

    @Test
    @DisplayName("HU-28 R12: muestra número, fecha de pago, espacio, fecha y horario de la reserva, monto y estado")
    void contenidoBasico() {
        var texto = texto(pagado("4242"));

        assertThat(texto).contains("(EspacioYa · Comprobante de pago)", "(N.º 87)",
                "(Fecha de pago: 04/11/2026)", "(Espacio: Cancha Los Olivos)",
                "(Reserva: 10/11/2026, 14:00 a 17:00)", "(Monto pagado: S/ 241.50)", "(Estado: Pagado)")
                .doesNotContain("Monto reembolsado").doesNotContain("simulado");
    }

    @Test
    @DisplayName("HU-28 R13: con el pago REEMBOLSADO muestra además el monto reembolsado")
    void conReembolso() {
        var datos = new DatosComprobante(88L, LocalDate.parse("2026-11-04"), "Cancha Los Olivos",
                LocalDate.parse("2026-11-10"), 8, 9, new BigDecimal("241.50"), "REEMBOLSADO",
                new BigDecimal("100"), "4242", false);

        assertThat(texto(datos)).contains("(Estado: Reembolsado)", "(Monto reembolsado: S/ 100.00)");
    }

    @Test
    @DisplayName("HU-28 R14: de la tarjeta muestra solo los últimos 4 dígitos")
    void soloUltimosCuatro() {
        var texto = texto(pagado("4242"));

        assertThat(texto).contains("(Tarjeta: terminada en 4242)").doesNotContain("no informada");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "424", "42424", "42a2", "4242 ", "٤٤٤٤"})
    @DisplayName("HU-28 R15: sin últimos 4 válidos dice que la tarjeta no está informada, sin dígitos")
    void tarjetaNoInformada(String ultimos4) {
        var texto = texto(pagado(ultimos4));

        assertThat(texto).contains("(Tarjeta: no informada)").doesNotContain("terminada en");
        if (ultimos4 != null && !ultimos4.isBlank()) {
            assertThat(texto).doesNotContain(ultimos4.strip());
        }
    }

    @Test
    @DisplayName("HU-28 R16: el texto no trae el id de la pasarela, correo, teléfono ni nombre; el único dato libre es el espacio")
    void sinDatosAjenos() {
        var campos = DatosComprobante.class.getRecordComponents();

        for (var campo : campos) {
            var nombre = campo.getName().toLowerCase();
            assertThat(nombre).doesNotContain("pasarela", "transaccion", "correo", "telefono", "usuario",
                    "arrendador", "nombre");
        }
        assertThat(texto(pagado("4242"))).doesNotContain("sim-").doesNotContain("@").doesNotContain("MP-");
    }

    @Test
    @DisplayName("HU-28 R19: con un pago simulado el comprobante dice que no se cobró dinero real")
    void simulado() {
        var datos = new DatosComprobante(89L, LocalDate.parse("2026-11-04"), "Cancha Los Olivos",
                LocalDate.parse("2026-11-10"), 14, 17, new BigDecimal("241.50"), "PAGADO", null, "4242", true);

        assertThat(texto(datos)).contains("(Pago simulado: no se cobró dinero real.)");
    }

    @Test
    @DisplayName("HU-28 R12: escapa \\ ( ) y descarta caracteres de control; lo no representable sale como ?")
    void escapado() {
        var datos = new DatosComprobante(90L, LocalDate.parse("2026-11-04"), "Salón (A) \\ B\n\u0007中",
                LocalDate.parse("2026-11-10"), 14, 17, new BigDecimal("241.50"), "PAGADO", null, "4242", false);

        assertThat(texto(datos)).contains("(Espacio: Salón \\(A\\) \\\\ B?)");
    }

    @Test
    @DisplayName("HU-28 R11: la salida es determinista, sin fechas de creación")
    void determinista() {
        assertThat(generador.generar(pagado("4242"))).isEqualTo(generador.generar(pagado("4242")));
    }
}
