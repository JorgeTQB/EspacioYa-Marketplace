package pe.edu.ulima.espacioya.pagos.dto;

import java.util.List;

/** Una página del historial, de 10 en 10, sin total (HU-28 R5). */
public record PaginaTransaccionesDTO(int pagina, List<TransaccionDTO> transacciones, boolean hayMas) {
}
