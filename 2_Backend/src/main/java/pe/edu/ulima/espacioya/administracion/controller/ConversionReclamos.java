package pe.edu.ulima.espacioya.administracion.controller;

import java.util.regex.Pattern;

import pe.edu.ulima.espacioya.comun.error.RecursoNoEncontradoException;

/** El {@code id} de las rutas de reclamos llega como texto: uno mal escrito es el mismo 404 genérico (R25, R40). */
final class ConversionReclamos {

    private static final Pattern ID_BIEN_ESCRITO = Pattern.compile("[0-9]{1,19}");

    private ConversionReclamos() {
    }

    /** 1 a 19 dígitos, sin desbordar un {@code long} y mayor que 0; si no, 404. */
    static Long idReclamo(String texto) {
        if (texto == null || !ID_BIEN_ESCRITO.matcher(texto).matches()) {
            throw new RecursoNoEncontradoException();
        }
        try {
            var id = Long.parseLong(texto);
            if (id <= 0) {
                throw new RecursoNoEncontradoException();
            }
            return id;
        } catch (NumberFormatException e) {
            throw new RecursoNoEncontradoException();
        }
    }
}
