package pe.edu.ulima.espacioya.comun.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import pe.edu.ulima.espacioya.MfaDePrueba;
import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.comun.cifrado.CifradorCampos;
import pe.edu.ulima.espacioya.cuentas.domain.Usuario;
import pe.edu.ulima.espacioya.cuentas.repository.UsuarioRepository;

/**
 * HU-06, D2: con {@code ADMIN_IPS_PERMITIDAS} vacía nadie entra al panel admin, ni siquiera desde la máquina local.
 * Con MFA activo tampoco (HU-17 P3: el control de IPs complementa a MFA).
 * Clase aparte porque necesita otra configuración del contexto.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "espacioya.admin.ips-permitidas=")
class FiltroIpsAdminIT {

    private static final String CORREO_ADMIN = "admin@espacioya.pe";
    private static final String CONTRASENA = "Segura123!";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private PasswordEncoder encoder;
    @Autowired
    private CifradorCampos cifrador;

    @BeforeEach
    void preparar() {
        jdbc.update("DELETE FROM sesion");
        jdbc.update("DELETE FROM auditoria");
        jdbc.update("DELETE FROM perfil_arrendador");
        jdbc.update("DELETE FROM usuario");
        var ahora = Instant.parse("2026-10-28T15:00:00Z");
        var admin = usuarios.save(Usuario.registrarAdmin(CORREO_ADMIN, encoder.encode(CONTRASENA), "2026-10", ahora));
        MfaDePrueba.activar(jdbc, cifrador, admin.getId(), ahora);
    }

    private static MockHttpServletRequestBuilder desdeLocal(MockHttpServletRequestBuilder solicitud) {
        return solicitud.with(peticion -> {
            peticion.setRemoteAddr("127.0.0.1");
            return peticion;
        });
    }

    @Test
    @DisplayName("HU-06 R5, HU-17 R9: con la lista vacía ni 127.0.0.1 entra al panel (403) y el admin con MFA "
            + "activo no inicia sesión ni recibe desafío")
    void listaVaciaNadieEntra() throws Exception {
        mvc.perform(desdeLocal(get("/api/admin/arrendadores/en-revision")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("No tienes permiso para realizar esta acción."));
        mvc.perform(desdeLocal(post("/api/sesiones")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"" + CORREO_ADMIN + "\",\"contrasena\":\"" + CONTRASENA + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Correo o contraseña incorrectos."));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM sesion", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM desafio_mfa", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT detalle FROM auditoria WHERE accion = 'LOGIN_FALLIDO'", String.class))
                .isEqualTo("motivo=IP_NO_PERMITIDA");
    }
}
