package apisunat.api_sunat.service.impl;

import apisunat.api_sunat.config.SunatProperties;
import apisunat.api_sunat.dto.ClienteRequest;
import apisunat.api_sunat.dto.FacturaRequest;
import apisunat.api_sunat.dto.ItemRequest;
import apisunat.api_sunat.dto.ResultadoEmision;
import apisunat.api_sunat.exception.CertificadoInvalidoException;
import apisunat.api_sunat.exception.SunatFaultException;
import apisunat.api_sunat.exception.SunatNoDisponibleException;
import apisunat.api_sunat.exception.SunatRespuestaInvalidaException;
import apisunat.api_sunat.sunat.cdr.CdrParser;
import apisunat.api_sunat.sunat.cdr.CdrResultado;
import apisunat.api_sunat.sunat.errores.CatalogoCodigos;
import apisunat.api_sunat.sunat.errores.ClasificadorErrores;
import apisunat.api_sunat.sunat.firma.CertificadoLoader;
import apisunat.api_sunat.sunat.firma.XmlSigner;
import apisunat.api_sunat.sunat.soap.BillServiceClient;
import apisunat.api_sunat.sunat.xml.FacturaXmlBuilder;
import apisunat.api_sunat.sunat.xml.XsdValidator;
import apisunat.api_sunat.util.Fixtures;
import apisunat.api_sunat.util.ZipUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.w3c.dom.Document;

import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static apisunat.api_sunat.model.enums.ClaseResultado.ACEPTADO;
import static apisunat.api_sunat.model.enums.ClaseResultado.ACEPTADO_CON_OBSERVACIONES;
import static apisunat.api_sunat.model.enums.ClaseResultado.DESCONOCIDO;
import static apisunat.api_sunat.model.enums.ClaseResultado.ERROR_DE_CONFIGURACION;
import static apisunat.api_sunat.model.enums.ClaseResultado.ERROR_DE_DATOS;
import static apisunat.api_sunat.model.enums.ClaseResultado.RECHAZO;
import static apisunat.api_sunat.model.enums.ClaseResultado.TRANSITORIO;
import static apisunat.api_sunat.model.enums.ClaseResultado.YA_REGISTRADO;
import static apisunat.api_sunat.util.FacturaXmlTestUtil.leer;
import static apisunat.api_sunat.util.FacturaXmlTestUtil.valor;
import static apisunat.api_sunat.util.FirmaTestUtil.verifica;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// El recorrido completo (calcular, XML, firmar, XSD, ZIP, enviar, leer la constancia, clasificar) con la SUNAT simulada
@ExtendWith(MockitoExtension.class)
class FacturaServiceImplTest {

    // 2026-10-29 03:30 UTC es todavía el 28 a las 22:30 en Lima
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-29T03:30:00Z"), ZoneOffset.UTC);

    @Mock
    private BillServiceClient cliente;

    private SunatProperties propiedades;
    private FacturaServiceImpl servicio;

    @BeforeEach
    void preparar() throws URISyntaxException {
        propiedades = new SunatProperties();
        propiedades.setRuc("20100066603");
        propiedades.getEmisor().setRazonSocial("EMPRESA DE PRUEBAS SAC");
        propiedades.getCertificado().setRuta(Path.of(getClass().getResource("/certificados/valido.p12").toURI()).toString());
        propiedades.getCertificado().setClave("prueba123");
        servicio = crear(propiedades);
    }

    private FacturaServiceImpl crear(SunatProperties p) {
        return new FacturaServiceImpl(p, RELOJ, new FacturaXmlBuilder(p), new CertificadoLoader(p, RELOJ), new XmlSigner(),
                new XsdValidator(), cliente, new CdrParser(), new ClasificadorErrores(new CatalogoCodigos()));
    }

    private static FacturaRequest factura() {
        return FacturaRequest.builder()
                .cliente(ClienteRequest.builder().ruc("20123456786").razonSocial("CLIENTE SAC").build())
                .items(List.of(ItemRequest.builder().descripcion("Polo básico negro").cantidad(new BigDecimal("2"))
                        .precioUnitario(new BigDecimal("35.00")).build()))
                .build();
    }

