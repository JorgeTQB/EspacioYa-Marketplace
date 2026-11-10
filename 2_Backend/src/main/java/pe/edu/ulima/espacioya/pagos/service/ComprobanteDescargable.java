package pe.edu.ulima.espacioya.pagos.service;

/** El PDF de un comprobante y el nombre con que se descarga (HU-28 R11). */
public record ComprobanteDescargable(String nombreArchivo, byte[] contenido) {
}
