package apisunat.api_sunat.seguridad;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ApiKeyFilterTest {

    private static final String LLAVE = "llave-de-prueba-con-un-largo-razonable-0123456789";

    private final FilterChain cadena = mock(FilterChain.class);
    private final MockHttpServletResponse respuesta = new MockHttpServletResponse();

    private MockHttpServletRequest peticion(String llave) {
        MockHttpServletRequest r = new MockHttpServletRequest("POST", "/api/v1/boletas");
        if (llave != null) {
            r.addHeader("X-API-Key", llave);
        }
        return r;
    }

    @Test
    void conLaLlaveCorrectaLaPeticionPasa() throws Exception {
        MockHttpServletRequest r = peticion(LLAVE);

        new ApiKeyFilter(LLAVE).doFilter(r, respuesta, cadena);

        verify(cadena).doFilter(r, respuesta);
        assertThat(respuesta.getStatus()).isEqualTo(200);
    }

    @Test
    void sinLlaveDa401YNoLlegaAlControlador() throws Exception {
        new ApiKeyFilter(LLAVE).doFilter(peticion(null), respuesta, cadena);

        verify(cadena, never()).doFilter(any(), any());
        assertThat(respuesta.getStatus()).isEqualTo(401);
        assertThat(respuesta.getHeader("WWW-Authenticate")).isEqualTo("ApiKey");
        assertThat(respuesta.getContentType()).startsWith("application/json");
        assertThat(respuesta.getContentAsString(StandardCharsets.UTF_8)).isEqualTo("{\"mensaje\":\"Falta la llave de la API o es incorrecta\"}");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"incorrecta", " ", "llave-de-prueba-con-un-largo-razonable-012345678", "llave-de-prueba-con-un-largo-razonable-01234567890",
            "LLAVE-DE-PRUEBA-CON-UN-LARGO-RAZONABLE-0123456789", " llave-de-prueba-con-un-largo-razonable-0123456789"})
    void unaLlaveIncorrectaDa401(String llave) throws Exception {
        new ApiKeyFilter(LLAVE).doFilter(peticion(llave), respuesta, cadena);

        verify(cadena, never()).doFilter(any(), any());
        assertThat(respuesta.getStatus()).isEqualTo(401);
    }

    @Test
    void laRespuestaDeRechazoNoRepiteLaLlaveRecibidaNiLaEsperada() throws Exception {
        new ApiKeyFilter(LLAVE).doFilter(peticion("intento-secreto-123"), respuesta, cadena);

        String cuerpo = respuesta.getContentAsString(StandardCharsets.UTF_8);
        assertThat(cuerpo).doesNotContain("intento-secreto-123").doesNotContain(LLAVE);
        assertThat(respuesta.getHeaderNames()).noneMatch(n -> respuesta.getHeader(n).contains(LLAVE) || respuesta.getHeader(n).contains("intento-secreto"));
    }

    @Test
    void elNombreDeLaCabeceraNoDistingueMayusculas() throws Exception {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", "/api/v1/boletas/1");
        r.addHeader("x-api-key", LLAVE);

        new ApiKeyFilter(LLAVE).doFilter(r, respuesta, cadena);

        verify(cadena).doFilter(r, respuesta);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void sinLlaveConfiguradaTodoPasa(String configurada) throws Exception {
        MockHttpServletRequest r = peticion(null);
        ApiKeyFilter filtro = new ApiKeyFilter(configurada);

        filtro.doFilter(r, respuesta, cadena);

        verify(cadena).doFilter(r, respuesta);
        assertThat(filtro.estaActivo()).isFalse();
    }

    @Test
    void conLlaveConfiguradaElFiltroEstaActivo() {
        assertThat(new ApiKeyFilter(LLAVE).estaActivo()).isTrue();
    }

    @Test
    void unaLlaveConCaracteresFueraDeAsciiNoSirveComoCabecera() throws Exception {
        String rara = "ñandú-€-😀-llave";
        MockHttpServletRequest r = peticion(null);
        r.addHeader("X-API-Key", new String(rara.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1));
        new ApiKeyFilter(rara).doFilter(r, respuesta, cadena);
        // Los servlets leen las cabeceras como ISO-8859-1: una llave con caracteres raros no coincide y eso es lo esperado (documentado en O-128)
        verify(cadena, never()).doFilter(any(), any());
    }
}
