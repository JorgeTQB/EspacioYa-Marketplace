package pe.edu.ulima.espacioya.cuentas.domain;

/** Por qué se rechazó una confirmación de correo (HU-20 R50). Solo va a la auditoría; el cliente ve un 400 genérico. */
public enum MotivoRechazoCambioCorreo {
    TOKEN_DESCONOCIDO,
    TOKEN_USADO,
    TOKEN_EXPIRADO,
    CUENTA_NO_HABILITADA,
    CORREO_EN_USO
}
