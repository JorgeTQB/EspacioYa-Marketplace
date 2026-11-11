package pe.edu.ulima.espacioya.administracion.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReporteTest {

    private static final Instant AHORA = Instant.parse("2026-11-04T15:00:00Z");
    private static final Instant DESPUES = AHORA.plusSeconds(60);

    @Test
    @DisplayName("HU-27 R1, R2: las fábricas guardan el contenido según su tipo y nacen POR_REVISAR")
    void fabricasPorTipo() {
        var deEspacio = Reporte.deEspacio(5L, 7L, MotivoReporte.FRAUDE, AHORA);
        var deResena = Reporte.deResena(5L, 9L, MotivoReporte.SPAM, AHORA);

        assertThat(deEspacio.getTipoObjetivo()).isEqualTo(TipoObjetivoReporte.ESPACIO);
        assertThat(deEspacio.getEspacioId()).isEqualTo(7L);
        assertThat(deEspacio.getResenaId()).isNull();
        assertThat(deEspacio.getObjetivoId()).isEqualTo(7L);
        assertThat(deEspacio.getEstado()).isEqualTo(EstadoReporte.POR_REVISAR);
        assertThat(deEspacio.getCreadoEn()).isEqualTo(AHORA);
        assertThat(deEspacio.getReportanteId()).isEqualTo(5L);
        assertThat(deEspacio.decision()).isEmpty();
        assertThat(deEspacio.isPorFraude()).isFalse();

        assertThat(deResena.getTipoObjetivo()).isEqualTo(TipoObjetivoReporte.RESENA);
        assertThat(deResena.getResenaId()).isEqualTo(9L);
        assertThat(deResena.getEspacioId()).isNull();
        assertThat(deResena.getObjetivoId()).isEqualTo(9L);
        assertThat(deResena.getMotivo()).isEqualTo(MotivoReporte.SPAM);
    }

    @Test
    @DisplayName("HU-27 R31: mantener deja MANTENIDO con admin, motivo y fecha")
    void mantener() {
        var reporte = Reporte.deEspacio(5L, 7L, MotivoReporte.SPAM, AHORA);

        reporte.resolver(DecisionReporte.MANTENER, 1L, "No infringe", DESPUES);

        assertThat(reporte.getEstado()).isEqualTo(EstadoReporte.MANTENIDO);
        assertThat(reporte.getAdminId()).isEqualTo(1L);
        assertThat(reporte.getMotivoDecision()).isEqualTo("No infringe");
        assertThat(reporte.getResueltoEn()).isEqualTo(DESPUES);
        assertThat(reporte.isPorFraude()).isFalse();
        assertThat(reporte.decision()).contains(DecisionReporte.MANTENER);
    }

    @Test
    @DisplayName("HU-27 R33, R78: ocultar deja OCULTADO; por fraude, además por_fraude y la decisión OCULTAR_POR_FRAUDE")
    void ocultarYOcultarPorFraude() {
        var oculto = Reporte.deResena(5L, 9L, MotivoReporte.SPAM, AHORA);
        var fraude = Reporte.deEspacio(5L, 7L, MotivoReporte.SPAM, AHORA);

        oculto.resolver(DecisionReporte.OCULTAR, 1L, "Ofensivo", DESPUES);
        fraude.resolver(DecisionReporte.OCULTAR_POR_FRAUDE, 1L, "Estafa", DESPUES);

        assertThat(oculto.getEstado()).isEqualTo(EstadoReporte.OCULTADO);
        assertThat(oculto.isPorFraude()).isFalse();
        assertThat(oculto.decision()).contains(DecisionReporte.OCULTAR);
        assertThat(fraude.getEstado()).isEqualTo(EstadoReporte.OCULTADO);
        assertThat(fraude.isPorFraude()).isTrue();
        assertThat(fraude.decision()).contains(DecisionReporte.OCULTAR_POR_FRAUDE);
    }

    @Test
    @DisplayName("HU-27 R41: un reporte ya resuelto no se resuelve otra vez")
    void noSeResuelveDosVeces() {
        var reporte = Reporte.deEspacio(5L, 7L, MotivoReporte.SPAM, AHORA);
        reporte.resolver(DecisionReporte.MANTENER, 1L, "Ok", DESPUES);

        assertThatThrownBy(() -> reporte.resolver(DecisionReporte.OCULTAR, 2L, "Otro", DESPUES))
                .isInstanceOf(IllegalStateException.class);
        assertThat(reporte.getEstado()).isEqualTo(EstadoReporte.MANTENIDO);
        assertThat(reporte.getAdminId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("HU-27 R63: OCULTAR_POR_FRAUDE sobre un reporte de reseña se rechaza sin cambiarlo")
    void fraudeNoEnResena() {
        var reporte = Reporte.deResena(5L, 9L, MotivoReporte.FRAUDE, AHORA);

        assertThatThrownBy(() -> reporte.resolver(DecisionReporte.OCULTAR_POR_FRAUDE, 1L, "Estafa", DESPUES))
                .isInstanceOf(IllegalStateException.class);
        assertThat(reporte.estaPorRevisar()).isTrue();
        assertThat(reporte.isPorFraude()).isFalse();
    }
}
