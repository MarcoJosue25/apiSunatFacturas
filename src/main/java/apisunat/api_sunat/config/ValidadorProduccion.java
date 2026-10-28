package apisunat.api_sunat.config;

import apisunat.api_sunat.exception.CertificadoInvalidoException;
import apisunat.api_sunat.sunat.firma.CertificadoFirma;
import apisunat.api_sunat.sunat.firma.CertificadoLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

// Con el perfil prod la aplicación no arranca si algo todavía apunta a las pruebas: es más barato un error al arrancar que una factura
// real firmada con un certificado de prueba o enviada al lugar equivocado. Los mensajes dicen qué está mal, nunca el valor de un secreto.
@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class ValidadorProduccion implements InitializingBean {

    static final String PREFIJO_URL_PRODUCCION = "https://e-factura.sunat.gob.pe/";
    static final String RUC_DE_PRUEBAS = "20100066603";
    static final String USUARIO_DE_PRUEBAS = "MODDATOS";
    static final String EMISOR_DE_PRUEBAS = "EMPRESA DE PRUEBAS SAC";
    static final int LARGO_MINIMO_LLAVE = 32;

    private static final Pattern LLAVE_ASCII = Pattern.compile("^[\\x21-\\x7E]+$");

    private final SunatProperties propiedades;
    private final CertificadoLoader certificadoLoader;

    @Override
    public void afterPropertiesSet() {
        List<String> problemas = problemas();
        if (!problemas.isEmpty()) {
            throw new IllegalStateException("La configuración no sirve para producción (perfil prod):\n - " + String.join("\n - ", problemas));
        }
        log.info("Perfil prod: la configuración apunta a la SUNAT de producción (RUC {})", propiedades.getRuc());
    }

    List<String> problemas() {
        List<String> problemas = new ArrayList<>();
        url(problemas);
        if (RUC_DE_PRUEBAS.equals(propiedades.getRuc())) {
            problemas.add("El RUC (SUNAT_RUC) es el de pruebas de la SUNAT: ponga el RUC del emisor");
        }
        if (USUARIO_DE_PRUEBAS.equals(propiedades.getUsuario()) || USUARIO_DE_PRUEBAS.equals(propiedades.getClave())) {
            problemas.add("El usuario o la clave SOL (SUNAT_USUARIO, SUNAT_CLAVE) son los de pruebas (" + USUARIO_DE_PRUEBAS + "): ponga el usuario secundario de SOL");
        }
        if (EMISOR_DE_PRUEBAS.equals(propiedades.getEmisor().getRazonSocial())) {
            problemas.add("La razón social del emisor (SUNAT_EMISOR_RAZON_SOCIAL) es la de pruebas");
        }
        certificado(problemas);
        llave(problemas);
        return problemas;
    }

    private void url(List<String> problemas) {
        String url = propiedades.getUrl();
        if (url == null || !url.startsWith(PREFIJO_URL_PRODUCCION)) {
            problemas.add(url != null && url.toLowerCase().contains("beta")
                    ? "La URL de la SUNAT (SUNAT_URL) es la del servicio de pruebas (beta)"
                    : "La URL de la SUNAT (SUNAT_URL) no es la de producción (debe empezar con " + PREFIJO_URL_PRODUCCION + ")");
        }
        if (propiedades.getConsultaCdr().isHabilitada() && !propiedades.getConsultaCdr().getUrl().startsWith(PREFIJO_URL_PRODUCCION)) {
            problemas.add("La URL de la consulta de constancias (SUNAT_CONSULTA_CDR_URL) no es la de producción");
        }
    }

    private void certificado(List<String> problemas) {
        CertificadoFirma certificado;
        try {
            certificado = certificadoLoader.cargar();
        } catch (CertificadoInvalidoException e) {
            problemas.add("El certificado digital no sirve: " + e.getMessage());
            return;
        }
        // Los certificados reales los emite una entidad certificadora; uno que se firmó a sí mismo es de pruebas
        if (certificado.certificado().getSubjectX500Principal().equals(certificado.certificado().getIssuerX500Principal())) {
            problemas.add("El certificado digital es autofirmado (de pruebas): use el certificado que emitió una entidad certificadora y está registrado en SOL");
        }
    }

    private void llave(List<String> problemas) {
        String llave = propiedades.getApiKey();
        if (llave == null || llave.isBlank()) {
            problemas.add("Falta la llave de la API (SUNAT_API_KEY): sin ella cualquiera podría emitir comprobantes");
        } else if (llave.length() < LARGO_MINIMO_LLAVE) {
            problemas.add("La llave de la API (SUNAT_API_KEY) es muy corta: debe tener al menos " + LARGO_MINIMO_LLAVE + " caracteres (por ejemplo openssl rand -hex 32)");
        } else if (!LLAVE_ASCII.matcher(llave).matches()) {
            problemas.add("La llave de la API (SUNAT_API_KEY) solo puede llevar caracteres ASCII visibles, sin espacios: viaja en una cabecera HTTP");
        }
    }
}
