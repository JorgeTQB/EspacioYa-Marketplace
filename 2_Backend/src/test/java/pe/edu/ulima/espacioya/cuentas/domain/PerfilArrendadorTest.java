package pe.edu.ulima.espacioya.cuentas.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PerfilArrendadorTest {

    @Test
    @DisplayName("HU-02 R2: crear deja el perfil NO_INICIADA, sin motivo de rechazo y con el tipo enviado")
    void crearDejaNoIniciada() {
        var perfil = PerfilArrendador.crear(7L, TipoDocumento.RUC, "20100070970", "a".repeat(64),
                "  Canchas del Sur SAC ", " Av. Siempre Viva 123 ");

        assertThat(perfil.getUsuarioId()).isEqualTo(7L);
        assertThat(perfil.getId()).isEqualTo(7L);
        assertThat(perfil.getTipoDocumento()).isEqualTo(TipoDocumento.RUC);
        assertThat(perfil.getNumeroDocumento()).isEqualTo("20100070970");
        assertThat(perfil.getHashDocumento()).isEqualTo("a".repeat(64));
        assertThat(perfil.getRazonSocial()).isEqualTo("Canchas del Sur SAC");
        assertThat(perfil.getDireccionFiscal()).isEqualTo("Av. Siempre Viva 123");
        assertThat(perfil.getEstadoIdentidad()).isEqualTo(EstadoIdentidad.NO_INICIADA);
        assertThat(perfil.getMotivoRechazo()).isNull();
    }

    @Test
    @DisplayName("HU-02 R2: un perfil recién creado es nuevo para que save haga persist")
    void perfilNuevo() {
        var perfil = PerfilArrendador.crear(7L, TipoDocumento.DNI, "12345678", "b".repeat(64), "Ana", "Lima");

        assertThat(perfil.isNew()).isTrue();
        perfil.marcarGuardado();
        assertThat(perfil.isNew()).isFalse();
    }

    private static PerfilArrendador perfilEn(EstadoIdentidad estado, String motivo) {
        var perfil = PerfilArrendador.crear(7L, TipoDocumento.RUC, "20100070970", "a".repeat(64),
                "Canchas SAC", "Lima");
        ReflectionTestUtils.setField(perfil, "estadoIdentidad", estado);
        ReflectionTestUtils.setField(perfil, "motivoRechazo", motivo);
        return perfil;
    }

    @Test
    @DisplayName("HU-05 R24: puede enviar documentos solo en NO_INICIADA o RECHAZADA")
    void puedeEnviarDocumentosPorEstado() {
        assertThat(perfilEn(EstadoIdentidad.NO_INICIADA, null).puedeEnviarDocumentos()).isTrue();
        assertThat(perfilEn(EstadoIdentidad.RECHAZADA, "Ilegible").puedeEnviarDocumentos()).isTrue();
        assertThat(perfilEn(EstadoIdentidad.EN_REVISION, null).puedeEnviarDocumentos()).isFalse();
        assertThat(perfilEn(EstadoIdentidad.VERIFICADA, null).puedeEnviarDocumentos()).isFalse();
    }

    @Test
    @DisplayName("HU-05 R2: enviar documentos desde NO_INICIADA deja el perfil EN_REVISION")
    void enviarDesdeNoIniciada() {
        var perfil = perfilEn(EstadoIdentidad.NO_INICIADA, null);

        perfil.enviarDocumentos();

        assertThat(perfil.getEstadoIdentidad()).isEqualTo(EstadoIdentidad.EN_REVISION);
        assertThat(perfil.getMotivoRechazo()).isNull();
    }

    @Test
    @DisplayName("HU-05 R4: enviar documentos desde RECHAZADA deja EN_REVISION y el motivo de rechazo nulo")
    void enviarDesdeRechazadaLimpiaMotivo() {
        var perfil = perfilEn(EstadoIdentidad.RECHAZADA, "El documento está borroso.");

        perfil.enviarDocumentos();

        assertThat(perfil.getEstadoIdentidad()).isEqualTo(EstadoIdentidad.EN_REVISION);
        assertThat(perfil.getMotivoRechazo()).isNull();
    }

    @Test
    @DisplayName("HU-05 R16: enviar documentos desde EN_REVISION o VERIFICADA lanza y no cambia el estado")
    void enviarDesdeEstadoNoPermitidoLanza() {
        for (var estado : new EstadoIdentidad[] {EstadoIdentidad.EN_REVISION, EstadoIdentidad.VERIFICADA}) {
            var perfil = perfilEn(estado, null);

            assertThatThrownBy(perfil::enviarDocumentos).isInstanceOf(IllegalStateException.class);
            assertThat(perfil.getEstadoIdentidad()).isEqualTo(estado);
        }
    }

    @Test
    @DisplayName("HU-05 R28: el aviso de EN_REVISION dice que los documentos están en revisión")
    void avisoEnRevision() {
        assertThat(perfilEn(EstadoIdentidad.EN_REVISION, null).avisoIdentidad())
                .isEqualTo("Estamos revisando tus documentos de identidad. Te avisaremos cuando terminemos.");
        assertThat(perfilEn(EstadoIdentidad.NO_INICIADA, null).avisoIdentidad())
                .isEqualTo("Para publicar tus espacios, primero debes verificar tu identidad.");
        assertThat(perfilEn(EstadoIdentidad.VERIFICADA, null).avisoIdentidad()).isNull();
    }

    @Test
    @DisplayName("HU-05 R29: el aviso de RECHAZADA incluye el motivo de rechazo guardado")
    void avisoRechazadaConMotivo() {
        assertThat(perfilEn(EstadoIdentidad.RECHAZADA, "El documento está borroso.").avisoIdentidad())
                .isEqualTo("Tu identidad fue rechazada. Vuelve a subir tus documentos. Motivo: "
                        + "El documento está borroso.");
    }
}
