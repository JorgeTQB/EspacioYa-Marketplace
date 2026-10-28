package pe.edu.ulima.espacioya.comun.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

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

    @Test
    @DisplayName("HU-05 R13: un archivo que supera el límite de multipart responde 400 con el mensaje de tamaño")
    void archivoDemasiadoGrandeResponde400() throws Exception {
        mvc.perform(get("/prueba/multipart-grande"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El archivo debe pesar como máximo 5 MB."))
                .andExpect(jsonPath("$.errores").isEmpty());
    }

    @Test
    @DisplayName("HU-05 R13: una solicitud multipart ilegible responde 400 con el mensaje de validación")
    void multipartIlegibleResponde400() throws Exception {
        mvc.perform(get("/prueba/multipart-ilegible"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Revisa los datos del formulario."))
                .andExpect(jsonPath("$.errores").isEmpty())
                .andExpect(content().string(not(containsString("detalle interno"))));
    }

    @Test
    @DisplayName("HU-05 R15: una validación con dos campos responde 400 con un error por campo")
    void validacionVariosCamposResponde400() throws Exception {
        mvc.perform(get("/prueba/validacion"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Revisa los datos del formulario."))
                .andExpect(jsonPath("$.errores.length()").value(2))
                .andExpect(jsonPath("$.errores[0].campo").value("documentoIdentidad"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("El archivo debe ser PDF, JPG o PNG."))
                .andExpect(jsonPath("$.errores[1].campo").value("documentoPropiedad"))
                .andExpect(jsonPath("$.errores[1].mensaje").value("El archivo debe pesar como máximo 5 MB."));
    }

    @Test
    @DisplayName("HU-05 R17, R18: acceso denegado responde 403 con el mensaje genérico")
    void accesoDenegadoResponde403() throws Exception {
        mvc.perform(get("/prueba/acceso"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("No tienes permiso para realizar esta acción."))
                .andExpect(jsonPath("$.errores").isEmpty());
    }

    @Test
    @DisplayName("HU-05 R36, R37: recurso no encontrado responde 404 con el mensaje genérico")
    void recursoNoEncontradoResponde404() throws Exception {
        mvc.perform(get("/prueba/recurso"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No encontramos lo que buscas o el enlace expiró."))
                .andExpect(jsonPath("$.errores").isEmpty());
    }

    @RestController
    static class ControladorDePrueba {

        @GetMapping("/prueba/multipart-grande")
        String multipartGrande() {
            throw new MaxUploadSizeExceededException(6L * 1024 * 1024);
        }

        @GetMapping("/prueba/multipart-ilegible")
        String multipartIlegible() {
            throw new MultipartException("detalle interno del parser");
        }

        @GetMapping("/prueba/validacion")
        String validacion() {
            throw new ValidacionException(List.of(
                    new ErrorCampoDTO("documentoIdentidad", "El archivo debe ser PDF, JPG o PNG."),
                    new ErrorCampoDTO("documentoPropiedad", "El archivo debe pesar como máximo 5 MB.")));
        }

        @GetMapping("/prueba/acceso")
        String acceso() {
            throw new AccesoDenegadoException();
        }

        @GetMapping("/prueba/recurso")
        String recurso() {
            throw new RecursoNoEncontradoException();
        }

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
