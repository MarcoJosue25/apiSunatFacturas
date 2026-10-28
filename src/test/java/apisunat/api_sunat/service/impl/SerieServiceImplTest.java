package apisunat.api_sunat.service.impl;

import apisunat.api_sunat.repository.ComprobanteRepository;
import apisunat.api_sunat.repository.EnvioIntentoRepository;
import apisunat.api_sunat.repository.SerieRepository;
import apisunat.api_sunat.service.SerieService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class SerieServiceImplTest {

    @Autowired
    private SerieService serieService;
    @Autowired
    private SerieRepository series;
    @Autowired
    private ComprobanteRepository comprobantes;
    @Autowired
    private EnvioIntentoRepository intentos;
    @Autowired
    private TransactionTemplate tx;

    @BeforeEach
    void limpiar() {
        intentos.deleteAll();
        comprobantes.deleteAll();
        series.deleteAll();
    }

    private long siguiente(String serie) {
        return siguiente("01", serie);
    }

    private long siguiente(String tipo, String serie) {
        serieService.asegurar(tipo, serie);
        Long numero = tx.execute(estado -> serieService.siguiente(tipo, serie));
        return numero;
    }

    @Test
    void laPrimeraVezEmpiezaEnUnoYDespuesSigueDeAUno() {
        assertThat(siguiente("F001")).isEqualTo(1);
        assertThat(siguiente("F001")).isEqualTo(2);
        assertThat(siguiente("F001")).isEqualTo(3);
    }

    @Test
    void cadaSerieLlevaSuPropiaCuenta() {
        assertThat(siguiente("F001")).isEqualTo(1);
        assertThat(siguiente("F002")).isEqualTo(1);
        assertThat(siguiente("F001")).isEqualTo(2);
        assertThat(siguiente("F002")).isEqualTo(2);
    }

    @Test
    void elMismoNumeroDeSerieEnOtroTipoDeDocumentoEsOtraCuenta() {
        assertThat(siguiente("01", "F001")).isEqualTo(1);
        assertThat(siguiente("07", "F001")).isEqualTo(1);
    }

    @Test
    void siLaTransaccionSeDeshaceElNumeroVuelveAEstarLibreYNoHayHuecos() {
        assertThat(siguiente("F001")).isEqualTo(1);
        serieService.asegurar("01", "F001");

        assertThatThrownBy(() -> tx.executeWithoutResult(e -> {
            assertThat(serieService.siguiente("01", "F001")).isEqualTo(2);
            throw new IllegalStateException("falló algo después de reservar");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(siguiente("F001")).isEqualTo(2);
    }

    @Test
    void sinTransaccionAbiertaFalla() {
        serieService.asegurar("01", "F001");
        assertThatThrownBy(() -> serieService.siguiente("01", "F001")).isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void pedirUnNumeroDeUnaSerieQueNoSeAseguroFallaConUnMensajeClaro() {
        assertThatThrownBy(() -> tx.execute(e -> serieService.siguiente("01", "F777")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("La serie F777 no existe: hay que llamar a asegurar() antes");
    }

    @Test
    void aseguraLaMismaSerieVariasVecesALaVezSinErrorYCreaUnaSolaFila() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(20);
        try {
            List<Future<?>> trabajos = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                trabajos.add(pool.submit(() -> serieService.asegurar("01", "F001")));
            }
            for (Future<?> t : trabajos) {
                t.get(30, TimeUnit.SECONDS);
            }
            assertThat(series.count()).isEqualTo(1);
            assertThat(series.findAll().get(0).getUltimoCorrelativo()).isZero();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aseguraUnaSerieQueYaExisteSinTocarSuContador() {
        siguiente("F001");
        siguiente("F001");
        serieService.asegurar("01", "F001");
        assertThat(siguiente("F001")).isEqualTo(3);
    }

    @Test
    void con50HilosALaVezNoHaySaltosNiRepetidos() throws Exception {
        int hilos = 50;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            List<Future<Long>> trabajos = new ArrayList<>();
            for (int i = 0; i < hilos; i++) {
                trabajos.add(pool.submit(() -> {
                    salida.await();
                    return siguiente("F001");
                }));
            }
            salida.countDown();
            Set<Long> obtenidos = new HashSet<>();
            for (Future<Long> t : trabajos) {
                obtenidos.add(t.get(60, TimeUnit.SECONDS));
            }
            assertThat(obtenidos).hasSize(hilos);
            assertThat(obtenidos).isEqualTo(LongStream.rangeClosed(1, hilos).boxed().collect(Collectors.toSet()));
            assertThat(siguiente("F001")).isEqualTo(hilos + 1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void con50HilosAlgunosQueFallanDespuesDeReservarTampocoDejanHuecos() throws Exception {
        int hilos = 50;
        serieService.asegurar("01", "F001");
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            List<Future<Long>> trabajos = new ArrayList<>();
            for (int i = 0; i < hilos; i++) {
                boolean falla = i % 3 == 0;
                trabajos.add(pool.submit(() -> {
                    salida.await();
                    try {
                        return tx.execute(e -> {
                            long n = serieService.siguiente("01", "F001");
                            if (falla) {
                                throw new IllegalStateException("falla");
                            }
                            return n;
                        });
                    } catch (IllegalStateException ex) {
                        return -1L;
                    }
                }));
            }
            salida.countDown();
            List<Long> ok = Collections.synchronizedList(new ArrayList<>());
            for (Future<Long> t : trabajos) {
                long n = t.get(60, TimeUnit.SECONDS);
                if (n > 0) {
                    ok.add(n);
                }
            }
            long esperados = ok.size();
            assertThat(ok).hasSize((int) esperados).doesNotHaveDuplicates();
            assertThat(new HashSet<>(ok)).isEqualTo(LongStream.rangeClosed(1, esperados).boxed().collect(Collectors.toSet()));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void elContadorQuedaGuardadoEnLaBase() {
        siguiente("F001");
        siguiente("F001");
        assertThat(series.findAll()).singleElement().satisfies(s -> {
            assertThat(s.getTipoDocumento()).isEqualTo("01");
            assertThat(s.getSerie()).isEqualTo("F001");
            assertThat(s.getUltimoCorrelativo()).isEqualTo(2);
        });
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"F00", "F0011", "f001", "F-01", " F01", "F001\n"})
    void unaSerieInvalidaSeRechaza(String serie) {
        assertThatThrownBy(() -> serieService.asegurar("01", serie)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tx.execute(e -> serieService.siguiente("01", serie))).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"1", "001", "AB", "1a"})
    void unTipoInvalidoSeRechaza(String tipo) {
        assertThatThrownBy(() -> serieService.asegurar(tipo, "F001")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tx.execute(e -> serieService.siguiente(tipo, "F001"))).isInstanceOf(IllegalArgumentException.class);
    }
}
