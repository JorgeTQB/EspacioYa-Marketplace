package pe.edu.ulima.espacioya.cuentas.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import pe.edu.ulima.espacioya.MfaDePrueba;
import pe.edu.ulima.espacioya.RelojControlable;
import pe.edu.ulima.espacioya.comun.cifrado.CifradorCampos;
import pe.edu.ulima.espacioya.comun.cifrado.HuellaCampos;
import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;
import pe.edu.ulima.espacioya.cuentas.domain.TipoDocumento;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.repository.PerfilArrendadorRepository;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;
import pe.edu.ulima.espacioya.cuentas.service.AvisoCambioCuentaBancaria;
import pe.edu.ulima.espacioya.cuentas.service.AvisoCancelacionArrendador;
import pe.edu.ulima.espacioya.cuentas.service.AvisoCancelacionUsuario;
import pe.edu.ulima.espacioya.cuentas.service.AvisoReservaPagada;
import pe.edu.ulima.espacioya.cuentas.service.ComprobantePago;
import pe.edu.ulima.espacioya.cuentas.service.NotificadorCorreo;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base de las IT de HU-20: PostgreSQL real, reloj controlable y un doble de {@link NotificadorCorreo} que guarda el
 * enlace de cambio de correo y el aviso al correo anterior. Cada test usa su propia IP (10.20.x.x) y adelanta el reloj
 * dos horas para que los contadores en memoria (fallos de contraseña, solicitudes de correo, login) no se arrastren.
 */
abstract class PerfilSoporte {

    static final String CONTRASENA = "Segura123!";
    static final String CORREO_USUARIO = "carla.perfil@correo.pe";
    static final String CORREO_ARRENDADOR = "ana.perfil@correo.pe";
    static final String CORREO_OTRO = "beto.perfil@correo.pe";
    static final String CORREO_ADMIN = "admin.perfil@correo.pe";
    static final String NOMBRE_ARRENDADOR = "Ana Perfilada";
    static final String TELEFONO_ARRENDADOR = "999111222";
    static final String RUC = "20100070970";
    static final String RAZON = "Inversiones Ocultas SAC";
    static final String DIRECCION = "Jr. Fiscal Escondido 777";
    static final String DEMASIADOS_INTENTOS = "Hiciste demasiados intentos. Espera un momento e inténtalo de nuevo.";
    static final String ACCESO_DENEGADO = "No tienes permiso para realizar esta acción.";
    static final String MENSAJE_VALIDACION = "Revisa los datos del formulario.";
    static final String IP_ADMIN = "127.0.0.1";

    private static final AtomicInteger SIGUIENTE_IP = new AtomicInteger(1);
    private static String hashContrasena;

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    RelojControlable reloj;
    @Autowired
    UsuarioRepository usuarios;
    @Autowired
    PerfilArrendadorRepository perfiles;
    @Autowired
    PasswordEncoder encoder;
    @Autowired
    JsonMapper json;
    @Autowired
    CifradorCampos cifrador;
    @Autowired
    HuellaCampos huella;
    @Autowired
    NotificadorFalso notificador;

    String ip;

    record CambioCorreo(String correo, String enlace) {
    }

    record AvisoCorreo(String correoActual, String nuevoEnmascarado) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Dobles {

        @Bean
        @Primary
        RelojControlable relojControlable() {
            return new RelojControlable(Instant.parse("2026-11-04T15:00:00Z"));
        }

        @Bean
        @Primary
        NotificadorFalso notificadorFalso() {
            return new NotificadorFalso();
        }
    }

    static class NotificadorFalso implements NotificadorCorreo {

        private final List<CambioCorreo> cambios = new CopyOnWriteArrayList<>();
        private final List<AvisoCorreo> avisos = new CopyOnWriteArrayList<>();
        private volatile boolean falla;

        @Override
        public void notificarVerificacion(String correoNormalizado) {
        }

        @Override
        public void notificarRecuperacion(String correoNormalizado, String enlace) {
        }

        @Override
        public void notificarIdentidadAprobada(String correoNormalizado) {
        }

        @Override
        public void notificarIdentidadRechazada(String correoNormalizado, String motivo) {
        }

        @Override
        public void notificarCuentaSuspendida(String correoNormalizado, String motivo) {
        }

        @Override
        public void notificarCuentaReactivada(String correoNormalizado) {
        }

        @Override
        public void notificarPagoConfirmado(String correoNormalizado, ComprobantePago comprobante) {
        }

        @Override
        public void notificarReservaPagada(String correoArrendador, AvisoReservaPagada aviso) {
        }

        @Override
        public void notificarCancelacionAlUsuario(String correoUsuario, AvisoCancelacionUsuario aviso) {
        }

        @Override
        public void notificarCancelacionAlArrendador(String correoArrendador, AvisoCancelacionArrendador aviso) {
        }

        @Override
        public void notificarCambioCuentaBancaria(String correoArrendador, AvisoCambioCuentaBancaria aviso) {
        }

