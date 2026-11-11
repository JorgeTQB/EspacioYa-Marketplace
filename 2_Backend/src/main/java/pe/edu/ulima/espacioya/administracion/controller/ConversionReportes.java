package pe.edu.ulima.espacioya.administracion.controller;

/**
 * El {@code id} de las rutas de reportes llega como texto: uno mal escrito es el mismo 404 genérico que uno inexistente
 * (HU-27 R27). Aplica exactamente la regla de los ids de reclamos (HU-26), a la que delega para no duplicarla.
 */
final class ConversionReportes {

    private ConversionReportes() {
    }

    /** 1 a 19 dígitos, sin desbordar un {@code long} y mayor que 0; si no, 404. */
    static Long idReporte(String texto) {
        return ConversionReclamos.idReclamo(texto);
    }
}
