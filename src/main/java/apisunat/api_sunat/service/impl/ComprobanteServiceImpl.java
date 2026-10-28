package apisunat.api_sunat.service.impl;

import apisunat.api_sunat.config.ClockConfig;
import apisunat.api_sunat.config.SunatProperties;
import apisunat.api_sunat.dto.FacturaRequest;
import apisunat.api_sunat.dto.FacturaResponse;
import apisunat.api_sunat.dto.ItemRequest;
import apisunat.api_sunat.dto.ResultadoEmision;
import apisunat.api_sunat.exception.ComprobanteNoEncontradoException;
import apisunat.api_sunat.exception.SolicitudInvalidaException;
import apisunat.api_sunat.exception.SunatNoDisponibleException;
import apisunat.api_sunat.exception.SunatRespuestaInvalidaException;
import apisunat.api_sunat.model.Comprobante;
import apisunat.api_sunat.model.EnvioIntento;
import apisunat.api_sunat.repository.ComprobanteRepository;
import apisunat.api_sunat.repository.EnvioIntentoRepository;
import apisunat.api_sunat.service.ComprobanteService;
import apisunat.api_sunat.service.FacturaService;
import apisunat.api_sunat.service.SerieService;
import apisunat.api_sunat.sunat.calculo.CalculadoraIgv;
import apisunat.api_sunat.sunat.calculo.LineaCalculada;
import apisunat.api_sunat.sunat.calculo.TotalesDocumento;
import apisunat.api_sunat.sunat.errores.PoliticaBackoff;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

// El recorrido completo con base de datos. Las transacciones son cortas y nunca incluyen la llamada a la SUNAT:
//   1. (transacción) reservar el número, armar y firmar el XML y guardar el comprobante. Si algo falla, el número no se pierde.
//   2. (transacción) marcarlo ENVIANDO.
//   3. enviar a la SUNAT, sin transacción abierta.
//   4. (transacción) guardar lo que contestó la SUNAT y el intento.
@Slf4j
@Service
@RequiredArgsConstructor
public class ComprobanteServiceImpl implements ComprobanteService {

    private static final int MAX_INTENTOS_INTERBLOQUEO = 3;
    private static final String RESULTADO_SIN_RESPUESTA = "SIN_RESPUESTA";

    private final SunatProperties propiedades;
    private final Clock clock;
    private final TransactionTemplate transaccion;
    private final SerieService serieService;
    private final FacturaService facturaService;
    private final PoliticaBackoff backoff;
    private final ComprobanteRepository comprobantes;
    private final EnvioIntentoRepository intentos;

    @Override
    public Emision emitir(FacturaRequest request) {
        Optional<Comprobante> existente = comprobantes.findByIdExterno(request.getIdExterno());
        if (existente.isPresent()) {
            log.info("La factura {} ya existía como {}: no se envía de nuevo", request.getIdExterno(), existente.get().numero());
            return new Emision(responder(existente.get()), false);
        }

        String serie = propiedades.getSerieFactura();
        serieService.asegurar(Comprobante.TIPO_FACTURA, serie);
        LocalDateTime emision = ahora();

        Comprobante guardado;
        try {
            guardado = conReintentoPorInterbloqueo(() -> transaccion.execute(estado -> reservarYGuardar(request, serie, emision)));
        } catch (DataIntegrityViolationException e) {
            // Otra petición con el mismo idExterno guardó primero: su transacción ganó y la nuestra se deshizo, sin gastar número
            Optional<Comprobante> ganador = comprobantes.findByIdExterno(request.getIdExterno());
            if (ganador.isPresent()) {
                log.info("La factura {} la guardó otra petición al mismo tiempo: se devuelve esa", request.getIdExterno());
                return new Emision(responder(ganador.get()), false);
            }
            throw e;
        }
        log.info("Factura {} guardada como {} (id {})", request.getIdExterno(), guardado.numero(), guardado.getId());
        return new Emision(enviar(guardado.getId()), true);
    }

    // MySQL puede abortar una transacción por interbloqueo aunque el código esté bien hecho; su propia documentación pide
    // volver a intentarla. Como la transacción se deshace entera (también el número), repetirla es seguro.
    private Comprobante conReintentoPorInterbloqueo(Supplier<Comprobante> accion) {
        for (int intento = 1; ; intento++) {
            try {
                return accion.get();
            } catch (PessimisticLockingFailureException e) {
                if (intento == MAX_INTENTOS_INTERBLOQUEO) {
                    throw e;
                }
                log.warn("Interbloqueo al reservar el número (intento {} de {}): se vuelve a intentar", intento, MAX_INTENTOS_INTERBLOQUEO);
            }
        }
    }

