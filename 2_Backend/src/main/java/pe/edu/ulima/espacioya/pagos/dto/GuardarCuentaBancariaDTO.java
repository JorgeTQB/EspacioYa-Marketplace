package pe.edu.ulima.espacioya.pagos.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Entrada de {@code PUT /api/arrendadores/actual/cuenta-bancaria} (HU-18). El titular no se envía: lo toma el servidor
 * de la razón social del perfil (P1). {@code toString} no muestra el número ni la contraseña (R22).
 */
public record GuardarCuentaBancariaDTO(
        @NotBlank(message = MENSAJE_BANCO)
        @Size(min = 2, max = 60, message = MENSAJE_BANCO)
        String banco,

        @NotBlank(message = MENSAJE_NUMERO)
        @Pattern(regexp = "\\d{10,20}", message = MENSAJE_NUMERO)
        String numero,

        @AssertTrue(message = MENSAJE_DECLARACION)
        boolean declaraTitularidad,

        @NotBlank(message = MENSAJE_CONTRASENA)
        @Size(max = 72, message = MENSAJE_CONTRASENA)
        String contrasena) {

    public static final String MENSAJE_BANCO = "Ingresa el nombre del banco (de 2 a 60 caracteres).";
    public static final String MENSAJE_NUMERO = "Ingresa el número de cuenta: solo dígitos, de 10 a 20.";
    public static final String MENSAJE_DECLARACION = "Confirma que la cuenta está a tu nombre o al de tu RUC.";
    public static final String MENSAJE_CONTRASENA = "Ingresa tu contraseña para confirmar.";

    @Override
    public String toString() {
        return "GuardarCuentaBancariaDTO[banco=" + banco + ", numero=***, declaraTitularidad=" + declaraTitularidad
                + ", contrasena=***]";
    }
}
