package pe.edu.ulima.espacioya.pagos.dto;

import java.time.Instant;

import pe.edu.ulima.espacioya.pagos.domain.CuentaBancaria;

// HU-22 R9, D4: el número va enmascarado también en la exportación del propio arrendador.
public record CuentaBancariaExportadaDTO(String banco, String numero, String titular, String estado) {

    public static CuentaBancariaExportadaDTO de(CuentaBancaria cuenta, Instant ahora) {
        return new CuentaBancariaExportadaDTO(cuenta.getBanco(), cuenta.enmascarado(), cuenta.getTitular(),
                cuenta.estado(ahora).name());
    }
}
