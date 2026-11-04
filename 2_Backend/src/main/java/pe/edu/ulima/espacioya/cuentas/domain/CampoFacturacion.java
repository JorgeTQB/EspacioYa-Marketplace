package pe.edu.ulima.espacioya.cuentas.domain;

public enum CampoFacturacion {
    TIPO_DOCUMENTO("tipoDocumento"),
    NUMERO_DOCUMENTO("numeroDocumento"),
    RAZON_SOCIAL("razonSocial"),
    DIRECCION_FISCAL("direccionFiscal");

    private final String nombre;

    CampoFacturacion(String nombre) {
        this.nombre = nombre;
    }

    /** Nombre del campo en la API; es lo único que va a la auditoría (HU-20 R51). */
    public String nombre() {
        return nombre;
    }
}
