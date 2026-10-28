package apisunat.api_sunat.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class RucValidatorTest {

    private static boolean valido(String ruc) {
        return new RucValidator().isValid(ruc, null);
    }

    // Generados con un cálculo independiente (Python). Incluyen los RUC de la SUNAT y los del manual, personas naturales (10)
    // y los casos especiales del dígito verificador: resto 1 da verificador 0 y resto 0 da verificador 1
    @ParameterizedTest
    @ValueSource(strings = {"10123456781", "10180967929", "10241902710", "10347017464", "10682360131", "10787789329", "10879217429", "10907579382", "10908100331", "15003307660", "15077283789", "15191614612", "15506123108", "15619345140", "15814538408", "16049403261", "16095461119", "16326616633", "16393095591", "16729792628", "16991023741", "17014673008", "17108436468", "17243933285", "17367916010", "17789914338", "17974821231", "20075400919", "20100066603", "20123456786", "20131312955", "20160790963", "20245892153", "20272887790", "20275892905", "20714521170", "20850255173", "20904954901"})
    void losRucConDigitoVerificadorCorrectoSonValidos(String ruc) {
        assertThat(valido(ruc)).as(ruc).isTrue();
    }

    // Los mismos RUC con el último dígito cambiado
    @ParameterizedTest
    @ValueSource(strings = {"10787789320", "10879217420", "10180967920", "10908100332", "10907579383", "10347017465", "15814538409", "15191614613", "15003307661", "15619345141", "15506123109", "15077283780", "16326616634", "16049403262", "16991023742", "16095461110", "16393095592", "16729792629", "17243933286", "17367916011", "17108436469", "17974821232", "17789914339", "17014673009", "20245892154"})
    void elMismoRucConOtroDigitoVerificadorEsInvalido(String ruc) {
        assertThat(valido(ruc)).as(ruc).isFalse();
    }

    @Test
    void unRucNuloSeConsideraValidoPorqueParaExigirloSeUsaNotBlank() {
        assertThat(valido(null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "2010006660", "201000666033", "2010006660a", "a0100066603", " 20100066603", "20100066603 ", "20100066603\n",
            "20-10006660", "٢٠١٠٠٠٦٦٦٠٣", "２０１００６６６０３", "00000000000", "99999999999", "30100066603", "11100066603", "21100066603"})
    void formatosRaros(String ruc) {
        assertThat(valido(ruc)).as(ruc).isFalse();
    }

    // Tienen el dígito verificador correcto (calculado aparte) pero un prefijo que ningún RUC usa: solo el prefijo los hace inválidos
    @ParameterizedTest
    @ValueSource(strings = {"00945807300", "01215736811", "11930364268", "12212997224", "13003322453", "14383236402", "18562241546",
            "19990951457", "21475277209", "30405608657", "40690702934", "99137585843"})
    void unPrefijoQueNoEsDeLaSunatEsInvalidoAunqueElVerificadorCuadre(String ruc) {
        assertThat(valido(ruc)).as(ruc).isFalse();
    }
}
