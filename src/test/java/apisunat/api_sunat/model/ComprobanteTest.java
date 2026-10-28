package apisunat.api_sunat.model;

import apisunat.api_sunat.dto.ResultadoEmision;
import apisunat.api_sunat.model.enums.ClaseResultado;
import apisunat.api_sunat.model.enums.EstadoComprobante;
import apisunat.api_sunat.sunat.cdr.CdrResultado;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComprobanteTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 28, 10, 0, 0);
    private static final LocalDateTime T1 = T0.plusSeconds(5);
    private static final LocalDateTime T2 = T0.plusSeconds(9);

    private static Comprobante nuevo() {
        return Comprobante.nuevo("pedido-77", "F001", 4355, T0, "20123456786", "CLIENTE SAC", BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("70.00"), new byte[]{1, 2, 3}, T0, T0.plusMinutes(1));
    }

    private static ResultadoEmision resultado(ClaseResultado clase, String codigo, String descripcion, List<CdrResultado.Observacion> obs, byte[] cdr) {
        return ResultadoEmision.builder().nombreArchivo("x").clase(clase).codigo(codigo).descripcion(descripcion)
                .observaciones(obs).xmlFirmado(new byte[]{1}).cdrZip(cdr).build();
    }

    @Test
    void unComprobanteNuevoEstaPendienteConSusDatos() {
        Comprobante c = nuevo();

        assertThat(c.getEstado()).isEqualTo(EstadoComprobante.PENDIENTE);
        assertThat(c.getTipoDocumento()).isEqualTo("01");
        assertThat(c.numero()).isEqualTo("F001-4355");
        assertThat(c.getIdExterno()).isEqualTo("pedido-77");
        assertThat(c.getXmlFirmado()).containsExactly(1, 2, 3);
        assertThat(c.getCreadoEn()).isEqualTo(T0);
        assertThat(c.getActualizadoEn()).isEqualTo(T0);
        assertThat(c.getCodigoSunat()).isNull();
        assertThat(c.getCdrZip()).isNull();
    }

    @Test
    void elNumeroNoLlevaCerosALaIzquierda() {
        Comprobante c = Comprobante.nuevo("a", "F001", 1, T0, "20123456786", "X", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.TEN, new byte[]{1}, T0, T0.plusMinutes(1));
        assertThat(c.numero()).isEqualTo("F001-1");
    }

    @Test
    void enviarYLuegoAceptarGuardaElCodigoLaConstanciaYLaFecha() {
        Comprobante c = nuevo();
        c.marcarEnviando(T1);
        assertThat(c.getEstado()).isEqualTo(EstadoComprobante.ENVIANDO);
        assertThat(c.getActualizadoEn()).isEqualTo(T1);

        c.registrarResultado(resultado(ClaseResultado.ACEPTADO, "0", "La Factura numero F001-4355, ha sido aceptada", List.of(), new byte[]{9, 9}), T2, T0.plusMinutes(5));

        assertThat(c.getEstado()).isEqualTo(EstadoComprobante.ACEPTADO);
        assertThat(c.getCodigoSunat()).isEqualTo("0");
        assertThat(c.getDescripcionSunat()).isEqualTo("La Factura numero F001-4355, ha sido aceptada");
        assertThat(c.getCdrZip()).containsExactly(9, 9);
        assertThat(c.getObservaciones()).isNull();
        assertThat(c.getActualizadoEn()).isEqualTo(T2);
        assertThat(c.getCreadoEn()).isEqualTo(T0);
    }

    @Test
    void lasObservacionesQuedanUnaPorLinea() {
        Comprobante c = nuevo();
        c.marcarEnviando(T1);
        c.registrarResultado(resultado(ClaseResultado.ACEPTADO_CON_OBSERVACIONES, "0", "ok",
                List.of(new CdrResultado.Observacion("4031", "Debe indicar el nombre comercial"), new CdrResultado.Observacion(null, "Aviso suelto")),
                new byte[]{1}), T2, T0.plusMinutes(5));

        assertThat(c.getEstado()).isEqualTo(EstadoComprobante.ACEPTADO_CON_OBSERVACIONES);
        assertThat(c.getObservaciones()).isEqualTo("4031 - Debe indicar el nombre comercial\nAviso suelto");
    }

    @Test
    void unRechazoGuardaElCodigoDelErrorYNoPierdeElNumero() {
        Comprobante c = nuevo();
        c.marcarEnviando(T1);
        c.registrarResultado(resultado(ClaseResultado.RECHAZO, "3270", "diferencia de montos", List.of(), new byte[]{1}), T2, T0.plusMinutes(5));

        assertThat(c.getEstado()).isEqualTo(EstadoComprobante.RECHAZADO);
        assertThat(c.getCodigoSunat()).isEqualTo("3270");
        assertThat(c.numero()).isEqualTo("F001-4355");
    }

    @Test
    void unErrorSoapNoTraeConstanciaYElCdrQuedaNulo() {
        Comprobante c = nuevo();
        c.marcarEnviando(T1);
        c.registrarResultado(resultado(ClaseResultado.ERROR_DE_DATOS, "0306", "xml mal", List.of(), null), T2, T0.plusMinutes(5));

        assertThat(c.getEstado()).isEqualTo(EstadoComprobante.ERROR_DE_DATOS);
        assertThat(c.getCdrZip()).isNull();
    }

    @Test
    void sinRespuestaDeLaSunatQuedaEnErrorTemporalConElMotivo() {
        Comprobante c = nuevo();
        c.marcarEnviando(T1);
        c.marcarErrorTemporal("La SUNAT no respondió a tiempo", T2, T0.plusMinutes(5));

        assertThat(c.getEstado()).isEqualTo(EstadoComprobante.ERROR_TEMPORAL);
        assertThat(c.getDescripcionSunat()).isEqualTo("La SUNAT no respondió a tiempo");
        assertThat(c.getCodigoSunat()).isNull();
    }

    @Test
    void despuesDeUnErrorTemporalSePuedeVolverAEnviar() {
        Comprobante c = nuevo();
        c.marcarEnviando(T1);
        c.marcarErrorTemporal("x", T1, T0.plusMinutes(5));
        c.marcarEnviando(T2);
        assertThat(c.getEstado()).isEqualTo(EstadoComprobante.ENVIANDO);
    }

    @Test
    void noSePuedeRegistrarUnResultadoSinHaberEnviado() {
        Comprobante c = nuevo();
        assertThatThrownBy(() -> c.registrarResultado(resultado(ClaseResultado.ACEPTADO, "0", "x", List.of(), null), T1, T0.plusMinutes(5)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Un comprobante PENDIENTE no puede pasar a ACEPTADO");
        assertThat(c.getEstado()).isEqualTo(EstadoComprobante.PENDIENTE);
        assertThat(c.getCodigoSunat()).isNull();
    }

    @Test
    void unComprobanteAceptadoNoSePuedeVolverAEnviar() {
        Comprobante c = nuevo();
        c.marcarEnviando(T1);
        c.registrarResultado(resultado(ClaseResultado.ACEPTADO, "0", "ok", List.of(), new byte[]{1}), T1, T0.plusMinutes(5));

        assertThatThrownBy(() -> c.marcarEnviando(T2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Un comprobante ACEPTADO no puede pasar a ENVIANDO");
        assertThat(c.getEstado()).isEqualTo(EstadoComprobante.ACEPTADO);
        assertThat(c.getActualizadoEn()).isEqualTo(T1);
    }

    @Test
    void unaTransicionProhibidaNoCambiaNingunDato() {
        Comprobante c = nuevo();
        c.marcarEnviando(T1);
        c.registrarResultado(resultado(ClaseResultado.RECHAZO, "3270", "malo", List.of(), new byte[]{7}), T1, T0.plusMinutes(5));

        assertThatThrownBy(() -> c.registrarResultado(resultado(ClaseResultado.ACEPTADO, "0", "ok", List.of(), new byte[]{8}), T2, T0.plusMinutes(5)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(c.getCodigoSunat()).isEqualTo("3270");
        assertThat(c.getCdrZip()).containsExactly(7);
    }

    @Test
    void noSePuedeMarcarErrorTemporalSinEstarEnviando() {
        assertThatThrownBy(() -> nuevo().marcarErrorTemporal("x", T1, T0.plusMinutes(5))).isInstanceOf(IllegalStateException.class);
    }

    // --- Reintentos y plazo ---

    @Test
    void unComprobanteNuevoTraeProgramadoSuPrimerIntentoYCeroFallos() {
        Comprobante c = nuevo();
        assertThat(c.getProximoIntento()).isEqualTo(T0.plusMinutes(1));
        assertThat(c.getIntentosFallidos()).isZero();
    }

    @Test
    void cadaFalloPasajeroSumaUnIntentoYProgramaElSiguiente() {
        Comprobante c = nuevo();
        c.marcarEnviando(T1);
        c.marcarErrorTemporal("sin conexión", T1, T1.plusMinutes(1));
        assertThat(c.getIntentosFallidos()).isEqualTo(1);
        assertThat(c.getProximoIntento()).isEqualTo(T1.plusMinutes(1));

        c.marcarEnviando(T2);
        c.marcarErrorTemporal("timeout", T2, T2.plusMinutes(2));
        assertThat(c.getIntentosFallidos()).isEqualTo(2);
        assertThat(c.getProximoIntento()).isEqualTo(T2.plusMinutes(2));
    }

    @Test
    void unaRespuestaPasajeraDeLaSunatTambienProgramaElReintento() {
        Comprobante c = nuevo();
        c.marcarEnviando(T1);
        c.registrarResultado(resultado(ClaseResultado.TRANSITORIO, "0100", "intente nuevamente", List.of(), null), T1, T1.plusMinutes(7));

        assertThat(c.getEstado()).isEqualTo(EstadoComprobante.ERROR_TEMPORAL);
        assertThat(c.getIntentosFallidos()).isEqualTo(1);
        assertThat(c.getProximoIntento()).isEqualTo(T1.plusMinutes(7));
    }

    @Test
    void unaRespuestaDefinitivaBorraElProximoIntentoYNoSumaFallos() {
        Comprobante c = nuevo();
        c.marcarEnviando(T1);
        c.registrarResultado(resultado(ClaseResultado.ACEPTADO, "0", "ok", List.of(), new byte[]{1}), T1, T1.plusMinutes(7));

        assertThat(c.getProximoIntento()).isNull();
        assertThat(c.getIntentosFallidos()).isZero();
    }

    @Test
    void unRechazoNoSeReintenta() {
        Comprobante c = nuevo();
        c.marcarEnviando(T1);
        c.registrarResultado(resultado(ClaseResultado.RECHAZO, "3270", "x", List.of(), new byte[]{1}), T1, T1.plusMinutes(7));
        assertThat(c.getProximoIntento()).isNull();
    }

    @Test
    void sinFechaDeProximoIntentoUnFalloPasajeroSeRechaza() {
        Comprobante c = nuevo();
        c.marcarEnviando(T1);
        assertThatThrownBy(() -> c.marcarErrorTemporal("x", T1, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unComprobantePendienteOEnErrorTemporalPuedeVencer() {
        Comprobante pendiente = nuevo();
        pendiente.marcarVencido(T2);
        assertThat(pendiente.getEstado()).isEqualTo(EstadoComprobante.VENCIDO);
        assertThat(pendiente.getProximoIntento()).isNull();
        assertThat(pendiente.getActualizadoEn()).isEqualTo(T2);

        Comprobante conError = nuevo();
        conError.marcarEnviando(T1);
        conError.marcarErrorTemporal("x", T1, T1.plusMinutes(1));
        conError.marcarVencido(T2);
        assertThat(conError.getEstado()).isEqualTo(EstadoComprobante.VENCIDO);
        assertThat(conError.getIntentosFallidos()).isEqualTo(1);
    }

    @Test
    void unComprobanteVencidoNoSePuedeEnviar() {
        Comprobante c = nuevo();
        c.marcarVencido(T1);
        assertThatThrownBy(() -> c.marcarEnviando(T2)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Un comprobante VENCIDO no puede pasar a ENVIANDO");
    }

    @Test
    void unComprobanteAceptadoNoPuedeVencer() {
        Comprobante c = nuevo();
        c.marcarEnviando(T1);
        c.registrarResultado(resultado(ClaseResultado.ACEPTADO, "0", "ok", List.of(), new byte[]{1}), T1, T1);
        assertThatThrownBy(() -> c.marcarVencido(T2)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unComprobanteQueSeEstaEnviandoNoPuedeVencer() {
        Comprobante c = nuevo();
        c.marcarEnviando(T1);
        assertThatThrownBy(() -> c.marcarVencido(T2)).isInstanceOf(IllegalStateException.class);
    }

    // --- Boletas ---

    private static Comprobante boleta() {
        return Comprobante.nuevaBoleta("boleta-1", "B001", 12, T0, "1", "46237547", "Luana Pazos", new BigDecimal("59.32"),
                new BigDecimal("10.68"), new BigDecimal("70.00"), new byte[]{1, 2}, T0);
    }

    @Test
    void unaBoletaNuevaEsperaSuResumenYNoTieneProximoIntento() {
        Comprobante b = boleta();

        assertThat(b.esBoleta()).isTrue();
        assertThat(b.getTipoDocumento()).isEqualTo("03");
        assertThat(b.getEstado()).isEqualTo(EstadoComprobante.PENDIENTE_DE_RESUMEN);
        assertThat(b.getProximoIntento()).isNull();
        assertThat(b.getResumenId()).isNull();
        assertThat(b.numero()).isEqualTo("B001-12");
        assertThat(b.getTipoDocumentoCliente()).isEqualTo("1");
        assertThat(b.getDocumentoCliente()).isEqualTo("46237547");
        assertThat(b.getTotalGravado()).isEqualByComparingTo("59.32");
        assertThat(b.getTotalIgv()).isEqualByComparingTo("10.68");
    }

    @Test
    void unaFacturaNoEsBoletaYGuardaSusTotales() {
        Comprobante f = nuevo();
        assertThat(f.esBoleta()).isFalse();
        assertThat(f.getTipoDocumentoCliente()).isEqualTo("6");
        assertThat(f.getTotalGravado()).isEqualByComparingTo("1");
    }

    @Test
    void laBoletaEntraAUnResumenYQuedaEnResumen() {
        Comprobante b = boleta();
        b.incluirEnResumen(77L, T1);

        assertThat(b.getEstado()).isEqualTo(EstadoComprobante.EN_RESUMEN);
        assertThat(b.getResumenId()).isEqualTo(77L);
        assertThat(b.getActualizadoEn()).isEqualTo(T1);
    }

    @Test
    void unaFacturaNoPuedeIrEnUnResumen() {
        assertThatThrownBy(() -> nuevo().incluirEnResumen(1L, T1)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Solo las boletas van en un resumen diario");
    }

    @Test
    void unaBoletaNoSePuedeEnviarSolaComoUnaFactura() {
        assertThatThrownBy(() -> boleta().marcarEnviando(T1)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Un comprobante PENDIENTE_DE_RESUMEN no puede pasar a ENVIANDO");
    }

    @Test
    void siElResumenSeAceptaLaBoletaQuedaAceptada() {
        Comprobante b = boleta();
        b.incluirEnResumen(77L, T1);
        b.aceptarPorResumen("0", "El Resumen RC-20261028-1 ha sido aceptado", null, T2);

        assertThat(b.getEstado()).isEqualTo(EstadoComprobante.ACEPTADO);
        assertThat(b.getCodigoSunat()).isEqualTo("0");
        assertThat(b.getObservaciones()).isNull();
    }

    @Test
    void siHayObservacionesParaEstaBoletaQuedaAceptadaConObservaciones() {
        Comprobante b = boleta();
        b.incluirEnResumen(77L, T1);
        b.aceptarPorResumen("0", "aceptado", "4443 - Informada fuera del plazo B001-12", T2);

        assertThat(b.getEstado()).isEqualTo(EstadoComprobante.ACEPTADO_CON_OBSERVACIONES);
        assertThat(b.getObservaciones()).isEqualTo("4443 - Informada fuera del plazo B001-12");
    }

    @Test
    void unaBoletaQueNoEstaEnUnResumenNoSePuedeAceptarPorResumen() {
        assertThatThrownBy(() -> boleta().aceptarPorResumen("0", "x", null, T1)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void siElResumenSeRechazaSeAnotaQueDijoLaSunatSinCambiarElEstado() {
        Comprobante b = boleta();
        b.incluirEnResumen(77L, T1);
        b.anotarRespuestaDelResumen("2987", "El comprobante ya fue informado B001-12", T2);

        assertThat(b.getEstado()).isEqualTo(EstadoComprobante.EN_RESUMEN);
        assertThat(b.getCodigoSunat()).isEqualTo("2987");
        assertThat(b.getDescripcionSunat()).isEqualTo("El comprobante ya fue informado B001-12");
        assertThat(b.getActualizadoEn()).isEqualTo(T2);
    }

    @Test
    void soloUnaBoletaEnUnResumenAdmiteAnotarLaRespuestaDelResumen() {
        assertThatThrownBy(() -> boleta().anotarRespuestaDelResumen("2987", "x", T1)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unaBoletaYaAceptadaNoSePuedeMeterEnOtroResumen() {
        Comprobante b = boleta();
        b.incluirEnResumen(77L, T1);
        b.aceptarPorResumen("0", "ok", null, T2);
        assertThatThrownBy(() -> b.incluirEnResumen(78L, T2)).isInstanceOf(IllegalStateException.class);
        assertThat(b.getResumenId()).isEqualTo(77L);
    }
}
