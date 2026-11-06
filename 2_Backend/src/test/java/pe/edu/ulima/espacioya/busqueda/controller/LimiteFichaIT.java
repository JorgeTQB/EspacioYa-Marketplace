package pe.edu.ulima.espacioya.busqueda.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import pe.edu.ulima.espacioya.RelojControlable;
import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.TipoDocumento;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;

/**
 * Cupo del catálogo (bajado a 3 por minuto) compartido por la búsqueda, la ficha, su disponibilidad y sus reseñas.
 * Usa las mismas propiedades y dobles que {@link LimiteCatalogoIT} para compartir contexto, pero su propio rango de IP
 * (10.13.x.x).
 */
@SpringBootTest(properties = {"espacioya.limite.catalogo.maximo=3", "espacioya.limite.catalogo.ventana=PT1M"})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, LimiteCatalogoIT.Dobles.class})
class LimiteFichaIT {

    private static final String DEMASIADOS_INTENTOS =
            "Hiciste demasiados intentos. Espera un momento e inténtalo de nuevo.";
    // El reloj de LimiteCatalogoIT.Dobles empieza el miércoles 2026-10-28 a las 10:00 de Lima.
    private static final LocalDate LUNES = LocalDate.of(2026, 11, 2);
    private static final String BUSQUEDA = "/api/espacios?tipo=CANCHA&zona=LOS_OLIVOS&fecha=" + LUNES;
    private static final AtomicInteger SIGUIENTE_IP = new AtomicInteger(1);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RelojControlable reloj;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private PerfilArrendadorRepository perfiles;

    private String ip;
    private long espacio;

    @BeforeEach
    void preparar() {
        jdbc.update("DELETE FROM suspension_cuenta");
        jdbc.update("DELETE FROM foto_espacio");
        jdbc.update("DELETE FROM espacio");
        jdbc.update("DELETE FROM documento_identidad");
        jdbc.update("DELETE FROM sesion");
        jdbc.update("DELETE FROM auditoria");
        jdbc.update("DELETE FROM perfil_arrendador");
        jdbc.update("DELETE FROM usuario");
        ip = siguienteIp();
        var arrendador = usuarios.save(Usuario.registrarArrendador("arrendador.limite@correo.pe", "x", "Ana Pérez",
                "987654321", "2026-10", reloj.instant())).getId();
        perfiles.save(PerfilArrendador.crear(arrendador, TipoDocumento.RUC, "20100070970", String.format("%064d", 1),
                "Canchas del Sur SAC", "Av. Javier Prado 4600"));
        espacio = jdbc.queryForObject("INSERT INTO espacio (arrendador_id, nombre, tipo, direccion, zona, capacidad, "
                        + "precio_hora, descripcion, estado, creado_en, actualizado_en) VALUES (?, 'Cancha', 'CANCHA', "
                        + "'Av. Universitaria 1234', 'LOS_OLIVOS', 20, ?, 'Cancha de grass sintético.', 'PUBLICADO', "
                        + "now(), now()) RETURNING id", Long.class, arrendador, new BigDecimal("80.00"));
    }

    // HU-10 usa 10.10.x.x y 10.11.x.x; FichaControllerIT, 10.12.x.x.
    private static String siguienteIp() {
        var n = SIGUIENTE_IP.getAndIncrement();
        return "10.13." + (n / 250) + "." + (n % 250 + 1);
    }

    private ResultActions pedir(String url) throws Exception {
        var origen = ip;
        return mvc.perform(get(URI.create(url)).with(peticion -> {
            peticion.setRemoteAddr(origen);
            return peticion;
        }));
    }

    private ResultActions ficha() throws Exception {
        return pedir("/api/espacios/" + espacio);
    }

    private ResultActions disponibilidad() throws Exception {
        return pedir("/api/espacios/" + espacio + "/disponibilidad?fecha=" + LUNES);
    }

    private ResultActions resenas() throws Exception {
        return pedir("/api/espacios/" + espacio + "/resenas");
    }

    private void esDemasiadosIntentos(ResultActions respuesta) throws Exception {
        respuesta.andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.mensaje").value(DEMASIADOS_INTENTOS))
                .andExpect(jsonPath("$.errores").isEmpty());
    }