    // La constancia de los fixtures, pero con el número de la factura que se envía
    private static byte[] constancia(String fixture, String numero) {
        String xml = Fixtures.texto(fixture).replace("FA01-981", numero).replace("FT01-982", numero);
        return ZipUtil.comprimir("R-20100066603-01-" + numero + ".xml", xml.getBytes(StandardCharsets.ISO_8859_1));
    }

    private void respondeConConstancia(String fixture) {
        when(cliente.sendBill(anyString(), any())).thenReturn(constancia(fixture, "F001-1"));
    }

    // --- Lo que se manda a la SUNAT ---

    @Test
    void mandaUnZipConNombreRucTipoSerieNumeroConElXmlFirmadoAdentro() {
        respondeConConstancia("sunat/cdr/aceptado.xml");

        servicio.emitir(factura(), "F001", 1);

        ArgumentCaptor<String> nombre = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> zip = ArgumentCaptor.forClass(byte[].class);
        verify(cliente).sendBill(nombre.capture(), zip.capture());

        assertThat(nombre.getValue()).isEqualTo("20100066603-01-F001-1.zip");
        ZipUtil.Entrada dentro = ZipUtil.leerXmlUnico(zip.getValue());
        assertThat(dentro.nombre()).isEqualTo("20100066603-01-F001-1.xml");
        assertThat(verifica(dentro.contenido())).isTrue();
    }

    @Test
    void elXmlEnviadoCumpleElEsquemaEstaFirmadoYCoincideConElNombreDelArchivo() {
        respondeConConstancia("sunat/cdr/aceptado.xml");

        ResultadoEmision r = servicio.emitir(factura(), "F001", 1);

        assertThat(new XsdValidator().validarFactura(r.getXmlFirmado())).isEmpty();
        assertThat(verifica(r.getXmlFirmado())).isTrue();
        Document doc = leer(r.getXmlFirmado());
        assertThat(valor(doc, "/inv:Invoice/cbc:ID")).isEqualTo("F001-1");
        assertThat(r.getNombreArchivo()).isEqualTo("20100066603-01-" + valor(doc, "/inv:Invoice/cbc:ID"));
    }

    @Test
    void laFechaYLaHoraDeEmisionSonLasDeLimaYNoLasDelServidor() {
        respondeConConstancia("sunat/cdr/aceptado.xml");

        Document doc = leer(servicio.emitir(factura(), "F001", 1).getXmlFirmado());

        assertThat(valor(doc, "/inv:Invoice/cbc:IssueDate")).isEqualTo("2026-10-28");
        assertThat(valor(doc, "/inv:Invoice/cbc:IssueTime")).isEqualTo("22:30:00");
    }

    @Test
    void elMismoXmlFirmadoDeUnaFacturaEsElQueSeEnvioEnElZip() {
        respondeConConstancia("sunat/cdr/aceptado.xml");

        ResultadoEmision r = servicio.emitir(factura(), "F001", 1);

        ArgumentCaptor<byte[]> zip = ArgumentCaptor.forClass(byte[].class);
        verify(cliente).sendBill(anyString(), zip.capture());
        assertThat(ZipUtil.leerXmlUnico(zip.getValue()).contenido()).isEqualTo(r.getXmlFirmado());
    }

    // --- Lo que contesta la SUNAT ---

    @Test
    void unaConstanciaAceptadaDaAceptado() {
        respondeConConstancia("sunat/cdr/aceptado.xml");

        ResultadoEmision r = servicio.emitir(factura(), "F001", 1);

        assertThat(r.getClase()).isEqualTo(ACEPTADO);
        assertThat(r.getCodigo()).isEqualTo("0");
        assertThat(r.getDescripcion()).isEqualTo("La Factura numero F001-1, ha sido aceptada");
        assertThat(r.getObservaciones()).isEmpty();
        assertThat(r.getNombreArchivo()).isEqualTo("20100066603-01-F001-1");
    }

    @Test
    void unaConstanciaConNotasDaAceptadoConObservaciones() {
        respondeConConstancia("sunat/cdr/aceptado-con-observaciones.xml");

        ResultadoEmision r = servicio.emitir(factura(), "F001", 1);

        assertThat(r.getClase()).isEqualTo(ACEPTADO_CON_OBSERVACIONES);
        assertThat(r.getObservaciones()).extracting(CdrResultado.Observacion::getCodigo).containsExactly("4031", "4001");
    }

