package pe.edu.ulima.espacioya.reservas.domain;

import java.util.List;

/**
 * Context del Strategy (★ HU-15): guarda las políticas y elige la única que aplica a la solicitud (R15). Agregar una
 * regla es agregar una clase a la lista, sin tocar al cliente (OCP).
 */
public final class CalculoReembolso {

    private final List<PoliticaReembolso> politicas;

    public CalculoReembolso(List<PoliticaReembolso> politicas) {
        this.politicas = List.copyOf(politicas);
    }

    /** Las tres reglas fijas de RN-06; no hay configuración (simplificación "política fija" de la historia). */
    public static CalculoReembolso politicaFija() {
        return new CalculoReembolso(List.of(new ReembolsoUsuarioConAnticipacion(),
                new ReembolsoUsuarioSinAnticipacion(), new ReembolsoCancelaArrendador()));
    }

    /** @throws IllegalStateException si no aplica ninguna o aplica más de una: es un error de programación. */
    public ReembolsoCalculado calcular(SolicitudCancelacion solicitud) {
        var aplican = politicas.stream().filter(politica -> politica.aplicaA(solicitud)).toList();
        if (aplican.size() != 1) {
            throw new IllegalStateException("Deben aplicar exactamente una política; aplican " + aplican.size());
        }
        var politica = aplican.getFirst();
        return new ReembolsoCalculado(politica.codigo(), politica.porcentaje(),
                politica.monto(solicitud.montoPagado()));
    }
}
