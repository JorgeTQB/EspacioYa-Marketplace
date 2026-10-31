package pe.edu.ulima.espacioya.pagos.service.pasarela;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Doble HTTP de la API de Mercado Pago, en un puerto libre de 127.0.0.1. Devuelve lo que el test le indique y guarda lo
 * que recibió, para probar el adaptador real sin salir a internet. Las URL e identificadores son evidentemente falsos.
 */
public class StubMercadoPago implements AutoCloseable {

    public static final String URL_PAGO = "https://pago.invalid/checkout/";
    public static final String URL_SANDBOX = "https://sandbox.invalid/checkout/";

    private final HttpServer servidor;
    private final ExecutorService hilos = Executors.newCachedThreadPool();
    private final List<Solicitud> solicitudes = new CopyOnWriteArrayList<>();
    private final Map<String, String> pagos = new ConcurrentHashMap<>();
    private final Map<String, Integer> estadosDePago = new ConcurrentHashMap<>();
    private final AtomicInteger siguientePreferencia = new AtomicInteger(1);
    private volatile int estadoPreferencia = 200;
    private volatile int estadoCierre = 200;
    private volatile String cuerpoPreferencia;
    private volatile Duration demora = Duration.ZERO;

    public record Solicitud(String metodo, String ruta, String autorizacion, String cuerpo) {
    }

    public StubMercadoPago() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.setExecutor(hilos);
        servidor.createContext("/", this::atender);
        servidor.start();
    }

    public String url() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    public List<Solicitud> solicitudes() {
        return solicitudes;
    }

    public List<Solicitud> solicitudes(String metodo) {
        return solicitudes.stream().filter(solicitud -> solicitud.metodo().equals(metodo)).toList();
    }

    public void reiniciar() {
        solicitudes.clear();
        pagos.clear();
        estadosDePago.clear();
        siguientePreferencia.set(1);
        estadoPreferencia = 200;
        estadoCierre = 200;
        cuerpoPreferencia = null;
        demora = Duration.ZERO;
    }

    public void pago(String id, String cuerpoJson) {
        pagos.put(id, cuerpoJson);
    }

    public void pago(String id, String estado, String monto, String moneda, String referencia, String ultimos4) {
        var ref = referencia == null ? "null" : "\"" + referencia + "\"";
        var tarjeta = ultimos4 == null ? "{}" : "{\"last_four_digits\":\"" + ultimos4 + "\"}";
        pagos.put(id, """
                {"id":%s,"status":"%s","transaction_amount":%s,"currency_id":"%s","external_reference":%s,
                 "live_mode":true,"card":%s}""".formatted(id, estado, monto, moneda, ref, tarjeta));
    }

    public void estadoDeConsulta(String id, int estado) {
        estadosDePago.put(id, estado);
    }

    public void estadoPreferencia(int estado) {
        estadoPreferencia = estado;
    }

    public void cuerpoPreferencia(String cuerpo) {
        cuerpoPreferencia = cuerpo;
    }

    public void estadoCierre(int estado) {
        estadoCierre = estado;
    }

    public void demora(Duration valor) {
        demora = valor;
    }

    @Override
    public void close() {
        servidor.stop(0);
        hilos.shutdownNow();
    }

    private void atender(HttpExchange intercambio) throws IOException {
        try (intercambio) {
            var cuerpo = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            var metodo = intercambio.getRequestMethod();
            var ruta = intercambio.getRequestURI().getPath();
            solicitudes.add(new Solicitud(metodo, ruta,
                    intercambio.getRequestHeaders().getFirst("Authorization"), cuerpo));
            esperar();
            if ("POST".equals(metodo) && ruta.equals("/checkout/preferences")) {
                responder(intercambio, estadoPreferencia, preferencia());
            } else if ("PUT".equals(metodo) && ruta.startsWith("/checkout/preferences/")) {
                responder(intercambio, estadoCierre, "{}");
            } else if ("GET".equals(metodo) && ruta.startsWith("/v1/payments/")) {
                consultar(intercambio, ruta.substring("/v1/payments/".length()));
            } else {
                responder(intercambio, 404, "{}");
            }
        }
    }

    private String preferencia() {
        if (cuerpoPreferencia != null) {
            return cuerpoPreferencia;
        }
        var id = "pref-" + siguientePreferencia.getAndIncrement();
        return "{\"id\":\"%s\",\"init_point\":\"%s%s\",\"sandbox_init_point\":\"%s%s\"}"
                .formatted(id, URL_PAGO, id, URL_SANDBOX, id);
    }

    private void consultar(HttpExchange intercambio, String id) throws IOException {
        var estado = estadosDePago.getOrDefault(id, pagos.containsKey(id) ? 200 : 404);
        responder(intercambio, estado, pagos.getOrDefault(id, "{}"));
    }

    private void esperar() {
        try {
            Thread.sleep(demora);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void responder(HttpExchange intercambio, int estado, String cuerpo) throws IOException {
        var bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().set("Content-Type", "application/json");
        intercambio.sendResponseHeaders(estado, bytes.length);
        intercambio.getResponseBody().write(bytes);
    }
}
