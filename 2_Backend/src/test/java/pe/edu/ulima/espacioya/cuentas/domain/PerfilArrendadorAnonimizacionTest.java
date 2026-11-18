package pe.edu.ulima.espacioya.cuentas.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PerfilArrendadorAnonimizacionTest {

    private static PerfilArrendador rechazado() {
        var perfil = PerfilArrendador.crear(41L, TipoDocumento.RUC, "20100070970", "a".repeat(64), "Ana Ríos EIRL",
                "Av. Arequipa 123");
        ReflectionTestUtils.setField(perfil, "estadoIdentidad", EstadoIdentidad.RECHAZADA);
        ReflectionTestUtils.setField(perfil, "motivoRechazo", "Documento ilegible de Ana Ríos");
        return perfil;
    }

    @Test
    @DisplayName("HU-22 R25: anonimizar pone el texto fijo en documento, razón social y dirección, la huella "
            + "eliminada-<id> y quita el motivo de rechazo")
    void anonimizaCampos() {
        var perfil = rechazado();

        perfil.anonimizar();

        assertThat(perfil.getNumeroDocumento()).isEqualTo(Usuario.TEXTO_ANONIMIZADO);
        assertThat(perfil.getRazonSocial()).isEqualTo(Usuario.TEXTO_ANONIMIZADO);
        assertThat(perfil.getDireccionFiscal()).isEqualTo(Usuario.TEXTO_ANONIMIZADO);
        assertThat(perfil.getHashDocumento()).isEqualTo("eliminada-41")
                .isEqualTo(PerfilArrendador.marcadorHuella(41L));
        assertThat(perfil.getMotivoRechazo()).isNull();
    }

    @Test
    @DisplayName("HU-22 R25: anonimizar conserva el tipo de documento y el estado de identidad")
    void conservaTipoYEstado() {
        var perfil = rechazado();

        perfil.anonimizar();

        assertThat(perfil.getTipoDocumento()).isEqualTo(TipoDocumento.RUC);
        assertThat(perfil.getEstadoIdentidad()).isEqualTo(EstadoIdentidad.RECHAZADA);
        assertThat(perfil.getHashDocumento()).hasSizeLessThanOrEqualTo(64).doesNotMatch("[0-9a-f]{64}");
    }
}
