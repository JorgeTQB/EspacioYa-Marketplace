package pe.edu.ulima.espacioya.comun.almacenamiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pe.edu.ulima.espacioya.comun.cifrado.CifradorArchivos;

@ExtendWith(MockitoExtension.class)
class ArchivosPrivadosTest {

    private static final Instant AHORA = Instant.parse("2026-10-28T15:00:00Z");
    private static final byte[] CLARO = "%PDF-1.4 contenido".getBytes(StandardCharsets.UTF_8);

    @Mock
    private AlmacenamientoArchivos almacenamiento;

    private final CifradorArchivos cifrador = new CifradorArchivos(FirmadorUrlTest.LLAVE_CIFRADO);
    private ArchivosPrivados archivos;

    @BeforeEach
    void preparar() {
        var firmador = new FirmadorUrl(FirmadorUrlTest.LLAVE_FIRMA, FirmadorUrlTest.LLAVE_CIFRADO,
                FirmadorUrlTest.LLAVE_HUELLA, Clock.fixed(AHORA, ZoneOffset.UTC));
        archivos = new ArchivosPrivados(almacenamiento, cifrador, firmador);
    }

    @Test
    @DisplayName("HU-05 R27: la clave es <carpeta>/<UUID> aleatorio y distinta en cada guardado")
    void claveAleatoria() {
        var clave = archivos.guardar("identidad", CLARO);
        var otra = archivos.guardar("identidad", CLARO);

        assertThat(clave).matches("^identidad/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
        assertThat(otra).isNotEqualTo(clave);
        assertThat(ArchivosPrivados.claveValida(clave)).isTrue();
    }

    @Test
    @DisplayName("HU-05 R30: lo que recibe el adaptador está cifrado y leer lo descifra")
    void guardaCifradoYLeeDescifrado() {
        var clave = archivos.guardar("identidad", CLARO);
        var guardado = ArgumentCaptor.forClass(byte[].class);
        verify(almacenamiento).guardar(eq(clave), guardado.capture());

        assertThat(guardado.getValue()).isNotEqualTo(CLARO);
        assertThat(new String(guardado.getValue(), StandardCharsets.ISO_8859_1)).doesNotContain("%PDF");
        assertThat(cifrador.descifrar(guardado.getValue())).isEqualTo(CLARO);

        when(almacenamiento.leer(clave)).thenReturn(Optional.of(guardado.getValue()));
        assertThat(archivos.leer(clave)).contains(CLARO);
    }

    @Test
    @DisplayName("HU-05 R27: una clave con .., / inicial, mayúsculas u otra forma no llega al adaptador")
    void claveMalFormadaNoTocaElAdaptador() {
        for (var clave : new String[] {"../identidad/0f8fad5b-d9cb-469f-a165-70867728950e",
                "/identidad/0f8fad5b-d9cb-469f-a165-70867728950e", "identidad/0F8FAD5B-D9CB-469F-A165-70867728950E",
                "identidad/..", "identidad", "", null}) {
            assertThat(archivos.leer(clave)).isEmpty();
            archivos.eliminar(clave);
        }
        verifyNoInteractions(almacenamiento);
    }

    @Test
    @DisplayName("HU-05 R38: urlFirmada delega en el firmador con vencimiento a 5 minutos")
    void urlFirmada() {
        var firmada = archivos.urlFirmada("identidad/0f8fad5b-d9cb-469f-a165-70867728950e", "image/png");

        assertThat(firmada.url()).startsWith("/api/archivos?clave=identidad%2F0f8fad5b");
        assertThat(firmada.expiraEn()).isEqualTo(AHORA.plusSeconds(300));
        verify(almacenamiento, never()).leer(any());
    }
}
