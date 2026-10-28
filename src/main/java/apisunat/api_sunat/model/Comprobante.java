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
    public static final String TIPO_BOLETA = "03";

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

    // El RUC de una factura; en una boleta, el documento del cliente ("-" si es anónimo)
    @Column(name = "documento_cliente", nullable = false, length = 20, updatable = false)
    private String documentoCliente;

    // Catálogo 06 de la SUNAT: 6 RUC, 1 DNI, 4 carné, 7 pasaporte, 0 sin documento
    @Column(name = "tipo_documento_cliente", nullable = false, length = 1, updatable = false)
    private String tipoDocumentoCliente;

    @Column(name = "razon_social_cliente", nullable = false, length = 1500, updatable = false)
    private String razonSocialCliente;

    @Column(name = "importe_total", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal importeTotal;

    @Column(name = "total_gravado", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal totalGravado;

    @Column(name = "total_igv", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal totalIgv;

    // Solo boletas: el resumen diario que la informó
    @Column(name = "resumen_id")
    private Long resumenId;

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

    // Cuántas veces falló el envío sin una respuesta definitiva de la SUNAT
    @Column(name = "intentos_fallidos", nullable = false)
    private int intentosFallidos;

    // Cuándo toca volver a intentar (solo mientras está PENDIENTE o ERROR_TEMPORAL)
    @Column(name = "proximo_intento")
    private LocalDateTime proximoIntento;

    @Version
    private Long version;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    // Una factura nueva: ya tiene su número y su XML firmado, y espera ser enviada
    public static Comprobante nuevo(String idExterno, String serie, long correlativo, LocalDateTime fechaEmision, String rucCliente,
                                    String razonSocialCliente, BigDecimal totalGravado, BigDecimal totalIgv, BigDecimal importeTotal,
                                    byte[] xmlFirmado, LocalDateTime ahora, LocalDateTime primerIntento) {
        return Comprobante.builder()
                .idExterno(idExterno).tipoDocumento(TIPO_FACTURA).serie(serie).correlativo(correlativo)
                .fechaEmision(fechaEmision).documentoCliente(rucCliente).tipoDocumentoCliente("6").razonSocialCliente(razonSocialCliente)
                .totalGravado(totalGravado).totalIgv(totalIgv).importeTotal(importeTotal).xmlFirmado(xmlFirmado)
                .estado(EstadoComprobante.PENDIENTE).proximoIntento(primerIntento).creadoEn(ahora).actualizadoEn(ahora).build();
    }

    // Una boleta nueva: ya tiene su número y su XML firmado, y espera a que el resumen diario la informe
    public static Comprobante nuevaBoleta(String idExterno, String serie, long correlativo, LocalDateTime fechaEmision, String tipoDocumentoCliente,
                                          String documentoCliente, String nombreCliente, BigDecimal totalGravado, BigDecimal totalIgv,
                                          BigDecimal importeTotal, byte[] xmlFirmado, LocalDateTime ahora) {
        return Comprobante.builder()
                .idExterno(idExterno).tipoDocumento(TIPO_BOLETA).serie(serie).correlativo(correlativo)
                .fechaEmision(fechaEmision).documentoCliente(documentoCliente).tipoDocumentoCliente(tipoDocumentoCliente)
                .razonSocialCliente(nombreCliente).totalGravado(totalGravado).totalIgv(totalIgv).importeTotal(importeTotal)
                .xmlFirmado(xmlFirmado).estado(EstadoComprobante.PENDIENTE_DE_RESUMEN).creadoEn(ahora).actualizadoEn(ahora).build();
    }

    public boolean esBoleta() {
        return TIPO_BOLETA.equals(tipoDocumento);
    }

    // La boleta entra a un resumen diario
    public void incluirEnResumen(Long resumenId, LocalDateTime ahora) {
        if (!esBoleta()) {
            throw new IllegalStateException("Solo las boletas van en un resumen diario");
        }
        cambiarA(EstadoComprobante.EN_RESUMEN, ahora);
        this.resumenId = resumenId;
    }

    // La SUNAT aceptó el resumen: la boleta queda aceptada, con las observaciones que le correspondan
    public void aceptarPorResumen(String codigo, String descripcion, String observaciones, LocalDateTime ahora) {
        cambiarA(observaciones == null ? EstadoComprobante.ACEPTADO : EstadoComprobante.ACEPTADO_CON_OBSERVACIONES, ahora);
        this.codigoSunat = codigo;
        this.descripcionSunat = descripcion;
        this.observaciones = observaciones;
    }

    // La SUNAT rechazó el resumen y nombró a esta boleta: se anota qué dijo, pero la boleta sigue en el resumen hasta que se corrija y reenvíe
    public void anotarRespuestaDelResumen(String codigo, String descripcion, LocalDateTime ahora) {
        if (estado != EstadoComprobante.EN_RESUMEN) {
            throw new IllegalStateException("Un comprobante " + estado + " no está en un resumen");
        }
        this.codigoSunat = codigo;
        this.descripcionSunat = descripcion;
        this.actualizadoEn = ahora;
    }

    // Serie y número tal como van en el XML, por ejemplo F001-4355
    public String numero() {
        return serie + "-" + correlativo;
    }

    public void marcarEnviando(LocalDateTime ahora) {
        cambiarA(EstadoComprobante.ENVIANDO, ahora);
    }

    // Guarda lo que contestó la SUNAT y pasa al estado que corresponde a esa respuesta
    // Si la respuesta es una falla pasajera (ERROR_TEMPORAL), queda programado el próximo intento para esa fecha
    public void registrarResultado(ResultadoEmision resultado, LocalDateTime ahora, LocalDateTime proximoIntentoSiFallaPasajera) {
        cambiarA(EstadoComprobante.desde(resultado.getClase()), ahora);
        if (estado == EstadoComprobante.ERROR_TEMPORAL) {
            programarReintento(proximoIntentoSiFallaPasajera);
        } else {
            this.proximoIntento = null;
        }
        this.codigoSunat = resultado.getCodigo();
        this.descripcionSunat = resultado.getDescripcion();
        this.cdrZip = resultado.getCdrZip();
        this.observaciones = resultado.getObservaciones() == null || resultado.getObservaciones().isEmpty() ? null
                : resultado.getObservaciones().stream().map(Comprobante::lineaDe).collect(Collectors.joining("\n"));
    }

    // La SUNAT no contestó: no se sabe si recibió el comprobante
    public void marcarErrorTemporal(String motivo, LocalDateTime ahora, LocalDateTime proximoIntento) {
        cambiarA(EstadoComprobante.ERROR_TEMPORAL, ahora);
        this.descripcionSunat = motivo;
        programarReintento(proximoIntento);
    }

    // Pasó el plazo de envío: ya no se intenta más
    public void marcarVencido(LocalDateTime ahora) {
        cambiarA(EstadoComprobante.VENCIDO, ahora);
        this.proximoIntento = null;
    }

    // Todavía no toca enviar (por ejemplo la SUNAT no pudo ni contestar la consulta): se deja para más tarde sin contar como fallo
    public void posponer(LocalDateTime proximoIntento, LocalDateTime ahora) {
        if (estado != EstadoComprobante.PENDIENTE && estado != EstadoComprobante.ERROR_TEMPORAL) {
            throw new IllegalStateException("Un comprobante " + estado + " no se puede posponer");
        }
        this.proximoIntento = proximoIntento;
        this.actualizadoEn = ahora;
    }

    private void programarReintento(LocalDateTime proximo) {
        if (proximo == null) {
            throw new IllegalArgumentException("Falta la fecha del próximo intento");
        }
        this.intentosFallidos++;
        this.proximoIntento = proximo;
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
