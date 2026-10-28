package apisunat.api_sunat.service;

import apisunat.api_sunat.dto.EstadoTicketResponse;

public interface SunatService {

    // Pregunta a la SUNAT en qué estado está un ticket (los que devuelve sendSummary y sendPack)
    EstadoTicketResponse consultarTicket(String ticket);
}
