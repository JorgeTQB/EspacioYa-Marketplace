package pe.edu.ulima.espacioya.cuentas.dto;

import java.util.List;

/**
 * Resultado de la búsqueda de cuentas del admin (HU-09, R1–R3): como mucho 20 cuentas ordenadas por correo;
 * {@code hayMas} pide afinar el texto en vez de paginar.
 */
public record BusquedaCuentasDTO(List<CuentaResumenDTO> cuentas, boolean hayMas) {

    public BusquedaCuentasDTO {
        cuentas = List.copyOf(cuentas);
    }
}
