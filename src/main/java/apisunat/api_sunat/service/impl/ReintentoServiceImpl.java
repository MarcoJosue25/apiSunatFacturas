package apisunat.api_sunat.service.impl;

import apisunat.api_sunat.config.ClockConfig;
import apisunat.api_sunat.config.SunatProperties;
import apisunat.api_sunat.dto.ResultadoEmision;
import apisunat.api_sunat.exception.ComprobanteNoEncontradoException;
import apisunat.api_sunat.exception.SunatFaultException;
import apisunat.api_sunat.exception.SunatNoDisponibleException;
import apisunat.api_sunat.exception.SunatRespuestaInvalidaException;
import apisunat.api_sunat.model.Comprobante;
import apisunat.api_sunat.model.EnvioIntento;
import apisunat.api_sunat.model.enums.ClaseResultado;
import apisunat.api_sunat.model.enums.EstadoComprobante;
import apisunat.api_sunat.repository.ComprobanteRepository;
import apisunat.api_sunat.repository.EnvioIntentoRepository;
import apisunat.api_sunat.service.ComprobanteService;
import apisunat.api_sunat.service.ReintentoService;
import apisunat.api_sunat.sunat.cdr.CdrParser;
import apisunat.api_sunat.sunat.cdr.CdrResultado;
import apisunat.api_sunat.sunat.errores.ClasificadorErrores;
import apisunat.api_sunat.sunat.errores.PlazoEnvio;
import apisunat.api_sunat.sunat.errores.PoliticaBackoff;
import apisunat.api_sunat.sunat.soap.BillServiceClient;
import apisunat.api_sunat.sunat.soap.RespuestaStatusCdr;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

// Vuelve a enviar lo que no llegó a la SUNAT. Todo el estado está en la base de datos (estado, intentos y próximo intento),
// así que sobrevive a un reinicio y no hay nada que recordar en memoria.
@Slf4j
@Service
@RequiredArgsConstructor
public class ReintentoServiceImpl implements ReintentoService {

    private static final List<EstadoComprobante> REENVIABLES = List.of(EstadoComprobante.PENDIENTE, EstadoComprobante.ERROR_TEMPORAL);

    private final SunatProperties propiedades;
    private final Clock clock;
    private final TransactionTemplate transaccion;
    private final ComprobanteRepository comprobantes;
    private final EnvioIntentoRepository intentos;
    private final ComprobanteService comprobanteService;
    private final BillServiceClient cliente;
    private final CdrParser cdrParser;
    private final ClasificadorErrores clasificador;
    private final PlazoEnvio plazo;
    private final PoliticaBackoff backoff;

    @Override
    public int procesarPendientes() {
        LocalDateTime ahora = ahora();
        int trabajados = recuperarAtascados(ahora);
        List<Long> ids = comprobantes.idsPendientes(REENVIABLES, ahora, PageRequest.of(0, propiedades.getReintentos().getLote()));
        for (Long id : ids) {
            try {
                procesar(id);
                trabajados++;
            } catch (RuntimeException e) {
                // Un comprobante con problemas no debe impedir que se procesen los demás
                log.error("No se pudo reintentar el comprobante {}: {}", id, e.toString());
            }
        }
        if (trabajados > 0) {
            log.info("Reintentos: {} comprobante(s) trabajado(s)", trabajados);
        }
        return trabajados;
    }

    // La aplicación se cayó con el comprobante "enviándose": no se sabe si la SUNAT lo recibió, así que vuelve a la cola de reintentos
    private int recuperarAtascados(LocalDateTime ahora) {
        LocalDateTime limite = ahora.minusMinutes(propiedades.getReintentos().getMinutosMaximoEnviando());
        int recuperados = 0;
        for (Long id : comprobantes.idsEnviandoDesde(limite)) {
            try {
                transaccion.executeWithoutResult(estado -> {
                    Comprobante c = comprobantes.findById(id).orElseThrow(() -> new ComprobanteNoEncontradoException(id));
                    c.marcarErrorTemporal("El envío se interrumpió sin respuesta de la SUNAT", ahora, ahora);
                    comprobantes.saveAndFlush(c);
                    intentos.save(EnvioIntento.builder().comprobanteId(id).fecha(ahora).resultado("SIN_RESPUESTA")
                            .descripcion("El envío se interrumpió sin respuesta de la SUNAT").build());
                });
                log.warn("El comprobante {} llevaba más de {} minutos enviándose: vuelve a la cola de reintentos", id,
                        propiedades.getReintentos().getMinutosMaximoEnviando());
                recuperados++;
            } catch (OptimisticLockingFailureException | IllegalStateException e) {
                log.debug("El comprobante {} cambió mientras se recuperaba: se deja", id);
            }
        }
        return recuperados;
    }

    @Override
    public void procesar(Long id) {
        LocalDateTime ahora = ahora();
        Comprobante c = comprobantes.findById(id).orElseThrow(() -> new ComprobanteNoEncontradoException(id));
        if (!REENVIABLES.contains(c.getEstado())) {
            return;
        }
        try {
            if (plazo.vencido(c.getFechaEmision(), ahora)) {
                vencer(id, ahora);
            } else if (propiedades.getConsultaCdr().isHabilitada() && c.getIntentosFallidos() > 0) {
                consultarYReenviar(c, ahora);
            } else {
                comprobanteService.enviar(id);
            }
        } catch (OptimisticLockingFailureException | IllegalStateException e) {
            // Otro proceso tomó este comprobante al mismo tiempo (o ya cambió de estado): no se envía dos veces
            log.info("El comprobante {} lo tomó otro proceso: no se reintenta aquí ({})", id, e.getClass().getSimpleName());
        }
    }

