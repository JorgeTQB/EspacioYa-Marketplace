package pe.edu.ulima.espacioya.reservas.service;

import org.springframework.stereotype.Service;

import pe.edu.ulima.espacioya.comun.transaccion.DespuesDelCommit;
import pe.edu.ulima.espacioya.cuentas.service.AvisoCancelacionArrendador;
import pe.edu.ulima.espacioya.cuentas.service.AvisoCancelacionUsuario;
import pe.edu.ulima.espacioya.cuentas.service.CuentaService;
import pe.edu.ulima.espacioya.cuentas.service.NotificadorCorreo;
import pe.edu.ulima.espacioya.reservas.domain.QuienCancela;
import pe.edu.ulima.espacioya.reservas.domain.ReembolsoCalculado;
import pe.edu.ulima.espacioya.reservas.domain.ReservaConHorario;

/**
 * Correos de una cancelación (HU-15 D8), siempre enviados (RN-14). Uno por sincronización: el fallo de uno no impide
 * el otro ni revierte la cancelación (R28), y sin commit no sale ninguno (R29).
 */
@Service
public class AvisosCancelacion {

    private final CuentaService cuentas;
    private final NotificadorCorreo notificador;

    public AvisosCancelacion(CuentaService cuentas, NotificadorCorreo notificador) {
        this.cuentas = cuentas;
        this.notificador = notificador;
    }

    /** Se llama dentro de la transacción que cancela: lee los correos ahí y los envía después del commit. */
    public void programar(Long usuarioId, Long arrendadorId, String espacioNombre, ReservaConHorario horario,
                          QuienCancela quien, ReembolsoCalculado reembolso) {
        var correoUsuario = cuentas.correoDe(usuarioId);
        var correoArrendador = cuentas.correoDe(arrendadorId);
        var alUsuario = new AvisoCancelacionUsuario(espacioNombre, horario.fecha(), horario.horaInicio(),
                horario.horaFin(), quien.name(), reembolso.porcentaje(), reembolso.monto());
        var alArrendador = new AvisoCancelacionArrendador(espacioNombre, horario.fecha(), horario.horaInicio(),
                horario.horaFin(), quien.name());
        DespuesDelCommit.ejecutar(() -> notificador.notificarCancelacionAlUsuario(correoUsuario, alUsuario));
        DespuesDelCommit.ejecutar(() -> notificador.notificarCancelacionAlArrendador(correoArrendador, alArrendador));
    }
}
