package pe.edu.ulima.espacioya.comun.limite;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Conteo del proceso, sin compartir entre réplicas: lo usan las pruebas unitarias (HU-30 D2). */
public class ConteoIntentosMemoria implements ConteoIntentos {

    private final ConcurrentMap<String, Deque<Instant>> intentosPorClave = new ConcurrentHashMap<>();
    private final Clock reloj;

    public ConteoIntentosMemoria(Clock reloj) {
        this.reloj = reloj;
    }

    @Override
    public boolean intentar(String clave, int maximo, Duration ventana) {
        var aceptado = new boolean[1];
        intentosPorClave.compute(clave, (k, intentos) -> {
            var ahora = Instant.now(reloj);
            var vigentes = intentos == null ? new ArrayDeque<Instant>() : intentos;
            purgar(vigentes, ahora.minus(ventana));
            // Un intento rechazado no se registra: si contara, quien insiste nunca saldría del bloqueo.
            aceptado[0] = vigentes.size() < maximo;
            if (aceptado[0]) {
                vigentes.addLast(ahora);
            }
            return vigentes.isEmpty() ? null : vigentes;
        });
        return aceptado[0];
    }

    @Override
    public boolean superaLimite(String clave, int maximo, Duration ventana) {
        var supera = new boolean[1];
        intentosPorClave.computeIfPresent(clave, (k, intentos) -> {
            purgar(intentos, Instant.now(reloj).minus(ventana));
            supera[0] = intentos.size() >= maximo;
            return intentos.isEmpty() ? null : intentos;
        });
        return supera[0];
    }

    @Override
    public void registrar(String clave) {
        intentosPorClave.compute(clave, (k, intentos) -> {
            var vigentes = intentos == null ? new ArrayDeque<Instant>() : intentos;
            vigentes.addLast(Instant.now(reloj));
            return vigentes;
        });
    }

    @Override
    public void limpiar(String clave) {
        intentosPorClave.remove(clave);
    }

    private static void purgar(Deque<Instant> intentos, Instant limite) {
        while (!intentos.isEmpty() && !intentos.peekFirst().isAfter(limite)) {
            intentos.pollFirst();
        }
    }
}
