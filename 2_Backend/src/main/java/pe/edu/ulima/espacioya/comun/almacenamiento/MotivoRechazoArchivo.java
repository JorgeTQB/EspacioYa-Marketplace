package pe.edu.ulima.espacioya.comun.almacenamiento;

import pe.edu.ulima.espacioya.comun.error.ErrorDTO;

public enum MotivoRechazoArchivo {
    VACIO("Sube un archivo."),
    TAMANO_EXCEDIDO(ErrorDTO.MENSAJE_ARCHIVO_DEMASIADO_GRANDE),
    TIPO_NO_PERMITIDO("El archivo debe ser PDF, JPG o PNG."),
    IMAGEN_ILEGIBLE("No pudimos leer la imagen. Revisa que no esté dañada y vuelve a subirla.");

    private final String mensaje;

    MotivoRechazoArchivo(String mensaje) {
        this.mensaje = mensaje;
    }

    public String mensaje() {
        return mensaje;
    }
}
