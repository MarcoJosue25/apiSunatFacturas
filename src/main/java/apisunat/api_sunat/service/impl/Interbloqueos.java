package apisunat.api_sunat.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;

import java.util.function.Supplier;

// MySQL puede abortar una transacción por interbloqueo aunque el código esté bien hecho; su propia documentación pide volver a
// intentarla. Como la transacción se deshace entera (también el número que reservó), repetirla es seguro.
@Slf4j
final class Interbloqueos {

    static final int MAX_INTENTOS = 3;

    private Interbloqueos() {
    }

    static void conReintento(Runnable accion) {
        conReintento(() -> {
            accion.run();
            return null;
        });
    }

    static <T> T conReintento(Supplier<T> accion) {
        for (int intento = 1; ; intento++) {
            try {
                return accion.get();
            } catch (PessimisticLockingFailureException e) {
                if (intento == MAX_INTENTOS) {
                    throw e;
                }
                log.warn("Interbloqueo al reservar el número (intento {} de {}): se vuelve a intentar", intento, MAX_INTENTOS);
            }
        }
    }
}
