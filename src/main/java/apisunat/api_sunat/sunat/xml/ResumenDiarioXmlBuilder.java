package apisunat.api_sunat.sunat.xml;

import apisunat.api_sunat.config.SunatProperties;
import apisunat.api_sunat.util.TextoSunat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

// Arma el XML del Resumen Diario de Boletas (SummaryDocuments, guía de resumen versión 2, UBL 2.0), sin firmar: así es como se
// informan las boletas a la SUNAT. Los elementos van en el orden del esquema oficial UBLPE-SummaryDocuments-1.0.xsd.
// El espacio de la firma queda vacío, como en la factura. Sale en ISO-8859-1 y sin sangrías (el XML se firma tal como se escribe).
@Component
@RequiredArgsConstructor
public class ResumenDiarioXmlBuilder {

    // La SUNAT recibe el resumen en bloques de hasta 500 líneas
    public static final int MAX_LINEAS = 500;

    static final String NS_RESUMEN = "urn:sunat:names:specification:ubl:peru:schema:xsd:SummaryDocuments-1";
    static final String NS_SAC = "urn:sunat:names:specification:ubl:peru:schema:xsd:SunatAggregateComponents-1";
    static final String NS_CAC = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
    static final String NS_CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
    static final String NS_EXT = "urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2";
    static final String NS_DS = "http://www.w3.org/2000/09/xmldsig#";

    private static final Pattern NOMBRE = Pattern.compile("RC-\\d{8}-[1-9]\\d{0,4}");
    private static final Pattern NUMERO_BOLETA = Pattern.compile("B[A-Z0-9]{3}-[1-9]\\d{0,7}");
    private static final Pattern TIPO_DOC_CLIENTE = Pattern.compile("[01467A-G]|-");
    private static final String MONEDA = "PEN";
    private static final String ESTADO_ADICIONAR = "1";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final SunatProperties propiedades;

