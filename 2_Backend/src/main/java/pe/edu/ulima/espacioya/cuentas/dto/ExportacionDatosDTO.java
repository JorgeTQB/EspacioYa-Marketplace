package pe.edu.ulima.espacioya.cuentas.dto;

import java.time.Instant;
import java.util.Map;

/** Archivo de la exportación (HU-22 R1, R2): {@code secciones} conserva el orden de los participantes. */
public record ExportacionDatosDTO(Instant generadaEn, PerfilExportadoDTO perfil, Map<String, Object> secciones) {
}
