package pe.edu.ulima.espacioya.pagos.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import pe.edu.ulima.espacioya.TestcontainersConfiguration;

/** Restricciones de {@code V4_3__reembolso.sql} contra PostgreSQL real (HU-16 R19, R42; principio 7). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PagosSoporte.Dobles.class})
class ReembolsoRestriccionesIT extends ReembolsosSoporte {

    private void insertar(Object pago, String monto, String origen, String estado, int intentos, boolean intentado,
                          String idPasarela) {
        jdbc.update("INSERT INTO reembolso (pago_id, monto, origen, estado, intentos, creado_en, ultimo_intento_en, "
                        + "id_reembolso_pasarela) VALUES (?, ?::numeric, ?, ?, ?, ?, ?, ?)", pago, monto, origen,
                estado, intentos, Timestamp.from(reloj.instant()),
                intentado ? Timestamp.from(reloj.instant()) : null, idPasarela);
    }

    @Test
    @DisplayName("HU-16 R19: un segundo reembolso con el mismo pago y origen se rechaza (uq_reembolso_pago_origen)")
    void unoPorPagoYOrigen() {
        var pago = pagoSembrado("PAGADO");
        insertar(pago, "241.50", "CANCELACION", "PENDIENTE", 0, false, null);

        assertThatThrownBy(() -> insertar(pago, "10.00", "CANCELACION", "PENDIENTE", 0, false, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_reembolso_pago_origen");
        insertar(pago, "10.00", "RECLAMO", "PENDIENTE", 0, false, null);
        assertThat(filas("reembolso")).isEqualTo(2);
    }

    @Test
    @DisplayName("HU-16 R42: origen, estado, monto, intentos, pago inexistente e id de la pasarela inválidos se "
            + "rechazan")
    void filasInvalidas() {
        var pago = pagoSembrado("PAGADO");

        assertThatThrownBy(() -> insertar(pago, "1.00", "OTRO", "PENDIENTE", 0, false, null))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_reembolso_origen");
        assertThatThrownBy(() -> insertar(pago, "1.00", "CANCELACION", "OTRO", 0, false, null))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_reembolso_estado");
        assertThatThrownBy(() -> insertar(pago, "0.00", "CANCELACION", "PENDIENTE", 0, false, null))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_reembolso_monto");
        assertThatThrownBy(() -> insertar(pago, "-5.00", "CANCELACION", "PENDIENTE", 0, false, null))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_reembolso_monto");
        assertThatThrownBy(() -> insertar(pago, "1.00", "CANCELACION", "PENDIENTE", -1, false, null))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_reembolso_intentos");
        assertThatThrownBy(() -> insertar(pago + 100000, "1.00", "CANCELACION", "PENDIENTE", 0, false, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertar(pago, "1.00", "CANCELACION", "REEMBOLSADO", 1, true, null))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_reembolso_confirmado");
        assertThatThrownBy(() -> insertar(pago, "1.00", "CANCELACION", "FALLIDO", 1, true, "sim-reembolso-1"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_reembolso_confirmado");
        assertThatThrownBy(() -> insertar(pago, "1.00", "CANCELACION", "PENDIENTE", 0, false, "sim-reembolso-1"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_reembolso_confirmado");
        assertThatThrownBy(() -> insertar(pago, "1.00", "CANCELACION", "FALLIDO", 0, true, null))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_reembolso_intentado");
        assertThatThrownBy(() -> insertar(pago, "1.00", "CANCELACION", "FALLIDO", 1, false, null))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_reembolso_intentado");
        assertThatThrownBy(() -> insertar(pago, "1.00", "CANCELACION", "REEMBOLSADO", 0, false, "sim-reembolso-1"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_reembolso_intentado");
        assertThat(filas("reembolso")).isZero();
    }

    @Test
    @DisplayName("HU-16 R42: se aceptan PENDIENTE sin intentos, FALLIDO intentado y REEMBOLSADO intentado con su id")
    void filasValidas() {
        insertar(pagoSembrado("PAGADO"), "241.50", "CANCELACION", "PENDIENTE", 0, false, null);
        insertar(pagoSembrado("PAGADO"), "0.01", "CANCELACION", "PENDIENTE", 3, true, null);
        insertar(pagoSembrado("REEMBOLSO_FALLIDO"), "161.00", "CANCELACION", "FALLIDO", 1, true, null);
        insertar(pagoSembrado("REEMBOLSADO"), "161.00", "RECLAMO", "REEMBOLSADO", 2, true, "sim-reembolso-x");

        assertThat(filas("reembolso")).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reembolso WHERE intentos = 0 AND ultimo_intento_en IS NULL",
                Integer.class)).isEqualTo(1);
    }
}
