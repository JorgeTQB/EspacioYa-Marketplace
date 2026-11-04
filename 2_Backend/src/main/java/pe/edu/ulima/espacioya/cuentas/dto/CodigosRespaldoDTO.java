package pe.edu.ulima.espacioya.cuentas.dto;

import java.util.List;

public record CodigosRespaldoDTO(List<String> codigos) {

    public CodigosRespaldoDTO {
        codigos = List.copyOf(codigos);
    }

    @Override
    public String toString() {
        return "CodigosRespaldoDTO[codigos=***]";
    }
}
