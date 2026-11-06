package pe.edu.ulima.espacioya.busqueda.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import pe.edu.ulima.espacioya.RelojControlable;
import pe.edu.ulima.espacioya.TestcontainersConfiguration;

/** Cupo del catálogo con la cifra bajada a 3 por minuto para probarlo sin cientos de solicitudes. */
@SpringBootTest(properties = {"espacioya.limite.catalogo.maximo=3", "espacioya.limite.catalogo.ventana=PT1M"})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, LimiteCatalogoIT.Dobles.class})
class LimiteCatalogoIT {

    private static final String DEMASIADOS_INTENTOS =
            "Hiciste demasiados intentos. Espera un momento e inténtalo de nuevo.";
    // Miércoles 2026-10-28 a las 10:00 de Lima: el lunes 2026-11-02 está en la ventana.
    private static final String VALIDA = "/api/espacios?tipo=CANCHA&zona=MIRAFLORES&fecha=" + LocalDate.of(2026, 11, 2);
    private static final String INVALIDA = "/api/espacios?tipo=PISCINA&zona=MIRAFLORES&fecha=2026-11-02";
    private static final String CON_FILTROS = VALIDA
            + "&precioMaximo=80&capacidadMinima=10&caracteristica=TECHADO&caracteristica=VESTUARIOS";
    private static final String FILTRO_INVALIDO = VALIDA + "&precioMaximo=0&caracteristica=PISCINA";
    private static final AtomicInteger SIGUIENTE_IP = new AtomicInteger(1);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private RelojControlable reloj;

    private String ip;

    @TestConfiguration(proxyBeanMethods = false)
    static class Dobles {

        @Bean
        @Primary
        RelojControlable relojControlable() {
            return new RelojControlable(Instant.parse("2026-10-28T15:00:00Z"));
        }
    }

    @BeforeEach
    void preparar() {
        ip = siguienteIp();
    }

    // HU-10 BusquedaControllerIT usa 10.10.x.x.
    private static String siguienteIp() {
        var n = SIGUIENTE_IP.getAndIncrement();
        return "10.11." + (n / 250) + "." + (n % 250 + 1);
    }

    private ResultActions desde(String origen, MockHttpServletRequestBuilder solicitud) throws Exception {
        return mvc.perform(solicitud.with(peticion -> {
            peticion.setRemoteAddr(origen);
            return peticion;
        }));
    }

    private ResultActions buscar(String url) throws Exception {
        return desde(ip, get(URI.create(url)));
    }