    // nombre: RC-AAAAMMDD-N, con la fecha de emisión de las boletas. fechaReferencia: la fecha de emisión de todas las boletas.
    // fechaGeneracion: el día en que se arma el resumen (no puede ser anterior a la emisión).
    public byte[] construir(String nombre, LocalDate fechaReferencia, LocalDate fechaGeneracion, List<LineaResumen> lineas) {
        if (nombre == null || !NOMBRE.matcher(nombre).matches()) {
            throw new IllegalArgumentException("El nombre del resumen debe ser RC-AAAAMMDD-N (N de 1 a 5 dígitos)");
        }
        if (fechaReferencia == null || fechaGeneracion == null) {
            throw new IllegalArgumentException("Las fechas del resumen son obligatorias");
        }
        if (!nombre.startsWith("RC-" + fechaReferencia.format(DateTimeFormatter.BASIC_ISO_DATE) + "-")) {
            throw new IllegalArgumentException("La fecha del nombre del resumen debe ser la fecha de emisión de las boletas");
        }
        if (fechaGeneracion.isBefore(fechaReferencia)) {
            throw new IllegalArgumentException("La fecha de generación del resumen no puede ser anterior a la de emisión de las boletas");
        }
        if (lineas == null || lineas.isEmpty()) {
            throw new IllegalArgumentException("El resumen necesita al menos una boleta");
        }
        if (lineas.size() > MAX_LINEAS) {
            throw new IllegalArgumentException("Un resumen admite como máximo " + MAX_LINEAS + " boletas");
        }
        Set<String> vistos = new HashSet<>();
        for (int i = 0; i < lineas.size(); i++) {
            validar(i + 1, lineas.get(i));
            if (!vistos.add(lineas.get(i).numero())) {
                throw new IllegalArgumentException("La boleta " + lineas.get(i).numero() + " está repetida en el resumen");
            }
        }

        Document doc = nuevoDocumento();
        Element raiz = doc.createElementNS(NS_RESUMEN, "SummaryDocuments");
        String xmlns = "http://www.w3.org/2000/xmlns/";
        raiz.setAttributeNS(xmlns, "xmlns:cac", NS_CAC);
        raiz.setAttributeNS(xmlns, "xmlns:cbc", NS_CBC);
        raiz.setAttributeNS(xmlns, "xmlns:ds", NS_DS);
        raiz.setAttributeNS(xmlns, "xmlns:ext", NS_EXT);
        raiz.setAttributeNS(xmlns, "xmlns:sac", NS_SAC);
        doc.appendChild(raiz);

        Element extension = hijo(doc, raiz, NS_EXT, "ext:UBLExtensions");
        extension = hijo(doc, extension, NS_EXT, "ext:UBLExtension");
        hijo(doc, extension, NS_EXT, "ext:ExtensionContent");

        texto(doc, raiz, "cbc:UBLVersionID", "2.0");
        texto(doc, raiz, "cbc:CustomizationID", "1.1");
        texto(doc, raiz, "cbc:ID", nombre);
        texto(doc, raiz, "cbc:ReferenceDate", fechaReferencia.format(FECHA));
        texto(doc, raiz, "cbc:IssueDate", fechaGeneracion.format(FECHA));

        String razon = limpiar(propiedades.getEmisor().getRazonSocial());
        Element firma = hijo(doc, raiz, NS_CAC, "cac:Signature");
        texto(doc, firma, "cbc:ID", "IDSignSP");
        Element firmante = hijo(doc, firma, NS_CAC, "cac:SignatoryParty");
        texto(doc, hijo(doc, firmante, NS_CAC, "cac:PartyIdentification"), "cbc:ID", propiedades.getRuc());
        texto(doc, hijo(doc, firmante, NS_CAC, "cac:PartyName"), "cbc:Name", razon);
        Element adjunto = hijo(doc, firma, NS_CAC, "cac:DigitalSignatureAttachment");
        texto(doc, hijo(doc, adjunto, NS_CAC, "cac:ExternalReference"), "cbc:URI", "#SignatureSP");

        Element emisor = hijo(doc, raiz, NS_CAC, "cac:AccountingSupplierParty");
        texto(doc, emisor, "cbc:CustomerAssignedAccountID", propiedades.getRuc());
        texto(doc, emisor, "cbc:AdditionalAccountID", "6");
        Element parte = hijo(doc, emisor, NS_CAC, "cac:Party");
        texto(doc, hijo(doc, parte, NS_CAC, "cac:PartyLegalEntity"), "cbc:RegistrationName", razon);

        for (int i = 0; i < lineas.size(); i++) {
            agregarLinea(doc, raiz, i + 1, lineas.get(i));
        }
        return serializar(doc);
    }

    private void validar(int fila, LineaResumen l) {
        String donde = "Boleta " + fila + ": ";
        if (l == null || l.numero() == null || !NUMERO_BOLETA.matcher(l.numero()).matches()) {
            throw new IllegalArgumentException(donde + "el número debe ser como B001-12");
        }
        if (l.tipoDocumentoCliente() == null || !TIPO_DOC_CLIENTE.matcher(l.tipoDocumentoCliente()).matches()) {
            throw new IllegalArgumentException(donde + "el tipo de documento del cliente no es válido");
        }
        if (l.numeroDocumentoCliente() == null || l.numeroDocumentoCliente().isBlank() || l.numeroDocumentoCliente().length() > 20
                || !l.numeroDocumentoCliente().equals(l.numeroDocumentoCliente().strip())) {
            throw new IllegalArgumentException(donde + "el número de documento del cliente no es válido");
        }
        for (BigDecimal monto : new BigDecimal[]{l.totalGravado(), l.totalIgv(), l.importeTotal()}) {
            if (monto == null || monto.signum() < 0 || monto.scale() > 2 || monto.precision() - monto.scale() > 12) {
                throw new IllegalArgumentException(donde + "los importes deben ser de 0 o más, con hasta 12 enteros y 2 decimales");
            }
        }
        if (l.totalGravado().signum() == 0) {
            throw new IllegalArgumentException(donde + "el valor de venta de operaciones gravadas debe ser mayor que cero");
        }
        if (l.totalGravado().add(l.totalIgv()).subtract(l.importeTotal()).abs().compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(donde + "el importe total no coincide con el valor de venta más el IGV");
        }
    }

