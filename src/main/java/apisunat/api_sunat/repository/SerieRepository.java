package apisunat.api_sunat.repository;

import apisunat.api_sunat.model.Serie;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SerieRepository extends JpaRepository<Serie, Long> {

    // Crea la serie si no existe (sin error si dos hilos lo intentan a la vez)
    @Modifying
    @Query(value = "INSERT IGNORE INTO series (tipo_documento, serie, ultimo_correlativo, version) VALUES (:tipo, :serie, 0, 0)",
            nativeQuery = true)
    void crearSiNoExiste(@Param("tipo") String tipo, @Param("serie") String serie);

    // SELECT ... FOR UPDATE: quien la bloquea es el único que puede pedir el siguiente número hasta que termine su transacción
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Serie> findByTipoDocumentoAndSerie(String tipoDocumento, String serie);
}
