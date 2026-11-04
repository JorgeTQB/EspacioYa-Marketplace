package pe.edu.ulima.espacioya.cuentas.repository;

/** Solo el id y el nombre de una cuenta (HU-14 D1): nunca el correo ni el teléfono (RNF-SEG-16). */
public record NombreCuenta(Long id, String nombre) {
}
