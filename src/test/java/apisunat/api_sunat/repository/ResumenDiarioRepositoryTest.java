package apisunat.api_sunat.repository;

import apisunat.api_sunat.model.Comprobante;
import apisunat.api_sunat.model.ResumenDiario;
import apisunat.api_sunat.model.enums.EstadoComprobante;
import apisunat.api_sunat.model.enums.EstadoResumen;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ResumenDiarioRepositoryTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 29, 22, 0, 0);
    private static final LocalDate DIA = LocalDate.of(2026, 10, 28);

    @Autowired
    private ResumenDiarioRepository resumenes;
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
        resumenes.deleteAll();
        series.deleteAll();
    }

    private static ResumenDiario resumen(LocalDate dia, int correlativo, byte[] xml) {
        String nombre = "RC-" + dia.toString().replace("-", "") + "-" + correlativo;
        return ResumenDiario.nuevo(nombre, dia, correlativo, dia.plusDays(1), xml, T0);
    }

    private static Comprobante boleta(long n, LocalDateTime emision) {
        return Comprobante.nuevaBoleta("boleta-" + n, "B001", n, emision, "1", "46237547", "Luana", new BigDecimal("59.32"),
                new BigDecimal("10.68"), new BigDecimal("70.00"), new byte[]{1}, T0);
    }

    @Test
    void guardaYLeeUnResumenConTodosSusCampos() {
        ResumenDiario r = resumen(DIA, 1, new byte[]{1, 2, 3});
        r.marcarEnviado("1793158812535", T0.plusSeconds(1), T0.plusMinutes(1));
        Long id = resumenes.saveAndFlush(r).getId();

        ResumenDiario leido = resumenes.findById(id).orElseThrow();

        assertThat(leido.getNombre()).isEqualTo("RC-20261028-1");
        assertThat(leido.getFechaReferencia()).isEqualTo(DIA);
        assertThat(leido.getFechaGeneracion()).isEqualTo(DIA.plusDays(1));
        assertThat(leido.getCorrelativo()).isEqualTo(1);
        assertThat(leido.getEstado()).isEqualTo(EstadoResumen.ENVIADO);
        assertThat(leido.getTicket()).isEqualTo("1793158812535");
        assertThat(leido.getProximoIntento()).isEqualTo(T0.plusMinutes(1));
        assertThat(leido.getXmlFirmado()).containsExactly(1, 2, 3);
    }

    @Test
    void elXmlYLaConstanciaGrandesSeGuardanIguales() {
        byte[] xml = new byte[300_000];
        byte[] cdr = new byte[120_000];
        new Random(5).nextBytes(xml);
        new Random(6).nextBytes(cdr);
        ResumenDiario r = resumen(DIA, 1, xml);
        r.marcarEnviado("1", T0, T0);
        r.registrarResultado(EstadoResumen.ACEPTADO, "0", "ok", cdr, T0);
        Long id = resumenes.saveAndFlush(r).getId();

        ResumenDiario leido = resumenes.findById(id).orElseThrow();
        assertThat(leido.getXmlFirmado()).isEqualTo(xml);
        assertThat(leido.getCdrZip()).isEqualTo(cdr);
    }

    @Test
    void elNombreYElCorrelativoDelDiaNoSePuedenRepetir() {
        resumenes.saveAndFlush(resumen(DIA, 1, new byte[]{1}));

        assertThatThrownBy(() -> resumenes.saveAndFlush(resumen(DIA, 1, new byte[]{1}))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void elMismoCorrelativoEnOtroDiaSiSePuede() {
        resumenes.saveAndFlush(resumen(DIA, 1, new byte[]{1}));
        assertThat(resumenes.saveAndFlush(resumen(DIA.plusDays(1), 1, new byte[]{1})).getId()).isNotNull();
    }

    @Test
    void buscaPorNombre() {
        resumenes.saveAndFlush(resumen(DIA, 3, new byte[]{1}));
        assertThat(resumenes.findByNombre("RC-20261028-3")).isPresent();
        assertThat(resumenes.findByNombre("RC-20261028-4")).isEmpty();
    }

    @Test
    void elMayorCorrelativoDelDiaEsCeroSiNoHayNingunoYSeSeparaPorDia() {
        assertThat(resumenes.maxCorrelativoDelDia(DIA)).isZero();
        resumenes.saveAndFlush(resumen(DIA, 1, new byte[]{1}));
        resumenes.saveAndFlush(resumen(DIA, 2, new byte[]{1}));
        resumenes.saveAndFlush(resumen(DIA.plusDays(1), 7, new byte[]{1}));

        assertThat(resumenes.maxCorrelativoDelDia(DIA)).isEqualTo(2);
        assertThat(resumenes.maxCorrelativoDelDia(DIA.plusDays(1))).isEqualTo(7);
        assertThat(resumenes.maxCorrelativoDelDia(DIA.plusDays(2))).isZero();
    }

    @Test
    void losResumenesPendientesSonLosDeLosEstadosPedidosCuyoMomentoYaLlego() {
        ResumenDiario listo = resumenes.saveAndFlush(resumen(DIA, 1, new byte[]{1}));
        ResumenDiario enviado = resumen(DIA, 3, new byte[]{1});
        enviado.marcarEnviado("123", T0, T0.plusMinutes(10));
        ResumenDiario consultable = resumen(DIA, 4, new byte[]{1});
        consultable.marcarEnviado("456", T0, T0.minusMinutes(1));
        resumenes.saveAndFlush(enviado);
        resumenes.saveAndFlush(consultable);
        ResumenDiario aceptado = resumen(DIA, 5, new byte[]{1});
        aceptado.marcarEnviado("789", T0, T0.minusMinutes(5));
        aceptado.registrarResultado(EstadoResumen.ACEPTADO, "0", "ok", null, T0);
        resumenes.saveAndFlush(aceptado);

        List<Long> ids = resumenes.idsPendientes(List.of(EstadoResumen.GENERADO, EstadoResumen.ENVIADO, EstadoResumen.ERROR_TEMPORAL), T0.plusMinutes(1),
                PageRequest.of(0, 10));

        // primero el más atrasado; no están el que espera 10 minutos más ni el ya aceptado
        assertThat(ids).containsExactly(consultable.getId(), listo.getId());
    }

    @Test
    void dosCambiosAlMismoTiempoSobreElMismoResumenNoSePisan() {
        Long id = resumenes.saveAndFlush(resumen(DIA, 1, new byte[]{1})).getId();
        ResumenDiario a = resumenes.findById(id).orElseThrow();
        ResumenDiario b = resumenes.findById(id).orElseThrow();
        a.marcarEnviado("1", T0, T0);
        resumenes.saveAndFlush(a);
        b.marcarEnviado("2", T0, T0);

        assertThatThrownBy(() -> resumenes.saveAndFlush(b)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    // --- Boletas y resúmenes ---

    @Test
    void lasBoletasPendientesSalenDeLaMasAntiguaALaMasNuevaYSinLasQueYaEstanEnUnResumen() {
        Comprobante b2 = comprobantes.saveAndFlush(boleta(2, LocalDateTime.of(2026, 10, 28, 12, 0)));
        Comprobante b1 = comprobantes.saveAndFlush(boleta(1, LocalDateTime.of(2026, 10, 28, 9, 0)));
        Comprobante b3 = comprobantes.saveAndFlush(boleta(3, LocalDateTime.of(2026, 10, 27, 20, 0)));
        Long resumenId = resumenes.saveAndFlush(resumen(DIA, 1, new byte[]{1})).getId();
        Comprobante yaEnResumen = boleta(4, LocalDateTime.of(2026, 10, 28, 8, 0));
        yaEnResumen.incluirEnResumen(resumenId, T0);
        comprobantes.saveAndFlush(yaEnResumen);

        assertThat(comprobantes.idsBoletasPendientesDeResumen()).containsExactly(b3.getId(), b1.getId(), b2.getId());
    }

    @Test
    void lasFacturasNoEntranEnLaListaDeBoletasPendientes() {
        comprobantes.saveAndFlush(Comprobante.nuevo("f1", "F001", 1, T0, "20123456786", "X", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.TEN,
                new byte[]{1}, T0, T0));
        assertThat(comprobantes.idsBoletasPendientesDeResumen()).isEmpty();
    }

    @Test
    void lasBoletasDeUnResumenSeLeenPorNumero() {
        Long resumenId = resumenes.saveAndFlush(resumen(DIA, 1, new byte[]{1})).getId();
        for (long n : new long[]{3, 1, 2}) {
            Comprobante b = boleta(n, LocalDateTime.of(2026, 10, 28, 10, 0));
            b.incluirEnResumen(resumenId, T0);
            comprobantes.saveAndFlush(b);
        }
        comprobantes.saveAndFlush(boleta(9, LocalDateTime.of(2026, 10, 28, 10, 0)));   // otra, sin resumen

        List<Comprobante> delResumen = comprobantes.findByResumenIdOrderByCorrelativo(resumenId);

        assertThat(delResumen).extracting(Comprobante::getCorrelativo).containsExactly(1L, 2L, 3L);
        assertThat(delResumen).allMatch(c -> c.getEstado() == EstadoComprobante.EN_RESUMEN);
    }

    @Test
    void unaBoletaNoPuedeApuntarAUnResumenQueNoExiste() {
        Comprobante b = boleta(1, T0);
        b.incluirEnResumen(999_999L, T0);
        assertThatThrownBy(() -> comprobantes.saveAndFlush(b)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void guardaLosDatosDeLaBoletaYSuCliente() {
        Comprobante b = comprobantes.saveAndFlush(boleta(5, LocalDateTime.of(2026, 10, 28, 10, 0)));

        Comprobante leida = comprobantes.findById(b.getId()).orElseThrow();

        assertThat(leida.getTipoDocumento()).isEqualTo("03");
        assertThat(leida.getTipoDocumentoCliente()).isEqualTo("1");
        assertThat(leida.getDocumentoCliente()).isEqualTo("46237547");
        assertThat(leida.getTotalGravado()).isEqualByComparingTo("59.32");
        assertThat(leida.getTotalIgv()).isEqualByComparingTo("10.68");
        assertThat(leida.getEstado()).isEqualTo(EstadoComprobante.PENDIENTE_DE_RESUMEN);
        assertThat(leida.getProximoIntento()).isNull();
    }
}
