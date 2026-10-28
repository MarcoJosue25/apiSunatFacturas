package apisunat.api_sunat.controller;

import apisunat.api_sunat.dto.EstadoTicketResponse;
import apisunat.api_sunat.service.SunatService;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/sunat")
@RequiredArgsConstructor
public class SunatController {

    private final SunatService sunatService;

    @GetMapping("/tickets/{ticket}")
    public EstadoTicketResponse consultarTicket(
            @PathVariable @Pattern(regexp = "^\\d{1,30}$", message = "El ticket debe ser un número de hasta 30 dígitos") String ticket) {
        log.info("Endpoint hit: GET /api/v1/sunat/tickets/{}", ticket);
        return sunatService.consultarTicket(ticket);
    }
}
