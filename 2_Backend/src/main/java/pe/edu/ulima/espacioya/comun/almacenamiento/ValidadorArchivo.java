package pe.edu.ulima.espacioya.comun.almacenamiento;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.springframework.stereotype.Component;

/**
 * Valida un archivo subido por su contenido real (RNF-SEG-08): tamaño, firma de tipo (magic bytes) y,
 * para imágenes, que se decodifiquen. Las imágenes se vuelven a codificar para descartar metadatos
 * (EXIF, bloques de texto) y cualquier contenido extra; los PDF se guardan sin cambios.
 */
@Component
public class ValidadorArchivo {

    public static final long TAMANO_MAXIMO = 5L * 1024 * 1024;
    // Tope antes de decodificar: evita que un PNG pequeño de dimensiones enormes agote la memoria.
    public static final long PIXELES_MAXIMOS = 40_000_000L;

    private static final byte[] FIRMA_PDF = {0x25, 0x50, 0x44, 0x46, 0x2D};
    private static final byte[] FIRMA_PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] FIRMA_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final float CALIDAD_JPEG = 0.9f;

    public sealed interface ResultadoValidacion {

        record Aceptado(ArchivoValidado archivo) implements ResultadoValidacion {
        }

        record Rechazado(MotivoRechazoArchivo motivo) implements ResultadoValidacion {
        }
    }

    public ResultadoValidacion validar(ArchivoRecibido archivo) {
        if (archivo == null || archivo.contenido() == null) {
            var tamano = archivo == null ? 0 : archivo.tamano();
            return rechazo(tamano > 0 ? MotivoRechazoArchivo.TAMANO_EXCEDIDO : MotivoRechazoArchivo.VACIO);
        }
        var contenido = archivo.contenido();
        if (archivo.tamano() == 0 || contenido.length == 0) {
            return rechazo(MotivoRechazoArchivo.VACIO);
        }
        if (archivo.tamano() > TAMANO_MAXIMO || contenido.length > TAMANO_MAXIMO) {
            return rechazo(MotivoRechazoArchivo.TAMANO_EXCEDIDO);
        }
        var tipo = tipoReal(contenido);
        if (tipo.isEmpty()) {
            return rechazo(MotivoRechazoArchivo.TIPO_NO_PERMITIDO);
        }
        if (tipo.get() == TipoArchivo.PDF) {
            return aceptado(contenido, TipoArchivo.PDF, archivo.tamano());
        }
        return recodificar(contenido, tipo.get())
                .map(limpio -> aceptado(limpio, tipo.get(), archivo.tamano()))
                .orElseGet(() -> rechazo(MotivoRechazoArchivo.IMAGEN_ILEGIBLE));
    }

    static Optional<TipoArchivo> tipoReal(byte[] contenido) {
        if (empiezaCon(contenido, FIRMA_PDF)) {
            return Optional.of(TipoArchivo.PDF);
        }
        if (empiezaCon(contenido, FIRMA_PNG)) {
            return Optional.of(TipoArchivo.PNG);
        }
        if (empiezaCon(contenido, FIRMA_JPEG)) {
            return Optional.of(TipoArchivo.JPEG);
        }
        return Optional.empty();
    }

    private static boolean empiezaCon(byte[] contenido, byte[] firma) {
        return contenido.length >= firma.length && Arrays.equals(contenido, 0, firma.length, firma, 0, firma.length);
    }

    private static Optional<byte[]> recodificar(byte[] contenido, TipoArchivo tipo) {
        var formato = tipo == TipoArchivo.PNG ? "png" : "jpeg";
        try {
            var imagen = leer(contenido, formato);
            return imagen.isEmpty() ? Optional.empty() : escribir(imagen.get(), formato);
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private static Optional<BufferedImage> leer(byte[] contenido, String formato) throws IOException {
        var lectores = ImageIO.getImageReadersByFormatName(formato);
        if (!lectores.hasNext()) {
            return Optional.empty();
        }
        ImageReader lector = lectores.next();
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(contenido))) {
            lector.setInput(entrada, true, true);
            long pixeles = (long) lector.getWidth(0) * lector.getHeight(0);
            if (pixeles <= 0 || pixeles > PIXELES_MAXIMOS) {
                return Optional.empty();
            }
            return Optional.ofNullable(lector.read(0));
        } finally {
            lector.dispose();
        }
    }

    private static Optional<byte[]> escribir(BufferedImage imagen, String formato) throws IOException {
        var escritores = ImageIO.getImageWritersByFormatName(formato);
        if (!escritores.hasNext()) {
            return Optional.empty();
        }
        ImageWriter escritor = escritores.next();
        var salida = new ByteArrayOutputStream();
        try (ImageOutputStream destino = ImageIO.createImageOutputStream(salida)) {
            escritor.setOutput(destino);
            var parametros = escritor.getDefaultWriteParam();
            if ("jpeg".equals(formato)) {
                parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                parametros.setCompressionQuality(CALIDAD_JPEG);
            }
            escritor.write(null, new IIOImage(imagen, null, null), parametros);
        } finally {
            escritor.dispose();
        }
        return Optional.of(salida.toByteArray());
    }

    private static ResultadoValidacion aceptado(byte[] contenido, TipoArchivo tipo, long tamanoRecibido) {
        return new ResultadoValidacion.Aceptado(new ArchivoValidado(contenido, tipo, tamanoRecibido));
    }

    private static ResultadoValidacion rechazo(MotivoRechazoArchivo motivo) {
        return new ResultadoValidacion.Rechazado(motivo);
    }
}
