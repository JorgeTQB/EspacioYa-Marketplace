package pe.edu.ulima.espacioya.cuentas.dto;

import pe.edu.ulima.espacioya.cuentas.domain.PerfilArrendador;

// HU-22 D4: el número va enmascarado también en la exportación; el archivo puede quedar en un equipo compartido.
public record FacturacionExportadaDTO(String tipoDocumento, String numeroDocumento, String razonSocial,
                                      String direccionFiscal, String estadoIdentidad) {

    public static FacturacionExportadaDTO de(PerfilArrendador perfil) {
        return new FacturacionExportadaDTO(perfil.getTipoDocumento().name(), perfil.numeroEnmascarado(),
                perfil.getRazonSocial(), perfil.getDireccionFiscal(), perfil.getEstadoIdentidad().name());
    }
}
