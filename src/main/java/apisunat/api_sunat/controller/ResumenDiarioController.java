package apisunat.api_sunat.controller;

import apisunat.api_sunat.dto.ResumenResponse;
import apisunat.api_sunat.service.ResumenDiarioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/resumenes")
@RequiredArgsConstructor
public class ResumenDiarioController {

    private final ResumenDiarioService resumenDiarioService;

    // Arma y envía ahora los resúmenes de las boletas pendientes, sin esperar a la hora programada. Sin boletas pendientes devuelve [].
    @PostMapping("/generar")
    public List<ResumenResponse> generar() {
        log.info("Endpoint hit: POST /api/v1/resumenes/generar");
        return resumenDiarioService.generarYEnviar().stream().map(resumenDiarioService::obtener).toList();
    }

    @GetMapping("/{id}")
    public ResumenResponse obtener(@PathVariable Long id) {
        log.info("Endpoint hit: GET /api/v1/resumenes/{}", id);
        return resumenDiarioService.obtener(id);
    }

    // Vuelve a enviar un resumen que la SUNAT rechazó, con el mismo nombre y el mismo contenido. 409 si no está rechazado.
    @PostMapping("/{id}/reenviar")
    public ResponseEntity<ResumenResponse> reenviar(@PathVariable Long id) {
        log.info("Endpoint hit: POST /api/v1/resumenes/{}/reenviar", id);
        return ResponseEntity.ok(resumenDiarioService.reenviar(id));
    }
}
