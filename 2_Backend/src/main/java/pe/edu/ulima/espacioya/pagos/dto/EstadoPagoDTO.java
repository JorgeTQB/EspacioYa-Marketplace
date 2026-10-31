package pe.edu.ulima.espacioya.pagos.dto;

public record EstadoPagoDTO(String ultimoResultado, boolean intentosAgotados, boolean pagoDisponible) {
}
