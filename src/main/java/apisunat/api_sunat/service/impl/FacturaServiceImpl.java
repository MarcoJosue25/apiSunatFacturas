package apisunat.api_sunat.service.impl;

import apisunat.api_sunat.config.ClockConfig;
import apisunat.api_sunat.dto.BoletaRequest;
import apisunat.api_sunat.dto.FacturaRequest;
import apisunat.api_sunat.dto.ResultadoEmision;
import apisunat.api_sunat.exception.SunatFaultException;
import apisunat.api_sunat.exception.SunatRespuestaInvalidaException;
import apisunat.api_sunat.model.enums.ClaseResultado;
import apisunat.api_sunat.service.FacturaService;
import apisunat.api_sunat.sunat.cdr.CdrParser;
import apisunat.api_sunat.sunat.cdr.CdrResultado;
import apisunat.api_sunat.sunat.errores.ClasificadorErrores;
import apisunat.api_sunat.sunat.firma.CertificadoFirma;
import apisunat.api_sunat.sunat.firma.CertificadoLoader;
import apisunat.api_sunat.sunat.firma.XmlSigner;
import apisunat.api_sunat.sunat.soap.BillServiceClient;
import apisunat.api_sunat.sunat.xml.FacturaXmlBuilder;
import apisunat.api_sunat.sunat.xml.XsdValidator;
import apisunat.api_sunat.config.SunatProperties;
import apisunat.api_sunat.util.NumeracionUtil;
import apisunat.api_sunat.util.ZipUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FacturaServiceImpl implements FacturaService {

    private final SunatProperties propiedades;
    private final Clock clock;
    private final FacturaXmlBuilder xmlBuilder;
    private final CertificadoLoader certificadoLoader;
    private final XmlSigner firmante;
    private final XsdValidator xsdValidator;
    private final BillServiceClient cliente;
    private final CdrParser cdrParser;
    private final ClasificadorErrores clasificador;

    @Override
    public ResultadoEmision emitir(FacturaRequest request, String serie, long correlativo) {
        // Siempre hora de Lima, aunque el reloj de la aplicación estuviera en otra zona
        LocalDateTime emision = LocalDateTime.now(clock.withZone(ClockConfig.ZONA_LIMA));
        byte[] firmado = prepararXml(request, serie, correlativo, emision);
        return enviar(serie, correlativo, firmado);
    }

    @Override
    public byte[] prepararXml(FacturaRequest request, String serie, long correlativo, LocalDateTime emision) {
        // Todo lo que puede fallar por nuestra culpa se hace antes de hablar con la SUNAT
        String nombre = NumeracionUtil.nombreArchivoFactura(propiedades.getRuc(), serie, correlativo);
        byte[] xml = xmlBuilder.construir(request, serie, correlativo, emision);
        CertificadoFirma certificado = certificadoLoader.cargar();
        byte[] firmado = firmante.firmar(xml, certificado);
        List<String> errores = xsdValidator.validarFactura(firmado);
        if (!errores.isEmpty()) {
            // El XML lo armamos nosotros: si no cumple el esquema es un error del programa, no del cliente
            throw new IllegalStateException("El XML armado para " + nombre + " no cumple el esquema de la SUNAT: " + errores);
        }
        return firmado;
    }

    @Override
    public byte[] prepararXmlBoleta(BoletaRequest request, String serie, long correlativo, LocalDateTime emision) {
        String nombre = NumeracionUtil.nombreArchivoBoleta(propiedades.getRuc(), serie, correlativo);
        byte[] xml = xmlBuilder.construirBoleta(request, serie, correlativo, emision);
        CertificadoFirma certificado = certificadoLoader.cargar();
        byte[] firmado = firmante.firmar(xml, certificado);
        List<String> errores = xsdValidator.validarFactura(firmado);
        if (!errores.isEmpty()) {
            throw new IllegalStateException("El XML armado para " + nombre + " no cumple el esquema de la SUNAT: " + errores);
        }
        return firmado;
    }

    @Override
    public ResultadoEmision enviar(String serie, long correlativo, byte[] xmlFirmado) {
        String nombre = NumeracionUtil.nombreArchivoFactura(propiedades.getRuc(), serie, correlativo);
        String numero = NumeracionUtil.numeroFactura(serie, correlativo);
        byte[] zip = ZipUtil.comprimir(nombre + ".xml", xmlFirmado);

        log.info("Enviando {} a la SUNAT", nombre);
        byte[] constancia;
        try {
            constancia = cliente.sendBill(nombre + ".zip", zip);
        } catch (SunatFaultException fault) {
            ClaseResultado clase = clasificador.clasificar(fault);
            log.warn("La SUNAT respondió con error {} para {}: {}", fault.getCodigo(), nombre, clase);
            return ResultadoEmision.builder()
                    .nombreArchivo(nombre).clase(clase).codigo(fault.getCodigo()).descripcion(fault.getFaultString())
                    .observaciones(List.of()).xmlFirmado(xmlFirmado).build();
        }

        CdrResultado cdr = cdrParser.leer(constancia);
        if (!numero.equals(cdr.getIdDocumento())) {
            throw new SunatRespuestaInvalidaException(
                    "La constancia de la SUNAT es del documento " + cdr.getIdDocumento() + " y se envió " + numero);
        }
        ClaseResultado clase = clasificador.clasificar(cdr);
        log.info("La SUNAT respondió para {}: {} (código {})", nombre, clase, cdr.getCodigo());
        return ResultadoEmision.builder()
                .nombreArchivo(nombre).clase(clase).codigo(cdr.getCodigo()).descripcion(cdr.getDescripcion())
                .observaciones(cdr.getObservaciones()).xmlFirmado(xmlFirmado).cdrZip(constancia).build();
    }
}
