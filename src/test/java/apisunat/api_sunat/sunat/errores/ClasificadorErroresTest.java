package apisunat.api_sunat.sunat.errores;

import apisunat.api_sunat.exception.SunatFaultException;
import apisunat.api_sunat.exception.SunatNoDisponibleException;
import apisunat.api_sunat.model.enums.ClaseResultado;
import apisunat.api_sunat.sunat.cdr.CdrResultado;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static apisunat.api_sunat.model.enums.ClaseResultado.ACEPTADO;
import static apisunat.api_sunat.model.enums.ClaseResultado.ACEPTADO_CON_OBSERVACIONES;
import static apisunat.api_sunat.model.enums.ClaseResultado.DESCONOCIDO;
import static apisunat.api_sunat.model.enums.ClaseResultado.ERROR_DE_CONFIGURACION;
import static apisunat.api_sunat.model.enums.ClaseResultado.ERROR_DE_DATOS;
import static apisunat.api_sunat.model.enums.ClaseResultado.RECHAZO;
import static apisunat.api_sunat.model.enums.ClaseResultado.TRANSITORIO;
import static apisunat.api_sunat.model.enums.ClaseResultado.YA_REGISTRADO;
import static org.assertj.core.api.Assertions.assertThat;

class ClasificadorErroresTest {

    private static final CatalogoCodigos CATALOGO = new CatalogoCodigos();
    private final ClasificadorErrores clasificador = new ClasificadorErrores(CATALOGO);

    // --- Una regla de las notas por prueba ---

