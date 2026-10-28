package pe.edu.ulima.espacioya.cuentas.dto;

public record SesionIniciadaDTO(String token, String rol, String aviso) {

    @Override
    public String toString() {
        return "SesionIniciadaDTO[token=***, rol=" + rol + ", aviso=" + aviso + "]";
    }
}
