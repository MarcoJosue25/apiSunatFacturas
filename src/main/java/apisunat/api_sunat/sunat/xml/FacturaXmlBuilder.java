package apisunat.api_sunat.sunat.xml;

import apisunat.api_sunat.config.SunatProperties;
import apisunat.api_sunat.dto.BoletaRequest;
import apisunat.api_sunat.dto.ClienteBoletaRequest;
import apisunat.api_sunat.dto.ClienteRequest;
import apisunat.api_sunat.dto.FacturaRequest;
import apisunat.api_sunat.dto.ItemRequest;
import apisunat.api_sunat.sunat.calculo.CalculadoraIgv;
import apisunat.api_sunat.sunat.calculo.LineaCalculada;
import apisunat.api_sunat.sunat.calculo.TotalesDocumento;
import apisunat.api_sunat.util.MontoEnLetras;
import apisunat.api_sunat.util.NumeracionUtil;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

// Arma el XML de una factura electrónica (UBL 2.1) con IGV al 18%, sin firmar. Sale en ISO-8859-1, como pide la SUNAT.
// El espacio de la firma queda vacío (ext:ExtensionContent) para que la fase de firma lo llene.
// Los elementos van en el orden exacto del esquema UBL: si se cambia el orden, el XSD lo rechaza.
@Component
@RequiredArgsConstructor
public class FacturaXmlBuilder {

    static final String NS_INVOICE = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
    static final String NS_CAC = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
    static final String NS_CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
    static final String NS_EXT = "urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2";
    static final String NS_DS = "http://www.w3.org/2000/09/xmldsig#";

    private static final String MONEDA = "PEN";
    private static final String TIPO_FACTURA = "01";
    private static final String TIPO_BOLETA = "03";
    private static final String NOMBRE_ANONIMO = "CLIENTES VARIOS";
    private static final String TIPO_OPERACION_VENTA_INTERNA = "0101";
    private static final String ID_FIRMA = "IDSignSP";
    private static final String URI_FIRMA = "#SignatureSP";
    private static final int MAX_DESCRIPCION = 500;
    private static final int MAX_NOMBRE = 1500;
    private static final int MAX_DIRECCION = 200;
    private static final int MAX_CODIGO = 30;
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final SunatProperties propiedades;

    // Quien compra, tal como va en el XML: tipo de documento del catálogo 06, número y nombre
    private record Comprador(String tipoDocumento, String numeroDocumento, String nombre, String direccion, String etiquetaNombre) {
    }

    public byte[] construir(FacturaRequest request, String serie, long correlativo, LocalDateTime emision) {
        if (request == null || request.getCliente() == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("La factura necesita un cliente y al menos un ítem");
        }
        if (emision == null) {
            throw new IllegalArgumentException("La fecha de emisión es obligatoria");
        }
        String numero = NumeracionUtil.numeroFactura(serie, correlativo);
        ClienteRequest cliente = request.getCliente();
        Comprador comprador = new Comprador("6", cliente.getRuc(), cliente.getRazonSocial(), cliente.getDireccion(), "La razón social del cliente");
        return armar(TIPO_FACTURA, numero, comprador, request.getItems(), emision, true);
    }

