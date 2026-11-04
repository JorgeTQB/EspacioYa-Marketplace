package pe.edu.ulima.espacioya.pagos.domain;

/** Estado de la cuenta bancaria del arrendador (HU-18). No se guarda: se calcula al leer con el reloj. */
public enum EstadoCuentaBancaria {
    DISPONIBLE,
    RETENIDA
}
