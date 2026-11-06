package pe.edu.ulima.espacioya.espacios.dto;

import java.util.HashSet;
import java.util.List;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import pe.edu.ulima.espacioya.espacios.domain.Caracteristica;

/** {@code null} es válido (R3: sin características). Inválido si un elemento no es un código o se repite. */
public final class ValidadorCaracteristicasDeLista
        implements ConstraintValidator<CaracteristicasDeLista, List<String>> {

    @Override
    public boolean isValid(List<String> caracteristicas, ConstraintValidatorContext contexto) {
        if (caracteristicas == null) {
            return true;
        }
        var vistas = new HashSet<String>();
        for (var codigo : caracteristicas) {
            if (!Caracteristica.esCodigo(codigo) || !vistas.add(codigo)) {
                return false;
            }
        }
        return true;
    }
}
