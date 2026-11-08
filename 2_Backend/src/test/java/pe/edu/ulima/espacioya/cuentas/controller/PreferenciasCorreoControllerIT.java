package pe.edu.ulima.espacioya.cuentas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import pe.edu.ulima.espacioya.TestcontainersConfiguration;
import pe.edu.ulima.espacioya.cuentas.dto.PreferenciasCorreoDTO;
import pe.edu.ulima.espacioya.cuentas.service.PreferenciasCorreoService;

/** HU-25 CA2 por la API: preferencia de recordatorios del titular de la sesión. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PerfilSoporte.Dobles.class})
class PreferenciasCorreoControllerIT extends PerfilSoporte {

    private static final String RUTA = "/api/usuarios/actual/preferencias-correo";

    private ResultActions consultar(String sesion) throws Exception {
        return mvc.perform(con(sesion, get(RUTA)));
    }

    private ResultActions guardar(String sesion, String cuerpo) throws Exception {
        return mvc.perform(con(sesion, put(RUTA)).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private boolean preferenciaGuardada(long id) {
        return (Boolean) filaUsuario(id).get("recordatorios_activos");
    }

    @Test
    @DisplayName("HU-25 R10: una cuenta nueva tiene recordatorios_activos = true en la base")
    void cuentaNuevaConRecordatorios() {
        assertThat(preferenciaGuardada(crearUsuario(CORREO_USUARIO))).isTrue();
        assertThat(preferenciaGuardada(crearArrendador())).isTrue();
    }

    @Test
    @DisplayName("HU-25 R1, R7: el USUARIO consulta su preferencia (true por defecto) con no-store")
    void usuarioConsulta() throws Exception {
        crearUsuario(CORREO_USUARIO);

        consultar(token(CORREO_USUARIO))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.recordatoriosActivos").value(true))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("HU-25 R2, R7, R8: el ARRENDADOR desactiva sus recordatorios, se guarda y se audita")
    void arrendadorDesactiva() throws Exception {
        var id = crearArrendador();
        var sesion = token(CORREO_ARRENDADOR);

        guardar(sesion, "{\"recordatoriosActivos\":false}")
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.recordatoriosActivos").value(false));

        assertThat(preferenciaGuardada(id)).isFalse();
        consultar(sesion).andExpect(status().isOk()).andExpect(jsonPath("$.recordatoriosActivos").value(false));
        var registros = auditorias(PreferenciasCorreoService.ACCION_PREFERENCIA);
        assertThat(registros).hasSize(1);
        assertThat(((Number) registros.getFirst().get("usuario_id")).longValue()).isEqualTo(id);
        assertThat(((Number) registros.getFirst().get("entidad_id")).longValue()).isEqualTo(id);
        assertThat(registros.getFirst().get("entidad")).isEqualTo("usuario");
        assertThat(registros.getFirst().get("ip")).isEqualTo(ip);
        assertThat(registros.getFirst().get("detalle")).isEqualTo("recordatoriosActivos=true->false");
    }

    @Test
    @DisplayName("HU-25 R2: el USUARIO vuelve a activar sus recordatorios")
    void usuarioReactiva() throws Exception {
        var id = crearUsuario(CORREO_USUARIO);
        var sesion = token(CORREO_USUARIO);
        guardar(sesion, "{\"recordatoriosActivos\":false}").andExpect(status().isOk());

        guardar(sesion, "{\"recordatoriosActivos\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordatoriosActivos").value(true));

        assertThat(preferenciaGuardada(id)).isTrue();
        assertThat(auditorias(PreferenciasCorreoService.ACCION_PREFERENCIA)).extracting(fila -> fila.get("detalle"))
                .containsExactly("recordatoriosActivos=true->false", "recordatoriosActivos=false->true");
    }

    @Test
    @DisplayName("HU-25 R9: guardar el mismo valor responde 200 sin auditoría")
    void mismoValorSinAuditoria() throws Exception {
        crearUsuario(CORREO_USUARIO);

        guardar(token(CORREO_USUARIO), "{\"recordatoriosActivos\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordatoriosActivos").value(true));

        assertThat(auditorias(PreferenciasCorreoService.ACCION_PREFERENCIA)).isEmpty();
    }

    @Test
    @DisplayName("HU-25 R3: un usuarioId en el cuerpo se ignora y solo cambia la cuenta de la sesión")
    void ignoraUsuarioIdDelCuerpo() throws Exception {
        var propio = crearUsuario(CORREO_USUARIO);
        var otro = crearUsuario(CORREO_OTRO);

        guardar(token(CORREO_USUARIO), "{\"recordatoriosActivos\":false,\"usuarioId\":" + otro + "}")
                .andExpect(status().isOk());

        assertThat(preferenciaGuardada(propio)).isFalse();
        assertThat(preferenciaGuardada(otro)).isTrue();
    }

    @Test
    @DisplayName("HU-25 R4: sin recordatoriosActivos o con null responde 400 en ese campo sin cambiar nada")
    void sinCampoDa400() throws Exception {
        var id = crearUsuario(CORREO_USUARIO);
        var sesion = token(CORREO_USUARIO);

        for (var cuerpo : new String[] {"{}", "{\"recordatoriosActivos\":null}"}) {
            guardar(sesion, cuerpo)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje").value(MENSAJE_VALIDACION))
                    .andExpect(jsonPath("$.errores.length()").value(1))
                    .andExpect(jsonPath("$.errores[0].campo").value("recordatoriosActivos"))
                    .andExpect(jsonPath("$.errores[0].mensaje")
                            .value(PreferenciasCorreoDTO.MENSAJE_RECORDATORIOS_OBLIGATORIO));
        }

        assertThat(preferenciaGuardada(id)).isTrue();
        assertThat(auditorias(PreferenciasCorreoService.ACCION_PREFERENCIA)).isEmpty();
    }

    @Test
    @DisplayName("HU-25 R5: el ADMIN recibe 403 en el GET y el PUT sin cambiar su preferencia")
    void adminRecibe403() throws Exception {
        var id = crearAdmin();
        var sesion = token(CORREO_ADMIN);

        consultar(sesion).andExpect(status().isForbidden()).andExpect(jsonPath("$.mensaje").value(ACCESO_DENEGADO));
        guardar(sesion, "{\"recordatoriosActivos\":false}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(ACCESO_DENEGADO));

        assertThat(preferenciaGuardada(id)).isTrue();
        assertThat(auditorias(PreferenciasCorreoService.ACCION_PREFERENCIA)).isEmpty();
    }

    @Test
    @DisplayName("HU-25 R6: sin sesión el GET y el PUT responden 401")
    void sinSesion401() throws Exception {
        consultar(null).andExpect(status().isUnauthorized());
        guardar(null, "{\"recordatoriosActivos\":false}").andExpect(status().isUnauthorized());
    }
}
