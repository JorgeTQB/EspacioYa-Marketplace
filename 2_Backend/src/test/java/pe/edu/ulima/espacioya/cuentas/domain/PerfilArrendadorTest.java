package pe.edu.ulima.espacioya.cuentas.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
