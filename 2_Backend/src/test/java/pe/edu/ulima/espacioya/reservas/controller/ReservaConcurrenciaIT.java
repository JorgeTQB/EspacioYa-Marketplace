package pe.edu.ulima.espacioya.reservas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.IllegalTransactionStateException;

import pe.edu.ulima.espacioya.cuentas.service.CuentaService;
import pe.edu.ulima.espacioya.espacios.service.DisponibilidadService;

/** Reglas de concurrencia con hilos reales, cada uno en su conexión (docs/verification.md, Nivel 2). */
class ReservaConcurrenciaIT extends ReservasControllerSoporte {

    private static final int RONDAS = 5;
    private static final String BLOQUEOS = "/api/arrendadores/actual/espacios/%d/bloqueos";
    private static final int SEGUNDOS_MAXIMOS = 30;

    @Autowired
    private DisponibilidadService disponibilidad;
    @Autowired
    private CuentaService cuentas;

    private List<MvcResult> aLaVez(List<Callable<MvcResult>> solicitudes) throws Exception {
        var salida = new CountDownLatch(1);
        var respuestas = new ArrayList<MvcResult>();
        try (var hilos = Executors.newFixedThreadPool(solicitudes.size())) {
            var pendientes = solicitudes.stream().map(solicitud -> hilos.submit(() -> {
                salida.await();
                return solicitud.call();
            })).toList();
            salida.countDown();
            for (var pendiente : pendientes) {
                respuestas.add(pendiente.get(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS));
            }
        }
        return respuestas;
    }

    private static List<Integer> estados(List<MvcResult> respuestas) {
        return respuestas.stream().map(respuesta -> respuesta.getResponse().getStatus()).toList();
    }

    private static String mensajeDelRechazo(List<MvcResult> respuestas) throws Exception {
        for (var respuesta : respuestas) {
            if (respuesta.getResponse().getStatus() != 201) {
                return respuesta.getResponse().getContentAsString();
            }
        }
        throw new AssertionError("Ninguna solicitud fue rechazada");
    }

    @Test
    @DisplayName("HU-12 R19: dos usuarios a la vez con el mismo bloque: un 201, un 400 horario no disponible y una "
            + "fila activa")
    void dosUsuariosElMismoBloque() throws Exception {
        crearUsuario(OTRO_CORREO);
        var primero = token(CORREO_USUARIO);
        var segundo = token(OTRO_CORREO);

        for (var ronda = 0; ronda < RONDAS; ronda++) {
            var dia = fecha;
            var hora = List.of(8 + ronda);

            var respuestas = aLaVez(List.of(
                    () -> reservar(primero, espacioId, dia, hora).andReturn(),
                    () -> reservar(segundo, espacioId, dia, hora).andReturn()));

            assertThat(estados(respuestas)).containsExactlyInAnyOrder(201, 400);
            assertThat(mensajeDelRechazo(respuestas)).contains(HORARIO_NO_DISPONIBLE);
            assertThat(bloquesActivos(espacioId)).isEqualTo(ronda + 1);
            assertThat(filas("reserva")).isEqualTo(ronda + 1);
            jdbc.update("UPDATE reserva SET estado = 'CONFIRMADA'");
        }
    }

    @Test
    @DisplayName("HU-12 R19: si solo coincide un bloque de varios, tampoco se crean las dos")
    void coincidenEnUnBloqueDeVarios() throws Exception {
        crearUsuario(OTRO_CORREO);
        var primero = token(CORREO_USUARIO);
        var segundo = token(OTRO_CORREO);

        var respuestas = aLaVez(List.of(
                () -> reservar(primero, espacioId, fecha, List.of(14, 15)).andReturn(),
                () -> reservar(segundo, espacioId, fecha, List.of(15, 16)).andReturn()));

        assertThat(estados(respuestas)).containsExactlyInAnyOrder(201, 400);
        assertThat(bloquesActivos(espacioId)).isEqualTo(2);
    }

    @Test
    @DisplayName("HU-12 R28: un usuario con 2 vigentes y dos espacios a la vez: un 201, un 400 cupo y 3 vigentes")
    void elCupoSeSerializaPorUsuario() throws Exception {
        var sesion = token(CORREO_USUARIO);
        var otroEspacio = publicado("90.00");
        franja(otroEspacio, fecha, 8, 22);
        var relleno = publicado("70.00");

        for (var ronda = 0; ronda < RONDAS; ronda++) {
            jdbc.update("DELETE FROM reserva");
            reservaEnBd(usuarioId, relleno, "PENDIENTE_PAGO", dentroDe(10), true, 8);
            reservaEnBd(usuarioId, relleno, "PENDIENTE_PAGO", dentroDe(10), true, 9);
            var dia = fecha;

            var respuestas = aLaVez(List.of(
                    () -> reservar(sesion, espacioId, dia, List.of(14)).andReturn(),
                    () -> reservar(sesion, otroEspacio, dia, List.of(14)).andReturn()));

            assertThat(estados(respuestas)).containsExactlyInAnyOrder(201, 400);
            assertThat(mensajeDelRechazo(respuestas)).contains(CUPO);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM reserva WHERE usuario_id = ? AND estado = "
                    + "'PENDIENTE_PAGO'", Integer.class, usuarioId)).isEqualTo(3);
        }
    }

    @Test
    @DisplayName("HU-12 R24: una reserva y un bloqueo de la misma fecha a la vez: solo uno de los dos queda")
    void reservaYBloqueoDeFecha() throws Exception {
        var usuario = token(CORREO_USUARIO);
        var arrendador = token(CORREO_ARRENDADOR);

        for (var ronda = 0; ronda < RONDAS; ronda++) {
            var dia = fecha.plusWeeks(ronda);
            var bloqueo = "{\"fecha\":\"" + dia + "\"}";

            var respuestas = aLaVez(List.of(
                    () -> reservar(usuario, espacioId, dia, List.of(14)).andReturn(),
                    () -> mvc.perform(con(arrendador, post(BLOQUEOS.formatted(espacioId)))
                            .contentType(MediaType.APPLICATION_JSON).content(bloqueo)).andReturn()));

            assertThat(estados(respuestas)).containsExactlyInAnyOrder(201, 400);
            var reservas = jdbc.queryForObject("SELECT count(*) FROM reserva_bloque WHERE espacio_id = ? "
                    + "AND fecha = ? AND activo", Integer.class, espacioId, Date.valueOf(dia));
            var bloqueos = jdbc.queryForObject("SELECT count(*) FROM bloqueo_fecha WHERE espacio_id = ? AND fecha = ?",
                    Integer.class, espacioId, Date.valueOf(dia));
            assertThat(reservas + bloqueos).isEqualTo(1);
            jdbc.update("DELETE FROM reserva");
            jdbc.update("DELETE FROM bloqueo_fecha");
        }
    }

    @Test
    @DisplayName("HU-12 R25: retenerSiLibres y bloquearCuenta sin transacción lanzan IllegalTransactionStateException")
    void exigenUnaTransaccion() {
        assertThatThrownBy(() -> disponibilidad.retenerSiLibres(espacioId, fecha, List.of(14)))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> cuentas.bloquearCuenta(usuarioId))
                .isInstanceOf(IllegalTransactionStateException.class);
    }
}
