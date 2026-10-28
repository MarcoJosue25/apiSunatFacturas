package apisunat.api_sunat.service.impl;

import apisunat.api_sunat.dto.ItemRequest;
import apisunat.api_sunat.sunat.calculo.CalculadoraIgv;
import apisunat.api_sunat.sunat.calculo.LineaCalculada;
import apisunat.api_sunat.sunat.calculo.TotalesDocumento;

import java.util.ArrayList;
import java.util.List;

// Los totales de una lista de ítems, con las mismas reglas que usa el armador del XML
final class TotalesDeItems {

    private TotalesDeItems() {
    }

    static TotalesDocumento de(List<ItemRequest> items) {
        List<LineaCalculada> lineas = new ArrayList<>();
        for (ItemRequest item : items) {
            lineas.add(CalculadoraIgv.calcularLinea(item.getPrecioUnitario(), item.getCantidad()));
        }
        return CalculadoraIgv.calcularTotales(lineas);
    }
}
