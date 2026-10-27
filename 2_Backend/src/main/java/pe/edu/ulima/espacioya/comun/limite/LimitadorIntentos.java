package pe.edu.ulima.espacioya.comun.limite;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

@Component
public class LimitadorIntentos {

    private final ConcurrentMap<String, Deque<Instant>> intentosPorClave = new ConcurrentHashMap<>();
    private final Clock reloj;

    public LimitadorIntentos(Clock reloj) {
        this.reloj = reloj;
    }

    public boolean intentar(String clave, int maximo, Duration ventana) {
        var aceptado = new boolean[1];
        intentosPorClave.compute(clave, (k, intentos) -> {
            var ahora = Instant.now(reloj);
            var vigentes = intentos == null ? new ArrayDeque<Instant>() : intentos;
            var limite = ahora.minus(ventana);
            while (!vigentes.isEmpty() && !vigentes.peekFirst().isAfter(limite)) {
                vigentes.pollFirst();
            }
            // Un intento rechazado no se registra: si contara, quien insiste nunca saldría del bloqueo.
            aceptado[0] = vigentes.size() < maximo;
            if (aceptado[0]) {
                vigentes.addLast(ahora);
            }
            return vigentes.isEmpty() ? null : vigentes;
        });
        return aceptado[0];
    }
}
