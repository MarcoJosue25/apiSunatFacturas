package apisunat.api_sunat.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// Un intento de enviar un comprobante a la SUNAT y lo que pasó
@Entity
@Table(name = "envio_intentos")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class EnvioIntento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "comprobante_id", nullable = false)
    private Long comprobanteId;

    @Column(nullable = false)
    private LocalDateTime fecha;

    // La clase de respuesta de la SUNAT (ClaseResultado) o SIN_RESPUESTA si no contestó
    @Column(nullable = false, length = 30)
    private String resultado;

    @Column(length = 10)
    private String codigo;

    @Column(length = 2000)
    private String descripcion;
}
