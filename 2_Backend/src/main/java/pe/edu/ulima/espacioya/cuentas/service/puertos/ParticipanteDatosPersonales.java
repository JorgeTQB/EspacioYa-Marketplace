package pe.edu.ulima.espacioya.cuentas.service.puertos;

import java.time.Instant;
import java.util.List;

/**
 * Puerto de HU-22 que implementa cada módulo que guarda datos de una cuenta ({@code reservas}, {@code pagos}; más
 * adelante {@code busqueda} con HU-24). {@code cuentas} lo recibe como {@code List<>} ordenada con {@code @Order}:
 * sumar un módulo es sumar un bean (OCP, R45). Ningún método exige sesión ni rol: el titular ya lo validó
 * {@code cuentas}.
 */
public interface ParticipanteDatosPersonales {

    /**
     * Secciones de la exportación; cada nombre es único en todo el JSON. Se une a la transacción del llamador y solo
     * lee.
     */
    List<SeccionDatos> exportar(TitularDatos titular);

    /**
     * {@code @Transactional(propagation = MANDATORY)}. Toma los bloqueos que necesite antes de leer (R43, R44) y
     * devuelve un {@link Impedimento} por campo, o una lista vacía. No escribe.
     */
    List<Impedimento> impedimentos(TitularDatos titular, Instant ahora);

    /**
     * {@code @Transactional(propagation = MANDATORY)}. Anonimiza o borra lo suyo, sin tocar registros contables; los
     * archivos se borran con {@code DespuesDelCommit}.
     */
    void anonimizar(TitularDatos titular);
}
