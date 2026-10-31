package pe.edu.ulima.espacioya.reservas.domain;

/** ConcreteStrategy: el usuario cancela con 48 horas o más de anticipación → 100 % (RN-06, HU-15 R11). */
public final class ReembolsoUsuarioConAnticipacion implements PoliticaReembolso {

    public static final String CODIGO = "USUARIO_CON_ANTICIPACION";

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    public boolean aplicaA(SolicitudCancelacion solicitud) {
        return solicitud.quien() == QuienCancela.USUARIO
                && solicitud.anticipacion().compareTo(ANTICIPACION_MINIMA) >= 0;
    }

    @Override
    public int porcentaje() {
        return 100;
    }
}
