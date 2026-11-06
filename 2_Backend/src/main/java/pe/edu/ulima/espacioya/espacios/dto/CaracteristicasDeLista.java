package pe.edu.ulima.espacioya.espacios.dto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Lista blanca de las características de la ficha (HU-23 R4, R5, STRIDE T): cada elemento es un código de la lista
 * fija y ninguno se repite. El error queda en el campo de la lista, no en {@code caracteristicas[i]}.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidadorCaracteristicasDeLista.class)
public @interface CaracteristicasDeLista {

    String message();

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
