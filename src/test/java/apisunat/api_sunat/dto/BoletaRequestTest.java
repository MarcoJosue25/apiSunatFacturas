package apisunat.api_sunat.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class BoletaRequestTest {

    private final Validator validador = Validation.buildDefaultValidatorFactory().getValidator();

    private static ItemRequest item() {
        return ItemRequest.builder().descripcion("Polo").cantidad(new BigDecimal("1")).precioUnitario(new BigDecimal("35.00")).build();
    }

    private static BoletaRequest boleta(ClienteBoletaRequest cliente) {
        return BoletaRequest.builder().idExterno("pedido-1").cliente(cliente).items(List.of(item())).build();
    }

    private static ClienteBoletaRequest cliente(String tipo, String numero) {
        return ClienteBoletaRequest.builder().tipoDocumento(tipo).numeroDocumento(numero).nombre("Juan Pérez").build();
    }

    private Set<String> rutas(Object o) {
        return validador.validate(o).stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    private List<String> mensajes(Object o) {
        return validador.validate(o).stream().map(ConstraintViolation::getMessage).sorted().toList();
    }

    @Test
    void unaBoletaSinClienteEsValidaYAnonima() {
        assertThat(validador.validate(boleta(null))).isEmpty();
    }

    @Test
    void unaBoletaConClienteVacioOSinDocumentoEsAnonima() {
        assertThat(validador.validate(boleta(new ClienteBoletaRequest()))).isEmpty();
        assertThat(validador.validate(boleta(cliente("0", null)))).isEmpty();
        assertThat(validador.validate(boleta(cliente(null, "  ")))).isEmpty();
        assertThat(new ClienteBoletaRequest().esAnonimo()).isTrue();
        assertThat(cliente("0", null).esAnonimo()).isTrue();
        assertThat(cliente("1", "12345678").esAnonimo()).isFalse();
    }

    @Test
    void sinDocumentoPeroConUnNumeroNoEsCoherente() {
        assertThat(rutas(boleta(cliente("0", "12345678")))).containsExactly("cliente.documentoCoherente");
    }

    @ParameterizedTest
    @CsvSource({"1,12345678", "1,00000001", "6,20123456786", "6,10180967929", "4,AB1234567", "4,1", "7,P12345678901", "7,X"})
    void losDocumentosConSuFormatoPasan(String tipo, String numero) {
        assertThat(validador.validate(boleta(cliente(tipo, numero)))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"1,1234567", "1,123456789", "1,1234567a", "1,'12345678 '", "6,20123456787", "6,2012345678", "6,12345678901", "4,", "4,'AB 12'",
            "4,ABCDEFGHIJKLM", "7,'P-123'", "6,", "1,"})
    void unDocumentoQueNoCorrespondeASuTipoFalla(String tipo, String numero) {
        assertThat(rutas(boleta(cliente(tipo, numero)))).containsExactly("cliente.documentoCoherente");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2", "3", "5", "8", "A", "a", "01", "-", "10", "6 "})
    void unTipoDeDocumentoFueraDeLaListaFalla(String tipo) {
        assertThat(rutas(boleta(cliente(tipo, "12345678")))).contains("cliente.tipoDocumento");
    }

    @Test
    void elNombreTieneUnMaximoDe1500() {
        ClienteBoletaRequest c = cliente("1", "12345678");
        c.setNombre("N".repeat(1500));
        assertThat(validador.validate(boleta(c))).isEmpty();
        c.setNombre("N".repeat(1501));
        assertThat(rutas(boleta(c))).containsExactly("cliente.nombre");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "con espacio", "ñ", "a/b"})
    void elIdExternoEsObligatorioYTieneFormato(String id) {
        BoletaRequest b = boleta(null);
        b.setIdExterno(id);
        assertThat(rutas(b)).containsExactly("idExterno");
    }

    @Test
    void sinItemsFalla() {
        BoletaRequest b = boleta(null);
        b.setItems(List.of());
        assertThat(mensajes(b)).containsExactly("La boleta necesita al menos un ítem");
        b.setItems(null);
        assertThat(mensajes(b)).containsExactly("La boleta necesita al menos un ítem");
    }

    @Test
    void conMasDe999ItemsFalla() {
        BoletaRequest b = boleta(null);
        b.setItems(java.util.Collections.nCopies(1000, item()));
        assertThat(mensajes(b)).containsExactly("La boleta no puede tener más de 999 ítems");
    }

    @Test
    void losErroresDeLosItemsSeReportan() {
        BoletaRequest b = boleta(null);
        b.setItems(List.of(ItemRequest.builder().descripcion("").cantidad(BigDecimal.ZERO).precioUnitario(BigDecimal.ZERO).build()));
        assertThat(rutas(b)).contains("items[0].descripcion", "items[0].cantidad", "items[0].precioUnitario");
    }
}
