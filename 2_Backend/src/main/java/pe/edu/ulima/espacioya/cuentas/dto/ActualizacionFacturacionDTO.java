package pe.edu.ulima.espacioya.cuentas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import pe.edu.ulima.espacioya.cuentas.service.ReautenticacionPerfil;

// numeroDocumento es opcional: vacío conserva el actual (HU-20 D16); su formato lo revisa el servicio con
// ValidadorDocumento antes de la contraseña (R35). Sin estadoIdentidad: lo decide el servidor (R4).
public record ActualizacionFacturacionDTO(
        // String y no el enum: un valor desconocido debe dar un error por campo y no un cuerpo ilegible.
        @NotNull(message = RegistroArrendadorDTO.MENSAJE_TIPO_DOCUMENTO)
        @Pattern(regexp = "DNI|RUC", message = RegistroArrendadorDTO.MENSAJE_TIPO_DOCUMENTO)
        String tipoDocumento,

        String numeroDocumento,

        @NotBlank(message = RegistroArrendadorDTO.MENSAJE_RAZON_SOCIAL)
        @Size(max = 150, message = RegistroArrendadorDTO.MENSAJE_RAZON_SOCIAL)
        String razonSocial,

        @NotBlank(message = RegistroArrendadorDTO.MENSAJE_DIRECCION)
        @Size(max = 200, message = RegistroArrendadorDTO.MENSAJE_DIRECCION)
        String direccionFiscal,

        @NotBlank(message = ReautenticacionPerfil.MENSAJE_CONTRASENA)
        @Size(max = 72, message = ReautenticacionPerfil.MENSAJE_CONTRASENA)
        String contrasena) {

    @Override
    public String toString() {
        return "ActualizacionFacturacionDTO[tipoDocumento=" + tipoDocumento
                + ", numeroDocumento=***, razonSocial=***, direccionFiscal=***, contrasena=***]";
    }
}
