package apisunat.api_sunat;

import apisunat.api_sunat.config.SunatProperties;
import apisunat.api_sunat.dto.ClienteRequest;
import apisunat.api_sunat.dto.FacturaRequest;
import apisunat.api_sunat.dto.ItemRequest;
import apisunat.api_sunat.dto.ResultadoEmision;
import apisunat.api_sunat.exception.SunatFaultException;
import apisunat.api_sunat.service.FacturaService;
import apisunat.api_sunat.sunat.cdr.CdrParser;
import apisunat.api_sunat.sunat.cdr.CdrResultado;
import apisunat.api_sunat.sunat.firma.CertificadoFirma;
import apisunat.api_sunat.sunat.firma.CertificadoLoader;
import apisunat.api_sunat.sunat.firma.XmlSigner;
import apisunat.api_sunat.sunat.soap.BillServiceClient;
import apisunat.api_sunat.sunat.xml.FacturaXmlBuilder;
import apisunat.api_sunat.util.ZipUtil;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static apisunat.api_sunat.model.enums.ClaseResultado.ACEPTADO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Envían facturas de verdad al servicio BETA de la SUNAT (necesitan internet): solo corren con -Pbeta.
// Varias de estas pruebas documentan cosas que el beta HACE y la producción probablemente NO (ver docs/ERRORES.md, L-020 a L-022).
// Si el beta cambia, estas pruebas avisan.
@Tag("beta")
@SpringBootTest(properties = {"sunat.certificado.ruta=src/test/resources/certificados/valido.p12", "sunat.certificado.clave=prueba123"})
class SunatBetaFacturaRealTest {

    // Los números no se repiten entre corridas: segundos desde 1970 módulo 90 millones, más un contador dentro de la corrida
    private static final AtomicLong SIGUIENTE = new AtomicLong((System.currentTimeMillis() / 1000) % 80_000_000L + 1_000);

    @Autowired
    private FacturaService servicio;
    @Autowired
    private FacturaXmlBuilder builder;
    @Autowired
    private XmlSigner firmante;
    @Autowired
    private CertificadoLoader cargador;
    @Autowired
    private BillServiceClient cliente;
    @Autowired
    private CdrParser cdrParser;
    @Autowired
    private SunatProperties propiedades;
    @Autowired
    private Clock reloj;

    private static long siguiente() {
        return SIGUIENTE.getAndIncrement();
    }

    private static FacturaRequest factura() {
        return FacturaRequest.builder()
                .cliente(ClienteRequest.builder().ruc("20123456786").razonSocial("CLIENTE SAC").build())
                .items(List.of(ItemRequest.builder().descripcion("Polo básico negro").cantidad(new BigDecimal("2"))
                        .precioUnitario(new BigDecimal("35.00")).build()))
                .build();
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(reloj);
    }

    private CdrResultado enviarXml(String nombre, byte[] xml) {
        byte[] constancia = cliente.sendBill(nombre + ".zip", ZipUtil.comprimir(nombre + ".xml", xml));
        return cdrParser.leer(constancia);
    }

    private String nombre(long correlativo) {
        return propiedades.getRuc() + "-01-F001-" + correlativo;
    }

    // --- Lo que debe funcionar ---

    @Test
    void elBetaAceptaUnaFacturaFirmadaPorNosotros() {
        long n = siguiente();

        ResultadoEmision r = servicio.emitir(factura(), "F001", n);

        assertThat(r.getClase()).isEqualTo(ACEPTADO);
        assertThat(r.getCodigo()).isEqualTo("0");
        assertThat(r.getDescripcion()).isEqualTo("La Factura numero F001-" + n + ", ha sido aceptada");
        assertThat(r.getObservaciones()).isEmpty();
    }

    @Test
    void reenviarElMismoXmlDevuelveLaMismaConstanciaAceptada() {
        long n = siguiente();
        CertificadoFirma cert = cargador.cargar();
        byte[] xml = firmante.firmar(builder.construir(factura(), "F001", n, ahora()), cert);

        CdrResultado primera = enviarXml(nombre(n), xml);
        CdrResultado segunda = enviarXml(nombre(n), xml);

        assertThat(primera.aceptado()).isTrue();
        assertThat(segunda).isEqualTo(primera);
    }

    // --- Cosas que el beta deja pasar (la producción probablemente no): no confiar en el beta para validar ---

