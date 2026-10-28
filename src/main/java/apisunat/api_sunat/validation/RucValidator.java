package apisunat.api_sunat.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Set;

public class RucValidator implements ConstraintValidator<Ruc, String> {

    private static final int[] PESOS = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};
    private static final Set<String> PREFIJOS = Set.of("10", "15", "16", "17", "20");

    @Override
    public boolean isValid(String ruc, ConstraintValidatorContext contexto) {
        return ruc == null || esValido(ruc);
    }

    public static boolean esValido(String ruc) {
        if (ruc.length() != 11 || !PREFIJOS.contains(ruc.substring(0, 2))) {
            return false;
        }
        int suma = 0;
        for (int i = 0; i < 11; i++) {
            char c = ruc.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
            if (i < 10) {
                suma += (c - '0') * PESOS[i];
            }
        }
        int digito = 11 - (suma % 11);
        if (digito == 10) {
            digito = 0;
        } else if (digito == 11) {
            digito = 1;
        }
        return digito == ruc.charAt(10) - '0';
    }
}
