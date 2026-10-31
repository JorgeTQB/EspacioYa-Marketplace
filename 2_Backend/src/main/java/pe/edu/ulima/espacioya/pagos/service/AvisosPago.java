package pe.edu.ulima.espacioya.pagos.service;

import org.springframework.stereotype.Service;

import pe.edu.ulima.espacioya.comun.transaccion.DespuesDelCommit;
import pe.edu.ulima.espacioya.cuentas.service.AvisoReservaPagada;
import pe.edu.ulima.espacioya.cuentas.service.ComprobantePago;
import pe.edu.ulima.espacioya.cuentas.service.CuentaService;
import pe.edu.ulima.espacioya.cuentas.service.NotificadorCorreo;
import pe.edu.ulima.espacioya.pagos.service.pasarela.PagoConsultado;
import pe.edu.ulima.espacioya.pagos.service.puertos.ReservaBloqueada;

/** Correos de una reserva confirmada (HU-13 D5). Uno por sincronización: el fallo de uno no impide el otro. */
@Service
public class AvisosPago {

    private final CuentaService cuentas;
    private final NotificadorCorreo notificador;

    public AvisosPago(CuentaService cuentas, NotificadorCorreo notificador) {
        this.cuentas = cuentas;
        this.notificador = notificador;
    }

    /** Se llama dentro de la transacción que confirma: lee los correos ahí y los envía después del commit. */
    public void programar(ReservaBloqueada reserva, PagoConsultado pago) {
        var correoUsuario = cuentas.correoDe(reserva.usuarioId());
        var correoArrendador = cuentas.correoDe(reserva.arrendadorId());
        var comprobante = new ComprobantePago(reserva.espacioNombre(), reserva.fecha(), reserva.horaInicio(),
                reserva.horaFin(), reserva.montoTotal(), pago.ultimos4(), pago.idTransaccion());
        var aviso = new AvisoReservaPagada(reserva.espacioNombre(), reserva.fecha(), reserva.horaInicio(),
                reserva.horaFin());
        DespuesDelCommit.ejecutar(() -> notificador.notificarPagoConfirmado(correoUsuario, comprobante));
        DespuesDelCommit.ejecutar(() -> notificador.notificarReservaPagada(correoArrendador, aviso));
    }
}
