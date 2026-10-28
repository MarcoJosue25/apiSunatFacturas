package apisunat.api_sunat.repository;

import apisunat.api_sunat.dto.ResultadoEmision;
import apisunat.api_sunat.model.Comprobante;
import apisunat.api_sunat.model.EnvioIntento;
import apisunat.api_sunat.model.enums.ClaseResultado;
import apisunat.api_sunat.model.enums.EstadoComprobante;
import apisunat.api_sunat.sunat.cdr.CdrResultado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ComprobanteRepositoryTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 28, 10, 0, 0);

    @Autowired
    private ComprobanteRepository comprobantes;
    @Autowired
    private EnvioIntentoRepository intentos;
    @Autowired
    private SerieRepository series;

    @BeforeEach
    void limpiar() {
        intentos.deleteAll();
        comprobantes.deleteAll();
        series.deleteAll();
    }

    private static Comprobante nuevo(String idExterno, long correlativo, byte[] xml) {
        return Comprobante.nuevo(idExterno, "F001", correlativo, T0, "20123456786", "CLIENTE SAC ñandú áéíóú", new BigDecimal("59.32"), new BigDecimal("10.68"), new BigDecimal("70.00"), xml, T0, T0.plusMinutes(1));
    }

    @Test
    void guardaYLeeUnComprobanteConTodosSusCampos() {
        Comprobante guardado = comprobantes.saveAndFlush(nuevo("pedido-1", 1, new byte[]{1, 2, 3}));

        Comprobante leido = comprobantes.findById(guardado.getId()).orElseThrow();

        assertThat(leido.getIdExterno()).isEqualTo("pedido-1");
        assertThat(leido.getTipoDocumento()).isEqualTo("01");
        assertThat(leido.getSerie()).isEqualTo("F001");
        assertThat(leido.getCorrelativo()).isEqualTo(1);
        assertThat(leido.getFechaEmision()).isEqualTo(T0);
        assertThat(leido.getDocumentoCliente()).isEqualTo("20123456786");
        assertThat(leido.getRazonSocialCliente()).isEqualTo("CLIENTE SAC ñandú áéíóú");
        assertThat(leido.getImporteTotal()).isEqualByComparingTo("70.00");
        assertThat(leido.getEstado()).isEqualTo(EstadoComprobante.PENDIENTE);
        assertThat(leido.getXmlFirmado()).containsExactly(1, 2, 3);
        assertThat(leido.getCreadoEn()).isEqualTo(T0);
        assertThat(leido.getVersion()).isNotNull();
    }

    @Test
    void elXmlYLaConstanciaDeVariosCientosDeKbSeGuardanYSeLeenIguales() {
        byte[] xml = new byte[400_000];
        byte[] cdr = new byte[250_000];
        new Random(1).nextBytes(xml);
        new Random(2).nextBytes(cdr);
        Comprobante c = nuevo("pedido-grande", 1, xml);
        c.marcarEnviando(T0);
        c.registrarResultado(ResultadoEmision.builder().clase(ClaseResultado.ACEPTADO).codigo("0").descripcion("ok")
                .observaciones(List.of()).cdrZip(cdr).build(), T0, T0.plusMinutes(5));
        Long id = comprobantes.saveAndFlush(c).getId();

        Comprobante leido = comprobantes.findById(id).orElseThrow();

        assertThat(leido.getXmlFirmado()).isEqualTo(xml);
        assertThat(leido.getCdrZip()).isEqualTo(cdr);
    }

    @Test
    void guardaLasTransicionesYLasObservaciones() {
        Comprobante c = nuevo("pedido-2", 2, new byte[]{1});
        c.marcarEnviando(T0.plusSeconds(1));
        c.registrarResultado(ResultadoEmision.builder().clase(ClaseResultado.ACEPTADO_CON_OBSERVACIONES).codigo("0").descripcion("aceptada")
                .observaciones(List.of(new CdrResultado.Observacion("4031", "Debe indicar el nombre comercial"))).cdrZip(new byte[]{5}).build(), T0.plusSeconds(2), T0.plusMinutes(5));
        Long id = comprobantes.saveAndFlush(c).getId();

        Comprobante leido = comprobantes.findById(id).orElseThrow();

        assertThat(leido.getEstado()).isEqualTo(EstadoComprobante.ACEPTADO_CON_OBSERVACIONES);
        assertThat(leido.getObservaciones()).isEqualTo("4031 - Debe indicar el nombre comercial");
        assertThat(leido.getActualizadoEn()).isEqualTo(T0.plusSeconds(2));
    }

    @Test
    void buscaPorIdExterno() {
        comprobantes.saveAndFlush(nuevo("pedido-3", 3, new byte[]{1}));

        assertThat(comprobantes.findByIdExterno("pedido-3")).isPresent().get().extracting(Comprobante::getCorrelativo).isEqualTo(3L);
        assertThat(comprobantes.findByIdExterno("otro")).isEmpty();
    }

    @Test
    void elIdExternoNoSePuedeRepetir() {
        comprobantes.saveAndFlush(nuevo("repetido", 1, new byte[]{1}));

        assertThatThrownBy(() -> comprobantes.saveAndFlush(nuevo("repetido", 2, new byte[]{1})))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void elNumeroDeLaFacturaNoSePuedeRepetir() {
        comprobantes.saveAndFlush(nuevo("a", 7, new byte[]{1}));

        assertThatThrownBy(() -> comprobantes.saveAndFlush(nuevo("b", 7, new byte[]{1})))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void elMismoCorrelativoEnOtraSerieSiSePuede() {
        comprobantes.saveAndFlush(nuevo("a", 7, new byte[]{1}));
        Comprobante otraSerie = Comprobante.nuevo("b", "F002", 7, T0, "20123456786", "X", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.TEN, new byte[]{1}, T0, T0.plusMinutes(1));

        assertThat(comprobantes.saveAndFlush(otraSerie).getId()).isNotNull();
    }

    @Test
    void dosCambiosAlMismoTiempoSobreElMismoComprobanteNoSePisan() {
        Long id = comprobantes.saveAndFlush(nuevo("concurrente", 1, new byte[]{1})).getId();
        Comprobante a = comprobantes.findById(id).orElseThrow();
        Comprobante b = comprobantes.findById(id).orElseThrow();

        a.marcarEnviando(T0.plusSeconds(1));
        comprobantes.saveAndFlush(a);
        b.marcarEnviando(T0.plusSeconds(2));

        assertThatThrownBy(() -> comprobantes.saveAndFlush(b)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void guardaCadaIntentoYLosDevuelveEnOrden() {
        Long id = comprobantes.saveAndFlush(nuevo("con-intentos", 1, new byte[]{1})).getId();
        intentos.save(EnvioIntento.builder().comprobanteId(id).fecha(T0).resultado("SIN_RESPUESTA").descripcion("timeout").build());
        intentos.save(EnvioIntento.builder().comprobanteId(id).fecha(T0.plusMinutes(1)).resultado("ACEPTADO").codigo("0").descripcion("ok").build());

        List<EnvioIntento> lista = intentos.findByComprobanteIdOrderByIdAsc(id);

        assertThat(lista).extracting(EnvioIntento::getResultado).containsExactly("SIN_RESPUESTA", "ACEPTADO");
        assertThat(lista.get(0).getCodigo()).isNull();
        assertThat(lista.get(1).getCodigo()).isEqualTo("0");
    }

    @Test
    void unIntentoDeUnComprobanteQueNoExisteSeRechaza() {
        assertThatThrownBy(() -> intentos.saveAndFlush(EnvioIntento.builder().comprobanteId(999_999L).fecha(T0).resultado("X").build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void noSePuedeCambiarElXmlFirmadoDespuesDeGuardarlo() {
        Comprobante c = comprobantes.saveAndFlush(nuevo("inmutable", 1, new byte[]{1, 1, 1}));
        Long id = c.getId();

        // El campo es updatable=false: aunque se cambiara en memoria, la base conserva el original
        comprobantes.findById(id).orElseThrow().marcarEnviando(T0.plusSeconds(1));
        comprobantes.flush();

        assertThat(comprobantes.findById(id).orElseThrow().getXmlFirmado()).containsExactly(1, 1, 1);
    }

    @Test
    void guardaLosIntentosFallidosYElProximoIntento() {
        Comprobante c = nuevo("con-reintento", 1, new byte[]{1});
        c.marcarEnviando(T0.plusSeconds(1));
        c.marcarErrorTemporal("timeout", T0.plusSeconds(2), T0.plusMinutes(2));
        Long id = comprobantes.saveAndFlush(c).getId();

        Comprobante leido = comprobantes.findById(id).orElseThrow();

        assertThat(leido.getEstado()).isEqualTo(EstadoComprobante.ERROR_TEMPORAL);
        assertThat(leido.getIntentosFallidos()).isEqualTo(1);
        assertThat(leido.getProximoIntento()).isEqualTo(T0.plusMinutes(2));
    }
}
