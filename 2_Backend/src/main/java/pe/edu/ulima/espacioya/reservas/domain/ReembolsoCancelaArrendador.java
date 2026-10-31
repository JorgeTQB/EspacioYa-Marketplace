package pe.edu.ulima.espacioya.reservas.domain;

/** ConcreteStrategy: cancela el arrendador, con cualquier anticipación → 100 % al usuario (RN-06, HU-15 R13). */
public final class ReembolsoCancelaArrendador implements PoliticaReembolso {

    public static final String CODIGO = "ARRENDADOR";

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    public boolean aplicaA(SolicitudCancelacion solicitud) {
        return solicitud.quien() == QuienCancela.ARRENDADOR;
    }

    @Override
    public int porcentaje() {
        return 100;
    }
}
