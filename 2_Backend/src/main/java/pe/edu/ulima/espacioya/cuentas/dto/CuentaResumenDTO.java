package pe.edu.ulima.espacioya.cuentas.dto;

/** Una cuenta en los resultados de la búsqueda del admin (HU-09, R1): solo lo necesario para elegirla. */
public record CuentaResumenDTO(Long id, String nombre, String correo, String rol, String estado) {
}
