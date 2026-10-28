package pe.edu.ulima.espacioya.comun.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class ManejadorGlobalErroresTest {

    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.standaloneSetup(new ControladorDePrueba())
                .setControllerAdvice(new ManejadorGlobalErrores())
                .build();
    }

    @Test
    @DisplayName("HU-01 R24: un error no previsto responde 500 con mensaje genérico y sin detalle técnico")
    void errorInesperadoEsGenerico() throws Exception {
        mvc.perform(get("/prueba/falla"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.mensaje").value("Ocurrió un problema. Intenta de nuevo más tarde."))
                .andExpect(jsonPath("$.errores").isEmpty())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("detalle interno"))));
    }

    @Test
    @DisplayName("HU-01 R13: una regla de negocio con campo responde 400 con el error en ese campo")
    void reglaNegocioConCampo() throws Exception {
        mvc.perform(get("/prueba/regla"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Revisa los datos del formulario."))
                .andExpect(jsonPath("$.errores[0].campo").value("correo"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Ingresa un correo válido."));
    }

    @Test
    @DisplayName("HU-03 R9: credenciales inválidas responde 401 con el mensaje genérico")
    void credencialesInvalidasResponde401() throws Exception {
        mvc.perform(get("/prueba/credenciales"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Correo o contraseña incorrectos."))
                .andExpect(jsonPath("$.errores").isEmpty());
    }

    @Test
    @DisplayName("HU-03 R16: demasiados intentos responde 429 con el mensaje de bloqueo")
    void demasiadosIntentosResponde429() throws Exception {
        mvc.perform(get("/prueba/intentos"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.mensaje")
                        .value("Hiciste demasiados intentos. Espera un momento e inténtalo de nuevo."))
                .andExpect(jsonPath("$.errores").isEmpty());
    }

    @RestController
    static class ControladorDePrueba {

        @GetMapping("/prueba/credenciales")
        String credenciales() {
            throw new CredencialesInvalidasException();
        }

        @GetMapping("/prueba/intentos")
        String intentos() {
            throw new DemasiadosIntentosException();
        }

        @GetMapping("/prueba/falla")
        String falla() {
            throw new IllegalStateException("detalle interno de la base de datos");
        }

        @GetMapping("/prueba/regla")
        String regla() {
            throw new ReglaNegocioException("correo", "Ingresa un correo válido.");
        }
    }
}
