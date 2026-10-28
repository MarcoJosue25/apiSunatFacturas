package apisunat.api_sunat.controller;

import apisunat.api_sunat.config.SunatProperties;
import apisunat.api_sunat.dto.FacturaRequest;
import apisunat.api_sunat.dto.FacturaResponse;
import apisunat.api_sunat.model.enums.EstadoComprobante;
import apisunat.api_sunat.service.ComprobanteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@Slf4j
@RestController
@RequestMapping("/api/v1/facturas")
@RequiredArgsConstructor
public class FacturaController {

    private static final MediaType XML_LATIN1 = new MediaType("application", "xml", StandardCharsets.ISO_8859_1);

    private final ComprobanteService comprobanteService;
    private final SunatProperties propiedades;

    // 201: se creó y la SUNAT ya contestó. 202: se creó y quedó guardada, pero la SUNAT no respondió (se reintentará).
    // 200: ya existía una factura con ese idExterno y se devuelve esa, sin enviar nada de nuevo.
    @PostMapping
    public ResponseEntity<FacturaResponse> emitir(@Valid @RequestBody FacturaRequest request) {
        log.info("Endpoint hit: POST /api/v1/facturas idExterno={}", request.getIdExterno());
        ComprobanteService.Emision emision = comprobanteService.emitir(request);
        HttpStatus estado;
        if (!emision.nueva()) {
            estado = HttpStatus.OK;
        } else if (emision.factura().getEstado() == EstadoComprobante.ERROR_TEMPORAL) {
            estado = HttpStatus.ACCEPTED;
        } else {
            estado = HttpStatus.CREATED;
        }
        return ResponseEntity.status(estado).body(emision.factura());
    }

    @GetMapping("/{id}")
    public FacturaResponse obtener(@PathVariable Long id) {
        log.info("Endpoint hit: GET /api/v1/facturas/{}", id);
        return comprobanteService.obtener(id);
    }

    @GetMapping("/{id}/xml")
    public ResponseEntity<byte[]> obtenerXml(@PathVariable Long id) {
        log.info("Endpoint hit: GET /api/v1/facturas/{}/xml", id);
        FacturaResponse factura = comprobanteService.obtener(id);
        String nombre = propiedades.getRuc() + "-01-" + factura.getNumero() + ".xml";
        return ResponseEntity.ok()
                .contentType(XML_LATIN1)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
                .body(comprobanteService.obtenerXml(id));
    }
}
