package apisunat.api_sunat.repository;

import apisunat.api_sunat.model.ResumenDiario;
import apisunat.api_sunat.model.enums.EstadoResumen;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ResumenDiarioRepository extends JpaRepository<ResumenDiario, Long> {

    Optional<ResumenDiario> findByNombre(String nombre);

    // El mayor correlativo ya usado ese día (0 si no hay ninguno): el siguiente resumen usa uno más
    @Query("select coalesce(max(r.correlativo), 0) from ResumenDiario r where r.fechaReferencia = :fecha")
    int maxCorrelativoDelDia(@Param("fecha") LocalDate fecha);

    // Los resúmenes que tocan ahora: enviar los que no se enviaron o consultar los que esperan respuesta
    @Query("select r.id from ResumenDiario r where r.estado in :estados and r.proximoIntento <= :ahora order by r.proximoIntento, r.id")
    List<Long> idsPendientes(@Param("estados") List<EstadoResumen> estados, @Param("ahora") LocalDateTime ahora, Pageable pagina);
}
