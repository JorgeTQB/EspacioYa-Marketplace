package pe.edu.ulima.espacioya.cuentas.dto;

import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;

// El número va enmascarado también para su dueño: una sesión robada no lo lee sin contraseña (HU-20 R6, D16).
public record FacturacionDTO(String tipoDocumento, String numeroDocumentoEnmascarado, String razonSocial,
                             String direccionFiscal, String estadoIdentidad) {

    public static FacturacionDTO de(PerfilArrendador perfil) {
        return new FacturacionDTO(perfil.getTipoDocumento().name(), perfil.numeroEnmascarado(),
                perfil.getRazonSocial(), perfil.getDireccionFiscal(), perfil.getEstadoIdentidad().name());
    }
}
