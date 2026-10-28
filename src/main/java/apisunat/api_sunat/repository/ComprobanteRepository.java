package apisunat.api_sunat.repository;

import apisunat.api_sunat.model.Comprobante;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ComprobanteRepository extends JpaRepository<Comprobante, Long> {

    Optional<Comprobante> findByIdExterno(String idExterno);
}
