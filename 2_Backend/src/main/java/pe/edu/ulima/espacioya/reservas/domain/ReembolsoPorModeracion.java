package pe.edu.ulima.espacioya.reservas.domain;

/**
 * ConcreteStrategy (★ HU-15): cancela EspacioYa al ocultar un espacio por fraude, con cualquier anticipación → 100 %
 * al usuario (HU-27 R67, DT-19).
 */
public final class ReembolsoPorModeracion implements PoliticaReembolso {

    public static final String CODIGO = "MODERACION";

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    public boolean aplicaA(SolicitudCancelacion solicitud) {
        return solicitud.quien() == QuienCancela.ADMIN;
    }

    @Override
    public int porcentaje() {
        return 100;
    }
}
