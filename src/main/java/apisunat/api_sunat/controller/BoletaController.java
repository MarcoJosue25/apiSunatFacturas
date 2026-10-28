package apisunat.api_sunat.controller;

import apisunat.api_sunat.config.SunatProperties;
import apisunat.api_sunat.dto.BoletaRequest;
import apisunat.api_sunat.dto.FacturaResponse;
import apisunat.api_sunat.service.BoletaService;
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
@RequestMapping("/api/v1/boletas")
@RequiredArgsConstructor
public class BoletaController {

    private static final MediaType XML_LATIN1 = new MediaType("application", "xml", StandardCharsets.ISO_8859_1);

    private final BoletaService boletaService;
    private final SunatProperties propiedades;

    // 201: se creó y quedó pendiente de su resumen diario (la SUNAT no se consulta aquí). 200: ya existía una boleta con ese idExterno.
    @PostMapping
    public ResponseEntity<FacturaResponse> emitir(@Valid @RequestBody BoletaRequest request) {
        log.info("Endpoint hit: POST /api/v1/boletas idExterno={}", request.getIdExterno());
        BoletaService.Emision emision = boletaService.emitir(request);
        return ResponseEntity.status(emision.nueva() ? HttpStatus.CREATED : HttpStatus.OK).body(emision.boleta());
    }

    @GetMapping("/{id}")
    public FacturaResponse obtener(@PathVariable Long id) {
        log.info("Endpoint hit: GET /api/v1/boletas/{}", id);
        return boletaService.obtener(id);
    }

    @GetMapping("/{id}/xml")
    public ResponseEntity<byte[]> obtenerXml(@PathVariable Long id) {
        log.info("Endpoint hit: GET /api/v1/boletas/{}/xml", id);
        FacturaResponse boleta = boletaService.obtener(id);
        String nombre = propiedades.getRuc() + "-03-" + boleta.getNumero() + ".xml";
        return ResponseEntity.ok()
                .contentType(XML_LATIN1)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
                .body(boletaService.obtenerXml(id));
    }
}
