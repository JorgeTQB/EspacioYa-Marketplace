package pe.edu.ulima.espacioya.cuentas.dto;

import java.util.List;

public record IdentidadDTO(String estadoIdentidad, String motivoRechazo, boolean puedeEnviar,
                           List<DocumentoIdentidadDTO> documentos) {
}
