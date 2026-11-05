package pe.edu.ulima.espacioya.espacios.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import pe.edu.ulima.espacioya.MfaDePrueba;
import pe.edu.ulima.espacioya.comun.cifrado.CifradorCampos;
import pe.edu.ulima.espacioya.comun.configuracion.ZonaHoraria;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.espacios.service.puertos.BloqueReservado;

/**
 * HU-21: pausa y baja de cualquier espacio por el admin (decisión 5). El admin entra con MFA desde una IP permitida
 * ({@link #IP_ADMIN}); los demás, desde 10.21.x.x.
 */
class EspacioAdminControllerIT extends EspaciosControllerSoporte {

    private static final String IP_ADMIN = "127.0.0.1";
    private static final String CORREO_ADMIN = "admin@espacioya.pe";
    private static final String ADMIN_ESPACIOS = "/api/admin/espacios/";
    private static final String SOLO_PUBLICADO = "Solo puedes pausar un espacio publicado.";
    private static final String BAJA_NO_PERMITIDA = "Solo puedes dar de baja un espacio publicado o pausado.";
    private static final String RESERVAS_POR_TERMINAR_ADMIN = "Este espacio tiene reservas por terminar: el "
            + "arrendador debe cancelarlas antes de darlo de baja.";
    private static final AtomicInteger SIGUIENTE_IP = new AtomicInteger(1);

    @Autowired
    CifradorCampos cifrador;

    private Usuario ana;
    private long espacioId;

    @BeforeEach
    void preparar() throws Exception {
        var n = SIGUIENTE_IP.getAndIncrement();
        ip = "10.21." + (100 + n / 250) + "." + (n % 250 + 1);
        ana = crearArrendador(CORREO);
        var tokenAna = token(CORREO);
        espacioId = crearEspacio(tokenAna);
        forzarEstado(espacioId, "PUBLICADO");
        reloj.avanzar(Duration.ofMinutes(5));
    }

    private String tokenAdmin() throws Exception {
        var admin = usuarios.save(Usuario.registrarAdmin(CORREO_ADMIN,
                encoder.encode(CONTRASENA), "2026-10", reloj.instant()));
        MfaDePrueba.activar(jdbc, cifrador, admin.getId(), reloj.instant());
        return MfaDePrueba.iniciarSesion(mvc, jdbc, CORREO_ADMIN, CONTRASENA, IP_ADMIN, reloj.instant());
    }

    private long adminId() {
        return usuarios.findByCorreo(CORREO_ADMIN).orElseThrow().getId();
    }

    private ResultActions accion(String token, String origen, long id, String ruta) throws Exception {
        return mvc.perform(post(ADMIN_ESPACIOS + id + "/" + ruta)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .with(peticion -> {
                    peticion.setRemoteAddr(origen);
                    return peticion;
                }));
    }

    private ResultActions pausar(String token, long id) throws Exception {
        return accion(token, IP_ADMIN, id, "pausa");
    }

    private ResultActions darDeBaja(String token, long id) throws Exception {
        return accion(token, IP_ADMIN, id, "baja");
    }

