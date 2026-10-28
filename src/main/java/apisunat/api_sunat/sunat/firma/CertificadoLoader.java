package apisunat.api_sunat.sunat.firma;

import apisunat.api_sunat.config.SunatProperties;
import apisunat.api_sunat.exception.CertificadoInvalidoException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.InvalidPathException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAKey;
import java.time.Clock;
import java.util.Date;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Lee el certificado digital (.p12 o .pfx) y comprueba que sirva para firmar las facturas del emisor.
// Se lee del disco cada vez que se pide: es barato y así un certificado renovado se usa sin reiniciar la aplicación.
@Slf4j
@Component
@RequiredArgsConstructor
public class CertificadoLoader {

    // La SUNAT exige claves RSA de al menos 1024 bits
    static final int BITS_MINIMOS_RSA = 1024;

    private static final Pattern RUC_EN_TEXTO = Pattern.compile("(?<!\\d)\\d{11}(?!\\d)");

    private final SunatProperties propiedades;
    private final Clock clock;

    // El certificado configurado (sunat.certificado.*), comprobado contra el RUC del emisor (sunat.ruc)
    public CertificadoFirma cargar() {
        SunatProperties.Certificado config = propiedades.getCertificado();
        if (config.getRuta() == null || config.getClave() == null) {
            throw new CertificadoInvalidoException(
                    "No hay un certificado digital configurado: defina sunat.certificado.ruta y sunat.certificado.clave");
        }
        return cargar(config.getRuta(), config.getClave().toCharArray(), propiedades.getRuc());
    }

    CertificadoFirma cargar(String ruta, char[] clave, String rucEsperado) {
        KeyStore almacen = abrir(ruta, clave);
        String alias = aliasConClavePrivada(almacen, ruta);
        PrivateKey clavePrivada = clavePrivada(almacen, alias, clave);
        X509Certificate certificado = certificado(almacen, alias);

        validarClave(clavePrivada);
        validarVigencia(certificado);
        String ruc = rucDelCertificado(certificado);
        if (!ruc.equals(rucEsperado)) {
            throw new CertificadoInvalidoException("El certificado es del RUC " + ruc + " y el emisor es el RUC " + rucEsperado);
        }
        log.info("Certificado cargado: RUC {}, vence el {}", ruc, certificado.getNotAfter());
        return new CertificadoFirma(clavePrivada, certificado, ruc);
    }

    private KeyStore abrir(String ruta, char[] clave) {
        Path path;
        try {
            path = Path.of(ruta);
        } catch (InvalidPathException e) {
            throw new CertificadoInvalidoException("La ruta del certificado no es válida: " + ruta, e);
        }
        if (!Files.isRegularFile(path)) {
            throw new CertificadoInvalidoException("No se encontró el archivo del certificado: " + ruta);
        }
        try (InputStream in = Files.newInputStream(path)) {
            KeyStore almacen = KeyStore.getInstance("PKCS12");
            almacen.load(in, clave);
            return almacen;
        } catch (IOException e) {
            // Con una clave equivocada el JDK avisa con una causa propia; si no, el archivo no es un PKCS12 o está dañado
            if (e.getCause() instanceof UnrecoverableKeyException || contiene(e.getMessage(), "password was incorrect")) {
                throw new CertificadoInvalidoException("La clave del certificado es incorrecta", e);
            }
            throw new CertificadoInvalidoException("El archivo no es un certificado .p12 válido o está dañado: " + ruta, e);
        } catch (NoSuchAlgorithmException | CertificateException | KeyStoreException e) {
            throw new CertificadoInvalidoException("No se pudo leer el certificado: " + ruta, e);
        }
    }

    private String aliasConClavePrivada(KeyStore almacen, String ruta) {
        try {
            Enumeration<String> alias = almacen.aliases();
            while (alias.hasMoreElements()) {
                String actual = alias.nextElement();
                if (almacen.isKeyEntry(actual)) {
                    return actual;
                }
            }
        } catch (KeyStoreException e) {
            throw new CertificadoInvalidoException("No se pudo leer el certificado: " + ruta, e);
        }
        throw new CertificadoInvalidoException("El archivo no contiene una clave privada para firmar: " + ruta);
    }

    private PrivateKey clavePrivada(KeyStore almacen, String alias, char[] clave) {
        try {
            Key key = almacen.getKey(alias, clave);
            if (key instanceof PrivateKey privada) {
                return privada;
            }
            throw new CertificadoInvalidoException("El archivo no contiene una clave privada para firmar");
        } catch (UnrecoverableKeyException e) {
            throw new CertificadoInvalidoException("La clave del certificado es incorrecta", e);
        } catch (KeyStoreException | NoSuchAlgorithmException e) {
            throw new CertificadoInvalidoException("No se pudo leer la clave privada del certificado", e);
        }
    }

    private X509Certificate certificado(KeyStore almacen, String alias) {
        try {
            if (almacen.getCertificate(alias) instanceof X509Certificate x509) {
                return x509;
            }
        } catch (KeyStoreException e) {
            throw new CertificadoInvalidoException("No se pudo leer el certificado", e);
        }
        throw new CertificadoInvalidoException("El archivo no contiene un certificado X.509");
    }

    // La SUNAT solo acepta RSA de 1024 bits o más. Otro algoritmo (por ejemplo EC) se rechaza antes de firmar.
    private void validarClave(PrivateKey clave) {
        if (!(clave instanceof RSAKey rsa)) {
            throw new CertificadoInvalidoException("La clave del certificado es " + clave.getAlgorithm() + " y la SUNAT solo acepta RSA");
        }
        int bits = rsa.getModulus().bitLength();
        if (bits < BITS_MINIMOS_RSA) {
            throw new CertificadoInvalidoException("La clave RSA del certificado tiene " + bits + " bits y la SUNAT exige al menos " + BITS_MINIMOS_RSA);
        }
    }

    private void validarVigencia(X509Certificate certificado) {
        try {
            certificado.checkValidity(Date.from(clock.instant()));
        } catch (CertificateExpiredException e) {
            throw new CertificadoInvalidoException("El certificado venció el " + certificado.getNotAfter().toInstant(), e);
        } catch (CertificateNotYetValidException e) {
            throw new CertificadoInvalidoException("El certificado todavía no está vigente: empieza el " + certificado.getNotBefore().toInstant(), e);
        }
    }

    // Los certificados que acepta la SUNAT llevan el RUC en la unidad organizativa (OU) del titular. Puede venir solo
    // ("20100066603") o con texto ("RUC 20100066603").
    private String rucDelCertificado(X509Certificate certificado) {
        try {
            LdapName titular = new LdapName(certificado.getSubjectX500Principal().getName());
            for (Rdn rdn : titular.getRdns()) {
                if ("OU".equalsIgnoreCase(rdn.getType())) {
                    Matcher m = RUC_EN_TEXTO.matcher(String.valueOf(rdn.getValue()));
                    if (m.find()) {
                        return m.group();
                    }
                }
            }
        } catch (InvalidNameException e) {
            throw new CertificadoInvalidoException("No se pudo leer el titular del certificado", e);
        }
        throw new CertificadoInvalidoException(
                "El certificado no trae el RUC en la unidad organizativa (OU) de su titular: " + certificado.getSubjectX500Principal());
    }

    private static boolean contiene(String texto, String parte) {
        return texto != null && texto.contains(parte);
    }
}
