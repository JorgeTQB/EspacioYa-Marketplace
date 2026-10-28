package pe.edu.ulima.espacioya.cuentas.dto;

import java.time.Instant;

public record DocumentoIdentidadDTO(Long id, String tipo, String tipoMime, long tamano, Instant subidoEn,
                                    String url, Instant urlExpiraEn) {
}
