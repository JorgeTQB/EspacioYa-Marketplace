package pe.edu.ulima.espacioya.comun.cifrado;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import org.springframework.stereotype.Component;

/**
 * Cifra al guardar y descifra al leer los campos anotados con
 * {@code @Convert(converter = ConvertidorCifrado.class)}. Hibernate obtiene la instancia del
 * contenedor de Spring, por eso recibe el {@link CifradorCampos} por constructor.
 */
@Component
@Converter
public class ConvertidorCifrado implements AttributeConverter<String, String> {

    private final CifradorCampos cifrador;

    public ConvertidorCifrado(CifradorCampos cifrador) {
        this.cifrador = cifrador;
    }

    @Override
    public String convertToDatabaseColumn(String claro) {
        return cifrador.cifrar(claro);
    }

    @Override
    public String convertToEntityAttribute(String guardado) {
        return cifrador.descifrar(guardado);
    }
}