    private Comprobante reservarYGuardar(FacturaRequest request, String serie, LocalDateTime emision) {
        long correlativo = serieService.siguiente(Comprobante.TIPO_FACTURA, serie);
        byte[] xml;
        try {
            xml = facturaService.prepararXml(request, serie, correlativo, emision);
        } catch (IllegalArgumentException e) {
            throw new SolicitudInvalidaException(e.getMessage(), e);
        }
        LocalDateTime ahora = ahora();
        TotalesDocumento totales = totales(request);
        Comprobante comprobante = Comprobante.nuevo(request.getIdExterno(), serie, correlativo, emision,
                request.getCliente().getRuc(), request.getCliente().getRazonSocial(), totales.getTotalGravado(), totales.getTotalIgv(),
                totales.getImporteTotal(), xml, ahora,
                ahora.plus(backoff.espera(1)));   // si la petición se cae antes de enviar, el reintento la recoge pasado este tiempo
        // flush: así un idExterno repetido falla aquí, dentro de la transacción, y no al terminarla
        return comprobantes.saveAndFlush(comprobante);
    }

    @Override
    public FacturaResponse enviar(Long id) {
        Comprobante aEnviar = transaccion.execute(estado -> {
            Comprobante c = comprobantes.findById(id).orElseThrow(() -> new ComprobanteNoEncontradoException(id));
            c.marcarEnviando(ahora());
            return comprobantes.saveAndFlush(c);
        });

        ResultadoEmision resultado;
        try {
            resultado = facturaService.enviar(aEnviar.getSerie(), aEnviar.getCorrelativo(), aEnviar.getXmlFirmado());
        } catch (SunatNoDisponibleException | SunatRespuestaInvalidaException e) {
            // No se sabe si la SUNAT recibió la factura: queda guardada para volver a intentar
            log.warn("Sin respuesta útil de la SUNAT para {}: {}", aEnviar.numero(), e.getMessage());
            return guardarSinRespuesta(id, e.getMessage());
        } catch (RuntimeException e) {
            guardarSinRespuesta(id, "Error interno al enviar");
            throw e;
        }

        return transaccion.execute(estado -> {
            Comprobante c = comprobantes.findById(id).orElseThrow(() -> new ComprobanteNoEncontradoException(id));
            LocalDateTime ahora = ahora();
            c.registrarResultado(resultado, ahora, ahora.plus(backoff.espera(c.getIntentosFallidos() + 1)));
            comprobantes.saveAndFlush(c);
            intentos.save(EnvioIntento.builder().comprobanteId(id).fecha(ahora).resultado(resultado.getClase().name())
                    .codigo(resultado.getCodigo()).descripcion(recortar(resultado.getDescripcion())).build());
            log.info("Comprobante {} (id {}): {} (código {})", c.numero(), id, c.getEstado(), c.getCodigoSunat());
            return responder(c);
        });
    }

    private FacturaResponse guardarSinRespuesta(Long id, String motivo) {
        return transaccion.execute(estado -> {
            Comprobante c = comprobantes.findById(id).orElseThrow(() -> new ComprobanteNoEncontradoException(id));
            LocalDateTime ahora = ahora();
            c.marcarErrorTemporal(recortar(motivo), ahora, ahora.plus(backoff.espera(c.getIntentosFallidos() + 1)));
            comprobantes.saveAndFlush(c);
            intentos.save(EnvioIntento.builder().comprobanteId(id).fecha(ahora).resultado(RESULTADO_SIN_RESPUESTA)
                    .descripcion(recortar(motivo)).build());
            return responder(c);
        });
    }

    @Override
    public FacturaResponse obtener(Long id) {
        return responder(comprobantes.findById(id).orElseThrow(() -> new ComprobanteNoEncontradoException(id)));
    }

    @Override
    public byte[] obtenerXml(Long id) {
        return comprobantes.findById(id).orElseThrow(() -> new ComprobanteNoEncontradoException(id)).getXmlFirmado();
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(clock.withZone(ClockConfig.ZONA_LIMA));
    }

    private static TotalesDocumento totales(FacturaRequest request) {
        List<LineaCalculada> lineas = new ArrayList<>();
        for (ItemRequest item : request.getItems()) {
            lineas.add(CalculadoraIgv.calcularLinea(item.getPrecioUnitario(), item.getCantidad()));
        }
        return CalculadoraIgv.calcularTotales(lineas);
    }

    private static String recortar(String texto) {
        return texto == null || texto.length() <= 2000 ? texto : texto.substring(0, 2000);
    }

    private static FacturaResponse responder(Comprobante c) {
        List<String> observaciones = c.getObservaciones() == null ? List.of() : List.of(c.getObservaciones().split("\n"));
        return FacturaResponse.builder()
                .id(c.getId()).idExterno(c.getIdExterno()).numero(c.numero()).estado(c.getEstado())
                .fechaEmision(c.getFechaEmision()).importeTotal(c.getImporteTotal())
                .codigoSunat(c.getCodigoSunat()).descripcionSunat(c.getDescripcionSunat()).observaciones(observaciones)
                .build();
    }
}
