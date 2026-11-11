package pe.edu.ulima.espacioya.administracion.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import pe.edu.ulima.espacioya.TestcontainersConfiguration;

/** Restricciones de {@code V8_2__reportes.sql} contra PostgreSQL real (HU-27 R11, R56–R58, R74, R75; principio 7). */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import({TestcontainersConfiguration.class, ReclamosSoporte.Dobles.class})
class ReporteRestriccionesIT extends ReportesSoporte {

    private void insertar(String tipo, Object espacioId, Object resenaId, String motivo, String estado, Object admin,
                          String motivoDecision, Instant resueltoEn, boolean porFraude) {
        jdbc.update("INSERT INTO reporte (reportante_id, tipo_objetivo, espacio_id, resena_id, motivo, estado, "
                        + "creado_en, admin_id, motivo_decision, resuelto_en, por_fraude) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", usuario, tipo, espacioId, resenaId, motivo,
                estado, Timestamp.from(reloj.instant()), admin, motivoDecision,
                resueltoEn == null ? null : Timestamp.from(resueltoEn), porFraude);
    }

    private void pendienteDeEspacio(Object espacioId) {
        insertar("ESPACIO", espacioId, null, "SPAM", "POR_REVISAR", null, null, null, false);
    }

    private String definicion(String restriccion) {
        return jdbc.queryForObject("SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                + "WHERE conname = ? AND conrelid = 'reporte'::regclass", String.class, restriccion);
    }

    private Instant despues() {
        return reloj.instant().plus(Duration.ofHours(1));
    }

    @Test
    @DisplayName("HU-27 R11: un reporte por reportante y contenido en cualquier estado; otro reportante sí puede")
    void unoPorReportanteYContenido() {
        var admin = crearAdmin();
        var resena = resena(usuario, espacio, "VISIBLE");
        insertar("ESPACIO", espacio, null, "SPAM", "MANTENIDO", admin, "Ok", despues(), false);
        insertar("RESENA", null, resena, "SPAM", "POR_REVISAR", null, null, null, false);

        assertThatThrownBy(() -> pendienteDeEspacio(espacio))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("uq_reporte_espacio");
        assertThatThrownBy(() -> insertar("RESENA", null, resena, "FRAUDE", "POR_REVISAR", null, null, null, false))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("uq_reporte_resena");
        var otro = crearUsuario(CORREO_OTRO);
        sembrarReporte(otro, "ESPACIO", espacio);
        sembrarReporte(otro, "RESENA", resena);
        assertThat(filas("reporte")).isEqualTo(4);
    }

    @Test
    @DisplayName("HU-27 R56: tipo, motivo, estado y coherencia tipo–contenido se rechazan con CHECK")
    void catalogosYCoherencia() {
        var resena = resena(usuario, espacio, "VISIBLE");

        // Un tipo fuera del catálogo viola también ck_reporte_objetivo, y un estado fuera del catálogo también
        // ck_reporte_decision; PostgreSQL informa el primer CHECK violado por nombre. Por eso se exige el rechazo por
        // CHECK y, aparte, que ck_reporte_tipo y ck_reporte_estado sean exactamente su catálogo.
        assertThatThrownBy(() -> insertar("FOTO", espacio, null, "SPAM", "POR_REVISAR", null, null, null, false))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("violates check constraint");
        assertThat(definicion("ck_reporte_tipo")).contains("tipo_objetivo", "'ESPACIO'", "'RESENA'")
                .doesNotContain("FOTO");
        assertThatThrownBy(() -> insertar("ESPACIO", espacio, null, "OTRO", "POR_REVISAR", null, null, null, false))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_reporte_motivo");
        assertThatThrownBy(() -> insertar("ESPACIO", espacio, null, "SPAM", "PENDIENTE", null, null, null, false))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("violates check constraint");
        assertThat(definicion("ck_reporte_estado")).contains("estado", "'POR_REVISAR'", "'MANTENIDO'", "'OCULTADO'")
                .doesNotContain("PENDIENTE");
        assertThatThrownBy(() -> insertar("ESPACIO", null, resena, "SPAM", "POR_REVISAR", null, null, null, false))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_reporte_objetivo");
        assertThatThrownBy(() -> insertar("ESPACIO", espacio, resena, "SPAM", "POR_REVISAR", null, null, null, false))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_reporte_objetivo");
        assertThatThrownBy(() -> insertar("RESENA", espacio, null, "SPAM", "POR_REVISAR", null, null, null, false))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_reporte_objetivo");
        assertThatThrownBy(() -> insertar("RESENA", null, null, "SPAM", "POR_REVISAR", null, null, null, false))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_reporte_objetivo");
        assertThat(filas("reporte")).isZero();

        pendienteDeEspacio(espacio);
        insertar("RESENA", null, resena, "CONTENIDO_INAPROPIADO", "POR_REVISAR", null, null, null, false);
        assertThat(filas("reporte")).isEqualTo(2);
    }

    @Test
    @DisplayName("HU-27 R57: los datos de la decisión deben ser coherentes con el estado (ck_reporte_decision)")
    void coherenciaDeLaDecision() {
        var admin = crearAdmin();
        var antes = reloj.instant().minusSeconds(1);

        assertThatThrownBy(() -> insertar("ESPACIO", espacio, null, "SPAM", "POR_REVISAR", admin, null, null, false))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_reporte_decision");
        assertThatThrownBy(() -> insertar("ESPACIO", espacio, null, "SPAM", "POR_REVISAR", null, "Ok", null, false))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_reporte_decision");
        assertThatThrownBy(() -> insertar("ESPACIO", espacio, null, "SPAM", "POR_REVISAR", null, null, despues(),
                false)).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_reporte_decision");
        assertThatThrownBy(() -> insertar("ESPACIO", espacio, null, "SPAM", "MANTENIDO", null, "Ok", despues(), false))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_reporte_decision");
        assertThatThrownBy(() -> insertar("ESPACIO", espacio, null, "SPAM", "MANTENIDO", admin, "  ", despues(),
                false)).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_reporte_decision");
        assertThatThrownBy(() -> insertar("ESPACIO", espacio, null, "SPAM", "OCULTADO", admin, null, despues(), false))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_reporte_decision");
        assertThatThrownBy(() -> insertar("ESPACIO", espacio, null, "SPAM", "OCULTADO", admin, "Ok", null, false))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_reporte_decision");
        assertThatThrownBy(() -> insertar("ESPACIO", espacio, null, "SPAM", "OCULTADO", admin, "Ok", antes, false))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_reporte_decision");
        assertThat(filas("reporte")).isZero();

        insertar("ESPACIO", espacio, null, "SPAM", "OCULTADO", admin, "Ok", reloj.instant(), false);
        assertThat(filas("reporte")).isEqualTo(1);
    }

    @Test
    @DisplayName("HU-27 R75: por_fraude solo en un reporte de espacio OCULTADO (ck_reporte_fraude)")
    void fraudeSoloEnEspacioOcultado() {
        var admin = crearAdmin();
        var resena = resena(usuario, espacio, "VISIBLE");

        assertThatThrownBy(() -> insertar("ESPACIO", espacio, null, "FRAUDE", "POR_REVISAR", null, null, null, true))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_reporte_fraude");
        assertThatThrownBy(() -> insertar("ESPACIO", espacio, null, "FRAUDE", "MANTENIDO", admin, "Ok", despues(),
                true)).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_reporte_fraude");
        assertThatThrownBy(() -> insertar("RESENA", null, resena, "FRAUDE", "OCULTADO", admin, "Ok", despues(), true))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_reporte_fraude");
        assertThat(filas("reporte")).isZero();

        insertar("ESPACIO", espacio, null, "SPAM", "OCULTADO", admin, "Fraude comprobado", despues(), true);
        assertThat(jdbc.queryForObject("SELECT por_fraude FROM reporte", Boolean.class)).isTrue();
    }

    @Test
    @DisplayName("HU-27 R58: las llaves foráneas rechazan referencias inexistentes y el borrado sin cascada")
    void llavesForaneasSinCascada() {
        var admin = crearAdmin();
        var autor = crearUsuario(CORREO_OTRO);
        var resena = resena(autor, espacio, "VISIBLE");

        assertThatThrownBy(() -> pendienteDeEspacio(espacio + 100000))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_reporte_espacio");
        assertThatThrownBy(() -> insertar("RESENA", null, resena + 100000, "SPAM", "POR_REVISAR", null, null, null,
                false)).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_reporte_resena");
        assertThatThrownBy(() -> insertar("ESPACIO", espacio, null, "SPAM", "MANTENIDO", admin + 100000, "Ok",
                despues(), false)).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_reporte_admin");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO reporte (reportante_id, tipo_objetivo, espacio_id, motivo, "
                        + "estado, creado_en) VALUES (?, 'ESPACIO', ?, 'SPAM', 'POR_REVISAR', now())",
                usuario + 100000, espacio)).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_reporte_reportante");

