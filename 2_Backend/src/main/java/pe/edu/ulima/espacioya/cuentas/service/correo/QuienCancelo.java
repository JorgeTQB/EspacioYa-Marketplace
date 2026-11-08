package pe.edu.ulima.espacioya.cuentas.service.correo;

/** Texto de "quién canceló" en los avisos de cancelación (HU-15 D8: {@code USUARIO} o {@code ARRENDADOR}). */
final class QuienCancelo {

    private QuienCancelo() {
    }

    static String texto(String canceladaPor) {
        return switch (canceladaPor) {
            case "USUARIO" -> "quien reservó";
            case "ARRENDADOR" -> "el arrendador";
            default -> canceladaPor;
        };
    }
}
