package apisunat.api_sunat.sunat.soap;

import apisunat.api_sunat.config.SunatProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Document;

import static apisunat.api_sunat.util.XmlTest.cantidad;
import static apisunat.api_sunat.util.XmlTest.leer;
import static apisunat.api_sunat.util.XmlTest.texto;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SoapEnvelopeBuilderTest {

    private SunatProperties propiedades(String ruc, String usuario, String clave) {
        SunatProperties p = new SunatProperties();
        p.setUrl("https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService");
        p.setRuc(ruc);
        p.setUsuario(usuario);
        p.setClave(clave);
        p.setConexionTimeoutMs(5000);
        p.setLecturaTimeoutMs(30000);
        return p;
    }

    private SoapEnvelopeBuilder constructor() {
        return new SoapEnvelopeBuilder(propiedades("20100066603", "MODDATOS", "MODDATOS"));
    }

    @Test
    void getStatusGeneraElSobreExactoDelManual() {
        String esperado = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\""
                + " xmlns:ser=\"http://service.sunat.gob.pe\""
                + " xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\">"
                + "<soapenv:Header><wsse:Security><wsse:UsernameToken>"
                + "<wsse:Username>20100066603MODDATOS</wsse:Username>"
                + "<wsse:Password>MODDATOS</wsse:Password>"
                + "</wsse:UsernameToken></wsse:Security></soapenv:Header>"
                + "<soapenv:Body><ser:getStatus><ticket>201100000011227</ticket></ser:getStatus></soapenv:Body>"
                + "</soapenv:Envelope>";
        assertThat(constructor().getStatus("201100000011227")).isEqualTo(esperado);
    }

    @Test
    void elUsuarioEsElRucPegadoAlUsuarioSol() {
        Document doc = leer(constructor().getStatus("1"));
        assertThat(texto(doc, "Username")).isEqualTo("20100066603MODDATOS");
        assertThat(texto(doc, "Password")).isEqualTo("MODDATOS");
    }

    @Test
    void usaElRucYElUsuarioQueTieneConfigurados() {
        SoapEnvelopeBuilder b = new SoapEnvelopeBuilder(propiedades("10456789012", "USUSEC", "clave-secreta"));
        Document doc = leer(b.getStatus("1"));
        assertThat(texto(doc, "Username")).isEqualTo("10456789012USUSEC");
        assertThat(texto(doc, "Password")).isEqualTo("clave-secreta");
    }

    @Test
    void elTicketViajaEnElCuerpo() {
        Document doc = leer(constructor().getStatus("201100000011227"));
        assertThat(texto(doc, "ticket")).isEqualTo("201100000011227");
        assertThat(cantidad(doc, "getStatus")).isEqualTo(1);
    }

    @Test
    void elSobreTieneUnaSolaSeguridadUnSoloHeaderYUnSoloBody() {
        Document doc = leer(constructor().getStatus("1"));
        assertThat(cantidad(doc, "Security")).isEqualTo(1);
        assertThat(cantidad(doc, "Header")).isEqualTo(1);
        assertThat(cantidad(doc, "Body")).isEqualTo(1);
        assertThat(cantidad(doc, "UsernameToken")).isEqualTo(1);
    }

    // --- Caracteres especiales: leídos de vuelta con un parser tienen que salir idénticos ---

    @ParameterizedTest
    @ValueSource(strings = {"a&b", "a<b>c", "comillas\"dobles\"", "comilla'simple'", "&amp; ya escapado", "]]>", "<![CDATA[x]]>",
            "ñandú áéíóú ÑÁÉÍÓÚ ü", "€ — “smart” 😀", "con espacios  dobles", "tab\ty", "línea\nnueva"})
    void laClaveConCaracteresEspecialesSeLeeIgualQueSeEnvio(String clave) {
        SoapEnvelopeBuilder b = new SoapEnvelopeBuilder(propiedades("20100066603", "MODDATOS", clave));
        Document doc = leer(b.getStatus("1"));
        assertThat(texto(doc, "Password")).isEqualTo(clave);
    }

    @ParameterizedTest
    @ValueSource(strings = {"U&S", "U<S>", "U\"S", "U'S", "Ñu"})
    void elUsuarioConCaracteresEspecialesSeLeeIgualQueSeEnvio(String usuario) {
        SoapEnvelopeBuilder b = new SoapEnvelopeBuilder(propiedades("20100066603", usuario, "x"));
        Document doc = leer(b.getStatus("1"));
        assertThat(texto(doc, "Username")).isEqualTo("20100066603" + usuario);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1&2", "<script>", "'; DROP TABLE", "</ticket><ticket>999", "12 34"})
    void unTicketMalintencionadoNoPuedeAlterarLaEstructuraDelXml(String ticket) {
        Document doc = leer(constructor().getStatus(ticket));
        assertThat(texto(doc, "ticket")).isEqualTo(ticket);
        assertThat(cantidad(doc, "ticket")).isEqualTo(1);
    }

    @Test
    void unaClaveQueIntentaCerrarElElementoNoRompeElSobre() {
        SoapEnvelopeBuilder b = new SoapEnvelopeBuilder(propiedades("20100066603", "MODDATOS", "</wsse:Password><x/>"));
        Document doc = leer(b.getStatus("1"));
        assertThat(texto(doc, "Password")).isEqualTo("</wsse:Password><x/>");
        assertThat(cantidad(doc, "x")).isZero();
    }

    // --- Entradas inválidas ---

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t\n", "\u000B"})
    void sinTicketNoSeArmaElSobre(String ticket) {
        assertThatThrownBy(() -> constructor().getStatus(ticket))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ticket");
    }

    @ParameterizedTest
    @ValueSource(strings = {"12\u0000", "\u0001abc", "a\u001Fb", "\u0008"})
    void losCaracteresDeControlSeRechazanPorqueNoExistenEnXml(String ticket) {
        assertThatThrownBy(() -> constructor().getStatus(ticket))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("control");
    }

    @Test
    void unaClaveConCaracterDeControlTambienSeRechaza() {
        SoapEnvelopeBuilder b = new SoapEnvelopeBuilder(propiedades("20100066603", "MODDATOS", "cla\u0000ve"));
        assertThatThrownBy(() -> b.getStatus("1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elMensajeDeErrorNoRevelaLaClave() {
        SoapEnvelopeBuilder b = new SoapEnvelopeBuilder(propiedades("20100066603", "MODDATOS", "cla\u0000ve-secreta"));
        assertThatThrownBy(() -> b.getStatus("1"))
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("secreta").doesNotContain("cla"));
    }
}
