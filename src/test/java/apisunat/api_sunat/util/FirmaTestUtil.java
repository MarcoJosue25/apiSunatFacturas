package apisunat.api_sunat.util;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.crypto.AlgorithmMethod;
import javax.xml.crypto.KeySelector;
import javax.xml.crypto.KeySelectorException;
import javax.xml.crypto.KeySelectorResult;
import javax.xml.crypto.XMLCryptoContext;
import javax.xml.crypto.XMLStructure;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import java.security.Key;
import java.security.cert.X509Certificate;

// El verificador de firmas vive solo en los tests: la aplicación firma pero nunca verifica (la verifica la SUNAT).
// Usa la clave pública del certificado que trae la propia firma en su KeyInfo, como haría la SUNAT.
public final class FirmaTestUtil {

    private static final String NS_DS = "http://www.w3.org/2000/09/xmldsig#";

    private FirmaTestUtil() {
    }

    public static boolean verifica(byte[] xmlFirmado) {
        Verificable v = preparar(xmlFirmado);
        try {
            return v.firma().validate(v.contexto());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // Verifica la firma y dice qué parte falló (el valor de la firma o las referencias) para que el test explique el porqué
    public static String diagnostico(byte[] xmlFirmado) {
        Verificable v = preparar(xmlFirmado);
        try {
            boolean general = v.firma().validate(v.contexto());
            boolean valorFirma = v.firma().getSignatureValue().validate(v.contexto());
            boolean referencias = true;
            for (Object r : v.firma().getSignedInfo().getReferences()) {
                referencias &= ((javax.xml.crypto.dsig.Reference) r).validate(v.contexto());
            }
            return "general=" + general + ", valorDeLaFirma=" + valorFirma + ", referencias=" + referencias;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static X509Certificate certificadoDeLaFirma(byte[] xmlFirmado) {
        KeyInfo keyInfo = preparar(xmlFirmado).firma().getKeyInfo();
        for (Object contenido : keyInfo.getContent()) {
            if (contenido instanceof X509Data datos) {
                for (Object dato : datos.getContent()) {
                    if (dato instanceof X509Certificate certificado) {
                        return certificado;
                    }
                }
            }
        }
        throw new IllegalStateException("La firma no trae certificado");
    }

    private record Verificable(XMLSignature firma, DOMValidateContext contexto) {
    }

    private static Verificable preparar(byte[] xmlFirmado) {
        Document doc = FacturaXmlTestUtil.leer(xmlFirmado);
        NodeList firmas = doc.getElementsByTagNameNS(NS_DS, "Signature");
        if (firmas.getLength() != 1) {
            throw new IllegalStateException("Se esperaba una sola firma y hay " + firmas.getLength());
        }
        try {
            DOMValidateContext contexto = new DOMValidateContext(new SelectorDeCertificado(), firmas.item(0));
            XMLSignature firma = XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(contexto);
            return new Verificable(firma, contexto);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class SelectorDeCertificado extends KeySelector {
        @Override
        public KeySelectorResult select(KeyInfo keyInfo, Purpose purpose, AlgorithmMethod method, XMLCryptoContext context) throws KeySelectorException {
            if (keyInfo == null) {
                throw new KeySelectorException("La firma no trae KeyInfo");
            }
            for (XMLStructure estructura : keyInfo.getContent()) {
                if (estructura instanceof X509Data datos) {
                    for (Object dato : datos.getContent()) {
                        if (dato instanceof X509Certificate certificado) {
                            Key clave = certificado.getPublicKey();
                            return () -> clave;
                        }
                    }
                }
            }
            throw new KeySelectorException("No hay certificado en el KeyInfo");
        }
    }
}
