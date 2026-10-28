package pe.edu.ulima.espacioya.comun.almacenamiento;

import java.util.Arrays;
import java.util.Optional;

public enum TipoArchivo {
    PDF("application/pdf", "pdf"),
    PNG("image/png", "png"),
    JPEG("image/jpeg", "jpg");

    private final String mime;
    private final String extension;

    TipoArchivo(String mime, String extension) {
        this.mime = mime;
        this.extension = extension;
    }

    public String mime() {
        return mime;
    }

    public String extension() {
        return extension;
    }

    public static Optional<TipoArchivo> deMime(String mime) {
        return Arrays.stream(values()).filter(tipo -> tipo.mime.equals(mime)).findFirst();
    }
}