    private int auditoriasDeEstado() {
        return jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE accion IN ('PAUSA_ESPACIO', 'BAJA_ESPACIO')",
                Integer.class);
    }

    private static void error(ResultActions respuesta, String mensaje) throws Exception {
        respuesta.andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje").value(mensaje))
                .andExpect(jsonPath("$.errores.length()").value(0));
    }

    @Test
    @DisplayName("HU-21 R28, R37: el admin pausa el PUBLICADO de otro arrendador, recibe id y estado y audita el actor")
    void adminPausa() throws Exception {
        var admin = tokenAdmin();

        var cuerpo = pausar(admin, espacioId).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(espacioId))
                .andExpect(jsonPath("$.estado").value("PAUSADO"))
                .andReturn().getResponse().getContentAsString();

        assertThat(mapa(cuerpo)).containsOnlyKeys("id", "estado");
        assertThat(espacio(espacioId).get("estado")).isEqualTo("PAUSADO");
        assertThat(instante(espacio(espacioId).get("actualizado_en"))).isEqualTo(reloj.instant());
        var auditadas = auditorias("PAUSA_ESPACIO");
        assertThat(auditadas).hasSize(1);
        Map<String, Object> fila = auditadas.getFirst();
        assertThat(fila.get("usuario_id")).isEqualTo(adminId());
        assertThat(fila.get("entidad")).isEqualTo("espacio");
        assertThat(((Number) fila.get("entidad_id")).longValue()).isEqualTo(espacioId);
        assertThat(fila.get("ip")).isEqualTo(IP_ADMIN);
        assertThat(fila.get("detalle"))
                .isEqualTo("estado=PUBLICADO->PAUSADO;actor=ADMIN;arrendadorId=" + ana.getId());
    }

    @Test
    @DisplayName("HU-21 R29, R37: el admin da de baja un PAUSADO sin reservas por terminar y audita el actor")
    void adminDaDeBaja() throws Exception {
        forzarEstado(espacioId, "PAUSADO");
        var admin = tokenAdmin();

        darDeBaja(admin, espacioId).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(espacioId))
                .andExpect(jsonPath("$.estado").value("BAJA"));

        assertThat(espacio(espacioId).get("estado")).isEqualTo("BAJA");
        var auditadas = auditorias("BAJA_ESPACIO");
        assertThat(auditadas).hasSize(1);
        assertThat(auditadas.getFirst().get("detalle"))
                .isEqualTo("estado=PAUSADO->BAJA;actor=ADMIN;arrendadorId=" + ana.getId());
    }

    @Test
    @DisplayName("HU-21 R30: la baja del admin con reservas por terminar da 400 en reservas sin cambiar el espacio")
    void adminBajaConReservas() throws Exception {
        reservasFalsas.cargar(new BloqueReservado(ZonaHoraria.hoy(reloj).plusDays(2), 9, true));
        var admin = tokenAdmin();
        var antes = espacio(espacioId).get("actualizado_en");

        darDeBaja(admin, espacioId).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_VALIDACION))
                .andExpect(jsonPath("$.errores.length()").value(1))
                .andExpect(jsonPath("$.errores[0].campo").value("reservas"))
                .andExpect(jsonPath("$.errores[0].mensaje").value(RESERVAS_POR_TERMINAR_ADMIN));

        assertThat(espacio(espacioId).get("estado")).isEqualTo("PUBLICADO");
        assertThat(espacio(espacioId).get("actualizado_en")).isEqualTo(antes);
        assertThat(auditoriasDeEstado()).isZero();
    }

    @Test
    @DisplayName("HU-21 R6, R16: el admin no puede pausar un PAUSADO ni dar de baja un BORRADOR o un BAJA")
    void adminEstadoInvalido() throws Exception {
        var admin = tokenAdmin();

        forzarEstado(espacioId, "PAUSADO");
        error(pausar(admin, espacioId), SOLO_PUBLICADO);
        forzarEstado(espacioId, "BORRADOR");
        error(darDeBaja(admin, espacioId), BAJA_NO_PERMITIDA);
        forzarEstado(espacioId, "BAJA");
        error(darDeBaja(admin, espacioId), BAJA_NO_PERMITIDA);

        assertThat(auditoriasDeEstado()).isZero();
    }

    @Test
    @DisplayName("HU-21 R31: un espacio inexistente en las rutas del admin da 404 genérico")
    void adminInexistente() throws Exception {
        var admin = tokenAdmin();

        for (var respuesta : List.of(pausar(admin, espacioId + 1000), darDeBaja(admin, espacioId + 1000))) {
            respuesta.andExpect(status().isNotFound()).andExpect(jsonPath("$.mensaje").value(NO_ENCONTRADO));
        }
    }

    @Test
    @DisplayName("HU-21 R32: un ARRENDADOR (aun el dueño) o un USUARIO reciben 403 en las rutas del admin")
    void sinRolAdmin() throws Exception {
        crearUsuario(CORREO_USUARIO);
        var sesiones = List.of(token(CORREO), token(CORREO_USUARIO));

        for (var sesion : sesiones) {
            for (var respuesta : List.of(pausar(sesion, espacioId), darDeBaja(sesion, espacioId))) {
                respuesta.andExpect(status().isForbidden()).andExpect(jsonPath("$.mensaje").value(ACCESO_DENEGADO));
            }
        }
        assertThat(espacio(espacioId).get("estado")).isEqualTo("PUBLICADO");
        assertThat(auditoriasDeEstado()).isZero();
    }

    @Test
    @DisplayName("HU-21 R33: el admin desde una IP no permitida recibe 403 y el espacio no cambia")
    void ipNoPermitida() throws Exception {
        var admin = tokenAdmin();

        for (var ruta : List.of("pausa", "baja")) {
            accion(admin, ip, espacioId, ruta).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.mensaje").value(ACCESO_DENEGADO));
        }
        assertThat(espacio(espacioId).get("estado")).isEqualTo("PUBLICADO");
        assertThat(auditoriasDeEstado()).isZero();
    }
}
