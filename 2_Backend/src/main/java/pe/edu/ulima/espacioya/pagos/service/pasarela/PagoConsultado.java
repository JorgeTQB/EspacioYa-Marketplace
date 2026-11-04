package pe.edu.ulima.espacioya.pagos.service.pasarela;

import java.math.BigDecimal;

/** Lo que dice la pasarela de un pago: es la única fuente de estado, monto y referencia (HU-13 R26). */
public record PagoConsultado(String idTransaccion, ResultadoPasarela resultado, BigDecimal monto, String moneda,
                             String ultimos4, String referencia) {
}
