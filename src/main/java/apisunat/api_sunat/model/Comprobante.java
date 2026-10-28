package apisunat.api_sunat.model;

import apisunat.api_sunat.dto.ResultadoEmision;
import apisunat.api_sunat.model.enums.EstadoComprobante;
import apisunat.api_sunat.sunat.cdr.CdrResultado;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

// Un comprobante emitido, con su número, su XML firmado y lo que contestó la SUNAT.
// Los cambios de estado se hacen solo con los métodos de esta clase, que no dejan saltar pasos.
@Entity
@Table(name = "comprobantes")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Comprobante {

    public static final String TIPO_FACTURA = "01";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // El identificador que pone quien pide la factura (mi agente), para no duplicarla si repite la petición
    @Column(name = "id_externo", nullable = false, length = 100, updatable = false)
    private String idExterno;

    @Column(name = "tipo_documento", nullable = false, length = 2, updatable = false)
    private String tipoDocumento;

    @Column(nullable = false, length = 4, updatable = false)
    private String serie;

    @Column(nullable = false, updatable = false)
    private long correlativo;

    @Column(name = "fecha_emision", nullable = false, updatable = false)
    private LocalDateTime fechaEmision;

    @Column(name = "ruc_cliente", nullable = false, length = 11, updatable = false)
    private String rucCliente;

    @Column(name = "razon_social_cliente", nullable = false, length = 1500, updatable = false)
    private String razonSocialCliente;

    @Column(name = "importe_total", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal importeTotal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoComprobante estado;

    @Column(name = "codigo_sunat", length = 10)
    private String codigoSunat;

    @Column(name = "descripcion_sunat", length = 2000)
    private String descripcionSunat;

    // Las advertencias de la constancia, una por línea: "4031 - Debe indicar el nombre comercial"
    @Column(columnDefinition = "TEXT")
    private String observaciones;

    @JdbcTypeCode(SqlTypes.LONGVARBINARY)
    @Column(name = "xml_firmado", updatable = false)
    private byte[] xmlFirmado;

    @JdbcTypeCode(SqlTypes.LONGVARBINARY)
    @Column(name = "cdr_zip")
    private byte[] cdrZip;

    @Version
    private Long version;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    // Un comprobante nuevo: ya tiene su número y su XML firmado, y espera ser enviado
    public static Comprobante nuevo(String idExterno, String serie, long correlativo, LocalDateTime fechaEmision, String rucCliente,
                                    String razonSocialCliente, BigDecimal importeTotal, byte[] xmlFirmado, LocalDateTime ahora) {
        return Comprobante.builder()
                .idExterno(idExterno).tipoDocumento(TIPO_FACTURA).serie(serie).correlativo(correlativo)
                .fechaEmision(fechaEmision).rucCliente(rucCliente).razonSocialCliente(razonSocialCliente)
                .importeTotal(importeTotal).xmlFirmado(xmlFirmado).estado(EstadoComprobante.PENDIENTE)
                .creadoEn(ahora).actualizadoEn(ahora).build();
    }

    // Serie y número tal como van en el XML, por ejemplo F001-4355
    public String numero() {
        return serie + "-" + correlativo;
    }

    public void marcarEnviando(LocalDateTime ahora) {
        cambiarA(EstadoComprobante.ENVIANDO, ahora);
    }

    // Guarda lo que contestó la SUNAT y pasa al estado que corresponde a esa respuesta
    public void registrarResultado(ResultadoEmision resultado, LocalDateTime ahora) {
        cambiarA(EstadoComprobante.desde(resultado.getClase()), ahora);
        this.codigoSunat = resultado.getCodigo();
        this.descripcionSunat = resultado.getDescripcion();
        this.cdrZip = resultado.getCdrZip();
        this.observaciones = resultado.getObservaciones() == null || resultado.getObservaciones().isEmpty() ? null
                : resultado.getObservaciones().stream().map(Comprobante::lineaDe).collect(Collectors.joining("\n"));
    }

    // La SUNAT no contestó: no se sabe si recibió el comprobante
    public void marcarErrorTemporal(String motivo, LocalDateTime ahora) {
        cambiarA(EstadoComprobante.ERROR_TEMPORAL, ahora);
        this.descripcionSunat = motivo;
    }

    private static String lineaDe(CdrResultado.Observacion o) {
        return o.getCodigo() == null ? o.getMensaje() : o.getCodigo() + " - " + o.getMensaje();
    }

    private void cambiarA(EstadoComprobante destino, LocalDateTime ahora) {
        if (!estado.puedePasarA(destino)) {
            throw new IllegalStateException("Un comprobante " + estado + " no puede pasar a " + destino);
        }
        this.estado = destino;
        this.actualizadoEn = ahora;
    }
}
