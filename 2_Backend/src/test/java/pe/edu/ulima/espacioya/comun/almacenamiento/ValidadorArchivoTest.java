package pe.edu.ulima.espacioya.comun.almacenamiento;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.edu.ulima.espacioya.comun.almacenamiento.ValidadorArchivo.ResultadoValidacion;

class ValidadorArchivoTest {

    private static final int LIMITE = 5_242_880;

    private final ValidadorArchivo validador = new ValidadorArchivo();

    private ResultadoValidacion validar(byte[] contenido) {
        return validador.validar(new ArchivoRecibido(contenido, contenido.length));
    }

    private static ArchivoValidado aceptado(ResultadoValidacion resultado) {
        assertThat(resultado).isInstanceOf(ResultadoValidacion.Aceptado.class);
        return ((ResultadoValidacion.Aceptado) resultado).archivo();
    }

    private static MotivoRechazoArchivo motivo(ResultadoValidacion resultado) {
        assertThat(resultado).isInstanceOf(ResultadoValidacion.Rechazado.class);
        return ((ResultadoValidacion.Rechazado) resultado).motivo();
    }

    @Test
    @DisplayName("HU-05 R8: un archivo ausente o de 0 bytes se rechaza como VACIO")
    void vacioSeRechaza() {
        assertThat(motivo(validador.validar(null))).isEqualTo(MotivoRechazoArchivo.VACIO);
        assertThat(motivo(validador.validar(new ArchivoRecibido(null, 0)))).isEqualTo(MotivoRechazoArchivo.VACIO);
        assertThat(motivo(validar(new byte[0]))).isEqualTo(MotivoRechazoArchivo.VACIO);
    }

    @Test
    @DisplayName("HU-05 R6: un PDF de exactamente 5 242 880 bytes se acepta")
    void limiteExactoSeAcepta() {
        var archivo = aceptado(validar(ArchivosDePrueba.pdf(LIMITE)));

        assertThat(archivo.tipo()).isEqualTo(TipoArchivo.PDF);
        assertThat(archivo.tamanoRecibido()).isEqualTo(LIMITE);
    }

    @Test
    @DisplayName("HU-05 R9: un archivo de 5 242 881 bytes se rechaza por tamaño")
    void unByteDeMasSeRechaza() {
        assertThat(motivo(validar(ArchivosDePrueba.pdf(LIMITE + 1)))).isEqualTo(MotivoRechazoArchivo.TAMANO_EXCEDIDO);
    }

    @Test
    @DisplayName("HU-05 R9: un archivo no leído por exceder el límite (contenido nulo, tamaño > 0) se rechaza")
    void noLeidoPorTamanoSeRechaza() {
        assertThat(motivo(validador.validar(new ArchivoRecibido(null, 7_000_000))))
                .isEqualTo(MotivoRechazoArchivo.TAMANO_EXCEDIDO);
    }

    @Test
    @DisplayName("HU-05 R10: texto plano y ZIP se rechazan como TIPO_NO_PERMITIDO aunque se declaren PDF")
    void tipoNoPermitido() {
        assertThat(motivo(validar(ArchivosDePrueba.texto()))).isEqualTo(MotivoRechazoArchivo.TIPO_NO_PERMITIDO);
        assertThat(motivo(validar(ArchivosDePrueba.zip()))).isEqualTo(MotivoRechazoArchivo.TIPO_NO_PERMITIDO);
        assertThat(MotivoRechazoArchivo.TIPO_NO_PERMITIDO.mensaje()).isEqualTo("El archivo debe ser PDF, JPG o PNG.");
    }

    @Test
    @DisplayName("HU-05 R11: el tipo sale de la firma real del contenido: PDF, PNG y JPEG")
    void tipoPorFirmaReal() {
        assertThat(aceptado(validar(ArchivosDePrueba.pdf(100))).tipo().mime()).isEqualTo("application/pdf");
        assertThat(aceptado(validar(ArchivosDePrueba.png(20, 10))).tipo().mime()).isEqualTo("image/png");
        assertThat(aceptado(validar(ArchivosDePrueba.jpeg(20, 10))).tipo().mime()).isEqualTo("image/jpeg");
    }

    @Test
    @DisplayName("HU-05 R12: una cabecera PNG seguida de basura se rechaza como IMAGEN_ILEGIBLE")
    void pngIlegible() {
        assertThat(motivo(validar(ArchivosDePrueba.pngIlegible()))).isEqualTo(MotivoRechazoArchivo.IMAGEN_ILEGIBLE);
    }

    @Test
    @DisplayName("HU-05 R12: un PNG que declara 8000 × 6000 píxeles se rechaza sin decodificarlo")
    void pngDemasiadosPixeles() {
        assertThat(motivo(validar(ArchivosDePrueba.pngConDimensiones(8000, 6000))))
                .isEqualTo(MotivoRechazoArchivo.IMAGEN_ILEGIBLE);
    }

    @Test
    @DisplayName("HU-05 R32: un JPEG con segmento APP1 Exif se guarda vuelto a codificar y sin el Exif")
    void jpegSinExif() throws IOException {
        var original = ArchivosDePrueba.jpegConExif();
        assertThat(ArchivosDePrueba.contiene(original, "Exif")).isTrue();

        var archivo = aceptado(validar(original));

        assertThat(archivo.tipo()).isEqualTo(TipoArchivo.JPEG);
        assertThat(archivo.tamanoRecibido()).isEqualTo(original.length);
        assertThat(ArchivosDePrueba.contiene(archivo.contenido(), "Exif")).isFalse();
        assertThat(ArchivosDePrueba.contiene(archivo.contenido(), ArchivosDePrueba.SECRETO_EXIF)).isFalse();
        var imagen = ImageIO.read(new ByteArrayInputStream(archivo.contenido()));
        assertThat(imagen.getWidth()).isEqualTo(40);
        assertThat(imagen.getHeight()).isEqualTo(30);
    }

    @Test
    @DisplayName("HU-05 R32: un PNG con bloque tEXt se guarda vuelto a codificar y sin bloques de texto")
    void pngSinTexto() throws IOException {
        var original = ArchivosDePrueba.pngConTexto();
        assertThat(ArchivosDePrueba.contiene(original, "tEXt")).isTrue();

        var archivo = aceptado(validar(original));

        assertThat(archivo.tipo()).isEqualTo(TipoArchivo.PNG);
        for (var marca : new String[] {"tEXt", "iTXt", "zTXt", ArchivosDePrueba.SECRETO_PNG}) {
            assertThat(ArchivosDePrueba.contiene(archivo.contenido(), marca)).isFalse();
        }
        var imagen = ImageIO.read(new ByteArrayInputStream(archivo.contenido()));
        assertThat(imagen.getWidth()).isEqualTo(40);
        assertThat(imagen.getHeight()).isEqualTo(30);
    }

    @Test
    @DisplayName("HU-05 R33: un PDF aceptado se guarda idéntico byte a byte")
    void pdfSinCambios() {
        var original = ArchivosDePrueba.pdf(2048);

        assertThat(aceptado(validar(original)).contenido()).isEqualTo(original);
    }
}
