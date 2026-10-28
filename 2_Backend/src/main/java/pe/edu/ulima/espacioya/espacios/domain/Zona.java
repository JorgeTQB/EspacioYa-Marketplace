package pe.edu.ulima.espacioya.espacios.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

/**
 * Distrito de Lima Metropolitana o del Callao: catálogo cerrado sembrado por la migración V2_2 (HU-07, DT-1).
 */
@Entity
@Table(name = "zona")
@Immutable
public class Zona {

    @Id
    @Column(length = 40)
    private String codigo;

    @Column(nullable = false, length = 60)
    private String nombre;

    protected Zona() {
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }
}
