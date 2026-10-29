package pe.edu.ulima.espacioya.cuentas.dto;

import java.time.Instant;
import java.util.List;

/**
 * Datos y documentos que el admin coteja para decidir sobre una identidad (HU-06, R23, R24). Lleva el número de
 * documento descifrado: solo se sirve con {@code Cache-Control: no-store} y nunca va a la auditoría ni al log.
 */
public record ExpedienteIdentidadDTO(Long arrendadorId, String nombre, String correo, String telefono,
                                     String tipoDocumento, String numeroDocumento, String razonSocial,
                                     String direccionFiscal, String estadoIdentidad, Instant enviadoEn,
                                     List<DocumentoIdentidadDTO> documentos) {

    public ExpedienteIdentidadDTO {
        documentos = documentos == null ? List.of() : List.copyOf(documentos);
    }

    @Override
    public String toString() {
        return "ExpedienteIdentidadDTO[arrendadorId=" + arrendadorId + ", estadoIdentidad=" + estadoIdentidad
                + ", documentos=" + documentos.size() + "]";
    }
}
