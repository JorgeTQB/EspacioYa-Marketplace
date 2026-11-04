package pe.edu.ulima.espacioya.cuentas.dto;

public record ConfiguracionMfaDTO(String secreto, String uri) {

    @Override
    public String toString() {
        return "ConfiguracionMfaDTO[secreto=***, uri=***]";
    }
}
