package apisunat.api_sunat.sunat.firma;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

// Lo que hace falta para firmar: la clave privada y el certificado que la acompaña. El RUC sale del propio certificado.
public record CertificadoFirma(PrivateKey clavePrivada, X509Certificate certificado, String ruc) {
}