        @Override
        public void notificarCambioCorreo(String correoNuevo, String enlace) {
            if (falla) {
                throw new IllegalStateException("el proveedor de correo falló");
            }
            cambios.add(new CambioCorreo(correoNuevo, enlace));
        }

        @Override
        public void notificarAvisoCambioCorreo(String correoActual, String correoNuevoEnmascarado) {
            if (falla) {
                throw new IllegalStateException("el proveedor de correo falló");
            }
            avisos.add(new AvisoCorreo(correoActual, correoNuevoEnmascarado));
        }

        List<CambioCorreo> cambios() {
            return cambios;
        }

        List<AvisoCorreo> avisos() {
            return avisos;
        }

        void fallar() {
            falla = true;
        }

        void reiniciar() {
            cambios.clear();
            avisos.clear();
            falla = false;
        }
    }

    @BeforeEach
    void prepararBase() {
        jdbc.update("DELETE FROM token");
        jdbc.update("DELETE FROM sesion");
        jdbc.update("DELETE FROM auditoria");
        jdbc.update("DELETE FROM documento_identidad");
        jdbc.update("DELETE FROM mfa");
        jdbc.update("DELETE FROM perfil_arrendador");
        jdbc.update("DELETE FROM usuario");
        notificador.reiniciar();
        reloj.avanzar(Duration.ofHours(2));
        ip = siguienteIp();
        if (hashContrasena == null) {
            hashContrasena = encoder.encode(CONTRASENA);
        }
    }

    private static String siguienteIp() {
        var n = SIGUIENTE_IP.getAndIncrement();
        return "10.20." + (n / 250) + "." + (n % 250 + 1);
    }

    // ── Datos ────────────────────────────────────────────────────────────

    long crearUsuario(String correo) {
        return usuarios.save(Usuario.registrar(correo, hashContrasena, "2026-10", reloj.instant())).getId();
    }

    long crearArrendador(String correo, String ruc) {
        var usuario = usuarios.save(Usuario.registrarArrendador(correo, hashContrasena, NOMBRE_ARRENDADOR,
                TELEFONO_ARRENDADOR, "2026-10", reloj.instant()));
        perfiles.save(PerfilArrendador.crear(usuario.getId(), TipoDocumento.RUC, ruc, huella.calcular(ruc), RAZON,
                DIRECCION));
        return usuario.getId();
    }

    long crearArrendador() {
        return crearArrendador(CORREO_ARRENDADOR, RUC);
    }

    // Con MFA activo (HU-17): sin él, el admin no tiene sesión.
    long crearAdmin() {
        var id = usuarios.save(Usuario.registrarAdmin(CORREO_ADMIN, hashContrasena, "2026-10", reloj.instant()))
                .getId();
        MfaDePrueba.activar(jdbc, cifrador, id, reloj.instant());
        return id;
    }

    void identidad(long arrendadorId, String estado) {
        jdbc.update("UPDATE perfil_arrendador SET estado_identidad = ? WHERE usuario_id = ?", estado, arrendadorId);
    }

    // ── Llamadas ─────────────────────────────────────────────────────────

    <T extends AbstractMockHttpServletRequestBuilder<T>> T desde(T solicitud, String origen) {
        solicitud.with(peticion -> {
            peticion.setRemoteAddr(origen);
            return peticion;
        });
        return solicitud;
    }

    <T extends AbstractMockHttpServletRequestBuilder<T>> T con(String token, T solicitud) {
        if (token != null) {
            solicitud.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return desde(solicitud, ip);
    }

    String token(String correo) throws Exception {
        if (CORREO_ADMIN.equals(correo)) {
            return MfaDePrueba.iniciarSesion(mvc, jdbc, correo, CONTRASENA, IP_ADMIN, reloj.instant());
        }
        var cuerpo = iniciarSesion(correo, CONTRASENA).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return (String) mapa(cuerpo).get("token");
    }

    ResultActions iniciarSesion(String correo, String contrasena) throws Exception {
        return mvc.perform(desde(post("/api/sesiones"), ip).contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"" + correo + "\",\"contrasena\":\"" + contrasena + "\"}"));
    }

    Map<String, Object> mapa(String cuerpo) {
        return json.readValue(cuerpo, new TypeReference<Map<String, Object>>() { });
    }

    String texto(ResultActions respuesta) throws Exception {
        return respuesta.andReturn().getResponse().getContentAsString();
    }

    // ── Consultas ────────────────────────────────────────────────────────

    List<Map<String, Object>> auditorias(String accion) {
        return jdbc.queryForList("SELECT * FROM auditoria WHERE accion = ? ORDER BY id", accion);
    }

    List<String> detallesDeAuditoria() {
        return jdbc.queryForList("SELECT coalesce(detalle, '') FROM auditoria", String.class);
    }

    Map<String, Object> filaUsuario(long id) {
        return jdbc.queryForMap("SELECT * FROM usuario WHERE id = ?", id);
    }

    Map<String, Object> filaPerfil(long id) {
        return jdbc.queryForMap("SELECT * FROM perfil_arrendador WHERE usuario_id = ?", id);
    }
}
