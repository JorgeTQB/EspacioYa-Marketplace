package pe.edu.ulima.espacioya.comun.almacenamiento;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;

/**
 * Archivos generados en memoria para las pruebas de HU-05: ninguno vive en el repositorio.
 */
public final class ArchivosDePrueba {

    public static final String SECRETO_EXIF = "SECRETO-GPS-LIMA";
    public static final String SECRETO_PNG = "SECRETO-TEXTO-PNG";

    private ArchivosDePrueba() {
    }

    public static byte[] pdf(int tamano) {
        var cabecera = "%PDF-1.4\n% documento de prueba\n".getBytes(StandardCharsets.US_ASCII);
        var contenido = new byte[tamano];
        Arrays.fill(contenido, (byte) 'x');
        System.arraycopy(cabecera, 0, contenido, 0, Math.min(cabecera.length, tamano));
        return contenido;
    }

    public static byte[] png(int ancho, int alto) {
        return imagen(ancho, alto, "png");
    }

    public static byte[] jpeg(int ancho, int alto) {
        return imagen(ancho, alto, "jpeg");
    }

    private static byte[] imagen(int ancho, int alto, String formato) {
        var imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        var grafico = imagen.createGraphics();
        grafico.setColor(Color.ORANGE);
        grafico.fillRect(0, 0, ancho, alto);
        grafico.setColor(Color.BLUE);
        grafico.fillRect(0, 0, ancho / 2, alto / 2);
        grafico.dispose();
        var salida = new ByteArrayOutputStream();
        try {
            ImageIO.write(imagen, formato, salida);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return salida.toByteArray();
    }

    /** JPEG con un segmento APP1 "Exif" insertado a mano justo después de SOI. */
    public static byte[] jpegConExif() {
        var jpeg = jpeg(40, 30);
        var tiff = new byte[] {'M', 'M', 0, 42, 0, 0, 0, 8, 0, 0, 0, 0, 0, 0};
        var datos = ByteBuffer.allocate(6 + tiff.length + SECRETO_EXIF.length())
                .put("Exif\0\0".getBytes(StandardCharsets.US_ASCII)).put(tiff)
                .put(SECRETO_EXIF.getBytes(StandardCharsets.US_ASCII)).array();
        var segmento = ByteBuffer.allocate(4 + datos.length)
                .put((byte) 0xFF).put((byte) 0xE1).putShort((short) (datos.length + 2)).put(datos).array();
        return ByteBuffer.allocate(jpeg.length + segmento.length)
                .put(jpeg, 0, 2).put(segmento).put(jpeg, 2, jpeg.length - 2).array();
    }

    /** PNG con un bloque tEXt insertado a mano justo después de IHDR. */
    public static byte[] pngConTexto() {
        var png = png(40, 30);
        var bloque = bloquePng("tEXt", ("Comment\0" + SECRETO_PNG).getBytes(StandardCharsets.ISO_8859_1));
        var finIhdr = 8 + 25;
        return ByteBuffer.allocate(png.length + bloque.length)
                .put(png, 0, finIhdr).put(bloque).put(png, finIhdr, png.length - finIhdr).array();
    }

    /** Cabecera PNG válida que declara dimensiones enormes, seguida de datos que no forman una imagen. */
    public static byte[] pngConDimensiones(int ancho, int alto) {
        var ihdr = ByteBuffer.allocate(13).putInt(ancho).putInt(alto)
                .put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0).array();
        var bloqueIhdr = bloquePng("IHDR", ihdr);
        var idat = bloquePng("IDAT", new byte[] {1, 2, 3, 4});
        return ByteBuffer.allocate(8 + bloqueIhdr.length + idat.length)
                .put(firmaPng()).put(bloqueIhdr).put(idat).array();
    }

    /** Firma PNG seguida de bytes que no son una imagen. */
    public static byte[] pngIlegible() {
        var basura = "esto no es una imagen png".getBytes(StandardCharsets.US_ASCII);
        return ByteBuffer.allocate(8 + basura.length).put(firmaPng()).put(basura).array();
    }

    public static byte[] zip() {
        return new byte[] {'P', 'K', 3, 4, 20, 0, 0, 0, 8, 0, 'x', 'y', 'z'};
    }

    public static byte[] texto() {
        return "Hola, esto es un archivo de texto plano.".getBytes(StandardCharsets.UTF_8);
    }

    public static boolean contiene(byte[] contenido, String texto) {
        return new String(contenido, StandardCharsets.ISO_8859_1).contains(texto);
    }

    private static byte[] firmaPng() {
        return new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    }

    private static byte[] bloquePng(String tipo, byte[] datos) {
        var tipoBytes = tipo.getBytes(StandardCharsets.US_ASCII);
        var crc = new CRC32();
        crc.update(tipoBytes);
        crc.update(datos);
        return ByteBuffer.allocate(12 + datos.length).putInt(datos.length).put(tipoBytes).put(datos)
                .putInt((int) crc.getValue()).array();
    }
}
