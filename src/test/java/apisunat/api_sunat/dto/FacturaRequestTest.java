package apisunat.api_sunat.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class FacturaRequestTest {

    private final Validator validador = Validation.buildDefaultValidatorFactory().getValidator();

    private static ItemRequest item() {
        return ItemRequest.builder().descripcion("Polo básico negro").cantidad(new BigDecimal("2")).precioUnitario(new BigDecimal("35.00")).build();
    }

    private static ClienteRequest cliente() {
        return ClienteRequest.builder().ruc("20123456786").razonSocial("CLIENTE SAC").build();
    }

    private static FacturaRequest factura() {
        return FacturaRequest.builder().cliente(cliente()).items(List.of(item())).build();
    }

    private Set<String> rutas(Object objeto) {
        return validador.validate(objeto).stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    private List<String> mensajes(Object objeto) {
        return validador.validate(objeto).stream().map(ConstraintViolation::getMessage).sorted().toList();
    }

    // --- Lo válido ---

    @Test
    void unaFacturaCorrectaNoTieneErrores() {
        assertThat(validador.validate(factura())).isEmpty();
    }

    @Test
    void laUnidadDeMedidaPorDefectoEsNiu() {
        assertThat(item().getUnidadMedida()).isEqualTo("NIU");
        assertThat(new ItemRequest().getUnidadMedida()).isEqualTo("NIU");
    }

    @Test
    void elLimiteDe999ItemsSePermite() {
        List<ItemRequest> muchos = new ArrayList<>();
        for (int i = 0; i < 999; i++) {
            muchos.add(item());
        }
        assertThat(validador.validate(FacturaRequest.builder().cliente(cliente()).items(muchos).build())).isEmpty();
    }

    // --- La factura ---

    @Test
    void sinClienteFalla() {
        assertThat(rutas(FacturaRequest.builder().items(List.of(item())).build())).containsExactly("cliente");
    }

    @Test
    void sinItemsOConItemsVaciosFalla() {
        assertThat(rutas(FacturaRequest.builder().cliente(cliente()).build())).containsExactly("items");
        assertThat(rutas(FacturaRequest.builder().cliente(cliente()).items(List.of()).build())).containsExactly("items");
    }

    @Test
    void masDe999ItemsFalla() {
        List<ItemRequest> muchos = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            muchos.add(item());
        }
        assertThat(mensajes(FacturaRequest.builder().cliente(cliente()).items(muchos).build())).containsExactly("La factura no puede tener más de 999 ítems");
    }

    @Test
    void loErroresDeUnItemDicenCualEs() {
        ItemRequest malo = item();
        malo.setCantidad(BigDecimal.ZERO);
        FacturaRequest f = FacturaRequest.builder().cliente(cliente()).items(List.of(item(), malo)).build();
        assertThat(rutas(f)).containsExactly("items[1].cantidad");
    }

    @Test
    void loErroresDelClienteDicenCualEs() {
        FacturaRequest f = FacturaRequest.builder().cliente(ClienteRequest.builder().ruc("123").razonSocial("X").build()).items(List.of(item())).build();
        assertThat(rutas(f)).containsExactly("cliente.ruc");
    }

    // --- Cliente ---

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "123", "20123456785", "2012345678a", "20123456786 ", "30123456786"})
    void unRucInvalidoDelClienteFalla(String ruc) {
        ClienteRequest c = cliente();
        c.setRuc(ruc);
        assertThat(rutas(c)).containsExactly("ruc");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void laRazonSocialDelClienteEsObligatoria(String razon) {
        ClienteRequest c = cliente();
        c.setRazonSocial(razon);
        assertThat(rutas(c)).containsExactly("razonSocial");
    }

    @Test
    void laRazonSocialTieneUnMaximoDe1500() {
        ClienteRequest c = cliente();
        c.setRazonSocial("A".repeat(1500));
        assertThat(validador.validate(c)).isEmpty();
        c.setRazonSocial("A".repeat(1501));
        assertThat(rutas(c)).containsExactly("razonSocial");
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 4, 12, 199, 200})
    void unaDireccionDeTresADoscientosCaracteresEsValida(int largo) {
        ClienteRequest c = cliente();
        c.setDireccion("A".repeat(largo));
        assertThat(validador.validate(c)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 201, 1000})
    void unaDireccionMuyCortaOMuyLargaFalla(int largo) {
        ClienteRequest c = cliente();
        c.setDireccion("A".repeat(largo));
        assertThat(rutas(c)).containsExactly("direccion");
    }

    @Test
    void laDireccionEsOpcional() {
        ClienteRequest c = cliente();
        c.setDireccion(null);
        assertThat(validador.validate(c)).isEmpty();
    }

    // --- Ítem ---

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void laDescripcionEsObligatoria(String descripcion) {
        ItemRequest i = item();
        i.setDescripcion(descripcion);
        assertThat(rutas(i)).containsExactly("descripcion");
    }

    @Test
    void laDescripcionTieneUnMaximoDe500() {
        ItemRequest i = item();
        i.setDescripcion("D".repeat(500));
        assertThat(validador.validate(i)).isEmpty();
        i.setDescripcion("D".repeat(501));
        assertThat(rutas(i)).containsExactly("descripcion");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.0", "-1", "-0.0000001"})
    void laCantidadYElPrecioDebenSerMayoresQueCero(String valor) {
        ItemRequest i = item();
        i.setCantidad(new BigDecimal(valor));
        i.setPrecioUnitario(new BigDecimal(valor));
        assertThat(rutas(i)).containsExactlyInAnyOrder("cantidad", "precioUnitario");
    }

    @Test
    void laCantidadYElPrecioSonObligatorios() {
        ItemRequest i = item();
        i.setCantidad(null);
        i.setPrecioUnitario(null);
        assertThat(rutas(i)).containsExactlyInAnyOrder("cantidad", "precioUnitario");
    }

    @Test
    void doceEnterosYDiezDecimalesSePermitenYUnoMasNo() {
        ItemRequest i = item();
        i.setCantidad(new BigDecimal("999999999999.0123456789"));
        i.setPrecioUnitario(new BigDecimal("999999999999.0123456789"));
        assertThat(validador.validate(i)).isEmpty();

        i.setCantidad(new BigDecimal("1000000000000"));
        i.setPrecioUnitario(new BigDecimal("1.12345678901"));
        assertThat(rutas(i)).containsExactlyInAnyOrder("cantidad", "precioUnitario");
    }

    @ParameterizedTest
    @ValueSource(strings = {"NIU", "ZZ", "KGM", "LTR", "MTR", "BX", "A1", "1"})
    void lasUnidadesDeMedidaConElPatronDeLaSunatSonValidas(String unidad) {
        ItemRequest i = item();
        i.setUnidadMedida(unidad);
        assertThat(validador.validate(i)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "niu", "Niu", "NIUU", "N I", "ÑIU", "N-U", " NI", "   "})
    void unaUnidadDeMedidaMalEscritaFalla(String unidad) {
        ItemRequest i = item();
        i.setUnidadMedida(unidad);
        assertThat(rutas(i)).containsExactly("unidadMedida");
    }

    @Test
    void sinUnidadDeMedidaSeUsaNiu() {
        ItemRequest i = item();
        i.setUnidadMedida(null);
        assertThat(validador.validate(i)).isEmpty();
        assertThat(i.getUnidadMedida()).isEqualTo("NIU");
    }

    @Test
    void unJsonConLaUnidadNulaOSinLaUnidadTambienUsaNiu() {
        ItemRequest sin = json.readValue("{\"descripcion\": \"a\", \"cantidad\": 1, \"precioUnitario\": 1}", ItemRequest.class);
        ItemRequest nula = json.readValue("{\"descripcion\": \"a\", \"cantidad\": 1, \"precioUnitario\": 1, \"unidadMedida\": null}", ItemRequest.class);
        assertThat(sin.getUnidadMedida()).isEqualTo("NIU");
        assertThat(nula.getUnidadMedida()).isEqualTo("NIU");
        assertThat(validador.validate(sin)).isEmpty();
        assertThat(validador.validate(nula)).isEmpty();
    }

    @Test
    void elCodigoDelProductoEsOpcionalYTieneUnMaximoDe30() {
        ItemRequest i = item();
        assertThat(validador.validate(i)).isEmpty();
        i.setCodigo("C".repeat(30));
        assertThat(validador.validate(i)).isEmpty();
        i.setCodigo("C".repeat(31));
        assertThat(rutas(i)).containsExactly("codigo");
    }

    // --- Los mensajes están en español y dicen qué pasa ---

    @Test
    void losMensajesDeErrorEstanEnEspanol() {
        FacturaRequest f = FacturaRequest.builder().cliente(ClienteRequest.builder().ruc("1").razonSocial("").build())
                .items(List.of(ItemRequest.builder().descripcion("").cantidad(BigDecimal.ZERO).precioUnitario(BigDecimal.ONE).build())).build();
        assertThat(mensajes(f)).contains("El RUC del cliente no es válido", "La razón social del cliente no puede estar vacía",
                "La descripción del ítem no puede estar vacía", "La cantidad del ítem debe ser mayor que cero");
    }

    // --- Cómo llega el JSON de verdad ---

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void elJsonDeUnaFacturaSeConvierteYQuedaValido() {
        String cuerpo = """
                {"cliente": {"ruc": "20123456786", "razonSocial": "CLIENTE SAC", "direccion": "Av. Perú 123"},
                 "items": [{"descripcion": "Polo básico negro", "cantidad": 2, "precioUnitario": 35.00, "codigo": "POLO-N"},
                           {"descripcion": "Servicio de estampado", "cantidad": 1.5, "precioUnitario": 12.5, "unidadMedida": "ZZ"}]}
                """;
        FacturaRequest f = json.readValue(cuerpo, FacturaRequest.class);

        assertThat(validador.validate(f)).isEmpty();
        assertThat(f.getItems()).hasSize(2);
        assertThat(f.getItems().get(0).getUnidadMedida()).isEqualTo("NIU");
        assertThat(f.getItems().get(1).getUnidadMedida()).isEqualTo("ZZ");
        assertThat(f.getItems().get(0).getPrecioUnitario()).isEqualByComparingTo("35.00");
        assertThat(f.getItems().get(1).getCantidad()).isEqualByComparingTo("1.5");
    }

    @Test
    void unPrecioEscritoComoNumeroJsonNoPierdeDecimalesPorPuntoFlotante() {
        FacturaRequest f = json.readValue("""
                {"cliente": {"ruc": "20123456786", "razonSocial": "X"}, "items": [{"descripcion": "a", "cantidad": 1, "precioUnitario": 0.1}]}
                """, FacturaRequest.class);
        assertThat(f.getItems().get(0).getPrecioUnitario()).isEqualByComparingTo(new BigDecimal("0.1"));
        assertThat(f.getItems().get(0).getPrecioUnitario().toPlainString()).isEqualTo("0.1");
    }

    @Test
    void unJsonSinCamposDaUnaFacturaInvalidaYNoUnaExcepcion() {
        FacturaRequest f = json.readValue("{}", FacturaRequest.class);
        assertThat(rutas(f)).containsExactlyInAnyOrder("cliente", "items");
    }
}
