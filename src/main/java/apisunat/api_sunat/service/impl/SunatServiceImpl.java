package apisunat.api_sunat.service.impl;

import apisunat.api_sunat.dto.EstadoTicketResponse;
import apisunat.api_sunat.service.SunatService;
import apisunat.api_sunat.sunat.soap.BillServiceClient;
import apisunat.api_sunat.sunat.soap.RespuestaStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SunatServiceImpl implements SunatService {

    private final BillServiceClient billServiceClient;

    @Override
    public EstadoTicketResponse consultarTicket(String ticket) {
        RespuestaStatus respuesta = billServiceClient.getStatus(ticket);
        return EstadoTicketResponse.builder()
                .statusCode(respuesta.getStatusCode())
                .content(respuesta.getContent())
                .build();
    }
}
