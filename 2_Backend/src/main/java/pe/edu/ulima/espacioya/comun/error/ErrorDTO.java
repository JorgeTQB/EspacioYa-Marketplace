package pe.edu.ulima.espacioya.comun.error;

import java.util.List;

public record ErrorDTO(String mensaje, List<ErrorCampoDTO> errores) {

    public static final String MENSAJE_VALIDACION = "Revisa los datos del formulario.";
    public static final String MENSAJE_INESPERADO = "Ocurrió un problema. Intenta de nuevo más tarde.";
    public static final String MENSAJE_DEMASIADOS_INTENTOS =
            "Hiciste demasiados intentos. Espera un momento e inténtalo de nuevo.";
    public static final String MENSAJE_CREDENCIALES_INVALIDAS = "Correo o contraseña incorrectos.";
    public static final String MENSAJE_SESION_INVALIDA = "Tu sesión no es válida o expiró. Inicia sesión de nuevo.";
    public static final String MENSAJE_ACCESO_DENEGADO = "No tienes permiso para realizar esta acción.";
    public static final String MENSAJE_RECURSO_NO_ENCONTRADO = "No encontramos lo que buscas o el enlace expiró.";
    public static final String MENSAJE_ARCHIVO_DEMASIADO_GRANDE = "El archivo debe pesar como máximo 5 MB.";
    public static final String MENSAJE_SERVICIO_EXTERNO =
            "No pudimos conectar con el servicio de pagos. Intenta de nuevo en unos minutos.";

    public ErrorDTO {
        errores = errores == null ? List.of() : List.copyOf(errores);
    }

    public static ErrorDTO de(String mensaje) {
        return new ErrorDTO(mensaje, List.of());
    }
}
