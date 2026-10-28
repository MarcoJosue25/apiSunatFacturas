package apisunat.api_sunat.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Un RUC peruano: 11 dígitos, que empiecen con 10, 15, 16, 17 o 20, y con el dígito verificador correcto.
// Un valor nulo se considera válido: para exigirlo se combina con @NotBlank.
@Documented
@Constraint(validatedBy = RucValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface Ruc {

    String message() default "El RUC no es válido";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
