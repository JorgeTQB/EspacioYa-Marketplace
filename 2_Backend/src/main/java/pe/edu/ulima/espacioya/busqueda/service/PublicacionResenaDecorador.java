package pe.edu.ulima.espacioya.busqueda.service;

import java.util.Objects;

import pe.edu.ulima.espacioya.busqueda.domain.Resena;

/**
 * ★ Decorator, rol <b>Decorator</b> (HU-24, DT-20): guarda la {@link PublicacionResena} envuelta y le reenvía
 * {@link #publicar}. Cada ConcreteDecorator redefine {@code publicar}, agrega su comportamiento antes, alrededor o
 * después, y llama a {@code super.publicar}.
 */
public abstract class PublicacionResenaDecorador implements PublicacionResena {

    private final PublicacionResena envuelta;

    protected PublicacionResenaDecorador(PublicacionResena envuelta) {
        this.envuelta = Objects.requireNonNull(envuelta, "envuelta");
    }

    @Override
    public Resena publicar(SolicitudResena solicitud) {
        return envuelta.publicar(solicitud);
    }

    /** De paquete: solo para fijar el orden de la cadena en las pruebas (R44). */
    PublicacionResena envuelta() {
        return envuelta;
    }
}
