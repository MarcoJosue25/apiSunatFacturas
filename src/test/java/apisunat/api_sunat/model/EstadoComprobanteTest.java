package apisunat.api_sunat.model;

import apisunat.api_sunat.model.enums.ClaseResultado;
import apisunat.api_sunat.model.enums.EstadoComprobante;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;

import static apisunat.api_sunat.model.enums.EstadoComprobante.*;
import static org.assertj.core.api.Assertions.assertThat;

class EstadoComprobanteTest {

    // Todas las transiciones permitidas, escritas a mano; cualquier otra combinación debe estar prohibida
    private static final Set<String> PERMITIDAS = Set.of(
            "PENDIENTE>ENVIANDO", "PENDIENTE>VENCIDO",
            "ERROR_TEMPORAL>ENVIANDO", "ERROR_TEMPORAL>VENCIDO",
            "ENVIANDO>ACEPTADO", "ENVIANDO>ACEPTADO_CON_OBSERVACIONES", "ENVIANDO>RECHAZADO", "ENVIANDO>ERROR_DE_DATOS",
            "ENVIANDO>ERROR_DE_CONFIGURACION", "ENVIANDO>ERROR_TEMPORAL", "ENVIANDO>REVISAR");

    static Stream<Arguments> todasLasCombinaciones() {
        Stream.Builder<Arguments> b = Stream.builder();
        for (EstadoComprobante origen : values()) {
            for (EstadoComprobante destino : values()) {
                b.add(Arguments.of(origen, destino, PERMITIDAS.contains(origen + ">" + destino)));
            }
        }
        return b.build();
    }

    @ParameterizedTest(name = "{0} a {1}: permitido = {2}")
    @MethodSource("todasLasCombinaciones")
    void cadaTransicionEstaPermitidaOProhibidaComoSeEscribio(EstadoComprobante origen, EstadoComprobante destino, boolean permitida) {
        assertThat(origen.puedePasarA(destino)).isEqualTo(permitida);
    }

    @Test
    void hayDiezEstadosYOnceTransicionesPermitidas() {
        assertThat(values()).hasSize(10);
        assertThat(PERMITIDAS).hasSize(11);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoComprobante.class, names = {"ACEPTADO", "ACEPTADO_CON_OBSERVACIONES", "RECHAZADO", "ERROR_DE_DATOS",
            "ERROR_DE_CONFIGURACION", "REVISAR", "VENCIDO"})
    void losEstadosFinalesNoPuedenPasarANingunOtro(EstadoComprobante estado) {
        for (EstadoComprobante destino : values()) {
            assertThat(estado.puedePasarA(destino)).as(estado + " a " + destino).isFalse();
        }
    }

    @ParameterizedTest
    @EnumSource(EstadoComprobante.class)
    void nadiePuedeQuedarseEnElMismoEstado(EstadoComprobante estado) {
        assertThat(estado.puedePasarA(estado)).isFalse();
    }

    @Test
    void soloLosQueTodaviaNoSeEnviaronNiTuvieronRespuestaPuedenVencer() {
        Set<EstadoComprobante> origenes = EnumSet.noneOf(EstadoComprobante.class);
        for (EstadoComprobante e : values()) {
            if (e.puedePasarA(VENCIDO)) {
                origenes.add(e);
            }
        }
        assertThat(origenes).containsExactlyInAnyOrder(PENDIENTE, ERROR_TEMPORAL);
    }

    @Test
    void soloSePuedeEntrarAEnviandoDesdePendienteOErrorTemporal() {
        Set<EstadoComprobante> origenes = EnumSet.noneOf(EstadoComprobante.class);
        for (EstadoComprobante e : values()) {
            if (e.puedePasarA(ENVIANDO)) {
                origenes.add(e);
            }
        }
        assertThat(origenes).containsExactlyInAnyOrder(PENDIENTE, ERROR_TEMPORAL);
    }

    // --- De la clase de respuesta de la SUNAT al estado ---

    @ParameterizedTest
    @MethodSource("clasesYEstados")
    void cadaClaseDeRespuestaTieneSuEstado(ClaseResultado clase, EstadoComprobante esperado) {
        assertThat(EstadoComprobante.desde(clase)).isEqualTo(esperado);
    }

    static Stream<Arguments> clasesYEstados() {
        return Stream.of(
                Arguments.of(ClaseResultado.ACEPTADO, ACEPTADO),
                Arguments.of(ClaseResultado.ACEPTADO_CON_OBSERVACIONES, ACEPTADO_CON_OBSERVACIONES),
                Arguments.of(ClaseResultado.RECHAZO, RECHAZADO),
                Arguments.of(ClaseResultado.ERROR_DE_DATOS, ERROR_DE_DATOS),
                Arguments.of(ClaseResultado.ERROR_DE_CONFIGURACION, ERROR_DE_CONFIGURACION),
                Arguments.of(ClaseResultado.TRANSITORIO, ERROR_TEMPORAL),
                Arguments.of(ClaseResultado.YA_REGISTRADO, REVISAR),
                Arguments.of(ClaseResultado.DESCONOCIDO, REVISAR));
    }

    @ParameterizedTest
    @EnumSource(ClaseResultado.class)
    void todaClaseDeRespuestaPuedeAlcanzarseDesdeEnviando(ClaseResultado clase) {
        assertThat(ENVIANDO.puedePasarA(EstadoComprobante.desde(clase))).isTrue();
    }
}
