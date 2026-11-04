package pe.edu.ulima.espacioya.pagos.dto;

import java.time.Instant;

import pe.edu.ulima.espacioya.pagos.domain.CuentaBancaria;
import pe.edu.ulima.espacioya.pagos.domain.EstadoCuentaBancaria;

/** Cuenta bancaria vista por su dueño: el número solo enmascarado (R19). {@code retenidaHasta} solo si RETENIDA. */
public record CuentaBancariaDTO(String banco, String numeroEnmascarado, String titular, EstadoCuentaBancaria estado,
                                Instant retenidaHasta) {

    public static CuentaBancariaDTO de(CuentaBancaria cuenta, Instant ahora) {
        var estado = cuenta.estado(ahora);
        return new CuentaBancariaDTO(cuenta.getBanco(), cuenta.enmascarado(), cuenta.getTitular(), estado,
                estado == EstadoCuentaBancaria.RETENIDA ? cuenta.getRetenerHasta() : null);
    }
}