    @Test
    void unaConstanciaRechazadaDaRechazoConSuCodigo() {
        when(cliente.sendBill(anyString(), any())).thenReturn(rechazoCon("3270"));

        ResultadoEmision r = servicio.emitir(factura(), "F001", 1);

        assertThat(r.getClase()).isEqualTo(RECHAZO);
        assertThat(r.getCodigo()).isEqualTo("3270");
    }

    private static byte[] rechazoCon(String codigo) {
        String xml = Fixtures.texto("sunat/cdr/rechazado.xml").replace("FT01-982", "F001-1").replace(">2047<", ">" + codigo + "<");
        return ZipUtil.comprimir("R-1.xml", xml.getBytes(StandardCharsets.ISO_8859_1));
    }

    @ParameterizedTest
    @CsvSource({"0111,ERROR_DE_CONFIGURACION", "0151,ERROR_DE_DATOS", "0306,ERROR_DE_DATOS", "1033,YA_REGISTRADO",
            "1079,ERROR_DE_DATOS", "0100,TRANSITORIO", "0109,TRANSITORIO", "0200,DESCONOCIDO", "2335,ERROR_DE_CONFIGURACION"})
    void unErrorSoapSeDevuelveClasificadoSinLanzarExcepcion(String codigo, String claseEsperada) {
        when(cliente.sendBill(anyString(), any())).thenThrow(new SunatFaultException("S:Client." + codigo, "Texto del error " + codigo, codigo));

        ResultadoEmision r = servicio.emitir(factura(), "F001", 1);

        assertThat(r.getClase().name()).isEqualTo(claseEsperada);
        assertThat(r.getCodigo()).isEqualTo(codigo);
        assertThat(r.getDescripcion()).isEqualTo("Texto del error " + codigo);
        assertThat(r.getNombreArchivo()).isEqualTo("20100066603-01-F001-1");
        assertThat(r.getXmlFirmado()).isNotEmpty();
    }

    @Test
    void unErrorSoapSinCodigoSeDevuelveComoDesconocido() {
        when(cliente.sendBill(anyString(), any())).thenThrow(new SunatFaultException("S:Server", "algo raro", null));

        ResultadoEmision r = servicio.emitir(factura(), "F001", 1);

        assertThat(r.getClase()).isEqualTo(DESCONOCIDO);
        assertThat(r.getCodigo()).isNull();
    }

    // --- Cuando no hay respuesta de la SUNAT, no se inventa un resultado ---

    @Test
    void siLaSunatNoEstaDisponibleSeLanzaLaExcepcionYNoSeDiceQueSeRechazo() {
        when(cliente.sendBill(anyString(), any())).thenThrow(new SunatNoDisponibleException("timeout", true, null));

        assertThatThrownBy(() -> servicio.emitir(factura(), "F001", 1))
                .isInstanceOfSatisfying(SunatNoDisponibleException.class, e -> assertThat(e.isTimeout()).isTrue());
    }

    @Test
    void siLaRespuestaEsInvalidaSeLanzaLaExcepcion() {
        when(cliente.sendBill(anyString(), any())).thenThrow(new SunatRespuestaInvalidaException("html"));
        assertThatThrownBy(() -> servicio.emitir(factura(), "F001", 1)).isInstanceOf(SunatRespuestaInvalidaException.class);
    }

    @Test
    void unaConstanciaDeOtroDocumentoSeRechazaComoRespuestaInvalida() {
        when(cliente.sendBill(anyString(), any())).thenReturn(constancia("sunat/cdr/aceptado.xml", "F001-2"));

        assertThatThrownBy(() -> servicio.emitir(factura(), "F001", 1))
                .isInstanceOf(SunatRespuestaInvalidaException.class)
                .hasMessage("La constancia de la SUNAT es del documento F001-2 y se envió F001-1");
    }

    @Test
    void unaConstanciaIlegibleSeLanzaComoRespuestaInvalida() {
        when(cliente.sendBill(anyString(), any())).thenReturn("no es un zip".getBytes());
        assertThatThrownBy(() -> servicio.emitir(factura(), "F001", 1)).isInstanceOf(SunatRespuestaInvalidaException.class);
    }

