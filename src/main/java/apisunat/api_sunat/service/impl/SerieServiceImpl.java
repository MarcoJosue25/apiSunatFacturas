package apisunat.api_sunat.service.impl;

import apisunat.api_sunat.model.Serie;
import apisunat.api_sunat.repository.SerieRepository;
import apisunat.api_sunat.service.SerieService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SerieServiceImpl implements SerieService {

    private static final Pattern TIPO = Pattern.compile("\\d{2}");
    private static final Pattern SERIE = Pattern.compile("[A-Z0-9]{4}");

    private final SerieRepository repositorio;

    // Se llama antes de abrir la transacción del comprobante. Es una transacción corta que termina enseguida: así no queda ningún
    // bloqueo puesto cuando después se pide el número. (Hacerlo dentro de la misma transacción con INSERT IGNORE provoca un
    // interbloqueo entre hilos, y hacerlo en una transacción anidada agota las conexiones: ver docs/ERRORES.md E-020 y E-021.)
    @Override
    @Transactional
    public void asegurar(String tipoDocumento, String serie) {
        validar(tipoDocumento, serie);
        // Casi siempre la serie ya existe: se comprueba con una lectura normal para no ejecutar el INSERT (que, aunque no inserte
        // nada, toma un bloqueo sobre la fila existente y compite con quienes la tienen bloqueada para pedir su número)
        if (!repositorio.existsByTipoDocumentoAndSerie(tipoDocumento, serie)) {
            repositorio.crearSiNoExiste(tipoDocumento, serie);
        }
    }

    // MANDATORY: sin una transacción abierta falla, porque el bloqueo de la fila solo dura lo que dura la transacción
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public long siguiente(String tipoDocumento, String serie) {
        validar(tipoDocumento, serie);
        Serie fila = repositorio.findByTipoDocumentoAndSerie(tipoDocumento, serie)
                .orElseThrow(() -> new IllegalStateException("La serie " + serie + " no existe: hay que llamar a asegurar() antes"));
        return fila.siguiente();
    }

    private static void validar(String tipoDocumento, String serie) {
        if (tipoDocumento == null || !TIPO.matcher(tipoDocumento).matches()) {
            throw new IllegalArgumentException("El tipo de documento debe tener 2 dígitos");
        }
        if (serie == null || !SERIE.matcher(serie).matches()) {
            throw new IllegalArgumentException("La serie debe tener 4 caracteres: mayúsculas y números");
        }
    }
}
