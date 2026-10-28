package apisunat.api_sunat.sunat.xml;

import org.springframework.stereotype.Component;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSInput;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

// Comprueba un XML contra los esquemas XSD oficiales de la SUNAT (UBL 2.1), que están dentro del proyecto (src/main/resources/xsd).
// Un esquema validado por la SUNAT es lo que ella usa para rechazar con el código 0306, así que conviene verlo antes de enviar.
// La validación se hace sobre el documento ya firmado: el esquema no admite un ExtensionContent vacío.
@Component
public class XsdValidator {

    private static final String BASE_LOGICA = "https://esquemas.locales/";
    private static final String RAIZ_CLASSPATH = "/xsd/";
    // Cada documento usa su versión de UBL: la factura y la boleta la 2.1, el resumen diario la 2.0
    private static final String CARPETA_FACTURA = "ubl-2.1";
    private static final String CARPETA_RESUMEN = "ubl-2.0";
    private static final int MAX_ERRORES = 50;

    // Cargar los esquemas tarda un par de segundos (son 2.7 MB), así que se hace la primera vez que se necesitan
    private volatile Schema esquemaFactura;
    private volatile Schema esquemaResumen;

    // Devuelve los errores encontrados, cada uno con su línea y columna. Lista vacía = el XML es válido.
    public List<String> validarFactura(byte[] xml) {
        return validar(xml, this::esquemaFactura);
    }

    // Lo mismo para el resumen diario de boletas (SummaryDocuments, UBL 2.0)
    public List<String> validarResumen(byte[] xml) {
        return validar(xml, this::esquemaResumen);
    }

    private List<String> validar(byte[] xml, java.util.function.Supplier<Schema> esquema) {
        if (xml == null || xml.length == 0) {
            return List.of("El XML está vacío");
        }
        List<String> errores = new ArrayList<>();
        try {
            Validator validador = esquema.get().newValidator();
            validador.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validador.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            validador.setErrorHandler(new Recolector(errores));
            validador.validate(new StreamSource(new ByteArrayInputStream(xml)));
        } catch (SAXParseException e) {
            // Un error fatal, como un XML mal formado, corta la lectura
            agregar(errores, e);
        } catch (SAXException | IOException e) {
            agregar(errores, "No se pudo leer el XML: " + e.getMessage());
        }
        return errores;
    }

    private Schema esquemaFactura() {
        Schema actual = esquemaFactura;
        if (actual == null) {
            synchronized (this) {
                actual = esquemaFactura;
                if (actual == null) {
                    actual = cargarEsquema(CARPETA_FACTURA, "maindoc/UBL-Invoice-2.1.xsd");
                    esquemaFactura = actual;
                }
            }
        }
        return actual;
    }

    private Schema esquemaResumen() {
        Schema actual = esquemaResumen;
        if (actual == null) {
            synchronized (this) {
                actual = esquemaResumen;
                if (actual == null) {
                    actual = cargarEsquema(CARPETA_RESUMEN, "maindoc/UBLPE-SummaryDocuments-1.0.xsd");
                    esquemaResumen = actual;
                }
            }
        }
        return actual;
    }

    private Schema cargarEsquema(String carpeta, String archivo) {
        String baseLogica = BASE_LOGICA + carpeta + "/";
        String raizClasspath = RAIZ_CLASSPATH + carpeta + "/";
        try {
            SchemaFactory fabrica = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            fabrica.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            fabrica.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            fabrica.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            DOMImplementationLS ls = implementacionLs();
            // Los esquemas se piden por una dirección lógica y se entregan desde el classpath: nada sale a internet
            fabrica.setResourceResolver((tipo, namespace, publicId, systemId, baseUri) -> {
                String absoluta = URI.create(baseUri == null ? baseLogica : baseUri).resolve(systemId).toString();
                if (!absoluta.startsWith(baseLogica)) {
                    return null;
                }
                InputStream in = XsdValidator.class.getResourceAsStream(raizClasspath + absoluta.substring(baseLogica.length()));
                if (in == null) {
                    return null;
                }
                LSInput entrada = ls.createLSInput();
                entrada.setByteStream(in);
                entrada.setSystemId(absoluta);
                entrada.setPublicId(publicId);
                return entrada;
            });
            InputStream raiz = XsdValidator.class.getResourceAsStream(raizClasspath + archivo);
            if (raiz == null) {
                throw new IllegalStateException("No se encuentra el esquema " + archivo);
            }
            return fabrica.newSchema(new StreamSource(raiz, baseLogica + archivo));
        } catch (SAXException e) {
            throw new IllegalStateException("No se pudo cargar el esquema " + archivo, e);
        }
    }

    private static DOMImplementationLS implementacionLs() {
        try {
            DOMImplementation impl = DocumentBuilderFactory.newInstance().newDocumentBuilder().getDOMImplementation();
            return (DOMImplementationLS) impl.getFeature("LS", "3.0");
        } catch (Exception e) {
            throw new IllegalStateException("El JDK no trae DOM Load and Save", e);
        }
    }

    private static void agregar(List<String> errores, SAXParseException e) {
        agregar(errores, "línea " + e.getLineNumber() + ", columna " + e.getColumnNumber() + ": " + e.getMessage());
    }

    private static void agregar(List<String> errores, String mensaje) {
        if (errores.size() < MAX_ERRORES) {
            errores.add(mensaje);
        }
    }

    private static final class Recolector implements ErrorHandler {

        private final List<String> errores;

        private Recolector(List<String> errores) {
            this.errores = errores;
        }

        @Override
        public void warning(SAXParseException e) {
            // Los avisos no invalidan el documento
        }

        @Override
        public void error(SAXParseException e) {
            agregar(errores, e);
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException {
            agregar(errores, e);
            throw e;
        }
    }
}
