package pe.edu.ulima.espacioya.cuentas.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Sin id, rol, correo ni estado: la cuenta sale de la sesión y Jackson ignora los campos extra (HU-20 R4). Que el
// arrendador los tenga obligatorios lo revisa el servicio (R10, D8).
public record DatosPerfilDTO(
        @Size(max = 100, message = RegistroArrendadorDTO.MENSAJE_NOMBRE)
        String nombre,

        @Pattern(regexp = "^$|9[0-9]{8}", message = RegistroArrendadorDTO.MENSAJE_TELEFONO)
        String telefono) {

    @Override
    public String toString() {
        return "DatosPerfilDTO[nombre=***, telefono=***]";
    }
}
