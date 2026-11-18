package pe.edu.ulima.espacioya.cuentas.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pe.edu.ulima.espacioya.cuentas.service.CambioCorreoService;
import pe.edu.ulima.espacioya.cuentas.service.ReautenticacionPerfil;

class PerfilDTOValidacionTest {

    private static final String CONTRASENA = "Segura123!";
    private static final String TOKEN = "Q2xhdmVTZWNyZXRhRGVQcnVlYmFQYXJhSFUwNEVuTG9n";

    private static ValidatorFactory fabrica;
    private static Validator validador;

    @BeforeAll
    static void crearValidador() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validador = fabrica.getValidator();
    }

    @AfterAll
    static void cerrar() {
        fabrica.close();
    }

    private static <T> Map<String, String> errores(T dto) {
        return validador.validate(dto).stream().collect(Collectors.toMap(v -> v.getPropertyPath().toString(),
                ConstraintViolation::getMessage, (a, b) -> a));
    }

    // ── DatosPerfilDTO ───────────────────────────────────────────────────

    @Test
    @DisplayName("HU-20 R9: un nombre de más de 100 caracteres o un teléfono mal formado dan un error por campo")
    void datosInvalidos() {
        var errores = errores(new DatosPerfilDTO("a".repeat(101), "812345678"));

        assertThat(errores).containsOnly(
                Map.entry("nombre", RegistroArrendadorDTO.MENSAJE_NOMBRE),
                Map.entry("telefono", RegistroArrendadorDTO.MENSAJE_TELEFONO));
        assertThat(errores(new DatosPerfilDTO("Ana", "98765432"))).containsOnlyKeys("telefono");
    }

    @Test
    @DisplayName("HU-20 R8, R11: nombre de 100, teléfono válido, vacío o nulo pasan la validación")
    void datosValidos() {
        assertThat(errores(new DatosPerfilDTO("a".repeat(100), "987654321"))).isEmpty();
        assertThat(errores(new DatosPerfilDTO("", ""))).isEmpty();
        assertThat(errores(new DatosPerfilDTO(null, null))).isEmpty();
    }

    // ── SolicitudCambioCorreoDTO ─────────────────────────────────────────

    @Test
    @DisplayName("HU-20 R22: un correo en blanco, sin formato o de más de 254 caracteres da error en correo")
    void correoInvalido() {
        var mensaje = Map.entry("correo", SolicitudCambioCorreoDTO.MENSAJE_CORREO);

        assertThat(errores(new SolicitudCambioCorreoDTO("  ", CONTRASENA))).containsOnly(mensaje);
        assertThat(errores(new SolicitudCambioCorreoDTO("sin-arroba", CONTRASENA))).containsOnly(mensaje);
        assertThat(errores(new SolicitudCambioCorreoDTO("a".repeat(245) + "@correo.pe", CONTRASENA)))
                .containsOnly(mensaje);
        assertThat(errores(new SolicitudCambioCorreoDTO("nuevo@correo.pe", CONTRASENA))).isEmpty();
    }

    @Test
    @DisplayName("HU-20 R18: sin contraseña o en blanco, la solicitud de correo da error en contrasena")
    void correoSinContrasena() {
        var mensaje = Map.entry("contrasena", ReautenticacionPerfil.MENSAJE_CONTRASENA);

        assertThat(errores(new SolicitudCambioCorreoDTO("nuevo@correo.pe", null))).containsOnly(mensaje);
        assertThat(errores(new SolicitudCambioCorreoDTO("nuevo@correo.pe", "   "))).containsOnly(mensaje);
        assertThat(errores(new SolicitudCambioCorreoDTO("nuevo@correo.pe", "a".repeat(73)))).containsOnly(mensaje);
    }

    // ── ConfirmacionCorreoDTO ────────────────────────────────────────────

    @Test
    @DisplayName("HU-20 R32: un token en blanco o demasiado largo da el mensaje de enlace no válido")
    void tokenInvalido() {
        var mensaje = Map.entry("token", CambioCorreoService.MENSAJE_ENLACE_INVALIDO);

        assertThat(errores(new ConfirmacionCorreoDTO(""))).containsOnly(mensaje);
        assertThat(errores(new ConfirmacionCorreoDTO("a".repeat(101)))).containsOnly(mensaje);
        assertThat(errores(new ConfirmacionCorreoDTO(TOKEN))).isEmpty();
    }

    // ── ActualizacionFacturacionDTO ──────────────────────────────────────

    @Test
    @DisplayName("HU-20 R35: tipo desconocido, razón social o dirección en blanco o largas dan un error por campo")
    void facturacionInvalida() {
        var errores = errores(new ActualizacionFacturacionDTO("CE", null, " ", "a".repeat(201), CONTRASENA));

        assertThat(errores).containsOnly(
                Map.entry("tipoDocumento", RegistroArrendadorDTO.MENSAJE_TIPO_DOCUMENTO),
                Map.entry("razonSocial", RegistroArrendadorDTO.MENSAJE_RAZON_SOCIAL),
                Map.entry("direccionFiscal", RegistroArrendadorDTO.MENSAJE_DIRECCION));
        assertThat(errores(new ActualizacionFacturacionDTO(null, null, "a".repeat(151), " ", CONTRASENA)))
                .containsOnlyKeys("tipoDocumento", "razonSocial", "direccionFiscal");
    }

    @Test
    @DisplayName("HU-20 R18, R36: sin contraseña la facturación da error en contrasena; el número es opcional")
    void facturacionContrasenaYNumeroOpcional() {
        assertThat(errores(new ActualizacionFacturacionDTO("RUC", null, "Canchas SAC", "Lima", " ")))
                .containsOnly(Map.entry("contrasena", ReautenticacionPerfil.MENSAJE_CONTRASENA));
        assertThat(errores(new ActualizacionFacturacionDTO("RUC", "", "Canchas SAC", "Lima", CONTRASENA))).isEmpty();
        assertThat(errores(new ActualizacionFacturacionDTO("DNI", "44556677", "Ana", "Lima", CONTRASENA))).isEmpty();
    }

    // ── toString (R46) ───────────────────────────────────────────────────

    @Test
    @DisplayName("HU-20 R46: el toString de los DTO de entrada no muestra ningún dato personal, contraseña ni token")
    void toStringEnmascarado() {
        assertThat(new DatosPerfilDTO("Ana Secreta", "987654321").toString())
                .isEqualTo("DatosPerfilDTO[nombre=***, telefono=***]");
        assertThat(new SolicitudCambioCorreoDTO("nuevo.secreto@correo.pe", CONTRASENA).toString())
                .isEqualTo("SolicitudCambioCorreoDTO[correo=***, contrasena=***]");
        assertThat(new ConfirmacionCorreoDTO(TOKEN).toString()).isEqualTo("ConfirmacionCorreoDTO[token=***]");
        assertThat(new ActualizacionFacturacionDTO("RUC", "20100070970", "Canchas Secretas SAC", "Jr. Oculto 1",
                CONTRASENA).toString())
                .doesNotContain("20100070970").doesNotContain("Canchas Secretas").doesNotContain("Jr. Oculto")
                .doesNotContain(CONTRASENA)
                .isEqualTo("ActualizacionFacturacionDTO[tipoDocumento=RUC, numeroDocumento=***, razonSocial=***, "
                        + "direccionFiscal=***, contrasena=***]");
    }
}
