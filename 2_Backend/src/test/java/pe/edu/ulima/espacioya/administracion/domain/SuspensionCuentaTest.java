package pe.edu.ulima.espacioya.administracion.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SuspensionCuentaTest {

    private static final Instant AHORA = Instant.parse("2026-10-29T15:00:00Z");

    @Test
    @DisplayName("HU-09 R17: suspender registra la cuenta, el admin, la acción SUSPENSION, el motivo recortado y la "
            + "fecha")
    void suspenderConMotivo() {
        var registro = SuspensionCuenta.suspender(7L, 1L, "  Publicó datos falsos.  ", AHORA);

        assertThat(registro.getUsuarioId()).isEqualTo(7L);
        assertThat(registro.getAdminId()).isEqualTo(1L);
        assertThat(registro.getAccion()).isEqualTo(AccionSuspension.SUSPENSION);
        assertThat(registro.getMotivo()).isEqualTo("Publicó datos falsos.");
        assertThat(registro.getFecha()).isEqualTo(AHORA);
        assertThat(registro.getId()).isNull();
    }

    @Test
    @DisplayName("HU-09 R31: reactivar registra la cuenta, el admin, la acción REACTIVACION, sin motivo y la fecha")
    void reactivarSinMotivo() {
        var registro = SuspensionCuenta.reactivar(7L, 1L, AHORA);

        assertThat(registro.getUsuarioId()).isEqualTo(7L);
        assertThat(registro.getAdminId()).isEqualTo(1L);
        assertThat(registro.getAccion()).isEqualTo(AccionSuspension.REACTIVACION);
        assertThat(registro.getMotivo()).isNull();
        assertThat(registro.getFecha()).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("HU-09 R43: una suspensión sin motivo o con motivo en blanco no se puede crear")
    void suspenderSinMotivoLanza() {
        for (var motivo : new String[] {null, "", "   "}) {
            assertThatThrownBy(() -> SuspensionCuenta.suspender(7L, 1L, motivo, AHORA))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
