package pe.edu.ulima.espacioya.cuentas.domain;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Qué cambia (o cambiaría) una edición de facturación y cómo queda la identidad (HU-20). */
public record CambioFacturacion(Set<CampoFacturacion> campos, EstadoIdentidad identidadAnterior,
                                EstadoIdentidad identidadNueva) {

    public CambioFacturacion {
        campos = Set.copyOf(campos);
    }

    public boolean hayCambios() {
        return !campos.isEmpty();
    }

    public boolean cambiaDocumento() {
        return campos.contains(CampoFacturacion.TIPO_DOCUMENTO) || campos.contains(CampoFacturacion.NUMERO_DOCUMENTO);
    }

    // D9: la razón social es el nombre que el admin verificó y el titular que copia HU-18.
    public boolean cambiaIdentidad() {
        return cambiaDocumento() || campos.contains(CampoFacturacion.RAZON_SOCIAL);
    }

    /** Nombres de los campos en el orden del enum, separados por comas ({@code tipoDocumento,numeroDocumento}). */
    public String nombresDeCampos() {
        return Arrays.stream(CampoFacturacion.values()).filter(campos::contains).map(CampoFacturacion::nombre)
                .collect(Collectors.joining(","));
    }
}
