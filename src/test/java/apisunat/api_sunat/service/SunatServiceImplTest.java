package apisunat.api_sunat.service;

import apisunat.api_sunat.dto.EstadoTicketResponse;
import apisunat.api_sunat.exception.SunatFaultException;
import apisunat.api_sunat.exception.SunatNoDisponibleException;
import apisunat.api_sunat.exception.SunatRespuestaInvalidaException;
import apisunat.api_sunat.service.impl.SunatServiceImpl;
import apisunat.api_sunat.sunat.soap.BillServiceClient;
import apisunat.api_sunat.sunat.soap.RespuestaStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SunatServiceImplTest {

    @Mock
    private BillServiceClient billServiceClient;

    @InjectMocks
    private SunatServiceImpl service;

    @Test
    void devuelveElStatusCodeYElContentTalComoLosDioLaSunat() {
        when(billServiceClient.getStatus("201100000011227"))
                .thenReturn(RespuestaStatus.builder().statusCode("0127").content("El ticket no existe").build());

        EstadoTicketResponse r = service.consultarTicket("201100000011227");

        assertThat(r.getStatusCode()).isEqualTo("0127");
        assertThat(r.getContent()).isEqualTo("El ticket no existe");
    }

    @Test
    void unContentNuloSeQuedaNulo() {
        when(billServiceClient.getStatus("1")).thenReturn(RespuestaStatus.builder().statusCode("98").build());

        EstadoTicketResponse r = service.consultarTicket("1");

        assertThat(r.getStatusCode()).isEqualTo("98");
        assertThat(r.getContent()).isNull();
    }

    @Test
    void hacePasarElTicketSinCambiarloYConsultaUnaSolaVez() {
        when(billServiceClient.getStatus("987654321")).thenReturn(RespuestaStatus.builder().statusCode("0").build());

        service.consultarTicket("987654321");

        verify(billServiceClient).getStatus("987654321");
        verifyNoMoreInteractions(billServiceClient);
    }

    @Test
    void noTragaLosErroresDelClienteLosDejaPasar() {
        when(billServiceClient.getStatus("1")).thenThrow(new SunatFaultException("S:Client.0200", "falló", "0200"));
        assertThatThrownBy(() -> service.consultarTicket("1")).isInstanceOf(SunatFaultException.class);

        when(billServiceClient.getStatus("2")).thenThrow(new SunatNoDisponibleException("no responde", true, null));
        assertThatThrownBy(() -> service.consultarTicket("2")).isInstanceOf(SunatNoDisponibleException.class);

        when(billServiceClient.getStatus("3")).thenThrow(new SunatRespuestaInvalidaException("basura"));
        assertThatThrownBy(() -> service.consultarTicket("3")).isInstanceOf(SunatRespuestaInvalidaException.class);
    }
}
