package pe.edu.ulima.espacioya.busqueda.service;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.ulima.espacioya.busqueda.repository.ResenaDAO;
import pe.edu.ulima.espacioya.comun.auditoria.AuditoriaService;
import pe.edu.ulima.espacioya.reservas.service.ReservasParaResena;

/**
 * Ensamblador de la cadena ★ Decorator de HU-24 (R44, DT-22). La arma una sola vez y la publica como el único bean
 * de tipo {@link PublicacionResena}; las piezas no son beans por separado.
 */
@Configuration
public class ConfiguracionPublicacionResena {

    @Bean
    PublicacionResena publicacionResena(ResenaDAO resenas, ReservasParaResena reservas, AuditoriaService auditoria,
                                        TransactionTemplate transacciones, Clock reloj) {
        return cadena(resenas, reservas, auditoria, transacciones, reloj);
    }

    /**
     * Único lugar que conoce el orden, de afuera hacia adentro: Sanitizada → Transaccional → Auditada → Base. Los
     * decoradores no conmutan: ver {@code specs/HU-24/design.md} § 12.1.
     */
    public static PublicacionResena cadena(ResenaDAO resenas, ReservasParaResena reservas, AuditoriaService auditoria,
                                           TransactionTemplate transacciones, Clock reloj) {
        return new PublicacionSanitizada(                       // 1. fuera de la transacción: R22 no abre ni lee nada
                new PublicacionTransaccional(                   // 2. abre la transacción y traduce la carrera (DT-6)
                        new PublicacionAuditada(                // 3. dentro de la transacción: R23 y R51
                                new PublicacionResenaBase(resenas, reservas, reloj), auditoria),
                        transacciones));
    }
}
