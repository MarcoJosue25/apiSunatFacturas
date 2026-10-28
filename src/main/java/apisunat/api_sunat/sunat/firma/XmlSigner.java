package apisunat.api_sunat.sunat.firma;

import apisunat.api_sunat.exception.FirmaException;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.crypto.MarshalException;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureException;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.GeneralSecurityException;
import java.util.List;

// Firma un XML UBL con XMLDSig, como pide la SUNAT: la firma va dentro de ext:UBLExtensions/ext:UBLExtension/ext:ExtensionContent,
// "envuelta" (enveloped) y sobre todo el documento (Reference URI=""). Sirve para la factura y, más adelante, para el resumen diario.
// El algoritmo es RSA con SHA-256. Si el beta lo rechazara habría que pasar a SHA-1 (ver docs/OBSERVACIONES.md).
@Component
public class XmlSigner {

    // El mismo Id que nombra el cac:Signature del documento (cbc:URI "#SignatureSP")
    static final String ID_FIRMA = "SignatureSP";

    static final String NS_DS = "http://www.w3.org/2000/09/xmldsig#";
    static final String NS_EXT = "urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2";
    static final String ALGORITMO_FIRMA = "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256";

    // Devuelve el mismo XML con la firma puesta, en ISO-8859-1 y sin sangrías (cualquier cambio posterior rompería la firma)
    public byte[] firmar(byte[] xml, CertificadoFirma certificado) {
        if (xml == null || xml.length == 0) {
            throw new FirmaException("No hay XML para firmar");
        }
        if (certificado == null) {
            throw new FirmaException("No hay certificado para firmar");
        }
        Document doc = leer(xml);
        Node contenido = espacioDeLaFirma(doc);
        try {
            XMLSignatureFactory fabrica = XMLSignatureFactory.getInstance("DOM");
            Reference documentoCompleto = fabrica.newReference("",
                    fabrica.newDigestMethod(DigestMethod.SHA256, null),
                    List.of(fabrica.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null)),
                    null, null);
            SignedInfo firmado = fabrica.newSignedInfo(
                    fabrica.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec) null),
                    fabrica.newSignatureMethod(ALGORITMO_FIRMA, null),
                    List.of(documentoCompleto));
            KeyInfoFactory claves = fabrica.getKeyInfoFactory();
            KeyInfo keyInfo = claves.newKeyInfo(List.of(claves.newX509Data(List.of(certificado.certificado()))));

            DOMSignContext contexto = new DOMSignContext(certificado.clavePrivada(), contenido);
            contexto.setDefaultNamespacePrefix("ds");
            fabrica.newXMLSignature(firmado, keyInfo, null, ID_FIRMA, null).sign(contexto);
        } catch (XMLSignatureException | GeneralSecurityException | MarshalException e) {
            throw new FirmaException("No se pudo firmar el XML: " + e.getMessage(), e);
        }
        base64EnUnaLinea(doc);
        return escribir(doc);
    }

    // El JDK parte el base64 en líneas de 76 caracteres con retornos de carro, que al escribir el XML quedan como "&#13;".
    // Es válido, pero feo y poco común: se dejan en una sola línea. Esto no afecta la firma, porque el Reference URI=""
    // excluye la propia firma y el base64 se lee sin espacios. El DigestValue no se toca: está dentro de lo firmado (SignedInfo).
    private void base64EnUnaLinea(Document doc) {
        for (String nombre : List.of("SignatureValue", "X509Certificate")) {
            NodeList nodos = doc.getElementsByTagNameNS(NS_DS, nombre);
            for (int i = 0; i < nodos.getLength(); i++) {
                nodos.item(i).setTextContent(nodos.item(i).getTextContent().replaceAll("\\s", ""));
            }
        }
    }

    private Document leer(byte[] xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        } catch (Exception e) {
            throw new FirmaException("El XML no se puede leer: " + e.getMessage(), e);
        }
    }

    // El único ext:ExtensionContent del documento, que debe estar vacío. Más de uno o uno ya ocupado serían ambiguos.
    private Node espacioDeLaFirma(Document doc) {
        NodeList espacios = doc.getElementsByTagNameNS(NS_EXT, "ExtensionContent");
        if (espacios.getLength() == 0) {
            throw new FirmaException("El XML no tiene el espacio para la firma (ext:UBLExtensions/ext:UBLExtension/ext:ExtensionContent)");
        }
        if (espacios.getLength() > 1) {
            throw new FirmaException("El XML tiene más de un ext:ExtensionContent y no se sabe dónde poner la firma");
        }
        Node espacio = espacios.item(0);
        if (espacio.hasChildNodes()) {
            throw new FirmaException("El XML ya tiene una firma o contenido en ext:ExtensionContent");
        }
        return espacio;
    }

    private byte[] escribir(Document doc) {
        try {
            TransformerFactory fabrica = TransformerFactory.newInstance();
            fabrica.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            Transformer t = fabrica.newTransformer();
            t.setOutputProperty(OutputKeys.ENCODING, "ISO-8859-1");
            t.setOutputProperty(OutputKeys.INDENT, "no");
            t.setOutputProperty(OutputKeys.METHOD, "xml");
            ByteArrayOutputStream salida = new ByteArrayOutputStream(8192);
            t.transform(new DOMSource(doc), new StreamResult(salida));
            return salida.toByteArray();
        } catch (Exception e) {
            throw new FirmaException("No se pudo escribir el XML firmado", e);
        }
    }
}