    // Una boleta lleva lo mismo que una factura, con tipo 03, serie B y sin forma de pago. Sin cliente (o sin documento) el comprador
    // es anónimo: tipo 0 y guión, que la SUNAT solo permite hasta S/ 700 (esa regla la comprueba quien llama, que sabe el total).
    public byte[] construirBoleta(BoletaRequest request, String serie, long correlativo, LocalDateTime emision) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("La boleta necesita al menos un ítem");
        }
        if (emision == null) {
            throw new IllegalArgumentException("La fecha de emisión es obligatoria");
        }
        String numero = NumeracionUtil.numeroBoleta(serie, correlativo);
        ClienteBoletaRequest cliente = request.getCliente();
        Comprador comprador;
        if (cliente == null || cliente.esAnonimo()) {
            String nombre = cliente != null && cliente.getNombre() != null && !cliente.getNombre().isBlank() ? cliente.getNombre() : NOMBRE_ANONIMO;
            comprador = new Comprador("0", "-", nombre, null, "El nombre del cliente");
        } else {
            String nombre = cliente.getNombre() != null && !cliente.getNombre().isBlank() ? cliente.getNombre() : NOMBRE_ANONIMO;
            comprador = new Comprador(cliente.getTipoDocumento(), cliente.getNumeroDocumento(), nombre, null, "El nombre del cliente");
        }
        return armar(TIPO_BOLETA, numero, comprador, request.getItems(), emision, false);
    }

    private byte[] armar(String tipoDocumento, String numero, Comprador comprador, List<ItemRequest> items, LocalDateTime emision,
                         boolean conFormaPago) {
        // Primero los números, para que cualquier problema salga antes de armar nada
        List<LineaCalculada> lineas = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ItemRequest item = items.get(i);
            try {
                lineas.add(CalculadoraIgv.calcularLinea(item.getPrecioUnitario(), item.getCantidad()));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Ítem " + (i + 1) + ": " + e.getMessage(), e);
            }
        }
        TotalesDocumento totales = CalculadoraIgv.calcularTotales(lineas);

        Document doc = nuevoDocumento();
        Element invoice = doc.createElementNS(NS_INVOICE, "Invoice");
        declararEspacios(invoice);
        doc.appendChild(invoice);

        // Espacio para la firma digital
        Element extension = hijo(doc, invoice, NS_EXT, "ext:UBLExtensions");
        extension = hijo(doc, extension, NS_EXT, "ext:UBLExtension");
        hijo(doc, extension, NS_EXT, "ext:ExtensionContent");

        texto(doc, invoice, "cbc:UBLVersionID", "2.1");
        texto(doc, invoice, "cbc:CustomizationID", "2.0");
        texto(doc, invoice, "cbc:ID", numero);
        texto(doc, invoice, "cbc:IssueDate", emision.format(FECHA));
        texto(doc, invoice, "cbc:IssueTime", emision.format(HORA));

        Element tipo = texto(doc, invoice, "cbc:InvoiceTypeCode", tipoDocumento);
        tipo.setAttribute("listAgencyName", "PE:SUNAT");
        tipo.setAttribute("listID", TIPO_OPERACION_VENTA_INTERNA);
        tipo.setAttribute("listName", "Tipo de Documento");
        tipo.setAttribute("listURI", "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo01");
        tipo.setAttribute("name", "Tipo de Operacion");
        tipo.setAttribute("listSchemeURI", "urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo51");

        Element leyenda = texto(doc, invoice, "cbc:Note", MontoEnLetras.convertir(totales.getImporteTotal()));
        leyenda.setAttribute("languageLocaleID", "1000");
        texto(doc, invoice, "cbc:DocumentCurrencyCode", MONEDA);

        String razonEmisor = limpiar("La razón social del emisor", propiedades.getEmisor().getRazonSocial(), MAX_NOMBRE);
        agregarFirma(doc, invoice, razonEmisor);
        agregarEmisor(doc, invoice, razonEmisor);
        agregarComprador(doc, invoice, comprador);

        if (conFormaPago) {
            Element pago = hijo(doc, invoice, NS_CAC, "cac:PaymentTerms");
            texto(doc, pago, "cbc:ID", "FormaPago");
            texto(doc, pago, "cbc:PaymentMeansID", "Contado");
        }

        agregarImpuestoDelDocumento(doc, invoice, totales);

        Element monetario = hijo(doc, invoice, NS_CAC, "cac:LegalMonetaryTotal");
        monto(doc, monetario, "cbc:LineExtensionAmount", totales.getTotalGravado());
        monto(doc, monetario, "cbc:TaxInclusiveAmount", totales.getImporteTotal());
        monto(doc, monetario, "cbc:PayableAmount", totales.getImporteTotal());

        for (int i = 0; i < lineas.size(); i++) {
            agregarLinea(doc, invoice, i + 1, items.get(i), lineas.get(i));
        }
        return serializar(doc);
    }

    private void agregarFirma(Document doc, Element invoice, String razonEmisor) {
        Element firma = hijo(doc, invoice, NS_CAC, "cac:Signature");
        texto(doc, firma, "cbc:ID", ID_FIRMA);
        Element firmante = hijo(doc, firma, NS_CAC, "cac:SignatoryParty");
        texto(doc, hijo(doc, firmante, NS_CAC, "cac:PartyIdentification"), "cbc:ID", propiedades.getRuc());
        texto(doc, hijo(doc, firmante, NS_CAC, "cac:PartyName"), "cbc:Name", razonEmisor);
        Element adjunto = hijo(doc, firma, NS_CAC, "cac:DigitalSignatureAttachment");
        texto(doc, hijo(doc, adjunto, NS_CAC, "cac:ExternalReference"), "cbc:URI", URI_FIRMA);
    }

    private void agregarEmisor(Document doc, Element invoice, String razonEmisor) {
        SunatProperties.Emisor emisor = propiedades.getEmisor();
        Element parte = hijo(doc, hijo(doc, invoice, NS_CAC, "cac:AccountingSupplierParty"), NS_CAC, "cac:Party");
        Element id = texto(doc, hijo(doc, parte, NS_CAC, "cac:PartyIdentification"), "cbc:ID", propiedades.getRuc());
        id.setAttribute("schemeID", "6");
        if (emisor.getNombreComercial() != null) {
            String comercial = limpiar("El nombre comercial del emisor", emisor.getNombreComercial(), MAX_NOMBRE);
            texto(doc, hijo(doc, parte, NS_CAC, "cac:PartyName"), "cbc:Name", comercial);
        }
        Element legal = hijo(doc, parte, NS_CAC, "cac:PartyLegalEntity");
        texto(doc, legal, "cbc:RegistrationName", razonEmisor);
        Element domicilio = hijo(doc, legal, NS_CAC, "cac:RegistrationAddress");
        texto(doc, domicilio, "cbc:AddressTypeCode", emisor.getCodigoEstablecimiento());
        if (emisor.getDireccion() != null) {
            texto(doc, hijo(doc, domicilio, NS_CAC, "cac:AddressLine"), "cbc:Line", limpiar("La dirección del emisor", emisor.getDireccion(), MAX_DIRECCION));
        }
    }

    private void agregarComprador(Document doc, Element invoice, Comprador comprador) {
        Element parte = hijo(doc, hijo(doc, invoice, NS_CAC, "cac:AccountingCustomerParty"), NS_CAC, "cac:Party");
        Element id = texto(doc, hijo(doc, parte, NS_CAC, "cac:PartyIdentification"), "cbc:ID", comprador.numeroDocumento());
        id.setAttribute("schemeID", comprador.tipoDocumento());
        Element legal = hijo(doc, parte, NS_CAC, "cac:PartyLegalEntity");
        texto(doc, legal, "cbc:RegistrationName", limpiar(comprador.etiquetaNombre(), comprador.nombre(), MAX_NOMBRE));
        if (comprador.direccion() != null) {
            Element domicilio = hijo(doc, legal, NS_CAC, "cac:RegistrationAddress");
            texto(doc, hijo(doc, domicilio, NS_CAC, "cac:AddressLine"), "cbc:Line", limpiar("La dirección del cliente", comprador.direccion(), MAX_DIRECCION));
        }
    }

    private void agregarImpuestoDelDocumento(Document doc, Element invoice, TotalesDocumento totales) {
        Element impuestos = hijo(doc, invoice, NS_CAC, "cac:TaxTotal");
        monto(doc, impuestos, "cbc:TaxAmount", totales.getTotalIgv());
        Element subtotal = hijo(doc, impuestos, NS_CAC, "cac:TaxSubtotal");
        monto(doc, subtotal, "cbc:TaxableAmount", totales.getTotalGravado());
        monto(doc, subtotal, "cbc:TaxAmount", totales.getTotalIgv());
        agregarEsquemaIgv(doc, hijo(doc, subtotal, NS_CAC, "cac:TaxCategory"));
    }

    private void agregarLinea(Document doc, Element invoice, int numero, ItemRequest item, LineaCalculada linea) {
        Element l = hijo(doc, invoice, NS_CAC, "cac:InvoiceLine");
        texto(doc, l, "cbc:ID", String.valueOf(numero));
        Element cantidad = texto(doc, l, "cbc:InvoicedQuantity", numeroSinExponente(linea.getCantidad(), 0));
        cantidad.setAttribute("unitCode", item.getUnidadMedida());
        monto(doc, l, "cbc:LineExtensionAmount", linea.getValorVenta());

        Element precio = hijo(doc, hijo(doc, l, NS_CAC, "cac:PricingReference"), NS_CAC, "cac:AlternativeConditionPrice");
        montoLargo(doc, precio, "cbc:PriceAmount", linea.getPrecioUnitario());
        texto(doc, precio, "cbc:PriceTypeCode", "01");

        Element impuestos = hijo(doc, l, NS_CAC, "cac:TaxTotal");
        monto(doc, impuestos, "cbc:TaxAmount", linea.getIgv());
        Element subtotal = hijo(doc, impuestos, NS_CAC, "cac:TaxSubtotal");
        monto(doc, subtotal, "cbc:TaxableAmount", linea.getValorVenta());
        monto(doc, subtotal, "cbc:TaxAmount", linea.getIgv());
        Element categoria = hijo(doc, subtotal, NS_CAC, "cac:TaxCategory");
        texto(doc, categoria, "cbc:Percent", "18.00");
        texto(doc, categoria, "cbc:TaxExemptionReasonCode", "10");
        agregarEsquemaIgv(doc, categoria);

        Element producto = hijo(doc, l, NS_CAC, "cac:Item");
        texto(doc, producto, "cbc:Description", limpiar("La descripción del ítem " + numero, item.getDescripcion(), MAX_DESCRIPCION));
        if (item.getCodigo() != null && !item.getCodigo().isBlank()) {
            texto(doc, hijo(doc, producto, NS_CAC, "cac:SellersItemIdentification"), "cbc:ID",
                    limpiar("El código del ítem " + numero, item.getCodigo(), MAX_CODIGO));
        }
        montoLargo(doc, hijo(doc, l, NS_CAC, "cac:Price"), "cbc:PriceAmount", linea.getValorUnitario());
    }

    private void agregarEsquemaIgv(Document doc, Element categoria) {
        Element esquema = hijo(doc, categoria, NS_CAC, "cac:TaxScheme");
        texto(doc, esquema, "cbc:ID", "1000");
        texto(doc, esquema, "cbc:Name", "IGV");
        texto(doc, esquema, "cbc:TaxTypeCode", "VAT");
    }

    // --- Ayudas para armar el DOM ---

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

    private static void declararEspacios(Element raiz) {
        String xmlns = "http://www.w3.org/2000/xmlns/";
        raiz.setAttributeNS(xmlns, "xmlns:cac", NS_CAC);
        raiz.setAttributeNS(xmlns, "xmlns:cbc", NS_CBC);
        raiz.setAttributeNS(xmlns, "xmlns:ds", NS_DS);
        raiz.setAttributeNS(xmlns, "xmlns:ext", NS_EXT);
    }

    private static String espacioDe(String nombreConPrefijo) {
        return switch (nombreConPrefijo.substring(0, nombreConPrefijo.indexOf(':'))) {
            case "cac" -> NS_CAC;
            case "cbc" -> NS_CBC;
            case "ext" -> NS_EXT;
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

    // Un monto en soles: siempre con dos decimales
    private static Element monto(Document doc, Element padre, String nombre, BigDecimal valor) {
        Element e = texto(doc, padre, nombre, valor.setScale(2, java.math.RoundingMode.UNNECESSARY).toPlainString());
        e.setAttribute("currencyID", MONEDA);
        return e;
    }

    // Un precio unitario: los decimales que tenga, pero al menos dos
    private static Element montoLargo(Document doc, Element padre, String nombre, BigDecimal valor) {
        Element e = texto(doc, padre, nombre, numeroSinExponente(valor, 2));
        e.setAttribute("currencyID", MONEDA);
        return e;
    }

    // Sin notación científica, sin ceros de más y con al menos los decimales indicados
    static String numeroSinExponente(BigDecimal valor, int decimalesMinimos) {
        BigDecimal limpio = valor.stripTrailingZeros();
        if (limpio.scale() < decimalesMinimos) {
            limpio = limpio.setScale(decimalesMinimos);
        }
        return limpio.toPlainString();
    }

    // Limpia el texto para Latin-1 y comprueba que no quede vacío ni se pase del largo que admite la SUNAT
    private static String limpiar(String campo, String valor, int maximo) {
        String limpio = TextoSunat.limpiar(valor);
        if (limpio == null || limpio.isEmpty()) {
            throw new IllegalArgumentException(campo + " queda vacía después de quitar los caracteres que el XML no admite");
        }
        if (limpio.length() > maximo) {
            throw new IllegalArgumentException(campo + " pasa de " + maximo + " caracteres");
        }
        return limpio;
    }

    // ISO-8859-1 declarado y escrito de verdad, sin sangrías (el XML se firma tal como se escribe)
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
