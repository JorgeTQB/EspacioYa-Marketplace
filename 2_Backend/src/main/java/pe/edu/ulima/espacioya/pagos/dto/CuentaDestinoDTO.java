package pe.edu.ulima.espacioya.pagos.dto;

import java.time.Instant;

/**
 * Cuenta vigente del arrendador en el detalle de una liquidación (HU-19 R36). {@code numeroCompleto} solo si la
 * liquidación es pagable (D4, DT-10); si no, nulo. {@code toString} no lo muestra (RNF-SEG-14).
 */
public record CuentaDestinoDTO(String banco, String titular, String numeroEnmascarado, String numeroCompleto,
                               String estado, Instant retenidaHasta) {

    @Override
    public String toString() {
        return "CuentaDestinoDTO[banco=" + banco + ", titular=" + titular + ", numeroEnmascarado=" + numeroEnmascarado
                + ", estado=" + estado + ", retenidaHasta=" + retenidaHasta + "]";
    }
}
