package pe.edu.ulima.espacioya.cuentas.dto;

// Sin Bean Validation: la forma del token la revisa el servicio, que responde el mismo mensaje de enlace inválido.
public record TokenRecuperacionDTO(String token) {

    @Override
    public String toString() {
        return "TokenRecuperacionDTO[token=***]";
    }
}
