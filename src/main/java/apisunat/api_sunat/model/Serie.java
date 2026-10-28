package apisunat.api_sunat.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// El último correlativo usado de una serie (por ejemplo 01 / F001)
@Entity
@Table(name = "series")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Serie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tipo_documento", nullable = false, length = 2)
    private String tipoDocumento;

    @Column(nullable = false, length = 4)
    private String serie;

    @Column(name = "ultimo_correlativo", nullable = false)
    private long ultimoCorrelativo;

    @Version
    private Long version;

    // Entrega el siguiente número. Solo se llama con la fila bloqueada (ver SerieRepository).
    public long siguiente() {
        ultimoCorrelativo++;
        return ultimoCorrelativo;
    }
}