    @Test
    @DisplayName("HU-11 R26: con el cupo agotado, la ficha de un espacio visible responde 429 sin llegar al controlador")
    void fichaConCupoAgotado() throws Exception {
        for (int i = 0; i < 3; i++) {
            ficha().andExpect(status().isOk());
        }

        esDemasiadosIntentos(ficha());
        ficha().andExpect(jsonPath("$.nombre").doesNotExist()).andExpect(jsonPath("$.id").doesNotExist());
    }

    @Test
    @DisplayName("HU-11 R27: con el cupo agotado, la disponibilidad responde 429 sin calcularla")
    void disponibilidadConCupoAgotado() throws Exception {
        for (int i = 0; i < 3; i++) {
            disponibilidad().andExpect(status().isOk());
        }

        esDemasiadosIntentos(disponibilidad());
        disponibilidad().andExpect(jsonPath("$.bloquesLibres").doesNotExist());
    }

    @Test
    @DisplayName("HU-11 R28: una búsqueda, una ficha y una disponibilidad agotan el mismo cupo de 3")
    void cupoCompartidoConLaBusqueda() throws Exception {
        pedir(BUSQUEDA).andExpect(status().isOk());
        ficha().andExpect(status().isOk());
        disponibilidad().andExpect(status().isOk());

        esDemasiadosIntentos(pedir(BUSQUEDA));
        esDemasiadosIntentos(ficha());
        esDemasiadosIntentos(disponibilidad());
    }

    @Test
    @DisplayName("HU-11 R28: tres fichas agotan también el cupo de la búsqueda")
    void fichasAgotanLaBusqueda() throws Exception {
        for (int i = 0; i < 3; i++) {
            ficha().andExpect(status().isOk());
        }

        esDemasiadosIntentos(pedir(BUSQUEDA));
    }

    @Test
    @DisplayName("HU-11 R29: las fichas con 404 cuentan en el cupo")
    void noEncontradasCuentan() throws Exception {
        pedir("/api/espacios/abc").andExpect(status().isNotFound());
        pedir("/api/espacios/" + (espacio + 1000)).andExpect(status().isNotFound());
        pedir("/api/espacios/abc/disponibilidad?fecha=" + LUNES).andExpect(status().isNotFound());

        esDemasiadosIntentos(ficha());
    }

    @Test
    @DisplayName("HU-11 R29: las disponibilidades con 400 cuentan en el cupo")
    void invalidasCuentan() throws Exception {
        pedir("/api/espacios/" + espacio + "/disponibilidad?fecha=2026-02-30").andExpect(status().isBadRequest());
        pedir("/api/espacios/" + espacio + "/disponibilidad").andExpect(status().isBadRequest());
        pedir("/api/espacios/abc").andExpect(status().isNotFound());

        esDemasiadosIntentos(disponibilidad());
    }

    @Test
    @DisplayName("HU-24 R37: una búsqueda, una ficha y una lista de reseñas agotan el mismo cupo de 3")
    void resenasCompartenElCupo() throws Exception {
        pedir(BUSQUEDA).andExpect(status().isOk());
        ficha().andExpect(status().isOk());
        resenas().andExpect(status().isOk());

        esDemasiadosIntentos(pedir(BUSQUEDA));
        esDemasiadosIntentos(ficha());
        esDemasiadosIntentos(resenas());
    }

    @Test
    @DisplayName("HU-24 R37: las listas de reseñas con 400 y 404 cuentan en el cupo")
    void resenasInvalidasCuentan() throws Exception {
        pedir("/api/espacios/" + espacio + "/resenas?pagina=0").andExpect(status().isBadRequest());
        pedir("/api/espacios/abc/resenas").andExpect(status().isNotFound());
        pedir("/api/espacios/" + (espacio + 1000) + "/resenas").andExpect(status().isNotFound());

        esDemasiadosIntentos(ficha());
    }

    @Test
    @DisplayName("HU-24 R38: con el cupo agotado, las reseñas responden 429 sin ejecutar la consulta")
    void resenasConCupoAgotado() throws Exception {
        for (int i = 0; i < 3; i++) {
            resenas().andExpect(status().isOk());
        }

        esDemasiadosIntentos(resenas());
        resenas().andExpect(jsonPath("$.resenas").doesNotExist()).andExpect(jsonPath("$.hayMas").doesNotExist());
    }
}
