package pe.edu.ulima.espacioya.cuentas.service.correo;

/**
 * Texto de "quién canceló" en los avisos de cancelación (HU-15 D8: {@code USUARIO} o {@code ARRENDADOR}; HU-27 DT-22:
 * {@code ADMIN} al ocultar un espacio por fraude).
 */
final class QuienCancelo {

    private QuienCancelo() {
    }

    static String texto(String canceladaPor) {
        return switch (canceladaPor) {
            case "USUARIO" -> "quien reservó";
            case "ARRENDADOR" -> "el arrendador";
            case "ADMIN" -> "EspacioYa";
            default -> canceladaPor;
        };
    }
}
