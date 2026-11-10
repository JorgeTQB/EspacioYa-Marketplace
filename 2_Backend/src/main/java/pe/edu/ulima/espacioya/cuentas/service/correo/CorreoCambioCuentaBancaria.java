package pe.edu.ulima.espacioya.cuentas.service.correo;

import pe.edu.ulima.espacioya.comun.correo.Correo;
import pe.edu.ulima.espacioya.comun.correo.FormatoCorreo;
import pe.edu.ulima.espacioya.cuentas.service.AvisoCambioCuentaBancaria;

/**
 * ★ Factory Method · ConcreteProduct de {@code NotificadorCorreo#notificarCambioCuentaBancaria} (HU-25, tabla C1).
 * Banco, número enmascarado y retención (HU-18 DT-10); nunca el número completo.
 */
public record CorreoCambioCuentaBancaria(AvisoCambioCuentaBancaria aviso) implements Correo {

    public static final String ASUNTO = "Cambió tu cuenta bancaria";

    @Override
    public String asunto() {
        return ASUNTO;
    }

    @Override
    public String cuerpo() {
        var retencion = aviso.retenidaHasta() == null
                ? ""
                : "\n\nPor seguridad, tus liquidaciones quedan retenidas hasta el "
                        + FormatoCorreo.instante(aviso.retenidaHasta()) + ".";
        return """
                Hola:

                Se cambió la cuenta bancaria donde recibes tus liquidaciones.

                Banco: %s
                Cuenta: %s%s

                Si no fuiste tú, cambia tu contraseña.

                El equipo de EspacioYa
                """.formatted(aviso.banco(), aviso.numeroEnmascarado(), retencion);
    }
}
