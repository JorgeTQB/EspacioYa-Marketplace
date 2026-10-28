package pe.edu.ulima.espacioya.cuentas.domain;

import java.util.Optional;
import java.util.Set;

public final class ValidadorDocumento {

    public static final String MENSAJE_NUMERO_FALTA = "Ingresa tu número de documento.";
    public static final String MENSAJE_DNI = "El DNI debe tener 8 dígitos.";
    public static final String MENSAJE_RUC_LONGITUD = "El RUC debe tener 11 dígitos.";
    public static final String MENSAJE_RUC_PREFIJO = "El RUC debe empezar con 10, 15, 17 o 20.";
    public static final String MENSAJE_RUC_VERIFICADOR = "El RUC no es válido. Revisa que lo hayas escrito bien.";

    private static final String DNI = "[0-9]{8}";
    private static final String RUC = "[0-9]{11}";
    private static final Set<String> PREFIJOS_RUC = Set.of("10", "15", "17", "20");
    private static final int[] PESOS_RUC = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};
    private static final int MODULO = 11;

    private ValidadorDocumento() {
    }

    public static String normalizar(String numero) {
        return numero == null ? null : numero.trim();
    }

    public static Optional<String> error(TipoDocumento tipo, String numeroNormalizado) {
        if (numeroNormalizado == null || numeroNormalizado.isEmpty()) {
            return Optional.of(MENSAJE_NUMERO_FALTA);
        }
        if (tipo == TipoDocumento.DNI) {
            return numeroNormalizado.matches(DNI) ? Optional.empty() : Optional.of(MENSAJE_DNI);
        }
        if (!numeroNormalizado.matches(RUC)) {
            return Optional.of(MENSAJE_RUC_LONGITUD);
        }
        if (!PREFIJOS_RUC.contains(numeroNormalizado.substring(0, 2))) {
            return Optional.of(MENSAJE_RUC_PREFIJO);
        }
        if (digitoVerificador(numeroNormalizado) != numeroNormalizado.charAt(PESOS_RUC.length) - '0') {
            return Optional.of(MENSAJE_RUC_VERIFICADOR);
        }
        return Optional.empty();
    }

    // Módulo 11 de SUNAT: 11 − (suma ponderada mod 11); un resultado de 10 da 0 y uno de 11 da 1.
    private static int digitoVerificador(String ruc) {
        var suma = 0;
        for (int i = 0; i < PESOS_RUC.length; i++) {
            suma += (ruc.charAt(i) - '0') * PESOS_RUC[i];
        }
        return (MODULO - suma % MODULO) % 10;
    }
}
