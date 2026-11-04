package pe.edu.ulima.espacioya.pagos.service.reembolso;

/**
 * Command (★ HU-16): un pedido de reembolso ya armado, que el Invoker {@link EjecutorReembolsos} ejecuta cuando toca
 * (tras el commit de la cancelación o en el acto, al reintentar). El Invoker no sabe qué reembolso es ni a qué pasarela
 * va: solo llama a {@link #ejecutar()}.
 */
public interface ComandoReembolso {

    ResultadoReembolso ejecutar();
}
