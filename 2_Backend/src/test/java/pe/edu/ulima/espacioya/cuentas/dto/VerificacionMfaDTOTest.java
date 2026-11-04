package pe.edu.ulima.espacioya.cuentas.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VerificacionMfaDTOTest {

    private static final String DESAFIO = "a".repeat(43);
    private static final String M_DESAFIO = "desafio: Inicia sesión de nuevo.";
    private static final String M_CODIGO = "codigo: Ingresa el código de verificación.";
    private static final String M_CONTRASENA = "contrasena: Ingresa tu contraseña.";

    private static ValidatorFactory fabrica;
    private static Validator validador;

    @BeforeAll
    static void crearValidador() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validador = fabrica.getValidator();
    }

    @AfterAll
    static void cerrarValidador() {
        fabrica.close();
    }

    private static <T> List<String> errores(Set<ConstraintViolation<T>> violaciones) {
        return violaciones.stream()
                .map(violacion -> violacion.getPropertyPath() + ": " + violacion.getMessage())
                .distinct()
                .toList();
    }

    private static List<String> errores(String desafio, String codigo) {
        return errores(validador.validate(new VerificacionMfaDTO(desafio, codigo)));
    }

    @Test
    @DisplayName("HU-17 R22: un desafío y un código dentro de los límites son válidos")
    void valido() {
        assertThat(errores(DESAFIO, "123456")).isEmpty();
        assertThat(errores("a".repeat(64), "c".repeat(20))).isEmpty();
    }

    @Test
    @DisplayName("HU-17 R22: desafío nulo, vacío o en blanco → error en el campo desafio")
    void desafioVacio() {
        for (var desafio : new String[] {null, "", "   "}) {
            assertThat(errores(desafio, "123456")).containsExactly(M_DESAFIO);
        }
    }

    @Test
    @DisplayName("HU-17 R22: un desafío de más de 64 caracteres → error en el campo desafio")
    void desafioLargo() {
        assertThat(errores("a".repeat(65), "123456")).containsExactly(M_DESAFIO);
    }

    @Test
    @DisplayName("HU-17 R22: código nulo, vacío o en blanco → error en el campo codigo")
    void codigoVacio() {
        for (var codigo : new String[] {null, "", "   "}) {
            assertThat(errores(DESAFIO, codigo)).containsExactly(M_CODIGO);
        }
    }

    @Test
    @DisplayName("HU-17 R22: un código de más de 20 caracteres → error en el campo codigo")
    void codigoLargo() {
        assertThat(errores(DESAFIO, "1".repeat(21))).containsExactly(M_CODIGO);
    }

    @Test
    @DisplayName("HU-17 R18, R22: DesafioDTO valida el desafío vacío y de más de 64 caracteres")
    void desafioDTO() {
        assertThat(errores(validador.validate(new DesafioDTO(DESAFIO)))).isEmpty();
        assertThat(errores(validador.validate(new DesafioDTO("")))).containsExactly(M_DESAFIO);
        assertThat(errores(validador.validate(new DesafioDTO("a".repeat(65))))).containsExactly(M_DESAFIO);
    }

    @Test
    @DisplayName("HU-17 R34: CodigoMfaDTO valida el código vacío y de más de 20 caracteres")
    void codigoMfaDTO() {
        assertThat(errores(validador.validate(new CodigoMfaDTO("123456")))).isEmpty();
        assertThat(errores(validador.validate(new CodigoMfaDTO(" ")))).containsExactly(M_CODIGO);
        assertThat(errores(validador.validate(new CodigoMfaDTO("1".repeat(21))))).containsExactly(M_CODIGO);
    }

    @Test
    @DisplayName("HU-17 R41: DesactivacionMfaDTO valida contraseña (hasta 72) y código (hasta 20)")
    void desactivacionMfaDTO() {
        assertThat(errores(validador.validate(new DesactivacionMfaDTO("Segura123!", "123456")))).isEmpty();
        assertThat(errores(validador.validate(new DesactivacionMfaDTO("", "123456"))))
                .containsExactly(M_CONTRASENA);
        assertThat(errores(validador.validate(new DesactivacionMfaDTO("a".repeat(73), "123456"))))
                .containsExactly(M_CONTRASENA);
        assertThat(errores(validador.validate(new DesactivacionMfaDTO("Segura123!", null))))
                .containsExactly(M_CODIGO);
    }
}