    @ParameterizedTest
    @ValueSource(strings = {"0100", "0109"})
    void soloEl0100YEl0109SonTransitorios(String codigo) {
        assertThat(clasificador.clasificar(codigo)).isEqualTo(TRANSITORIO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0111", "0154", "1078", "1080", "1086", "2325", "2326", "2335"})
    void loQueEsDeConfiguracionSeAvisaAlDuenoYNoSeReintenta(String codigo) {
        assertThat(clasificador.clasificar(codigo)).isEqualTo(ERROR_DE_CONFIGURACION);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0151", "0155", "0156", "0157", "0158", "0159", "0160", "0161", "0306", "0127", "1079"})
    void losErroresNuestrosDelRangoCeroSonErroresDeDatosYNoTransitorios(String codigo) {
        assertThat(clasificador.clasificar(codigo)).isEqualTo(ERROR_DE_DATOS);
    }

    @Test
    void el0306QueEsUnXmlQueNoCumpleElEsquemaNoSeReintentaNunca() {
        // El caso que más tienta reintentar por estar en el rango "de excepciones de SUNAT" y es nuestro
        assertThat(clasificador.clasificar("0306")).isEqualTo(ERROR_DE_DATOS).isNotEqualTo(TRANSITORIO);
    }

    @Test
    void elTicketQueNoExisteEsUnErrorDeDatos() {
        assertThat(clasificador.clasificar("0127")).isEqualTo(ERROR_DE_DATOS);
    }

    @Test
    void unaBoletaPorSendBillEsUnErrorDeDatosPorqueVaPorResumenDiario() {
        assertThat(clasificador.clasificar("1079")).isEqualTo(ERROR_DE_DATOS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1001", "1003", "1004", "1007", "1008", "1034", "1035", "1036", "1037", "1083", "1084"})
    void losDemasErroresDeFormatoDelRangoMilSonErroresDeDatos(String codigo) {
        assertThat(clasificador.clasificar(codigo)).isEqualTo(ERROR_DE_DATOS);
    }

    @Test
    void elComprobanteYaInformadoComoAnuladoORechazadoEsUnRechazo() {
        assertThat(clasificador.clasificar("1032")).isEqualTo(RECHAZO);
    }

    @Test
    void elComprobanteRegistradoAntesEsYaRegistradoYNoUnErrorCualquiera() {
        assertThat(clasificador.clasificar("1033")).isEqualTo(YA_REGISTRADO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2010", "2011", "2800", "3000", "3270", "3271", "3292", "3294"})
    void losCodigosDeRechazoDelRangoDosMilATresMilNoveSonRechazos(String codigo) {
        assertThat(clasificador.clasificar(codigo)).isEqualTo(RECHAZO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2266", "2404", "2901", "2910", "2919", "2988", "3228"})
    void lasObservacionesQueEstanDentroDelRangoDeRechazosSiguenSiendoObservaciones(String codigo) {
        assertThat(clasificador.clasificar(codigo)).isEqualTo(ACEPTADO_CON_OBSERVACIONES);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2917", "3219"})
    void losDosCodigosQueSonErrorUObservacionSeTratanComoRechazoPorPrecaucion(String codigo) {
        assertThat(clasificador.clasificar(codigo)).isEqualTo(RECHAZO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"4005", "4006", "4252", "4287", "4301"})
    void losCodigosDeCuatroMilEnAdelanteSonObservaciones(String codigo) {
        assertThat(clasificador.clasificar(codigo)).isEqualTo(ACEPTADO_CON_OBSERVACIONES);
    }

    // --- Lo desconocido nunca se reintenta ---

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"0000", "0200", "0102", "0103", "9999", "100", "01000", " 0100", "0100 ", "abcd", "0100\n", "0 100"})
    void unCodigoQueNoEstaEnLaTablaEsDesconocidoYNoSeReintenta(String codigo) {
        ClaseResultado clase = clasificador.clasificar(codigo);
        assertThat(clase).isEqualTo(DESCONOCIDO);
        assertThat(clase.esReintentable()).isFalse();
    }

    @Test
    void elCodigo0200QueDioElBetaSinSeguridadNoSeReintentaPorqueNoEstaEnLaTabla() {
        assertThat(clasificador.clasificar(new SunatFaultException("soap-env:Client.0200", "No se pudo procesar su solicitud", "0200")))
                .isEqualTo(DESCONOCIDO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2047", "4001", "4031"})
    void losCodigosDeEjemploDelManualDe2012YaNoExistenEnLaTablaActual(String codigo) {
        // El manual usa 2047, 4001 y 4031 en sus ejemplos, pero las Reglas de validación de 2026 ya no los tienen
        assertThat(clasificador.clasificar(codigo)).isEqualTo(DESCONOCIDO);
    }

    // --- Todos los códigos de la tabla ---

    @Test
    void todosLosCodigosDeLaTablaReciben_unaClaseSinFallar() {
        for (CodigoSunat c : CATALOGO.todos()) {
            assertThat(clasificador.clasificar(c.codigo())).as(c.codigo()).isNotNull();
        }
    }

    @Test
    void ningunCodigoDeLaTablaQuedaComoDesconocido() {
        List<String> sinClase = CATALOGO.todos().stream()
                .filter(c -> clasificador.clasificar(c.codigo()) == DESCONOCIDO)
                .map(CodigoSunat::codigo).toList();
        assertThat(sinClase).isEmpty();
    }

    @Test
    void lasCantidadesPorClaseCoincidenConElCalculoIndependienteEnPython() {
        Map<ClaseResultado, Long> cantidades = new EnumMap<>(ClaseResultado.class);
        CATALOGO.todos().forEach(c -> cantidades.merge(clasificador.clasificar(c.codigo()), 1L, Long::sum));

        assertThat(cantidades).containsExactlyInAnyOrderEntriesOf(Map.of(
                TRANSITORIO, 2L, ERROR_DE_CONFIGURACION, 8L, ERROR_DE_DATOS, 24L,
                RECHAZO, 654L, YA_REGISTRADO, 1L, ACEPTADO_CON_OBSERVACIONES, 180L));
    }

    @Test
    void entreTodosLosCodigosSoloDosSePuedenReintentar() {
        List<String> reintentables = CATALOGO.todos().stream()
                .map(CodigoSunat::codigo)
                .filter(c -> clasificador.clasificar(c).esReintentable())
                .sorted().collect(Collectors.toList());
        assertThat(reintentables).containsExactly("0100", "0109");
    }

    @Test
    void todosLosCodigosDeTipoObservacionDeLaTablaSonObservacionesAceptadas() {
        for (CodigoSunat c : CATALOGO.todos()) {
            if (c.tipo() == CodigoSunat.Tipo.OBSERVACION) {
                assertThat(clasificador.clasificar(c.codigo())).as(c.codigo()).isEqualTo(ACEPTADO_CON_OBSERVACIONES);
            }
        }
    }

    @Test
    void ningunCodigoDeTipoErrorDeLaTablaSeAceptaComoObservacion() {
        for (CodigoSunat c : CATALOGO.todos()) {
            if (c.tipo() == CodigoSunat.Tipo.ERROR) {
                assertThat(clasificador.clasificar(c.codigo())).as(c.codigo()).isNotEqualTo(ACEPTADO_CON_OBSERVACIONES).isNotEqualTo(ACEPTADO);
            }
        }
    }

    // --- Quién se puede reintentar ---

    @ParameterizedTest
    @EnumSource(value = ClaseResultado.class, names = "TRANSITORIO", mode = EnumSource.Mode.EXCLUDE)
    void ningunaClaseQueNoSeaTransitoriaSeReintenta(ClaseResultado clase) {
        assertThat(clase.esReintentable()).isFalse();
    }

    @Test
    void laClaseTransitoriaSiSeReintenta() {
        assertThat(TRANSITORIO.esReintentable()).isTrue();
    }

    // --- Constancias (CDR) ---

    private static CdrResultado cdr(String codigo, CdrResultado.Observacion... observaciones) {
        CdrResultado.CdrResultadoBuilder b = CdrResultado.builder().idDocumento("F001-1").codigo(codigo).descripcion("x");
        for (CdrResultado.Observacion o : observaciones) {
            b.observacion(o);
        }
        return b.build();
    }

    @Test
    void unaConstanciaAceptadaSinNotasEsAceptada() {
        assertThat(clasificador.clasificar(cdr("0"))).isEqualTo(ACEPTADO);
    }

    @Test
    void unaConstanciaAceptadaConNotasEsAceptadaConObservaciones() {
        assertThat(clasificador.clasificar(cdr("0", new CdrResultado.Observacion("4031", "x")))).isEqualTo(ACEPTADO_CON_OBSERVACIONES);
    }

    @Test
    void unaConstanciaRechazadaSeClasificaPorSuCodigo() {
        assertThat(clasificador.clasificar(cdr("2010"))).isEqualTo(RECHAZO);
        assertThat(clasificador.clasificar(cdr("2335"))).isEqualTo(ERROR_DE_CONFIGURACION);
        assertThat(clasificador.clasificar(cdr("0100"))).isEqualTo(TRANSITORIO);
        assertThat(clasificador.clasificar(cdr("9999"))).isEqualTo(DESCONOCIDO);
    }

    @Test
    void unaConstanciaConCodigoDeObservacionPeroNoCeroNuncaSeTomaComoAceptada() {
        // Un 4287 como código de respuesta (no como nota) sería raro: se deja como desconocido para que alguien lo mire
        assertThat(clasificador.clasificar(cdr("4287"))).isEqualTo(DESCONOCIDO);
    }

    // --- Errores de la comunicación ---

    @Test
    void unErrorSoapSeClasificaPorSuCodigo() {
        assertThat(clasificador.clasificar(new SunatFaultException("S:Client.0111", "sin perfil", "0111"))).isEqualTo(ERROR_DE_CONFIGURACION);
        assertThat(clasificador.clasificar(new SunatFaultException("S:Client.0151", "zip", "0151"))).isEqualTo(ERROR_DE_DATOS);
        assertThat(clasificador.clasificar(new SunatFaultException("S:Client.0306", "xml", "0306"))).isEqualTo(ERROR_DE_DATOS);
        assertThat(clasificador.clasificar(new SunatFaultException("S:Server.0100", "intente", "0100"))).isEqualTo(TRANSITORIO);
    }

    @Test
    void unErrorSoapSinCodigoEsDesconocido() {
        assertThat(clasificador.clasificar(new SunatFaultException("S:Server", "algo raro", null))).isEqualTo(DESCONOCIDO);
    }

    @Test
    void sinConexionOTimeoutOErrorHttpEsTransitorio() {
        assertThat(clasificador.clasificar(new SunatNoDisponibleException("timeout", true, null))).isEqualTo(TRANSITORIO);
        assertThat(clasificador.clasificar(new SunatNoDisponibleException("HTTP 500", false, null))).isEqualTo(TRANSITORIO);
    }
}
