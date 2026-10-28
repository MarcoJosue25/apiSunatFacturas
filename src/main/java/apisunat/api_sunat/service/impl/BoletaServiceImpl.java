package apisunat.api_sunat.service.impl;

import apisunat.api_sunat.config.ClockConfig;
import apisunat.api_sunat.config.SunatProperties;
import apisunat.api_sunat.dto.BoletaRequest;
import apisunat.api_sunat.dto.ClienteBoletaRequest;
import apisunat.api_sunat.dto.FacturaResponse;
import apisunat.api_sunat.exception.ComprobanteNoEncontradoException;
import apisunat.api_sunat.exception.SolicitudInvalidaException;
import apisunat.api_sunat.model.Comprobante;
import apisunat.api_sunat.repository.ComprobanteRepository;
import apisunat.api_sunat.service.BoletaService;
import apisunat.api_sunat.service.FacturaService;
import apisunat.api_sunat.service.SerieService;
import apisunat.api_sunat.sunat.calculo.TotalesDocumento;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// Emitir una boleta: reservar el número, armar y firmar el XML y guardarla, todo en una transacción (si algo falla, el número no se pierde).
// No se envía nada a la SUNAT: la boleta queda PENDIENTE_DE_RESUMEN hasta que el resumen diario la informe.
@Slf4j
@Service
@RequiredArgsConstructor
public class BoletaServiceImpl implements BoletaService {

    // Desde este importe (sin llegar a él) la SUNAT exige el documento del cliente
    static final BigDecimal LIMITE_SIN_DOCUMENTO = new BigDecimal("700.00");

    private final SunatProperties propiedades;
    private final Clock clock;
    private final TransactionTemplate transaccion;
    private final SerieService serieService;
    private final FacturaService facturaService;
    private final ComprobanteRepository comprobantes;

    @Override
    public Emision emitir(BoletaRequest request) {
        Optional<Comprobante> existente = comprobantes.findByIdExterno(request.getIdExterno());
        if (existente.isPresent()) {
            log.info("La boleta {} ya existía como {}", request.getIdExterno(), existente.get().numero());
            return new Emision(ComprobanteServiceImpl.responder(existente.get()), false);
        }
        validarMonto(request);

        String serie = propiedades.getSerieBoleta();
        serieService.asegurar(Comprobante.TIPO_BOLETA, serie);
        // A segundos enteros: es lo que lleva el XML (IssueTime) y lo que guarda la base, así la respuesta coincide con lo guardado
        LocalDateTime emision = ahora().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        try {
            Comprobante guardada = Interbloqueos.conReintento(() -> transaccion.execute(estado -> reservarYGuardar(request, serie, emision)));
            log.info("Boleta {} guardada como {} (id {}), pendiente de resumen", request.getIdExterno(), guardada.numero(), guardada.getId());
            return new Emision(ComprobanteServiceImpl.responder(guardada), true);
        } catch (DataIntegrityViolationException e) {
            Optional<Comprobante> ganadora = comprobantes.findByIdExterno(request.getIdExterno());
            if (ganadora.isPresent()) {
                log.info("La boleta {} la guardó otra petición al mismo tiempo: se devuelve esa", request.getIdExterno());
                return new Emision(ComprobanteServiceImpl.responder(ganadora.get()), false);
            }
            throw e;
        }
    }

    // Una boleta de más de S/ 700 necesita el documento del cliente; se comprueba antes de reservar ningún número
    private void validarMonto(BoletaRequest request) {
        TotalesDocumento totales;
        try {
            totales = TotalesDeItems.de(request.getItems());
        } catch (IllegalArgumentException e) {
            throw new SolicitudInvalidaException(e.getMessage(), e);
        }
        ClienteBoletaRequest cliente = request.getCliente();
        boolean anonima = cliente == null || cliente.esAnonimo();
        if (anonima && totales.getImporteTotal().compareTo(LIMITE_SIN_DOCUMENTO) > 0) {
            throw new SolicitudInvalidaException("Una boleta de más de S/ 700.00 necesita el documento del cliente (el total es S/ "
                    + totales.getImporteTotal().toPlainString() + ")", null);
        }
    }

    private Comprobante reservarYGuardar(BoletaRequest request, String serie, LocalDateTime emision) {
        long correlativo = serieService.siguiente(Comprobante.TIPO_BOLETA, serie);
        byte[] xml;
        TotalesDocumento totales;
        try {
            xml = facturaService.prepararXmlBoleta(request, serie, correlativo, emision);
            totales = TotalesDeItems.de(request.getItems());
        } catch (IllegalArgumentException e) {
            throw new SolicitudInvalidaException(e.getMessage(), e);
        }
        ClienteBoletaRequest cliente = request.getCliente();
        boolean anonima = cliente == null || cliente.esAnonimo();
        String tipo = anonima ? "0" : cliente.getTipoDocumento();
        String documento = anonima ? "-" : cliente.getNumeroDocumento();
        String nombre = cliente != null && cliente.getNombre() != null && !cliente.getNombre().isBlank() ? cliente.getNombre() : "CLIENTES VARIOS";
        Comprobante boleta = Comprobante.nuevaBoleta(request.getIdExterno(), serie, correlativo, emision, tipo, documento, nombre,
                totales.getTotalGravado(), totales.getTotalIgv(), totales.getImporteTotal(), xml, ahora());
        return comprobantes.saveAndFlush(boleta);
    }

    @Override
    public FacturaResponse obtener(Long id) {
        Comprobante c = comprobantes.findById(id).filter(Comprobante::esBoleta).orElseThrow(() -> new ComprobanteNoEncontradoException(id));
        return ComprobanteServiceImpl.responder(c);
    }

    @Override
    public byte[] obtenerXml(Long id) {
        return comprobantes.findById(id).filter(Comprobante::esBoleta).orElseThrow(() -> new ComprobanteNoEncontradoException(id)).getXmlFirmado();
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(clock.withZone(ClockConfig.ZONA_LIMA));
    }
}
