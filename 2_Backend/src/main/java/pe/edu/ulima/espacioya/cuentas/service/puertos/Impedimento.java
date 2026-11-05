package pe.edu.ulima.espacioya.cuentas.service.puertos;

/** Lo que impide eliminar la cuenta (HU-22 CA3): se devuelve al cliente como un error de campo. */
public record Impedimento(String campo, String mensaje) {
}
