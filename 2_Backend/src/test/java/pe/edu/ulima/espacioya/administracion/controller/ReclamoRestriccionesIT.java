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

/** Restricciones de {@code V8_1__reclamos.sql} contra PostgreSQL real (HU-26 R13, R64, R65, R66; principio 7). */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import({TestcontainersConfiguration.class, ReclamosSoporte.Dobles.class})
class ReclamoRestriccionesIT extends ReclamosSoporte {

    private long reserva;
    private long admin;

    private void preparar() {
        reserva = confirmada(hoy.minusDays(1), 8);
        admin = crearAdmin();
    }

    private void insertar(Object enReserva, String motivo, String descripcion, String estado, String resolucion,
                          Object resueltoPor, Instant resueltoEn, String monto) {
        jdbc.update("INSERT INTO reclamo (reserva_id, usuario_id, arrendador_id, motivo, descripcion, estado, "
                        + "abierto_en, resolucion, resuelto_por, resuelto_en, monto_reembolso) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::numeric)", enReserva, usuario, arrendador, motivo,
                descripcion, estado, Timestamp.from(reloj.instant()), resolucion, resueltoPor,
                resueltoEn == null ? null : Timestamp.from(resueltoEn), monto);
    }

    private void abierto(Object enReserva) {
        insertar(enReserva, "OTRO", "Algo pasó", "ABIERTO", null, null, null, null);
    }

    private Instant despues() {
        return reloj.instant().plus(Duration.ofHours(1));
    }

    private void mensaje(long reclamo, Object autor, String papel, String texto) {
        jdbc.update("INSERT INTO mensaje_reclamo (reclamo_id, autor_id, papel, texto, fecha) VALUES (?, ?, ?, ?, ?)",
                reclamo, autor, papel, texto, Timestamp.from(reloj.instant()));
    }

    private long reclamo() {
        return jdbc.queryForObject("SELECT id FROM reclamo WHERE reserva_id = ?", Long.class, reserva);
    }

    @Test
    @DisplayName("HU-26 R13: un segundo reclamo de la misma reserva se rechaza, en cualquier estado (uq_reclamo_reserva)")
    void unoPorReserva() {
        preparar();
        insertar(reserva, "OTRO", "Algo", "RESUELTO_ARRENDADOR", "No procede", admin, despues(), null);

        assertThatThrownBy(() -> abierto(reserva))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("uq_reclamo_reserva");
        assertThat(filas("reclamo")).isEqualTo(1);
    }

