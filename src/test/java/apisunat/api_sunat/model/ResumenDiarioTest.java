package apisunat.api_sunat.model;

import apisunat.api_sunat.model.enums.EstadoResumen;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Stream;

import static apisunat.api_sunat.model.enums.EstadoResumen.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResumenDiarioTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 29, 22, 0, 0);
    private static final LocalDateTime T1 = T0.plusMinutes(1);
    private static final LocalDateTime T2 = T0.plusMinutes(3);

    private static ResumenDiario nuevo() {
        return ResumenDiario.nuevo("RC-20261028-1", LocalDate.of(2026, 10, 28), 1, LocalDate.of(2026, 10, 29), new byte[]{1, 2, 3}, T0);
    }

    // --- Transiciones permitidas, escritas a mano ---

    private static final Set<String> PERMITIDAS = Set.of(
            "GENERADO>ENVIADO", "GENERADO>ERROR_TEMPORAL", "GENERADO>RECHAZADO", "GENERADO>REVISAR",
            "ERROR_TEMPORAL>ENVIADO", "ERROR_TEMPORAL>RECHAZADO", "ERROR_TEMPORAL>REVISAR",
            "ENVIADO>ACEPTADO", "ENVIADO>ACEPTADO_CON_OBSERVACIONES", "ENVIADO>RECHAZADO", "ENVIADO>REVISAR",
            "RECHAZADO>GENERADO");

    static Stream<Arguments> combinaciones() {
        Stream.Builder<Arguments> b = Stream.builder();
        for (EstadoResumen o : values()) {
            for (EstadoResumen d : values()) {
                b.add(Arguments.of(o, d, PERMITIDAS.contains(o + ">" + d)));
            }
        }
        return b.build();
    }

    @ParameterizedTest(name = "{0} a {1}: permitido = {2}")
    @MethodSource("combinaciones")
    void cadaTransicionEstaPermitidaOProhibidaComoSeEscribio(EstadoResumen origen, EstadoResumen destino, boolean permitida) {
        assertThat(origen.puedePasarA(destino)).isEqualTo(permitida);
    }

    @Test
    void hayDoceTransicionesPermitidasEntreSieteEstados() {
        assertThat(values()).hasSize(7);
        assertThat(PERMITIDAS).hasSize(12);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoResumen.class, names = {"ACEPTADO", "ACEPTADO_CON_OBSERVACIONES", "REVISAR"})
    void losEstadosTerminadosNoTienenSalida(EstadoResumen e) {
        assertThat(e.terminado()).isTrue();
        for (EstadoResumen d : values()) {
            assertThat(e.puedePasarA(d)).isFalse();
        }
    }

    @ParameterizedTest
    @EnumSource(value = EstadoResumen.class, names = {"GENERADO", "ENVIADO", "RECHAZADO", "ERROR_TEMPORAL"})
    void losDemasNoSonTerminados(EstadoResumen e) {
        assertThat(e.terminado()).isFalse();
    }

    // --- La entidad ---

    @Test
    void unResumenNuevoEstaGeneradoYListoParaEnviar() {
        ResumenDiario r = nuevo();

        assertThat(r.getEstado()).isEqualTo(GENERADO);
        assertThat(r.getNombre()).isEqualTo("RC-20261028-1");
        assertThat(r.nombreArchivo("20100066603")).isEqualTo("20100066603-RC-20261028-1");
        assertThat(r.getFechaReferencia()).isEqualTo(LocalDate.of(2026, 10, 28));
        assertThat(r.getFechaGeneracion()).isEqualTo(LocalDate.of(2026, 10, 29));
        assertThat(r.getCorrelativo()).isEqualTo(1);
        assertThat(r.getTicket()).isNull();
        assertThat(r.getProximoIntento()).isEqualTo(T0);
        assertThat(r.getXmlFirmado()).containsExactly(1, 2, 3);
    }

    @Test
    void alEnviarseGuardaElTicketYProgramaLaPrimeraConsulta() {
        ResumenDiario r = nuevo();
        r.marcarEnviado("1793158812535", T1, T1.plusMinutes(1));

        assertThat(r.getEstado()).isEqualTo(ENVIADO);
        assertThat(r.getTicket()).isEqualTo("1793158812535");
        assertThat(r.getProximoIntento()).isEqualTo(T1.plusMinutes(1));
        assertThat(r.getIntentosFallidos()).isZero();
    }

    @Test
    void siNoSePuedeEnviarQuedaConErrorTemporalYSeSumanFallos() {
        ResumenDiario r = nuevo();
        r.marcarErrorEnvio("sin conexión", T1, T1.plusMinutes(1));
        assertThat(r.getEstado()).isEqualTo(ERROR_TEMPORAL);
        assertThat(r.getIntentosFallidos()).isEqualTo(1);

        r.marcarErrorEnvio("timeout", T2, T2.plusMinutes(2));
        assertThat(r.getEstado()).isEqualTo(ERROR_TEMPORAL);
        assertThat(r.getIntentosFallidos()).isEqualTo(2);
        assertThat(r.getProximoIntento()).isEqualTo(T2.plusMinutes(2));
        assertThat(r.getDescripcionSunat()).isEqualTo("timeout");
    }

    @Test
    void despuesDeUnErrorTemporalSePuedeEnviarYLosFallosVuelvenACero() {
        ResumenDiario r = nuevo();
        r.marcarErrorEnvio("x", T1, T1);
        r.marcarEnviado("123", T2, T2.plusMinutes(1));
        assertThat(r.getEstado()).isEqualTo(ENVIADO);
        assertThat(r.getIntentosFallidos()).isZero();
        assertThat(r.getDescripcionSunat()).isNull();
    }

    @Test
    void mientrasLaSunatProcesaSePosponeLaConsultaSinCambiarDeEstado() {
        ResumenDiario r = nuevo();
        r.marcarEnviado("123", T1, T1);
        r.posponerConsulta(T2, T1);

        assertThat(r.getEstado()).isEqualTo(ENVIADO);
        assertThat(r.getProximoIntento()).isEqualTo(T2);
        assertThat(r.getIntentosFallidos()).isEqualTo(1);
    }

    @Test
    void soloUnResumenEnviadoSePuedePosponerEnLaConsulta() {
        assertThatThrownBy(() -> nuevo().posponerConsulta(T1, T1)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void elResultadoAceptadoGuardaCodigoDescripcionYConstanciaYBorraElProximoIntento() {
        ResumenDiario r = nuevo();
        r.marcarEnviado("123", T1, T1);
        r.registrarResultado(ACEPTADO, "0", "El Resumen RC-20261028-1 ha sido aceptado", new byte[]{9}, T2);

        assertThat(r.getEstado()).isEqualTo(ACEPTADO);
        assertThat(r.getCodigoSunat()).isEqualTo("0");
        assertThat(r.getCdrZip()).containsExactly(9);
        assertThat(r.getProximoIntento()).isNull();
    }

    @Test
    void unResultadoQueNoEsDeLaSunatSeRechaza() {
        ResumenDiario r = nuevo();
        r.marcarEnviado("123", T1, T1);
        assertThatThrownBy(() -> r.registrarResultado(ENVIADO, "0", "x", null, T2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> r.registrarResultado(GENERADO, "0", "x", null, T2)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noSePuedeRegistrarUnResultadoSinHaberEnviado() {
        assertThatThrownBy(() -> nuevo().registrarResultado(ACEPTADO, "0", "x", null, T1)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Un resumen GENERADO no puede pasar a ACEPTADO");
    }

    @Test
    void unResumenRechazadoSeReenviaConElMismoNombreYElMismoXml() {
        ResumenDiario r = nuevo();
        r.marcarEnviado("123", T1, T1);
        r.registrarResultado(RECHAZADO, "2223", "El archivo ya fue presentado anteriormente", new byte[]{7}, T2);

        r.prepararReenvio(T2.plusMinutes(5));

        assertThat(r.getEstado()).isEqualTo(GENERADO);
        assertThat(r.getTicket()).isNull();
        assertThat(r.getNombre()).isEqualTo("RC-20261028-1");
        assertThat(r.getXmlFirmado()).containsExactly(1, 2, 3);
        assertThat(r.getProximoIntento()).isEqualTo(T2.plusMinutes(5));
    }

    @Test
    void unResumenAceptadoNoSePuedeReenviar() {
        ResumenDiario r = nuevo();
        r.marcarEnviado("123", T1, T1);
        r.registrarResultado(ACEPTADO, "0", "ok", null, T2);
        assertThatThrownBy(() -> r.prepararReenvio(T2)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unaTransicionProhibidaNoCambiaNingunDato() {
        ResumenDiario r = nuevo();
        r.marcarEnviado("123", T1, T1);
        r.registrarResultado(ACEPTADO, "0", "ok", new byte[]{1}, T2);
        assertThatThrownBy(() -> r.marcarEnviado("999", T2, T2)).isInstanceOf(IllegalStateException.class);
        assertThat(r.getTicket()).isEqualTo("123");
    }
}
