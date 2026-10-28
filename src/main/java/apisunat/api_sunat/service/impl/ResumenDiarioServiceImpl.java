package apisunat.api_sunat.service.impl;

import apisunat.api_sunat.config.ClockConfig;
import apisunat.api_sunat.config.SunatProperties;
import apisunat.api_sunat.dto.ResumenResponse;
import apisunat.api_sunat.exception.OperacionNoPermitidaException;
import apisunat.api_sunat.exception.ResumenNoEncontradoException;
import apisunat.api_sunat.exception.SunatFaultException;
import apisunat.api_sunat.exception.SunatNoDisponibleException;
import apisunat.api_sunat.exception.SunatRespuestaInvalidaException;
import apisunat.api_sunat.model.Comprobante;
import apisunat.api_sunat.model.ResumenDiario;
import apisunat.api_sunat.model.enums.ClaseResultado;
import apisunat.api_sunat.model.enums.EstadoComprobante;
import apisunat.api_sunat.model.enums.EstadoResumen;
import apisunat.api_sunat.repository.ComprobanteRepository;
import apisunat.api_sunat.repository.ResumenDiarioRepository;
import apisunat.api_sunat.service.ResumenDiarioService;
import apisunat.api_sunat.sunat.cdr.CdrParser;
import apisunat.api_sunat.sunat.cdr.CdrResultado;
import apisunat.api_sunat.sunat.errores.ClasificadorErrores;
import apisunat.api_sunat.sunat.errores.PoliticaBackoff;
import apisunat.api_sunat.sunat.firma.CertificadoLoader;
import apisunat.api_sunat.sunat.firma.XmlSigner;
import apisunat.api_sunat.sunat.soap.BillServiceClient;
import apisunat.api_sunat.sunat.soap.RespuestaStatus;
import apisunat.api_sunat.sunat.xml.LineaResumen;
import apisunat.api_sunat.sunat.xml.ResumenDiarioXmlBuilder;
import apisunat.api_sunat.sunat.xml.XsdValidator;
import apisunat.api_sunat.util.ZipUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// El resumen diario de boletas: juntar las boletas del día, firmar el resumen, enviarlo, consultar el ticket y resolver cada boleta.
// Las llamadas a la SUNAT nunca van dentro de una transacción.
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumenDiarioServiceImpl implements ResumenDiarioService {

    private static final int INTENTOS_AL_CREAR = 3;
    private static final int MAX_DESCRIPCION = 2000;
    private static final List<EstadoResumen> POR_TRABAJAR = List.of(EstadoResumen.GENERADO, EstadoResumen.ERROR_TEMPORAL, EstadoResumen.ENVIADO);
    private static final DateTimeFormatter FECHA_NOMBRE = DateTimeFormatter.BASIC_ISO_DATE;

    private final SunatProperties propiedades;
    private final Clock clock;
    private final TransactionTemplate transaccion;
    private final ComprobanteRepository comprobantes;
    private final ResumenDiarioRepository resumenes;
    private final ResumenDiarioXmlBuilder xmlBuilder;
    private final CertificadoLoader certificadoLoader;
    private final XmlSigner firmante;
    private final XsdValidator xsdValidator;
    private final BillServiceClient cliente;
    private final CdrParser cdrParser;
    private final ClasificadorErrores clasificador;
    private final PoliticaBackoff backoff;

    // ---------------------------------------------------------------------------------------------------- generar

    @Override
    public List<Long> generarPendientes() {
        List<Long> pendientes = comprobantes.idsBoletasPendientesDeResumen();
        if (pendientes.isEmpty()) {
            return List.of();
        }
        // Las boletas se agrupan por su fecha de emisión (en Lima) y cada grupo se parte en bloques de 500
        Map<LocalDate, List<Long>> porFecha = new LinkedHashMap<>();
        for (Comprobante c : comprobantes.findAllById(pendientes).stream()
                .sorted(java.util.Comparator.comparing(Comprobante::getFechaEmision).thenComparing(Comprobante::getCorrelativo)).toList()) {
            porFecha.computeIfAbsent(c.getFechaEmision().toLocalDate(), f -> new ArrayList<>()).add(c.getId());
        }
        List<Long> creados = new ArrayList<>();
        for (Map.Entry<LocalDate, List<Long>> dia : porFecha.entrySet()) {
            List<Long> ids = dia.getValue();
            for (int desde = 0; desde < ids.size(); desde += ResumenDiarioXmlBuilder.MAX_LINEAS) {
                List<Long> bloque = ids.subList(desde, Math.min(ids.size(), desde + ResumenDiarioXmlBuilder.MAX_LINEAS));
                Long id = crearConReintento(dia.getKey(), bloque);
                if (id != null) {
                    creados.add(id);
                }
            }
        }
        return creados;
    }

    // Dos procesos que arman resúmenes a la vez pueden pedir el mismo correlativo del día: el segundo choca con la unicidad, se deshace
    // sin dejar nada y vuelve a intentar con el correlativo siguiente.
    private Long crearConReintento(LocalDate fecha, List<Long> idsBoletas) {
        for (int intento = 1; ; intento++) {
            try {
                return Interbloqueos.conReintento(() -> transaccion.execute(estado -> crear(fecha, idsBoletas)));
            } catch (DataIntegrityViolationException | OptimisticLockingFailureException e) {
                if (intento == INTENTOS_AL_CREAR) {
                    throw e;
                }
                log.warn("Otro proceso armó un resumen del {} al mismo tiempo (intento {}): se vuelve a intentar", fecha, intento);
            }
        }
    }

    private Long crear(LocalDate fecha, List<Long> idsBoletas) {
        List<Comprobante> boletas = comprobantes.findAllById(idsBoletas).stream()
                .filter(c -> c.getEstado() == EstadoComprobante.PENDIENTE_DE_RESUMEN)
                .sorted(java.util.Comparator.comparing(Comprobante::getCorrelativo)).toList();
        if (boletas.isEmpty()) {
            return null;   // otro proceso ya las tomó
        }
        int correlativo = resumenes.maxCorrelativoDelDia(fecha) + 1;
        if (correlativo > 99_999) {
            throw new IllegalStateException("Ya se usaron los 99999 resúmenes del " + fecha);
        }
        LocalDateTime ahora = ahora();
        String nombre = "RC-" + fecha.format(FECHA_NOMBRE) + "-" + correlativo;

        List<LineaResumen> lineas = new ArrayList<>();
        for (Comprobante b : boletas) {
            boolean anonima = "0".equals(b.getTipoDocumentoCliente());
            lineas.add(new LineaResumen(b.numero(), anonima ? "-" : b.getTipoDocumentoCliente(), anonima ? "-" : b.getDocumentoCliente(),
                    b.getTotalGravado(), b.getTotalIgv(), b.getImporteTotal()));
        }
        byte[] firmado = firmante.firmar(xmlBuilder.construir(nombre, fecha, ahora.toLocalDate(), lineas), certificadoLoader.cargar());
        List<String> errores = xsdValidator.validarResumen(firmado);
        if (!errores.isEmpty()) {
            throw new IllegalStateException("El XML armado para " + nombre + " no cumple el esquema de la SUNAT: " + errores);
        }

        ResumenDiario resumen = resumenes.saveAndFlush(ResumenDiario.nuevo(nombre, fecha, correlativo, ahora.toLocalDate(), firmado, ahora));
        for (Comprobante b : boletas) {
            b.incluirEnResumen(resumen.getId(), ahora);
        }
        comprobantes.saveAll(boletas);
        comprobantes.flush();
        log.info("Resumen {} armado con {} boleta(s) (id {})", nombre, boletas.size(), resumen.getId());
        return resumen.getId();
    }

    // ---------------------------------------------------------------------------------------------------- enviar

    @Override
    public void enviar(Long id) {
        ResumenDiario r = cargar(id);
        if (r.getEstado() != EstadoResumen.GENERADO && r.getEstado() != EstadoResumen.ERROR_TEMPORAL) {
            throw new IllegalStateException("Un resumen " + r.getEstado() + " no se envía");
        }
        String archivo = r.nombreArchivo(propiedades.getRuc());
        byte[] zip = ZipUtil.comprimir(archivo + ".xml", r.getXmlFirmado());

        log.info("Enviando el resumen {} a la SUNAT", r.getNombre());
        String ticket;
        try {
            ticket = cliente.sendSummary(archivo + ".zip", zip);
        } catch (SunatNoDisponibleException | SunatRespuestaInvalidaException e) {
            guardarFalloDeEnvio(id, e.getMessage());
            return;
        } catch (SunatFaultException fault) {
            resolverConFault(id, fault, false);
            return;
        }
        transaccion.executeWithoutResult(estado -> {
            ResumenDiario actual = cargar(id);
            LocalDateTime ahora = ahora();
            actual.marcarEnviado(ticket, ahora, ahora.plus(backoff.espera(1)));
            resumenes.saveAndFlush(actual);
        });
        log.info("Resumen {} recibido por la SUNAT, ticket {}", r.getNombre(), ticket);
    }

    private void guardarFalloDeEnvio(Long id, String motivo) {
        transaccion.executeWithoutResult(estado -> {
            ResumenDiario actual = cargar(id);
            LocalDateTime ahora = ahora();
            actual.marcarErrorEnvio(recortar(motivo), ahora, ahora.plus(backoff.espera(actual.getIntentosFallidos() + 1)));
            resumenes.saveAndFlush(actual);
        });
        log.warn("No se pudo enviar el resumen {}: {}", id, motivo);
    }

    // ---------------------------------------------------------------------------------------------------- consultar

    @Override
    public void consultar(Long id) {
        ResumenDiario r = cargar(id);
        if (r.getEstado() != EstadoResumen.ENVIADO || r.getTicket() == null) {
            throw new IllegalStateException("Un resumen " + r.getEstado() + " no se consulta");
        }
        RespuestaStatus respuesta;
        try {
            respuesta = cliente.getStatus(r.getTicket());
        } catch (SunatNoDisponibleException | SunatRespuestaInvalidaException e) {
            log.warn("No se pudo consultar el ticket del resumen {}: {}", r.getNombre(), e.getMessage());
            posponerConsulta(id);
            return;
        } catch (SunatFaultException fault) {
            resolverConFault(id, fault, true);
            return;
        }
        switch (respuesta.getStatusCode()) {
            case "98" -> posponerConsulta(id);   // sigue en proceso
            case "0", "99" -> resolverConConstancia(id, respuesta);
            default -> {
                // 0127 (el ticket no existe) u otro código: no se sabe qué hacer solo
                log.warn("El ticket del resumen {} dio el código {}", r.getNombre(), respuesta.getStatusCode());
                registrar(id, EstadoResumen.REVISAR, respuesta.getStatusCode(), respuesta.getContent(), null);
            }
        }
    }

    private void posponerConsulta(Long id) {
        transaccion.executeWithoutResult(estado -> {
            ResumenDiario actual = cargar(id);
            LocalDateTime ahora = ahora();
            actual.posponerConsulta(ahora.plus(backoff.espera(actual.getIntentosFallidos() + 1)), ahora);
            resumenes.saveAndFlush(actual);
        });
    }

    private void resolverConConstancia(Long id, RespuestaStatus respuesta) {
        byte[] zip;
        CdrResultado cdr;
        try {
            if (respuesta.getContent() == null || respuesta.getContent().isBlank()) {
                throw new SunatRespuestaInvalidaException("La SUNAT terminó pero no mandó la constancia");
            }
            zip = Base64.getDecoder().decode(respuesta.getContent().replaceAll("\\s", ""));
            cdr = cdrParser.leer(zip);
        } catch (IllegalArgumentException | SunatRespuestaInvalidaException e) {
            log.warn("La constancia del resumen {} no se pudo leer: {}", id, e.getMessage());
            registrar(id, EstadoResumen.REVISAR, respuesta.getStatusCode(), "No se pudo leer la constancia de la SUNAT: " + e.getMessage(), null);
            return;
        }
        if (cdr.aceptado()) {
            aceptar(id, cdr, zip);
        } else {
            rechazar(id, cdr, zip);
        }
    }

    // La SUNAT aceptó el resumen: cada boleta queda aceptada; las observaciones que nombran una boleta van a esa boleta
    private void aceptar(Long id, CdrResultado cdr, byte[] zip) {
        transaccion.executeWithoutResult(estado -> {
            ResumenDiario resumen = cargar(id);
            LocalDateTime ahora = ahora();
            List<Comprobante> boletas = comprobantes.findByResumenIdOrderByCorrelativo(id);
            List<String> sinBoleta = new ArrayList<>();
            Map<Long, List<String>> porBoleta = new LinkedHashMap<>();
            for (CdrResultado.Observacion o : cdr.getObservaciones()) {
                String linea = o.getCodigo() == null ? o.getMensaje() : o.getCodigo() + " - " + o.getMensaje();
                boolean asignada = false;
                for (Comprobante b : boletas) {
                    if (menciona(linea, b.numero())) {
                        porBoleta.computeIfAbsent(b.getId(), k -> new ArrayList<>()).add(linea);
                        asignada = true;
                    }
                }
                if (!asignada) {
                    sinBoleta.add(linea);
                }
            }
            for (Comprobante b : boletas) {
                if (b.getEstado() == EstadoComprobante.EN_RESUMEN) {
                    List<String> propias = porBoleta.get(b.getId());
                    b.aceptarPorResumen(cdr.getCodigo(), cdr.getDescripcion(), propias == null ? null : String.join("\n", propias), ahora);
                }
            }
            comprobantes.saveAll(boletas);
            boolean conObservaciones = !cdr.getObservaciones().isEmpty();
            String descripcion = cdr.getDescripcion() + (sinBoleta.isEmpty() ? "" : " | " + String.join(" | ", sinBoleta));
            resumen.registrarResultado(conObservaciones ? EstadoResumen.ACEPTADO_CON_OBSERVACIONES : EstadoResumen.ACEPTADO,
                    cdr.getCodigo(), recortar(descripcion), zip, ahora);
            resumenes.saveAndFlush(resumen);
            log.info("Resumen {} aceptado por la SUNAT: {} boleta(s)", resumen.getNombre(), boletas.size());
        });
    }

    // La SUNAT rechazó todo el resumen: las boletas siguen en él (se corrige y se reenvía). A la boleta que la SUNAT nombra se le anota qué dijo.
    private void rechazar(Long id, CdrResultado cdr, byte[] zip) {
        transaccion.executeWithoutResult(estado -> {
            ResumenDiario resumen = cargar(id);
            LocalDateTime ahora = ahora();
            String texto = cdr.getDescripcion() == null ? "" : cdr.getDescripcion();
            List<Comprobante> boletas = comprobantes.findByResumenIdOrderByCorrelativo(id);
            for (Comprobante b : boletas) {
                if (menciona(texto, b.numero())) {
                    b.anotarRespuestaDelResumen(cdr.getCodigo(), recortar(texto), ahora);
                }
            }
            comprobantes.saveAll(boletas);
            resumen.registrarResultado(EstadoResumen.RECHAZADO, cdr.getCodigo(), recortar(texto), zip, ahora);
            resumenes.saveAndFlush(resumen);
            log.warn("Resumen {} RECHAZADO por la SUNAT: {} {}", resumen.getNombre(), cdr.getCodigo(), texto);
        });
    }

    // Un error SOAP al enviar o al consultar: según su clase el resumen espera, se rechaza o queda para revisar
    private void resolverConFault(Long id, SunatFaultException fault, boolean consultando) {
        ClaseResultado clase = clasificador.clasificar(fault);
        log.warn("La SUNAT respondió con el error {} ({}) para el resumen {}", fault.getCodigo(), clase, id);
        switch (clase) {
            case TRANSITORIO -> {
                if (consultando) {
                    posponerConsulta(id);
                } else {
                    guardarFalloDeEnvio(id, "La SUNAT respondió con el error " + fault.getCodigo());
                }
            }
            case ERROR_DE_DATOS, RECHAZO -> registrar(id, EstadoResumen.RECHAZADO, fault.getCodigo(), fault.getFaultString(), null);
            default -> registrar(id, EstadoResumen.REVISAR, fault.getCodigo(), fault.getFaultString(), null);
        }
    }

    private void registrar(Long id, EstadoResumen destino, String codigo, String descripcion, byte[] zip) {
        transaccion.executeWithoutResult(estado -> {
            ResumenDiario resumen = cargar(id);
            resumen.registrarResultado(destino, codigo, recortar(descripcion), zip, ahora());
            resumenes.saveAndFlush(resumen);
        });
    }

    // ---------------------------------------------------------------------------------------------------- reenviar y otros

    @Override
    public ResumenResponse reenviar(Long id) {
        EstadoResumen actual = cargar(id).getEstado();
        if (actual != EstadoResumen.RECHAZADO) {
            throw new OperacionNoPermitidaException("Solo se reenvía un resumen rechazado, y este está " + actual);
        }
        transaccion.executeWithoutResult(estado -> {
            ResumenDiario resumen = cargar(id);
            resumen.prepararReenvio(ahora());
            resumenes.saveAndFlush(resumen);
        });
        enviar(id);
        return obtener(id);
    }

    @Override
    public ResumenResponse obtener(Long id) {
        ResumenDiario r = cargar(id);
        return ResumenResponse.builder().id(r.getId()).nombre(r.getNombre()).estado(r.getEstado()).fechaReferencia(r.getFechaReferencia())
                .ticket(r.getTicket()).codigoSunat(r.getCodigoSunat()).descripcionSunat(r.getDescripcionSunat())
                .cantidadBoletas((int) comprobantes.countByResumenId(id)).build();
    }

    @Override
    public int procesarPendientes() {
        int trabajados = 0;
        List<Long> ids = resumenes.idsPendientes(POR_TRABAJAR, ahora(), PageRequest.of(0, propiedades.getReintentos().getLote()));
        for (Long id : ids) {
            try {
                ResumenDiario r = cargar(id);
                if (r.getEstado() == EstadoResumen.ENVIADO) {
                    consultar(id);
                } else {
                    enviar(id);
                }
                trabajados++;
            } catch (OptimisticLockingFailureException | IllegalStateException e) {
                log.info("El resumen {} lo tomó otro proceso o cambió de estado: se deja ({})", id, e.getClass().getSimpleName());
            } catch (RuntimeException e) {
                log.error("No se pudo trabajar el resumen {}: {}", id, e.toString());
            }
        }
        return trabajados;
    }

    @Override
    public List<Long> generarYEnviar() {
        List<Long> creados = generarPendientes();
        for (Long id : creados) {
            try {
                enviar(id);
            } catch (RuntimeException e) {
                log.error("No se pudo enviar el resumen {}: {}", id, e.toString());
            }
        }
        return creados;
    }

    // ---------------------------------------------------------------------------------------------------- ayudas

    private ResumenDiario cargar(Long id) {
        return resumenes.findById(id).orElseThrow(() -> new ResumenNoEncontradoException(id));
    }

    // El número de una boleta (B001-12) aparece en el texto como palabra completa, no dentro de otro número (B001-123)
    static boolean menciona(String texto, String numero) {
        return texto != null && Pattern.compile("(?<![A-Za-z0-9-])" + Pattern.quote(numero) + "(?![0-9])").matcher(texto).find();
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(clock.withZone(ClockConfig.ZONA_LIMA));
    }

    private static String recortar(String texto) {
        return texto == null || texto.length() <= MAX_DESCRIPCION ? texto : texto.substring(0, MAX_DESCRIPCION);
    }
}