    // --- Lo que falla antes de llamar a la SUNAT: nunca se le manda nada ---

    @ParameterizedTest
    @ValueSource(strings = {"B001", "f001", "F00", "F0011", ""})
    void unaSerieInvalidaFallaSinLlamarALaSunat(String serie) {
        assertThatThrownBy(() -> servicio.emitir(factura(), serie, 1)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(cliente);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, 100_000_000L})
    void unCorrelativoInvalidoFallaSinLlamarALaSunat(long correlativo) {
        assertThatThrownBy(() -> servicio.emitir(factura(), "F001", correlativo)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(cliente);
    }

    @Test
    void unaFacturaSinItemsFallaSinLlamarALaSunat() {
        FacturaRequest sinItems = FacturaRequest.builder()
                .cliente(ClienteRequest.builder().ruc("20123456786").razonSocial("CLIENTE SAC").build()).items(List.of()).build();
        assertThatThrownBy(() -> servicio.emitir(sinItems, "F001", 1)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(cliente);
    }

    @Test
    void sinCertificadoConfiguradoFallaSinLlamarALaSunat() {
        propiedades.getCertificado().setRuta(null);
        propiedades.getCertificado().setClave(null);

        assertThatThrownBy(() -> servicio.emitir(factura(), "F001", 1))
                .isInstanceOf(CertificadoInvalidoException.class)
                .hasMessageContaining("sunat.certificado.ruta");
        verify(cliente, never()).sendBill(anyString(), any());
    }

    @Test
    void conUnCertificadoDeOtroRucFallaSinLlamarALaSunat() throws Exception {
        propiedades.getCertificado().setRuta(Path.of(getClass().getResource("/certificados/otro-ruc.p12").toURI()).toString());

        assertThatThrownBy(() -> servicio.emitir(factura(), "F001", 1))
                .isInstanceOf(CertificadoInvalidoException.class)
                .hasMessageContaining("20512345678");
        verifyNoInteractions(cliente);
    }

    @Test
    void conUnCertificadoVencidoFallaSinLlamarALaSunat() throws Exception {
        propiedades.getCertificado().setRuta(Path.of(getClass().getResource("/certificados/vencido.p12").toURI()).toString());

        assertThatThrownBy(() -> servicio.emitir(factura(), "F001", 1))
                .isInstanceOf(CertificadoInvalidoException.class)
                .hasMessageContaining("venció");
        verifyNoInteractions(cliente);
    }

    @Test
    void siElXmlArmadoNoCumpleElEsquemaEsUnErrorDelProgramaYNoSeEnvia() {
        XsdValidator roto = mock(XsdValidator.class);
        when(roto.validarFactura(any())).thenReturn(List.of("línea 3: elemento inesperado"));
        FacturaServiceImpl conEsquemaRoto = new FacturaServiceImpl(propiedades, RELOJ, new FacturaXmlBuilder(propiedades),
                new CertificadoLoader(propiedades, RELOJ), new XmlSigner(), roto, cliente, new CdrParser(),
                new ClasificadorErrores(new CatalogoCodigos()));

        assertThatThrownBy(() -> conEsquemaRoto.emitir(factura(), "F001", 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("20100066603-01-F001-1")
                .hasMessageContaining("línea 3: elemento inesperado");
        verifyNoInteractions(cliente);
    }

    @Test
    void conOtroRucEnLaConfiguracionElNombreYElXmlUsanEseRuc() throws Exception {
        propiedades.setRuc("20512345678");
        propiedades.getCertificado().setRuta(Path.of(getClass().getResource("/certificados/otro-ruc.p12").toURI()).toString());
        when(cliente.sendBill(anyString(), any())).thenReturn(constancia("sunat/cdr/aceptado.xml", "F001-1"));

        ResultadoEmision r = servicio.emitir(factura(), "F001", 1);

        assertThat(r.getNombreArchivo()).isEqualTo("20512345678-01-F001-1");
        assertThat(valor(leer(r.getXmlFirmado()), "/inv:Invoice/cac:AccountingSupplierParty/cac:Party/cac:PartyIdentification/cbc:ID"))
                .isEqualTo("20512345678");
    }
}
