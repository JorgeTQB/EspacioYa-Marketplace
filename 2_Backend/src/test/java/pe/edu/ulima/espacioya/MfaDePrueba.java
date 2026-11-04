package pe.edu.ulima.espacioya;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.regex.Pattern;

import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import pe.edu.ulima.espacioya.comun.cifrado.CifradorCampos;
import pe.edu.ulima.espacioya.cuentas.domain.Base32;
import pe.edu.ulima.espacioya.cuentas.domain.Totp;

/**
 * Soporte de las IT para cuentas con MFA (HU-17 design § 11): activa MFA con un secreto conocido y completa el inicio
 * de sesión de dos pasos. El secreto es el ASCII {@code 12345678901234567890} de RFC 6238.
 */
public final class MfaDePrueba {

    public static final String SECRETO = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    private static final Pattern CAMPO_DESAFIO = Pattern.compile("\"desafio\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern CAMPO_TOKEN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");

    private MfaDePrueba() {
    }

    public static void activar(JdbcTemplate jdbc, CifradorCampos cifrador, long usuarioId, Instant ahora) {
        jdbc.update("INSERT INTO mfa (usuario_id, secreto, activo, creado_en, activado_en) VALUES (?, ?, true, ?, ?)",
                usuarioId, cifrador.cifrar(SECRETO), Timestamp.from(ahora), Timestamp.from(ahora));
    }

    public static String codigo(Instant ahora) {
        return Totp.codigo(Base32.decodificar(SECRETO), Totp.pasoDe(ahora));
    }

    public static void olvidarUltimoPaso(JdbcTemplate jdbc, long usuarioId) {
        jdbc.update("UPDATE mfa SET ultimo_paso = NULL WHERE usuario_id = ?", usuarioId);
    }

    // Antes olvida el último paso: así una prueba puede iniciar sesión varias veces dentro del mismo paso de 30 s.
    public static String iniciarSesion(MockMvc mvc, JdbcTemplate jdbc, String correo, String contrasena, String ip,
                                       Instant ahora) throws Exception {
        jdbc.update("UPDATE mfa SET ultimo_paso = NULL WHERE usuario_id = (SELECT id FROM usuario WHERE correo = ?)",
                correo);
        var paso1 = mvc.perform(desde(post("/api/sesiones"), ip).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"" + correo + "\",\"contrasena\":\"" + contrasena + "\"}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        var paso2 = mvc.perform(desde(post("/api/sesiones/mfa"), ip).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"desafio\":\"" + campo(CAMPO_DESAFIO, paso1) + "\",\"codigo\":\""
                                + codigo(ahora) + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return campo(CAMPO_TOKEN, paso2);
    }

    private static MockHttpServletRequestBuilder desde(MockHttpServletRequestBuilder solicitud, String ip) {
        return solicitud.with(peticion -> {
            peticion.setRemoteAddr(ip);
            return peticion;
        });
    }

    private static String campo(Pattern patron, String json) {
        var encontrado = patron.matcher(json);
        if (!encontrado.find()) {
            throw new IllegalStateException("La respuesta no tiene el campo esperado");
        }
        return encontrado.group(1);
    }
}
