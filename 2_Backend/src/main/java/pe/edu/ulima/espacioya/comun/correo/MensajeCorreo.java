package pe.edu.ulima.espacioya.comun.correo;

/**
 * Lo que recibe el Target {@link EnvioCorreo} (HU-25 D8): un mensaje de texto plano con un único destinatario, sin
 * copia ni copia oculta (R30). Sin tipos del proveedor.
 */
public record MensajeCorreo(String para, String asunto, String cuerpo) {

    public MensajeCorreo {
        obligatorio(para, "para");
        obligatorio(asunto, "asunto");
        obligatorio(cuerpo, "cuerpo");
    }

    private static void obligatorio(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("El mensaje de correo necesita " + campo);
        }
    }
}
