package pe.edu.ulima.espacioya.cuentas.dto;

import pe.edu.ulima.espacioya.cuentas.domain.PasoMfa;

public record DesafioMfaDTO(String desafio, PasoMfa paso) implements RespuestaInicioSesion {

    @Override
    public String toString() {
        return "DesafioMfaDTO[desafio=***, paso=" + paso + "]";
    }
}
