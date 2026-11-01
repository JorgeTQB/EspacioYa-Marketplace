package pe.edu.ulima.espacioya.pagos.domain;

/** Por qué no se puede iniciar otro intento de un reembolso (HU-16 R21, R22). */
public enum MotivoNoReintentable {
    YA_REEMBOLSADO,
    EN_PROCESO
}