    private void agregarLinea(Document doc, Element raiz, int fila, LineaResumen l) {
        Element linea = hijo(doc, raiz, NS_SAC, "sac:SummaryDocumentsLine");
        texto(doc, linea, "cbc:LineID", String.valueOf(fila));
        texto(doc, linea, "cbc:DocumentTypeCode", "03");
        texto(doc, linea, "cbc:ID", l.numero());

        Element cliente = hijo(doc, linea, NS_CAC, "cac:AccountingCustomerParty");
        texto(doc, cliente, "cbc:CustomerAssignedAccountID", l.numeroDocumentoCliente());
        texto(doc, cliente, "cbc:AdditionalAccountID", l.tipoDocumentoCliente());

        texto(doc, hijo(doc, linea, NS_CAC, "cac:Status"), "cbc:ConditionCode", ESTADO_ADICIONAR);
        monto(doc, linea, NS_SAC, "sac:TotalAmount", l.importeTotal());

        Element pago = hijo(doc, linea, NS_SAC, "sac:BillingPayment");
        monto(doc, pago, NS_CBC, "cbc:PaidAmount", l.totalGravado());
        texto(doc, pago, "cbc:InstructionID", "01");

        Element impuestos = hijo(doc, linea, NS_CAC, "cac:TaxTotal");
        monto(doc, impuestos, NS_CBC, "cbc:TaxAmount", l.totalIgv());
        Element subtotal = hijo(doc, impuestos, NS_CAC, "cac:TaxSubtotal");
        monto(doc, subtotal, NS_CBC, "cbc:TaxAmount", l.totalIgv());
        Element categoria = hijo(doc, subtotal, NS_CAC, "cac:TaxCategory");
        texto(doc, categoria, "cbc:Percent", "18.00");
        Element esquema = hijo(doc, categoria, NS_CAC, "cac:TaxScheme");
        texto(doc, esquema, "cbc:ID", "1000");
        texto(doc, esquema, "cbc:Name", "IGV");
        texto(doc, esquema, "cbc:TaxTypeCode", "VAT");
    }

    // --- Ayudas para armar el DOM ---

    private static String limpiar(String razonSocial) {
        String limpio = TextoSunat.limpiar(razonSocial);
        if (limpio.isBlank()) {
            throw new IllegalArgumentException("La razón social del emisor no puede quedar vacía");
        }
        return limpio;
    }

    private static Document nuevoDocumento() {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            return f.newDocumentBuilder().newDocument();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo crear el documento XML", e);
        }
    }

    private static String espacioDe(String nombreConPrefijo) {
        return switch (nombreConPrefijo.substring(0, nombreConPrefijo.indexOf(':'))) {
            case "cac" -> NS_CAC;
            case "cbc" -> NS_CBC;
            case "ext" -> NS_EXT;
            case "sac" -> NS_SAC;
            default -> throw new IllegalArgumentException("Prefijo desconocido en " + nombreConPrefijo);
        };
    }

    private static Element hijo(Document doc, Element padre, String espacio, String nombre) {
        Element e = doc.createElementNS(espacio, nombre);
        padre.appendChild(e);
        return e;
    }

    private static Element texto(Document doc, Element padre, String nombre, String valor) {
        Element e = hijo(doc, padre, espacioDe(nombre), nombre);
        e.setTextContent(valor);
        return e;
    }

    private static void monto(Document doc, Element padre, String espacio, String nombre, BigDecimal valor) {
        Element e = hijo(doc, padre, espacio, nombre);
        e.setAttribute("currencyID", MONEDA);
        e.setTextContent(valor.setScale(2, RoundingMode.UNNECESSARY).toPlainString());
    }

    private static byte[] serializar(Document doc) {
        try {
            TransformerFactory fabrica = TransformerFactory.newInstance();
            fabrica.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            Transformer t = fabrica.newTransformer();
            t.setOutputProperty(OutputKeys.ENCODING, "ISO-8859-1");
            t.setOutputProperty(OutputKeys.INDENT, "no");
            t.setOutputProperty(OutputKeys.METHOD, "xml");
            ByteArrayOutputStream salida = new ByteArrayOutputStream(4096);
            t.transform(new DOMSource(doc), new StreamResult(salida));
            return salida.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo escribir el XML", e);
        }
    }
}