    @Test
    @DisplayName("HU-26 R64: estado, motivo y papel fuera de su lista y textos en blanco se rechazan con CHECK")
    void catalogos() {
        preparar();

        assertThatThrownBy(() -> insertar(reserva, "OTRO", "Algo", "EN_REVISION", null, null, null, null))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_reclamo_estado");
        assertThatThrownBy(() -> insertar(reserva, "SUCIO", "Algo", "ABIERTO", null, null, null, null))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_reclamo_motivo");
        assertThatThrownBy(() -> insertar(reserva, "OTRO", "   ", "ABIERTO", null, null, null, null))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_reclamo_descripcion");
        abierto(reserva);
        var reclamo = reclamo();
        assertThatThrownBy(() -> mensaje(reclamo, usuario, "ADMIN", "Hola"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_mensaje_reclamo_papel");
        assertThatThrownBy(() -> mensaje(reclamo, usuario, "USUARIO", "  "))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_mensaje_reclamo_texto");
        mensaje(reclamo, usuario, "USUARIO", "Hola");
        mensaje(reclamo, arrendador, "ARRENDADOR", "Hola");
        assertThat(filas("mensaje_reclamo")).isEqualTo(2);
    }

    @Test
    @DisplayName("HU-26 R65: los datos de la resolución deben ser coherentes con el estado (ck_reclamo_resolucion)")
    void coherenciaDeLaResolucion() {
        preparar();
        var antes = reloj.instant().minusSeconds(1);

        assertThatThrownBy(() -> insertar(reserva, "OTRO", "Algo", "ABIERTO", "Texto", null, null, null))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_reclamo_resolucion");
        assertThatThrownBy(() -> insertar(reserva, "OTRO", "Algo", "ABIERTO", null, null, null, "10.00"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_reclamo_resolucion");
        assertThatThrownBy(() -> insertar(reserva, "OTRO", "Algo", "RESUELTO_USUARIO", "Ok", admin, despues(), null))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_reclamo_resolucion");
        assertThatThrownBy(() -> insertar(reserva, "OTRO", "Algo", "RESUELTO_USUARIO", "Ok", admin, despues(),
                "0.00")).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_reclamo_resolucion");
        assertThatThrownBy(() -> insertar(reserva, "OTRO", "Algo", "RESUELTO_USUARIO", " ", admin, despues(),
                "10.00")).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_reclamo_resolucion");
        assertThatThrownBy(() -> insertar(reserva, "OTRO", "Algo", "RESUELTO_USUARIO", "Ok", null, despues(),
                "10.00")).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_reclamo_resolucion");
        assertThatThrownBy(() -> insertar(reserva, "OTRO", "Algo", "RESUELTO_USUARIO", "Ok", admin, antes, "10.00"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_reclamo_resolucion");
        assertThatThrownBy(() -> insertar(reserva, "OTRO", "Algo", "RESUELTO_ARRENDADOR", "Ok", admin, despues(),
                "10.00")).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_reclamo_resolucion");
        assertThatThrownBy(() -> insertar(reserva, "OTRO", "Algo", "RESUELTO_ARRENDADOR", "Ok", admin, null, null))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_reclamo_resolucion");
        assertThat(filas("reclamo")).isZero();

        insertar(reserva, "OTRO", "Algo", "RESUELTO_USUARIO", "Ok", admin, despues(), "10.00");
        insertar(confirmada(hoy.minusDays(1), 9), "ESPACIO_CERRADO", "Algo", "RESUELTO_ARRENDADOR", "Ok", admin,
                despues(), null);
        insertar(confirmada(hoy.minusDays(1), 10), "DISTINTO_A_LO_PUBLICADO", "Algo", "ABIERTO", null, null, null,
                null);
        assertThat(filas("reclamo")).isEqualTo(3);
    }

    @Test
    @DisplayName("HU-26 R66: las llaves foráneas rechazan referencias inexistentes y el borrado sin cascada")
    void llavesForaneasSinCascada() {
        preparar();

        assertThatThrownBy(() -> abierto(reserva + 100000)).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_reclamo_reserva");
        abierto(reserva);
        var reclamo = reclamo();
        assertThatThrownBy(() -> mensaje(reclamo, usuario + 100000, "USUARIO", "Hola"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_mensaje_reclamo_autor");
        assertThatThrownBy(() -> mensaje(reclamo + 100000, usuario, "USUARIO", "Hola"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_mensaje_reclamo_reclamo");
        mensaje(reclamo, usuario, "USUARIO", "Hola");

        assertThatThrownBy(() -> jdbc.update("DELETE FROM reserva WHERE id = ?", reserva))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_reclamo_reserva");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM reclamo WHERE id = ?", reclamo))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_mensaje_reclamo_reclamo");
        // Una cuenta sin reservas ni espacios: solo la retiene el reclamo.
        var otro = crearUsuario(CORREO_OTRO);
        sembrarReclamo(confirmada(hoy.minusDays(1), 9), otro, arrendador, reloj.instant());
        assertThatThrownBy(() -> jdbc.update("DELETE FROM usuario WHERE id = ?", otro))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_reclamo_usuario");
        assertThat(filas("reclamo")).isEqualTo(2);
        assertThat(filas("mensaje_reclamo")).isEqualTo(1);
    }
}
