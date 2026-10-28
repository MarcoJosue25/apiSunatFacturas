package apisunat.api_sunat.repository;

import apisunat.api_sunat.model.EnvioIntento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EnvioIntentoRepository extends JpaRepository<EnvioIntento, Long> {

    List<EnvioIntento> findByComprobanteIdOrderByIdAsc(Long comprobanteId);
}
