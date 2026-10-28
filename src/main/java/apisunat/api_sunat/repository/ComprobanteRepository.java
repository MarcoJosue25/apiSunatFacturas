package apisunat.api_sunat.repository;

import apisunat.api_sunat.model.Comprobante;
import apisunat.api_sunat.model.enums.EstadoComprobante;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ComprobanteRepository extends JpaRepository<Comprobante, Long> {

    Optional<Comprobante> findByIdExterno(String idExterno);

    // Los pendientes cuyo próximo intento ya llegó, los más atrasados primero. Solo los ids: así no se cargan los XML de todos.
    @Query("select c.id from Comprobante c where c.estado in :estados and c.proximoIntento <= :ahora order by c.proximoIntento, c.id")
    List<Long> idsPendientes(@Param("estados") List<EstadoComprobante> estados, @Param("ahora") LocalDateTime ahora, Pageable pagina);

    // Los que llevan demasiado tiempo "enviándose": la aplicación se cayó a mitad del envío
    @Query("select c.id from Comprobante c where c.estado = apisunat.api_sunat.model.enums.EstadoComprobante.ENVIANDO and c.actualizadoEn < :limite order by c.id")
    List<Long> idsEnviandoDesde(@Param("limite") LocalDateTime limite);

    // Las boletas que todavía no están en ningún resumen, de la más antigua a la más nueva
    @Query("select c.id from Comprobante c where c.estado = apisunat.api_sunat.model.enums.EstadoComprobante.PENDIENTE_DE_RESUMEN "
            + "order by c.fechaEmision, c.correlativo")
    List<Long> idsBoletasPendientesDeResumen();

    List<Comprobante> findByResumenIdOrderByCorrelativo(Long resumenId);
}
