package pe.edu.ulima.espacioya.cuentas.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import pe.edu.ulima.espacioya.TestcontainersConfiguration;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PoliticaPrivacidadControllerIT {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("HU-01 R26: un visitante sin sesión consulta la versión vigente de la política")
    void devuelveVersionVigente() throws Exception {
        mvc.perform(get("/api/politicas-privacidad/vigente"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"version\":\"2026-10\"}", true));
    }
}
