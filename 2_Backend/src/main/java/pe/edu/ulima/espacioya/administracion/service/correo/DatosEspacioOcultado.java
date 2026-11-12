package pe.edu.ulima.espacioya.administracion.service.correo;

/**
 * Evento "el admin ocultó tu espacio" (HU-27 R47). {@code motivo} es el de la decisión, nunca el del reporte;
 * {@code reservasCanceladas} solo cuenta con {@code porFraude}.
 */
public record DatosEspacioOcultado(String espacio, String motivo, boolean porFraude, int reservasCanceladas) {
}
