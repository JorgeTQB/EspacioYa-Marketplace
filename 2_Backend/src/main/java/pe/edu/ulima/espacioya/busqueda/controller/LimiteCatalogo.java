package pe.edu.ulima.espacioya.busqueda.controller;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import pe.edu.ulima.espacioya.comun.limite.ReglaLimite;

/**
 * Cupo por IP del catálogo público (HU-10 DT-13, §9.9): búsqueda, ficha y filtros comparten el grupo
 * {@link #GRUPO}. HU-11 y HU-23 crean sus reglas con {@link #regla(String, String)}.
 */
@Component
public class LimiteCatalogo {

    public static final String GRUPO = "catalogo";

    private final int maximo;
    private final Duration ventana;

    public LimiteCatalogo(@Value("${espacioya.limite.catalogo.maximo}") int maximo,
                          @Value("${espacioya.limite.catalogo.ventana}") Duration ventana) {
        if (maximo < 1) {
            throw new IllegalStateException("espacioya.limite.catalogo.maximo debe ser al menos 1");
        }
        if (ventana == null || ventana.isZero() || ventana.isNegative()) {
            throw new IllegalStateException("espacioya.limite.catalogo.ventana debe ser una duración positiva");
        }
        this.maximo = maximo;
        this.ventana = ventana;
    }

    public int maximo() {
        return maximo;
    }

    public Duration ventana() {
        return ventana;
    }

    public ReglaLimite regla(String metodo, String patron) {
        return new ReglaLimite(metodo, patron, maximo, ventana, GRUPO);
    }
}
