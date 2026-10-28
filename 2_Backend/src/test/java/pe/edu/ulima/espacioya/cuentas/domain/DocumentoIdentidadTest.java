package pe.edu.ulima.espacioya.cuentas.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DocumentoIdentidadTest {

    @Test
    @DisplayName("HU-05 R3: registrar guarda arrendador, tipo, clave, tipo MIME real, tamaño y fecha de subida")
    void registrarGuardaLosDatos() {
        var ahora = Instant.parse("2026-10-28T15:00:00Z");

        var documento = DocumentoIdentidad.registrar(7L, TipoDocumentoIdentidad.PROPIEDAD,
                "identidad/0f8fad5b-d9cb-469f-a165-70867728950e", "image/png", 12345, ahora);

        assertThat(documento.getId()).isNull();
        assertThat(documento.getArrendadorId()).isEqualTo(7L);
        assertThat(documento.getTipo()).isEqualTo(TipoDocumentoIdentidad.PROPIEDAD);
        assertThat(documento.getRutaArchivo()).isEqualTo("identidad/0f8fad5b-d9cb-469f-a165-70867728950e");
        assertThat(documento.getTipoMime()).isEqualTo("image/png");
        assertThat(documento.getTamano()).isEqualTo(12345);
        assertThat(documento.getSubidoEn()).isEqualTo(ahora);
    }
}
