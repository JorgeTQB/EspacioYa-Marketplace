package pe.edu.ulima.espacioya.reservas.domain;

/** ConcreteStrategy: el usuario cancela con menos de 48 horas de anticipación → 0 % (RN-06, HU-15 R12). */
public final class ReembolsoUsuarioSinAnticipacion implements PoliticaReembolso {

    public static final String CODIGO = "USUARIO_SIN_ANTICIPACION";

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    public boolean aplicaA(SolicitudCancelacion solicitud) {
        return solicitud.quien() == QuienCancela.USUARIO
                && solicitud.anticipacion().compareTo(ANTICIPACION_MINIMA) < 0;
    }

    @Override
    public int porcentaje() {
        return 0;
    }
}