        insertar("ESPACIO", espacio, null, "SPAM", "MANTENIDO", admin, "Ok", despues(), false);
        insertar("RESENA", null, resena, "SPAM", "POR_REVISAR", null, null, null, false);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM resena WHERE id = ?", resena))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_reporte_resena");
        jdbc.update("DELETE FROM reporte WHERE resena_id IS NOT NULL");
        jdbc.update("DELETE FROM resena");
        jdbc.update("DELETE FROM reserva_bloque");
        jdbc.update("DELETE FROM reserva");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM espacio WHERE id = ?", espacio))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_reporte_espacio");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM usuario WHERE id = ?", usuario))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_reporte_reportante");
        jdbc.update("DELETE FROM sesion");
        jdbc.update("DELETE FROM codigo_respaldo");
        jdbc.update("DELETE FROM mfa");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM usuario WHERE id = ?", admin))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_reporte_admin");
        assertThat(filas("reporte")).isEqualTo(1);
    }

    @Test
    @DisplayName("HU-27 R74: reserva.cancelada_por admite ADMIN y sigue rechazando otros valores")
    void canceladaPorAdmin() {
        var cancelada = reserva(usuario, espacio, "CANCELADA", hoy.plusDays(2), 10);

        assertThatThrownBy(() -> jdbc.update("UPDATE reserva SET cancelada_por = 'OTRO', cancelada_en = now(), "
                + "monto_reembolso = 0 WHERE id = ?", cancelada))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_reserva_cancelada_por");
        assertThat(jdbc.update("UPDATE reserva SET cancelada_por = 'ADMIN', cancelada_en = now(), "
                + "monto_reembolso = monto_total WHERE id = ?", cancelada)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT cancelada_por FROM reserva WHERE id = ?", String.class, cancelada))
                .isEqualTo("ADMIN");
    }
}
