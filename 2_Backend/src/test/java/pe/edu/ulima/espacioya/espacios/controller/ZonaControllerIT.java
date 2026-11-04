package pe.edu.ulima.espacioya.espacios.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.text.Collator;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ZonaControllerIT extends EspaciosControllerSoporte {

    @Test
    @DisplayName("HU-07 R8: con sesión lista las 50 zonas con solo codigo y nombre, ordenadas por nombre")
    void listaZonas() throws Exception {
        crearUsuario(CORREO_USUARIO);

        var cuerpo = mvc.perform(con(token(CORREO_USUARIO), get(ZONAS))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var zonas = lista(cuerpo);
        assertThat(zonas).hasSize(50);
        assertThat(zonas).allSatisfy(zona -> assertThat(zona).containsOnlyKeys("codigo", "nombre"));
        assertThat(zonas).contains(Map.of("codigo", "MIRAFLORES", "nombre", "Miraflores"),
                Map.of("codigo", "MI_PERU", "nombre", "Mi Perú"));
        var nombres = zonas.stream().map(zona -> (String) zona.get("nombre")).toList();
        var orden = Collator.getInstance(Locale.of("es", "PE"));
        assertThat(nombres).isSortedAccordingTo(orden::compare);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM zona", Integer.class)).isEqualTo(50);
    }

    @Test
    @DisplayName("HU-10 R13: sin sesión el catálogo de zonas responde 200 (reemplaza HU-07 R43 para /api/zonas)")
    void sinSesion() throws Exception {
        var cuerpo = mvc.perform(desde(get(ZONAS))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var zonas = lista(cuerpo);
        assertThat(zonas).hasSize(50);
        assertThat(zonas).allSatisfy(zona -> assertThat(zona).containsOnlyKeys("codigo", "nombre"));
        var nombres = zonas.stream().map(zona -> (String) zona.get("nombre")).toList();
        var orden = Collator.getInstance(Locale.of("es", "PE"));
        assertThat(nombres).isSortedAccordingTo(orden::compare);
    }

    @Test
    @DisplayName("HU-10 R13: con un token inválido el catálogo de zonas responde 200 igual que sin sesión")
    void tokenInvalido() throws Exception {
        mvc.perform(con("token-que-no-existe", get(ZONAS))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(50));
    }
}
