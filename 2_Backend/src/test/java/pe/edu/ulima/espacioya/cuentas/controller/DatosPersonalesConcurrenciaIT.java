package pe.edu.ulima.espacioya.cuentas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.comun.seguridad.UsuarioAutenticado;
import pe.edu.ulima.espacioya.pagos.service.LiquidacionAdminService;

/**
 * Reglas de concurrencia de HU-22 (R42, R43, R44) con hilos reales, cada uno en su conexión a PostgreSQL
 * (docs/verification.md, Nivel 2). Gane quien gane, cada ronda comprueba el invariante: nunca una cuenta
 * {@code ELIMINADA} con algo que la eliminación debía impedir. La eliminación calcula dos bcrypt antes de bloquear;
 * para que las rondas cubran los dos órdenes, la otra operación sale con un retraso creciente. Los dos órdenes se
 * prueban además uno tras otro, de forma determinista. IP 10.22.200.x en adelante.
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import({TestcontainersConfiguration.class, DatosPersonalesSoporte.Dobles.class})
class DatosPersonalesConcurrenciaIT extends DatosPersonalesSoporte {

    private static final int RONDAS = 10;
    private static final long PASO_MS = 120;
    private static final int SEGUNDOS_MAXIMOS = 30;
    private static final String RESERVAS = "/api/reservas";
    private static final AtomicInteger SIGUIENTE_IP = new AtomicInteger(1);
    private static final AtomicInteger SIGUIENTE_CUENTA = new AtomicInteger(1);

    @Autowired
    private LiquidacionAdminService liquidaciones;

    @BeforeEach
    void prepararConcurrencia() {
        cambiarIp();
    }

    // Una IP por ronda: los inicios de sesión de cada ronda no se acumulan en el límite por IP.
    private void cambiarIp() {
        var n = SIGUIENTE_IP.getAndIncrement();
        ip = "10.22." + (200 + n / 250) + "." + (n % 250 + 1);
    }

    // ── Hilos ────────────────────────────────────────────────────────────

    private <T> List<T> aLaVez(List<Callable<T>> tareas) throws Exception {
        var salida = new CountDownLatch(1);
        var resultados = new ArrayList<T>();
        try (var hilos = Executors.newFixedThreadPool(tareas.size())) {
            var pendientes = tareas.stream().map(tarea -> hilos.submit(() -> {
                salida.await();
                return tarea.call();
            })).toList();
            salida.countDown();
            for (var pendiente : pendientes) {
                resultados.add(pendiente.get(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS));
            }
        }
        return resultados;
    }

    private static <T> Callable<T> despues(long milisegundos, Callable<T> tarea) {
        return () -> {
            Thread.sleep(milisegundos);
            return tarea.call();
        };
    }

    // ── Datos ────────────────────────────────────────────────────────────

    private record Cuenta(long id, String correo) {
    }

    private Cuenta nuevoArrendador() {
        var n = SIGUIENTE_CUENTA.getAndIncrement();
        var correo = "hu22.arrendador" + n + "@correo.pe";
        return new Cuenta(crearArrendador(correo, "20" + String.format("%09d", 500_000 + n)), correo);
    }

    private Cuenta nuevoUsuario() {
        var n = SIGUIENTE_CUENTA.getAndIncrement();
        var correo = "hu22.usuario" + n + "@correo.pe";
        return new Cuenta(crearUsuario(correo), correo);
    }

    /** Espacio publicado con franja de 6 a 22 el día de la semana de la fecha de reserva. */
    private long espacioConFranja(long arrendador) {
        var espacio = espacioDe(arrendador, "Cancha " + SIGUIENTE_CUENTA.getAndIncrement());
        jdbc.update("INSERT INTO horario_semanal (espacio_id, dia_semana, hora_inicio, hora_fin) VALUES (?, ?, 6, 22)",
                espacio, fechaReserva().getDayOfWeek().getValue());
        return espacio;
    }

    private LocalDate fechaReserva() {
        return hoy().plusDays(7);
    }

    // ── Llamadas ─────────────────────────────────────────────────────────

    private MvcResult eliminarCuenta(String sesion) throws Exception {
        return eliminar(sesion, CONTRASENA).andReturn();
    }

    private MvcResult reservar(String sesion, long espacio, int hora) throws Exception {
        var cuerpo = new LinkedHashMap<String, Object>();
        cuerpo.put("espacioId", espacio);
        cuerpo.put("fecha", fechaReserva().toString());
        cuerpo.put("horas", List.of(hora));
        return mvc.perform(con(sesion, post(RESERVAS)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(cuerpo))).andReturn();
    }

    private static int estado(MvcResult respuesta) {
        return respuesta.getResponse().getStatus();
    }

    @SuppressWarnings("unchecked")
    private List<String> camposDeError(MvcResult respuesta) throws Exception {
        var errores = (List<Map<String, Object>>) mapa(respuesta.getResponse().getContentAsString())
                .get("errores");
        return errores.stream().map(error -> (String) error.get("campo")).toList();
    }

    // ── Consultas ────────────────────────────────────────────────────────

    private String estadoCuenta(long id) {
        return (String) filaUsuario(id).get("estado");
    }

    private int reservasVigentesDelEspacio(long espacio) {
        return contar("SELECT count(*) FROM reserva WHERE espacio_id = ? AND estado IN ('PENDIENTE_PAGO', "
                + "'CONFIRMADA')", espacio);
    }

    private int reservasVigentesDelUsuario(long usuario) {
        return contar("SELECT count(*) FROM reserva WHERE usuario_id = ? AND estado IN ('PENDIENTE_PAGO', "
                + "'CONFIRMADA')", usuario);
    }

    private int liquidacionesPendientes(long arrendador) {
        return contar("SELECT count(*) FROM liquidacion WHERE arrendador_id = ? AND estado IN ('GENERADA', "
                + "'PENDIENTE_CUENTA')", arrendador);
    }

    private int eliminacionesAuditadas(long usuario) {
        return contar("SELECT count(*) FROM auditoria WHERE accion = 'CUENTA_ELIMINADA' AND usuario_id = ?", usuario);
    }

    // ── R43: eliminación del arrendador y reserva en su espacio ──────────

    /** O la reserva entra y la eliminación la ve (400 en reservas), o la eliminación confirma y la reserva da 404. */
    private void unaDeDosArrendador(Cuenta arrendador, long espacio, MvcResult eliminacion, MvcResult reserva)
            throws Exception {
        if (estado(reserva) == 201) {
            assertThat(estado(eliminacion)).isEqualTo(400);
            assertThat(camposDeError(eliminacion)).containsExactly("reservas");
            assertThat(estadoCuenta(arrendador.id())).isEqualTo("ACTIVA");
            assertThat(reservasVigentesDelEspacio(espacio)).isEqualTo(1);
            assertThat(eliminacionesAuditadas(arrendador.id())).isZero();
        } else {
            assertThat(estado(eliminacion)).isEqualTo(200);
            assertThat(estado(reserva)).isEqualTo(404);
            assertThat(estadoCuenta(arrendador.id())).isEqualTo("ELIMINADA");
            assertThat(reservasVigentesDelEspacio(espacio)).isZero();
            assertThat(eliminacionesAuditadas(arrendador.id())).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("HU-22 R43: el arrendador elimina su cuenta mientras reservan en su espacio: o reserva 201 y "
            + "eliminación 400 en reservas, o eliminación 200 y reserva 404; nunca ELIMINADA con una reserva vigente")
    void arrendadorEliminaMientrasReservan() throws Exception {
        for (var ronda = 0; ronda < RONDAS; ronda++) {
            cambiarIp();
            var arrendador = nuevoArrendador();
            var espacio = espacioConFranja(arrendador.id());
            var sesionArrendador = token(arrendador.correo());
            var sesionUsuario = token(nuevoUsuario().correo());

            var respuestas = aLaVez(List.<Callable<MvcResult>>of(
                    () -> eliminarCuenta(sesionArrendador),
                    despues(ronda * PASO_MS, () -> reservar(sesionUsuario, espacio, 10))));

            unaDeDosArrendador(arrendador, espacio, respuestas.get(0), respuestas.get(1));
        }
    }

    @Test
    @DisplayName("HU-22 R43: si la reserva entra primero, la eliminación del arrendador se rechaza; si la eliminación "
            + "entra primero, la reserva da 404")
    void arrendadorLosDosOrdenes() throws Exception {
        var primero = nuevoArrendador();
        var espacioPrimero = espacioConFranja(primero.id());
        var usuario = token(nuevoUsuario().correo());
        var reserva = reservar(usuario, espacioPrimero, 10);
        var eliminacion = eliminarCuenta(token(primero.correo()));
        assertThat(estado(reserva)).isEqualTo(201);
        unaDeDosArrendador(primero, espacioPrimero, eliminacion, reserva);

        var segundo = nuevoArrendador();
        var espacioSegundo = espacioConFranja(segundo.id());
        eliminacion = eliminarCuenta(token(segundo.correo()));
        reserva = reservar(usuario, espacioSegundo, 10);
        assertThat(estado(eliminacion)).isEqualTo(200);
        unaDeDosArrendador(segundo, espacioSegundo, eliminacion, reserva);
    }

    // ── R44: eliminación del arrendador y generación de liquidaciones ────

    @Test
    @DisplayName("HU-22 R44: el arrendador con un cobro por liquidar elimina su cuenta mientras se generan las "
            + "liquidaciones: la eliminación siempre da 400 (liquidaciones o pagos) y nunca queda ELIMINADA con una "
            + "liquidación pendiente")
    void arrendadorEliminaMientrasSeLiquida() throws Exception {
        var admin = new UsuarioAutenticado(crearAdmin(), null, "ADMIN");
        for (var ronda = 0; ronda < RONDAS; ronda++) {
            cambiarIp();
            var arrendador = nuevoArrendador();
            cuentaBancaria(arrendador.id());
            var espacio = espacioDe(arrendador.id(), "Cancha liquidable " + ronda);
            var usuario = nuevoUsuario();
            pago(reserva(usuario.id(), espacio, "CONFIRMADA", hoy().minusDays(3), 8), "PAGADO");
            var sesion = token(arrendador.correo());

            var eliminacion = new MvcResult[1];
            var generadas = aLaVez(List.<Callable<Integer>>of(
                    () -> {
                        eliminacion[0] = eliminarCuenta(sesion);
                        return -1;
                    },
                    despues(ronda * PASO_MS, () -> liquidaciones.generar(admin, IP_ADMIN).liquidaciones().size())));

            assertThat(generadas.get(1)).isEqualTo(1);
            assertThat(estado(eliminacion[0])).isEqualTo(400);
            assertThat(camposDeError(eliminacion[0])).hasSize(1).first().isIn("liquidaciones", "pagos");
            assertThat(estadoCuenta(arrendador.id())).isEqualTo("ACTIVA");
            assertThat(contar("SELECT count(*) FROM cuenta_bancaria WHERE arrendador_id = ?", arrendador.id()))
                    .isEqualTo(1);
            assertThat(liquidacionesPendientes(arrendador.id())).isEqualTo(1);
            assertThat(eliminacionesAuditadas(arrendador.id())).isZero();
        }
    }

    @Test
    @DisplayName("HU-22 R44: si la generación entra primero, la eliminación ve la liquidación; si la eliminación entra "
            + "primero, ve el cobro por liquidar")
    void liquidacionLosDosOrdenes() throws Exception {
        var admin = new UsuarioAutenticado(crearAdmin(), null, "ADMIN");
        var arrendador = nuevoArrendador();
        var espacio = espacioDe(arrendador.id(), "Cancha liquidable");
        var usuario = nuevoUsuario();
        pago(reserva(usuario.id(), espacio, "CONFIRMADA", hoy().minusDays(3), 8), "PAGADO");
        var sesion = token(arrendador.correo());

        var eliminacion = eliminarCuenta(sesion);
        assertThat(estado(eliminacion)).isEqualTo(400);
        assertThat(camposDeError(eliminacion)).containsExactly("pagos");

        assertThat(liquidaciones.generar(admin, IP_ADMIN).liquidaciones()).hasSize(1);
        eliminacion = eliminarCuenta(sesion);
        assertThat(estado(eliminacion)).isEqualTo(400);
        assertThat(camposDeError(eliminacion)).containsExactly("liquidaciones");
        assertThat(estadoCuenta(arrendador.id())).isEqualTo("ACTIVA");
    }

    // ── R42: eliminación del usuario y su propia reserva; dos eliminaciones ──

    /** O la reserva entra y la eliminación la ve, o la eliminación confirma y la sesión ya no vale (401). */
    private void unaDeDosUsuario(Cuenta usuario, MvcResult eliminacion, MvcResult reserva) throws Exception {
        if (estado(reserva) == 201) {
            assertThat(estado(eliminacion)).isEqualTo(400);
            assertThat(camposDeError(eliminacion)).containsExactly("reservas");
            assertThat(estadoCuenta(usuario.id())).isEqualTo("ACTIVA");
            assertThat(reservasVigentesDelUsuario(usuario.id())).isEqualTo(1);
            assertThat(eliminacionesAuditadas(usuario.id())).isZero();
        } else {
            assertThat(estado(eliminacion)).isEqualTo(200);
            assertThat(estado(reserva)).isEqualTo(401);
            assertThat(estadoCuenta(usuario.id())).isEqualTo("ELIMINADA");
            assertThat(reservasVigentesDelUsuario(usuario.id())).isZero();
            assertThat(eliminacionesAuditadas(usuario.id())).isEqualTo(1);
        }
    }

    // La carrera "el usuario elimina su cuenta mientras crea una reserva" no está aquí a propósito: una reserva cuya
    // sesión se validó antes de que la eliminación confirme se crea igual (ReservaServiceImpl no relee el estado tras
    // bloquear la cuenta). Riesgo aceptado por el dueño (2026-11-05): ver specs/HU-22/design.md § 15.1.

    @Test
    @DisplayName("HU-22 R42: si la reserva entra primero, la eliminación del usuario se rechaza; si la eliminación "
            + "entra primero, la reserva da 401")
    void usuarioLosDosOrdenes() throws Exception {
        var arrendador = nuevoArrendador();
        var espacio = espacioConFranja(arrendador.id());

        var primero = nuevoUsuario();
        var sesionPrimero = token(primero.correo());
        var reserva = reservar(sesionPrimero, espacio, 10);
        var eliminacion = eliminarCuenta(sesionPrimero);
        assertThat(estado(reserva)).isEqualTo(201);
        unaDeDosUsuario(primero, eliminacion, reserva);

        var segundo = nuevoUsuario();
        var sesionSegundo = token(segundo.correo());
        eliminacion = eliminarCuenta(sesionSegundo);
        reserva = reservar(sesionSegundo, espacio, 12);
        assertThat(estado(eliminacion)).isEqualTo(200);
        unaDeDosUsuario(segundo, eliminacion, reserva);
    }

    @Test
    @DisplayName("HU-22 R42: dos eliminaciones de la misma cuenta a la vez: una sola da 200 y queda una sola fila "
            + "CUENTA_ELIMINADA")
    void dosEliminacionesALaVez() throws Exception {
        for (var ronda = 0; ronda < RONDAS; ronda++) {
            cambiarIp();
            var usuario = nuevoUsuario();
            var sesionA = token(usuario.correo());
            var sesionB = token(usuario.correo());

            var respuestas = aLaVez(List.<Callable<MvcResult>>of(
                    () -> eliminarCuenta(sesionA),
                    despues(ronda * PASO_MS / 2, () -> eliminarCuenta(sesionB))));

            var estados = respuestas.stream().map(DatosPersonalesConcurrenciaIT::estado).toList();
            assertThat(estados).filteredOn(codigo -> codigo == 200).hasSize(1);
            // La perdedora espera el bloqueo de la fila y la encuentra ELIMINADA (design § 6.2, paso 1: 500), o ya
            // no pasa la contraseña (400) o la sesión (401). Nunca anonimiza dos veces.
            assertThat(estados).filteredOn(codigo -> codigo != 200).hasSize(1).first()
                    .isIn(400, 401, 500);
            assertThat(estadoCuenta(usuario.id())).isEqualTo("ELIMINADA");
            assertThat(eliminacionesAuditadas(usuario.id())).isEqualTo(1);
        }
    }
}