    private void agotarCupo() throws Exception {
        for (int i = 0; i < 3; i++) {
            buscar(VALIDA).andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("HU-10 R33: con 3 búsquedas en el minuto, la 4.ª responde 429 con el mensaje genérico")
    void cuartaResponde429() throws Exception {
        agotarCupo();

        buscar(VALIDA).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.mensaje").value(DEMASIADOS_INTENTOS))
                .andExpect(jsonPath("$.errores").isEmpty())
                .andExpect(jsonPath("$.resultados").doesNotExist());
    }

    @Test
    @DisplayName("HU-10 R34: cuando la búsqueda más antigua sale de la ventana vuelve a aceptar")
    void vuelveAAceptarTrasLaVentana() throws Exception {
        agotarCupo();
        buscar(VALIDA).andExpect(status().isTooManyRequests());

        reloj.avanzar(Duration.ofSeconds(61));

        buscar(VALIDA).andExpect(status().isOk());
    }

    @Test
    @DisplayName("HU-10 R35: con el cupo de una IP agotado, otra IP sigue buscando")
    void otraIpSigue() throws Exception {
        agotarCupo();
        buscar(VALIDA).andExpect(status().isTooManyRequests());

        desde(siguienteIp(), get(URI.create(VALIDA))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("HU-10 R36: las búsquedas rechazadas con 429 no cuentan; al salir la más antigua entra exactamente una")
    void rechazadasNoCuentan() throws Exception {
        buscar(VALIDA).andExpect(status().isOk());
        reloj.avanzar(Duration.ofSeconds(20));
        buscar(VALIDA).andExpect(status().isOk());
        buscar(VALIDA).andExpect(status().isOk());
        reloj.avanzar(Duration.ofSeconds(10));
        buscar(VALIDA).andExpect(status().isTooManyRequests());
        buscar(VALIDA).andExpect(status().isTooManyRequests());

        reloj.avanzar(Duration.ofSeconds(31));

        buscar(VALIDA).andExpect(status().isOk());
        buscar(VALIDA).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("HU-10 R37: las búsquedas que terminan en 400 también cuentan en el cupo")
    void invalidasCuentan() throws Exception {
        for (int i = 0; i < 3; i++) {
            buscar(INVALIDA).andExpect(status().isBadRequest());
        }

        buscar(VALIDA).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("HU-10 R38: con el cupo agotado, zonas y fotos responden como sin límite")
    void zonasYFotosSinLimite() throws Exception {
        agotarCupo();

        for (int i = 0; i < 5; i++) {
            desde(ip, get("/api/zonas")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(50));
            desde(ip, get("/api/fotos/" + UUID.randomUUID())).andExpect(status().isNotFound());
        }
    }

    @Test
    @DisplayName("HU-10 R38: zonas y fotos no descuentan del cupo del catálogo")
    void zonasYFotosNoConsumen() throws Exception {
        buscar(VALIDA).andExpect(status().isOk());
        buscar(VALIDA).andExpect(status().isOk());
        for (int i = 0; i < 5; i++) {
            desde(ip, get("/api/zonas")).andExpect(status().isOk());
            desde(ip, get("/api/fotos/" + UUID.randomUUID())).andExpect(status().isNotFound());
        }

        buscar(VALIDA).andExpect(status().isOk());
        buscar(VALIDA).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("HU-10 R44: la ruta con codificación porcentual /api/%65spacios cuenta en el mismo cupo")
    void rutaCodificadaCuenta() throws Exception {
        for (int i = 0; i < 3; i++) {
            buscar(VALIDA.replace("/api/espacios", "/api/%65spacios")).andExpect(status().isOk());
        }

        buscar(VALIDA).andExpect(status().isTooManyRequests());
        buscar(VALIDA.replace("/api/espacios", "/api/%65spacios")).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("HU-10 R47: el registro sigue limitado a 5 por IP por hora en su propio grupo")
    void registroSigueLimitado() throws Exception {
        agotarCupo();
        for (int i = 0; i < 5; i++) {
            desde(ip, post("/api/usuarios").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
        }

        desde(ip, post("/api/usuarios").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.mensaje").value(DEMASIADOS_INTENTOS));
        desde(ip, post("/api/arrendadores").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isTooManyRequests());
        desde(siguienteIp(), post("/api/usuarios").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("HU-23 R27: las búsquedas con filtros descuentan del mismo cupo del catálogo que las de HU-10")
    void filtrosDescuentanDelMismoCupo() throws Exception {
        buscar(CON_FILTROS).andExpect(status().isOk());
        buscar(VALIDA).andExpect(status().isOk());
        buscar(CON_FILTROS).andExpect(status().isOk());

        buscar(VALIDA).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("HU-23 R28: con el cupo agotado, una búsqueda con filtros responde 429 sin ejecutar la búsqueda")
    void filtrosCon429() throws Exception {
        agotarCupo();

        buscar(CON_FILTROS).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.mensaje").value(DEMASIADOS_INTENTOS))
                .andExpect(jsonPath("$.errores").isEmpty())
                .andExpect(jsonPath("$.resultados").doesNotExist());
        // Si la búsqueda se ejecutara, el filtro inválido respondería 400.
        buscar(FILTRO_INVALIDO).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.mensaje").value(DEMASIADOS_INTENTOS));
    }
}
