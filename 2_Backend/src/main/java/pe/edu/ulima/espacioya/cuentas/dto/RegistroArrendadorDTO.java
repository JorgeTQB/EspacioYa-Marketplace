package pe.edu.ulima.espacioya.cuentas.dto;

import static pe.edu.ulima.espacioya.cuentas.dto.RegistroUsuarioDTO.MENSAJE_ACEPTACION;
import static pe.edu.ulima.espacioya.cuentas.dto.RegistroUsuarioDTO.MENSAJE_CORREO;
import static pe.edu.ulima.espacioya.cuentas.dto.RegistroUsuarioDTO.MENSAJE_VERSION_DESACTUALIZADA;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import pe.edu.ulima.espacioya.cuentas.domain.PoliticaContrasena;
import pe.edu.ulima.espacioya.cuentas.domain.ValidadorDocumento;

// Sin rol, estado, correoVerificado ni estadoIdentidad: los fija el servidor y Jackson ignora los campos extra.
public record RegistroArrendadorDTO(
        @NotBlank(message = MENSAJE_CORREO)
        @Email(message = MENSAJE_CORREO)
        @Size(max = 254, message = MENSAJE_CORREO)
        String correo,

        @NotNull(message = PoliticaContrasena.REQUISITOS)
        String contrasena,

        @NotNull(message = MENSAJE_ACEPTACION)
        @AssertTrue(message = MENSAJE_ACEPTACION)
        Boolean aceptaPrivacidad,

        @NotBlank(message = MENSAJE_VERSION_DESACTUALIZADA)
        String versionPolitica,

        @NotBlank(message = RegistroArrendadorDTO.MENSAJE_NOMBRE)
        @Size(max = 100, message = RegistroArrendadorDTO.MENSAJE_NOMBRE)
        String nombre,

        @NotNull(message = RegistroArrendadorDTO.MENSAJE_TELEFONO)
        @Pattern(regexp = "9[0-9]{8}", message = RegistroArrendadorDTO.MENSAJE_TELEFONO)
        String telefono,

        // String y no el enum: un valor desconocido debe dar un error por campo y no un cuerpo ilegible.
        @NotNull(message = RegistroArrendadorDTO.MENSAJE_TIPO_DOCUMENTO)
        @Pattern(regexp = "DNI|RUC", message = RegistroArrendadorDTO.MENSAJE_TIPO_DOCUMENTO)
        String tipoDocumento,

        @NotBlank(message = ValidadorDocumento.MENSAJE_NUMERO_FALTA)
        String numeroDocumento,

        @NotBlank(message = RegistroArrendadorDTO.MENSAJE_RAZON_SOCIAL)
        @Size(max = 150, message = RegistroArrendadorDTO.MENSAJE_RAZON_SOCIAL)
        String razonSocial,

        @NotBlank(message = RegistroArrendadorDTO.MENSAJE_DIRECCION)
        @Size(max = 200, message = RegistroArrendadorDTO.MENSAJE_DIRECCION)
        String direccionFiscal) {

    public static final String MENSAJE_NOMBRE = "Ingresa tu nombre completo (máximo 100 caracteres).";
    public static final String MENSAJE_TELEFONO = "Ingresa un celular de 9 dígitos que empiece con 9.";
    public static final String MENSAJE_TIPO_DOCUMENTO = "Elige el tipo de documento: DNI o RUC.";
    public static final String MENSAJE_RAZON_SOCIAL = "Ingresa la razón social o tu nombre (máximo 150 caracteres).";
    public static final String MENSAJE_DIRECCION = "Ingresa la dirección fiscal (máximo 200 caracteres).";

    @Override
    public String toString() {
        return "RegistroArrendadorDTO[correo=***, contrasena=***, aceptaPrivacidad=" + aceptaPrivacidad
                + ", versionPolitica=" + versionPolitica + ", nombre=***, telefono=***, tipoDocumento="
                + tipoDocumento + ", numeroDocumento=***, razonSocial=***, direccionFiscal=***]";
    }
}