    @Test
    void elBetaAceptaUnaFacturaConElTotalMalCalculado() {
        long n = siguiente();
        String xml = new String(builder.construir(factura(), "F001", n, ahora()), StandardCharsets.ISO_8859_1)
                .replace("<cbc:PayableAmount currencyID=\"PEN\">70.00", "<cbc:PayableAmount currencyID=\"PEN\">71.00");

        CdrResultado cdr = enviarXml(nombre(n), firmante.firmar(xml.getBytes(StandardCharsets.ISO_8859_1), cargador.cargar()));

        assertThat(cdr.aceptado()).isTrue();
    }

    @Test
    void elBetaAceptaUnaBoletaEnviadaPorSendBill() {
        long n = siguiente();
        String xml = new String(builder.construir(factura(), "F001", n, ahora()), StandardCharsets.ISO_8859_1)
                .replace("F001-" + n, "B001-" + n).replace(">01</cbc:InvoiceTypeCode>", ">03</cbc:InvoiceTypeCode>");

        CdrResultado cdr = enviarXml(propiedades.getRuc() + "-03-B001-" + n,
                firmante.firmar(xml.getBytes(StandardCharsets.ISO_8859_1), cargador.cargar()));

        assertThat(cdr.aceptado()).isTrue();   // en producción se espera el error 1079
        assertThat(cdr.getIdDocumento()).isEqualTo("B001-" + n);
    }

    @Test
    void elBetaAceptaFechasViejasYFuturas() {
        for (int dias : new int[]{-30, -2, 1}) {
            long n = siguiente();
            byte[] xml = firmante.firmar(builder.construir(factura(), "F001", n, ahora().plusDays(dias)), cargador.cargar());
            assertThat(enviarXml(nombre(n), xml).aceptado()).as("días " + dias).isTrue();
        }
    }

    // --- Lo que el beta sí rechaza ---

    @Test
    void unXmlSinFirmaSeRechazaConElCodigo2335() {
        long n = siguiente();
        byte[] sinFirmar = builder.construir(factura(), "F001", n, ahora());

        assertThatThrownBy(() -> enviarXml(nombre(n), sinFirmar))
                .isInstanceOfSatisfying(SunatFaultException.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo("2335");
                    assertThat(e.getFaultString()).contains("No signature in message");
                });
    }

    @Test
    void unXmlAlteradoDespuesDeFirmarSeRechazaConElCodigo2335() {
        long n = siguiente();
        String firmado = new String(firmante.firmar(builder.construir(factura(), "F001", n, ahora()), cargador.cargar()),
                StandardCharsets.ISO_8859_1).replace("Polo básico negro", "Polo básico negra");

        assertThatThrownBy(() -> enviarXml(nombre(n), firmado.getBytes(StandardCharsets.ISO_8859_1)))
                .isInstanceOfSatisfying(SunatFaultException.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo("2335");
                    assertThat(e.getFaultString()).contains("Incorrect reference digest value");
                });
    }

    @Test
    void unXmlConOtroNombreQueElZipSeRechazaConElCodigo0161() {
        long n = siguiente();
        byte[] xml = firmante.firmar(builder.construir(factura(), "F001", n, ahora()), cargador.cargar());
        byte[] zip = ZipUtil.comprimir(nombre(n + 1_000_000) + ".xml", xml);

        assertThatThrownBy(() -> cliente.sendBill(nombre(n) + ".zip", zip))
                .isInstanceOfSatisfying(SunatFaultException.class, e -> assertThat(e.getCodigo()).isEqualTo("0161"));
    }

    @Test
    void unNombreConOtroRucQueElDelXmlSeRechazaConElCodigo1034() {
        long n = siguiente();
        byte[] xml = firmante.firmar(builder.construir(factura(), "F001", n, ahora()), cargador.cargar());

        assertThatThrownBy(() -> enviarXml("20512345678-01-F001-" + n, xml))
                .isInstanceOfSatisfying(SunatFaultException.class, e -> assertThat(e.getCodigo()).isEqualTo("1034"));
    }

    @Test
    void unXmlSinLosDatosObligatoriosSeRechazaConUnCodigoDeLaTabla() {
        long n = siguiente();

        assertThatThrownBy(() -> enviarXml(nombre(n), "<Invoice/>".getBytes(StandardCharsets.ISO_8859_1)))
                .isInstanceOfSatisfying(SunatFaultException.class, e -> assertThat(e.getCodigo()).isEqualTo("2075"));
    }
}
