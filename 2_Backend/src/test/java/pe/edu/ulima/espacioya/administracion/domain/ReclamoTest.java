package pe.edu.ulima.espacioya.administracion.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReclamoTest {

    private static final Instant ABIERTO_EN = Instant.parse("2026-11-04T21:00:00Z");
    private static final Instant RESUELTO_EN = Instant.parse("2026-11-05T15:00:00Z");
    private static final long RESERVA = 90L;
    private static final long USUARIO = 7L;
    private static final long ARRENDADOR = 3L;
    private static final long ADMIN = 1L;

    private static Reclamo abierto() {
        return Reclamo.abrir(RESERVA, USUARIO, ARRENDADOR, MotivoReclamo.ESPACIO_CERRADO, "Estaba cerrado",
                ABIERTO_EN);
    }

    @Test
    @DisplayName("HU-26 R1: abrir deja el reclamo ABIERTO con sus partes, motivo, descripción y fecha, sin resolución")
    void abre() {
        var reclamo = abierto();

        assertThat(reclamo.getEstado()).isEqualTo(EstadoReclamo.ABIERTO);
        assertThat(reclamo.estaAbierto()).isTrue();
        assertThat(reclamo.getReservaId()).isEqualTo(RESERVA);
        assertThat(reclamo.getUsuarioId()).isEqualTo(USUARIO);
        assertThat(reclamo.getArrendadorId()).isEqualTo(ARRENDADOR);
        assertThat(reclamo.getMotivo()).isEqualTo(MotivoReclamo.ESPACIO_CERRADO);
        assertThat(reclamo.getDescripcion()).isEqualTo("Estaba cerrado");
        assertThat(reclamo.getAbiertoEn()).isEqualTo(ABIERTO_EN);
        assertThat(reclamo.aFavorDe()).isEmpty();
        assertThat(reclamo.getResolucion()).isNull();
        assertThat(reclamo.getResueltoPor()).isNull();
        assertThat(reclamo.getResueltoEn()).isNull();
        assertThat(reclamo.getMontoReembolso()).isNull();
    }

    @Test
    @DisplayName("HU-26 R25: papelDe reconoce al usuario y al arrendador y deja vacía a cualquier otra cuenta")
    void papeles() {
        var reclamo = abierto();

        assertThat(reclamo.papelDe(USUARIO)).contains(PapelReclamo.USUARIO);
        assertThat(reclamo.papelDe(ARRENDADOR)).contains(PapelReclamo.ARRENDADOR);
        assertThat(reclamo.papelDe(ADMIN)).isEmpty();
        assertThat(reclamo.papelDe(999L)).isEmpty();
    }

    @Test
    @DisplayName("HU-26 R42: a favor del usuario queda RESUELTO_USUARIO con admin, texto, fecha y monto")
    void aFavorDelUsuario() {
        var reclamo = abierto();

        reclamo.resolverAFavorDelUsuario(ADMIN, "Se comprobó", new BigDecimal("80.00"), RESUELTO_EN);

        assertThat(reclamo.getEstado()).isEqualTo(EstadoReclamo.RESUELTO_USUARIO);
        assertThat(reclamo.estaAbierto()).isFalse();
        assertThat(reclamo.aFavorDe()).contains(PapelReclamo.USUARIO);
        assertThat(reclamo.getResueltoPor()).isEqualTo(ADMIN);
        assertThat(reclamo.getResolucion()).isEqualTo("Se comprobó");
        assertThat(reclamo.getResueltoEn()).isEqualTo(RESUELTO_EN);
        assertThat(reclamo.getMontoReembolso()).isEqualByComparingTo("80.00");
    }

    @Test
    @DisplayName("HU-26 R49: a favor del arrendador queda RESUELTO_ARRENDADOR sin monto")
    void aFavorDelArrendador() {
        var reclamo = abierto();

        reclamo.resolverAFavorDelArrendador(ADMIN, "No se comprobó", RESUELTO_EN);

        assertThat(reclamo.getEstado()).isEqualTo(EstadoReclamo.RESUELTO_ARRENDADOR);
        assertThat(reclamo.aFavorDe()).contains(PapelReclamo.ARRENDADOR);
        assertThat(reclamo.getResueltoPor()).isEqualTo(ADMIN);
        assertThat(reclamo.getResolucion()).isEqualTo("No se comprobó");
        assertThat(reclamo.getResueltoEn()).isEqualTo(RESUELTO_EN);
        assertThat(reclamo.getMontoReembolso()).isNull();
    }

    @Test
    @DisplayName("HU-26 R54: resolver un reclamo ya resuelto lanza IllegalStateException y no cambia nada")
    void resolverDosVeces() {
        var reclamo = abierto();
        reclamo.resolverAFavorDelArrendador(ADMIN, "No se comprobó", RESUELTO_EN);

        assertThatThrownBy(() -> reclamo.resolverAFavorDelUsuario(2L, "Otra", BigDecimal.TEN, RESUELTO_EN))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> reclamo.resolverAFavorDelArrendador(2L, "Otra", RESUELTO_EN))
                .isInstanceOf(IllegalStateException.class);
        assertThat(reclamo.getEstado()).isEqualTo(EstadoReclamo.RESUELTO_ARRENDADOR);
        assertThat(reclamo.getResueltoPor()).isEqualTo(ADMIN);
        assertThat(reclamo.getResolucion()).isEqualTo("No se comprobó");
    }

    @Test
    @DisplayName("HU-26 R42: un monto de 0, negativo o nulo lanza IllegalStateException y el reclamo sigue ABIERTO")
    void montoNoPositivo() {
        var reclamo = abierto();

        assertThatThrownBy(() -> reclamo.resolverAFavorDelUsuario(ADMIN, "x", BigDecimal.ZERO, RESUELTO_EN))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> reclamo.resolverAFavorDelUsuario(ADMIN, "x", new BigDecimal("-1"), RESUELTO_EN))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> reclamo.resolverAFavorDelUsuario(ADMIN, "x", null, RESUELTO_EN))
                .isInstanceOf(IllegalStateException.class);
        assertThat(reclamo.estaAbierto()).isTrue();
        assertThat(reclamo.getResolucion()).isNull();
    }

    @Test
    @DisplayName("HU-26 R23: un mensaje guarda reclamo, autor, papel, texto y fecha")
    void mensaje() {
        var mensaje = MensajeReclamo.nuevo(5L, ARRENDADOR, PapelReclamo.ARRENDADOR, "Lo revisamos", RESUELTO_EN);

        assertThat(mensaje.getReclamoId()).isEqualTo(5L);
        assertThat(mensaje.getAutorId()).isEqualTo(ARRENDADOR);
        assertThat(mensaje.getPapel()).isEqualTo(PapelReclamo.ARRENDADOR);
        assertThat(mensaje.getTexto()).isEqualTo("Lo revisamos");
        assertThat(mensaje.getFecha()).isEqualTo(RESUELTO_EN);
    }
}
