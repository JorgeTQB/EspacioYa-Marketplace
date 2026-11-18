package pe.edu.ulima.espacioya.comun.correo;

import java.util.Objects;

/**
 * ★ Factory Method · Creator (HU-25 D9): "crear el correo según el evento". El envío (armar el {@link MensajeCorreo}
 * con un único destinatario y entregarlo al Target del Adapter) vive solo aquí y no conoce ningún producto concreto;
 * cada ConcreteCreator decide qué {@link Correo} crear en {@link #crearCorreo}. Un correo nuevo es una subclase y un
 * {@code record}, sin tocar esta clase ni {@link EnvioCorreo} (OCP). Las fábricas no son beans: las crea su cliente
 * con el {@link EnvioCorreo} inyectado.
 *
 * @param <E> datos del evento que necesita el correo
 */
public abstract class FabricaCorreo<E> {

    private final EnvioCorreo envio;

    protected FabricaCorreo(EnvioCorreo envio) {
        this.envio = Objects.requireNonNull(envio);
    }

    public final void enviar(String destinatario, E evento) {
        var correo = crearCorreo(evento);
        envio.enviar(new MensajeCorreo(destinatario, correo.asunto(), correo.cuerpo()));
    }

    /** El método fábrica. */
    protected abstract Correo crearCorreo(E evento);
}
