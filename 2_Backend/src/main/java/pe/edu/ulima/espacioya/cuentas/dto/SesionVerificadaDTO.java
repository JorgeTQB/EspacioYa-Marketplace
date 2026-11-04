package pe.edu.ulima.espacioya.cuentas.dto;

import java.util.List;

// codigosRespaldo va solo cuando el inicio de sesión activó MFA (HU-17 R26); si no, null (R10).
public record SesionVerificadaDTO(String token, String rol, String aviso, List<String> codigosRespaldo) {

    public SesionVerificadaDTO {
        codigosRespaldo = codigosRespaldo == null ? null : List.copyOf(codigosRespaldo);
    }

    @Override
    public String toString() {
        return "SesionVerificadaDTO[token=***, rol=" + rol + ", aviso=" + aviso + ", codigosRespaldo=***]";
    }
}