    private void vencer(Long id, LocalDateTime ahora) {
        transaccion.executeWithoutResult(estado -> {
            Comprobante c = comprobantes.findById(id).orElseThrow(() -> new ComprobanteNoEncontradoException(id));
            c.marcarVencido(ahora);
            comprobantes.saveAndFlush(c);
            intentos.save(EnvioIntento.builder().comprobanteId(id).fecha(ahora).resultado("VENCIDO")
                    .descripcion("Pasó el plazo de envío (" + propiedades.getPlazoEnvioDias() + " día(s) después de la emisión)").build());
            log.warn("El comprobante {} VENCIÓ sin llegar a la SUNAT: el plazo de envío pasó. Hay que emitir una factura nueva.", c.numero());
        });
    }

    // Antes de volver a mandar una factura de la que no se sabe nada, se le pregunta a la SUNAT si ya la tiene
    private void consultarYReenviar(Comprobante c, LocalDateTime ahora) {
        RespuestaStatusCdr respuesta;
        try {
            respuesta = cliente.getStatusCdr(propiedades.getRuc(), Comprobante.TIPO_FACTURA, c.getSerie(), String.valueOf(c.getCorrelativo()));
        } catch (SunatNoDisponibleException | SunatRespuestaInvalidaException | SunatFaultException e) {
            // Sin saber si ya la tiene, reenviar podría duplicar el trabajo: se espera y se vuelve a consultar
            log.warn("No se pudo consultar la constancia de {}: {}", c.numero(), e.getMessage());
            posponer(c.getId(), ahora);
            return;
        }
        switch (respuesta.getStatusCode()) {
            case "0001", "0002" -> adoptar(c, respuesta, ahora);
            case "0011" -> comprobanteService.enviar(c.getId());   // la SUNAT no la tiene: se envía
            default -> {
                // De baja, RUC no inscrito, no le pertenece, código desconocido...: no se reenvía sin que alguien mire
                log.warn("La consulta de {} dio el código {}: se deja para revisar", c.numero(), respuesta.getStatusCode());
                registrar(c.getId(), ResultadoEmision.builder().nombreArchivo(c.numero()).clase(ClaseResultado.DESCONOCIDO)
                        .codigo(respuesta.getStatusCode()).descripcion(respuesta.getStatusMessage()).observaciones(List.of()).build(), ahora);
            }
        }
    }

    // La SUNAT ya tenía el comprobante: se guarda su constancia y no se reenvía
    private void adoptar(Comprobante c, RespuestaStatusCdr respuesta, LocalDateTime ahora) {
        boolean aceptada = "0001".equals(respuesta.getStatusCode());
        ResultadoEmision resultado;
        if (respuesta.getConstancia() != null) {
            CdrResultado cdr = cdrParser.leer(respuesta.getConstancia());
            resultado = ResultadoEmision.builder().nombreArchivo(c.numero()).clase(clasificador.clasificar(cdr)).codigo(cdr.getCodigo())
                    .descripcion(cdr.getDescripcion()).observaciones(cdr.getObservaciones()).cdrZip(respuesta.getConstancia()).build();
        } else {
            // La SUNAT dijo que existe pero no mandó la constancia: se guarda el estado sin ella
            resultado = ResultadoEmision.builder().nombreArchivo(c.numero())
                    .clase(aceptada ? ClaseResultado.ACEPTADO : ClaseResultado.RECHAZO).codigo(aceptada ? "0" : respuesta.getStatusCode())
                    .descripcion(respuesta.getStatusMessage()).observaciones(List.of()).build();
        }
        log.info("La SUNAT ya tenía {}: se recuperó su constancia ({})", c.numero(), resultado.getClase());
        registrar(c.getId(), resultado, ahora);
    }

    // Guarda un resultado que no vino de enviar: se pasa por ENVIANDO dentro de la misma transacción para respetar los estados
    private void registrar(Long id, ResultadoEmision resultado, LocalDateTime ahora) {
        transaccion.executeWithoutResult(estado -> {
            Comprobante c = comprobantes.findById(id).orElseThrow(() -> new ComprobanteNoEncontradoException(id));
            c.marcarEnviando(ahora);
            c.registrarResultado(resultado, ahora, ahora.plus(backoff.espera(c.getIntentosFallidos() + 1)));
            comprobantes.saveAndFlush(c);
            intentos.save(EnvioIntento.builder().comprobanteId(id).fecha(ahora).resultado(resultado.getClase().name())
                    .codigo(resultado.getCodigo()).descripcion(recortar("Constancia consultada a la SUNAT: " + resultado.getDescripcion())).build());
        });
    }

    private void posponer(Long id, LocalDateTime ahora) {
        transaccion.executeWithoutResult(estado -> {
            Comprobante c = comprobantes.findById(id).orElseThrow(() -> new ComprobanteNoEncontradoException(id));
            c.posponer(ahora.plus(backoff.espera(Math.max(1, c.getIntentosFallidos()))), ahora);
            comprobantes.saveAndFlush(c);
        });
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(clock.withZone(ClockConfig.ZONA_LIMA));
    }

    private static String recortar(String texto) {
        return texto == null || texto.length() <= 2000 ? texto : texto.substring(0, 2000);
    }
}
