package pe.edu.ulima.espacioya.administracion.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RevisionIdentidadTest {

    private static final Instant AHORA = Instant.parse("2026-10-28T15:00:00Z");

    @Test
    @DisplayName("HU-06 R29: aprobar registra el arrendador, el admin, la decisión APROBADA, sin motivo y la fecha")
    void aprobarSinMotivo() {
        var revision = RevisionIdentidad.aprobar(7L, 1L, AHORA);

        assertThat(revision.getArrendadorId()).isEqualTo(7L);
        assertThat(revision.getAdminId()).isEqualTo(1L);
        assertThat(revision.getDecision()).isEqualTo(DecisionIdentidad.APROBADA);
        assertThat(revision.getMotivo()).isNull();
        assertThat(revision.getFecha()).isEqualTo(AHORA);
        assertThat(revision.getId()).isNull();
    }

    @Test
    @DisplayName("HU-06 R33: rechazar registra la decisión RECHAZADA con el motivo recortado y la fecha")
    void rechazarConMotivo() {
        var revision = RevisionIdentidad.rechazar(7L, 1L, "  El DNI está borroso.  ", AHORA);

        assertThat(revision.getArrendadorId()).isEqualTo(7L);
        assertThat(revision.getAdminId()).isEqualTo(1L);
        assertThat(revision.getDecision()).isEqualTo(DecisionIdentidad.RECHAZADA);
        assertThat(revision.getMotivo()).isEqualTo("El DNI está borroso.");
        assertThat(revision.getFecha()).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("HU-06 R33: un rechazo sin motivo o con motivo en blanco no se puede crear")
    void rechazarSinMotivoLanza() {
        for (var motivo : new String[] {null, "", "   "}) {
            assertThatThrownBy(() -> RevisionIdentidad.rechazar(7L, 1L, motivo, AHORA))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
