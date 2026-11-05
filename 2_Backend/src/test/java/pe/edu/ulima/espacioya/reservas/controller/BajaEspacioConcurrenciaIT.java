package pe.edu.ulima.espacioya.reservas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * HU-21 R17: una baja y una reserva nueva del mismo espacio se serializan en el bloqueo de la fila del espacio. Cada
 * ronda usa un espacio y un usuario nuevos (el cupo de 3 pendientes de HU-12 no interviene). Gane quien gane, la
 * prueba comprueba el invariante, así que no depende del orden de los hilos; los dos órdenes posibles se prueban
 * además uno tras otro, de forma determinista. IP 10.21.250.x.
 */
class BajaEspacioConcurrenciaIT extends ReservasControllerSoporte {

    private static final int RONDAS = 6;
    private static final int SEGUNDOS_MAXIMOS = 30;
    private static final String ESPACIOS = "/api/arrendadores/actual/espacios/";
    private static final AtomicInteger SIGUIENTE_IP = new AtomicInteger(1);

    private String arrendador;

    @BeforeEach
    void prepararHu21() throws Exception {
        var n = SIGUIENTE_IP.getAndIncrement();
        ip = "10.21." + (250 + n / 250) + "." + (n % 250 + 1);
        arrendador = token(CORREO_ARRENDADOR);
    }

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

    private MvcResult darDeBaja(long espacio) throws Exception {
        return mvc.perform(con(arrendador, post(ESPACIOS + espacio + "/baja"))).andReturn();
    }

    private long espacioConFranja() {
        var espacio = publicado("80.50");
        franja(espacio, fecha, 8, 22);
        return espacio;
    }

    private String usuarioNuevo(int n) throws Exception {
        var correo = "hu21.ronda" + n + "@correo.pe";
        crearUsuario(correo);
        return token(correo);
    }

    private String estadoDe(long espacio) {
        return jdbc.queryForObject("SELECT estado FROM espacio WHERE id = ?", String.class, espacio);
    }

    private int reservasActivas(long espacio) {
        return jdbc.queryForObject("SELECT count(*) FROM reserva WHERE espacio_id = ? AND estado IN "
                + "('CONFIRMADA', 'PENDIENTE_PAGO')", Integer.class, espacio);
    }

    /** Uno de los dos órdenes válidos, y nunca un espacio en BAJA con una reserva activa. */
    private void unaDeDos(long espacio, int baja, int reserva) {
        if (reserva == 201) {
            assertThat(baja).isEqualTo(400);
            assertThat(estadoDe(espacio)).isEqualTo("PUBLICADO");
            assertThat(reservasActivas(espacio)).isEqualTo(1);
            assertThat(bloquesActivos(espacio)).isEqualTo(1);
        } else {
            assertThat(baja).isEqualTo(200);
            assertThat(reserva).isEqualTo(404);
            assertThat(estadoDe(espacio)).isEqualTo("BAJA");
            assertThat(reservasActivas(espacio)).isZero();
            assertThat(bloquesActivos(espacio)).isZero();
        }
    }

    @Test
    @DisplayName("HU-21 R17: baja y reserva del mismo espacio a la vez: o reserva 201 y baja 400, o baja 200 y "
            + "reserva 404; nunca BAJA con una reserva activa")
    void bajaYReservaALaVez() throws Exception {
        for (var ronda = 0; ronda < RONDAS; ronda++) {
            var espacio = espacioConFranja();
            var usuario = usuarioNuevo(ronda);
            var hora = List.of(8 + ronda);

            var respuestas = aLaVez(List.of(
                    () -> darDeBaja(espacio),
                    () -> reservar(usuario, espacio, fecha, hora).andReturn()));

            unaDeDos(espacio, respuestas.get(0).getResponse().getStatus(),
                    respuestas.get(1).getResponse().getStatus());
        }
    }

    @Test
    @DisplayName("HU-21 R17: si la reserva entra primero, la baja se rechaza; si la baja entra primero, la reserva da "
            + "404")
    void losDosOrdenes() throws Exception {
        var usuario = usuarioNuevo(100);

        var primero = espacioConFranja();
        var reserva = reservar(usuario, primero, fecha, List.of(10)).andReturn().getResponse().getStatus();
        var baja = darDeBaja(primero).getResponse().getStatus();
        assertThat(reserva).isEqualTo(201);
        unaDeDos(primero, baja, reserva);

        var segundo = espacioConFranja();
        baja = darDeBaja(segundo).getResponse().getStatus();
        reserva = reservar(usuario, segundo, fecha, List.of(10)).andReturn().getResponse().getStatus();
        assertThat(baja).isEqualTo(200);
        unaDeDos(segundo, baja, reserva);
    }
}
