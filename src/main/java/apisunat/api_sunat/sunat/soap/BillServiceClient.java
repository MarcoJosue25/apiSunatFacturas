package apisunat.api_sunat.sunat.soap;

import apisunat.api_sunat.config.SunatProperties;
import apisunat.api_sunat.exception.SunatFaultException;
import apisunat.api_sunat.exception.SunatNoDisponibleException;
import apisunat.api_sunat.exception.SunatRespuestaInvalidaException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

// Habla con el billService de la SUNAT. Nunca decide por el código HTTP: la SUNAT manda errores SOAP con HTTP 200,
// así que siempre se mira el cuerpo. Las fallas de red salen como SunatNoDisponibleException.
@Slf4j
@Component
public class BillServiceClient {

    private static final int MAX_RESPUESTA_BYTES = 20 * 1024 * 1024;
    private static final MediaType TEXT_XML_UTF8 = MediaType.parseMediaType("text/xml;charset=UTF-8");

    private final RestClient restClient;
    private final SunatProperties propiedades;
    private final SoapEnvelopeBuilder envelopeBuilder;
    private final SoapResponseParser parser;
    private final int maxBytes;

    @Autowired
    public BillServiceClient(RestClient restClient, SunatProperties propiedades,
                             SoapEnvelopeBuilder envelopeBuilder, SoapResponseParser parser) {
        this(restClient, propiedades, envelopeBuilder, parser, MAX_RESPUESTA_BYTES);
    }

    // El tope de tamaño se puede bajar solo para probarlo sin armar respuestas gigantes
    BillServiceClient(RestClient restClient, SunatProperties propiedades,
                      SoapEnvelopeBuilder envelopeBuilder, SoapResponseParser parser, int maxBytes) {
        this.restClient = restClient;
        this.propiedades = propiedades;
        this.envelopeBuilder = envelopeBuilder;
        this.parser = parser;
        this.maxBytes = maxBytes;
    }

    public RespuestaStatus getStatus(String ticket) {
        return llamar("getStatus", envelopeBuilder.getStatus(ticket), parser::leerGetStatus);
    }

    // Envía el ZIP del comprobante y devuelve el ZIP de la constancia (CDR) que contesta la SUNAT
    public byte[] sendBill(String nombreZip, byte[] zip) {
        return llamar("sendBill", envelopeBuilder.sendBill(nombreZip, zip), parser::leerSendBill);
    }

    // Pregunta a la SUNAT por la constancia de una factura (billConsultService, que solo existe en producción)
    public RespuestaStatusCdr getStatusCdr(String rucEmisor, String tipoComprobante, String serie, String numero) {
        return llamar("getStatusCdr", propiedades.getConsultaCdr().getUrl(),
                envelopeBuilder.getStatusCdr(rucEmisor, tipoComprobante, serie, numero), parser::leerGetStatusCdr);
    }

    private <T> T llamar(String operacion, String sobre, Function<String, T> lector) {
        return llamar(operacion, propiedades.getUrl(), sobre, lector);
    }

    private <T> T llamar(String operacion, String url, String sobre, Function<String, T> lector) {
        Respuesta respuesta = enviar(operacion, url, sobre);
        if (!respuesta.estado().is2xxSuccessful()) {
            // Un error HTTP puede traer igual el detalle SOAP; si no lo trae, la SUNAT simplemente no está disponible
            try {
                lector.apply(respuesta.cuerpo());
            } catch (SunatFaultException e) {
                throw e;
            } catch (SunatRespuestaInvalidaException ignorado) {
                // sin detalle útil
            }
            throw new SunatNoDisponibleException("La SUNAT respondió con el error HTTP " + respuesta.estado().value(), false, null);
        }
        return lector.apply(respuesta.cuerpo());
    }

    private Respuesta enviar(String operacion, String url, String sobre) {
        long inicio = System.currentTimeMillis();
        try {
            Respuesta respuesta = restClient.post()
                    .uri(url)
                    .contentType(TEXT_XML_UTF8)
                    .header("SOAPAction", "urn:" + operacion)
                    .body(sobre)
                    .exchange((peticion, res) -> {
                        byte[] bytes = res.getBody().readNBytes(maxBytes + 1);
                        if (bytes.length > maxBytes) {
                            throw new SunatRespuestaInvalidaException("La respuesta de la SUNAT es demasiado grande");
                        }
                        return new Respuesta(res.getStatusCode(), new String(bytes, StandardCharsets.UTF_8));
                    });
            log.info("SUNAT {} -> HTTP {} en {} ms", operacion, respuesta.estado().value(), System.currentTimeMillis() - inicio);
            return respuesta;
        } catch (ResourceAccessException e) {
            boolean timeout = esTimeout(e);
            log.warn("SUNAT {} sin respuesta tras {} ms ({})", operacion, System.currentTimeMillis() - inicio, timeout ? "timeout" : "sin conexión");
            throw new SunatNoDisponibleException(
                    timeout ? "La SUNAT no respondió a tiempo" : "No se pudo conectar con la SUNAT", timeout, e);
        } catch (RestClientException e) {
            log.warn("SUNAT {} falló: {}", operacion, e.getClass().getSimpleName());
            throw new SunatNoDisponibleException("No se pudo completar la llamada a la SUNAT", false, e);
        }
    }

    private static boolean esTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof HttpTimeoutException || t instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private record Respuesta(HttpStatusCode estado, String cuerpo) {
    }
}
