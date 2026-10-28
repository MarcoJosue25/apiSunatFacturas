package apisunat.api_sunat.model;

import apisunat.api_sunat.model.enums.EstadoResumen;
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

import java.time.LocalDate;
import java.time.LocalDateTime;

// Un resumen diario de boletas: junta hasta 500 boletas de una misma fecha de emisión y se informa a la SUNAT con un ticket.
// Los cambios de estado se hacen solo con los métodos de esta clase.
@Entity
@Table(name = "resumenes_diarios")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ResumenDiario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // RC-AAAAMMDD-N, con la fecha de emisión de las boletas; el archivo se llama RUC-RC-AAAAMMDD-N
    @Column(nullable = false, length = 20, updatable = false)
    private String nombre;

    @Column(name = "fecha_referencia", nullable = false, updatable = false)
    private LocalDate fechaReferencia;

    @Column(nullable = false, updatable = false)
    private int correlativo;

    @Column(name = "fecha_generacion", nullable = false, updatable = false)
    private LocalDate fechaGeneracion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoResumen estado;

    @Column(length = 30)
    private String ticket;

    @Column(name = "codigo_sunat", length = 10)
    private String codigoSunat;

    @Column(name = "descripcion_sunat", length = 2000)
    private String descripcionSunat;

    @JdbcTypeCode(SqlTypes.LONGVARBINARY)
    @Column(name = "xml_firmado", updatable = false)
    private byte[] xmlFirmado;

    @JdbcTypeCode(SqlTypes.LONGVARBINARY)
    @Column(name = "cdr_zip")
    private byte[] cdrZip;

    @Column(name = "intentos_fallidos", nullable = false)
    private int intentosFallidos;

    @Column(name = "proximo_intento")
    private LocalDateTime proximoIntento;

    @Version
    private Long version;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    public static ResumenDiario nuevo(String nombre, LocalDate fechaReferencia, int correlativo, LocalDate fechaGeneracion, byte[] xmlFirmado,
                                      LocalDateTime ahora) {
        return ResumenDiario.builder().nombre(nombre).fechaReferencia(fechaReferencia).correlativo(correlativo)
                .fechaGeneracion(fechaGeneracion).xmlFirmado(xmlFirmado).estado(EstadoResumen.GENERADO)
                .proximoIntento(ahora).creadoEn(ahora).actualizadoEn(ahora).build();
    }

    // El archivo que se manda a la SUNAT: RUC-RC-AAAAMMDD-N
    public String nombreArchivo(String ruc) {
        return ruc + "-" + nombre;
    }

    // La SUNAT recibió el resumen y dio un ticket; la primera consulta queda programada
    public void marcarEnviado(String ticket, LocalDateTime ahora, LocalDateTime primeraConsulta) {
        cambiarA(EstadoResumen.ENVIADO, ahora);
        this.ticket = ticket;
        this.intentosFallidos = 0;
        this.proximoIntento = primeraConsulta;
        this.codigoSunat = null;
        this.descripcionSunat = null;
    }

    // No se pudo enviar: se reintenta en la fecha indicada. Se puede repetir mientras siga sin enviarse.
    public void marcarErrorEnvio(String motivo, LocalDateTime ahora, LocalDateTime proximoIntento) {
        if (estado != EstadoResumen.ERROR_TEMPORAL) {
            cambiarA(EstadoResumen.ERROR_TEMPORAL, ahora);
        }
        this.descripcionSunat = motivo;
        this.intentosFallidos++;
        this.proximoIntento = proximoIntento;
        this.actualizadoEn = ahora;
    }

    // Todavía no termina o no se pudo consultar: se vuelve a preguntar más tarde
    public void posponerConsulta(LocalDateTime proximaConsulta, LocalDateTime ahora) {
        if (estado != EstadoResumen.ENVIADO) {
            throw new IllegalStateException("Un resumen " + estado + " no se consulta");
        }
        this.intentosFallidos++;
        this.proximoIntento = proximaConsulta;
        this.actualizadoEn = ahora;
    }

    // La SUNAT terminó de procesar el resumen
    public void registrarResultado(EstadoResumen destino, String codigo, String descripcion, byte[] cdrZip, LocalDateTime ahora) {
        if (destino != EstadoResumen.ACEPTADO && destino != EstadoResumen.ACEPTADO_CON_OBSERVACIONES && destino != EstadoResumen.RECHAZADO
                && destino != EstadoResumen.REVISAR) {
            throw new IllegalArgumentException("Un resultado de la SUNAT no puede ser " + destino);
        }
        cambiarA(destino, ahora);
        this.codigoSunat = codigo;
        this.descripcionSunat = descripcion;
        this.cdrZip = cdrZip;
        this.proximoIntento = null;
    }

    // Un resumen rechazado se vuelve a enviar con el mismo nombre y el mismo contenido
    public void prepararReenvio(LocalDateTime ahora) {
        cambiarA(EstadoResumen.GENERADO, ahora);
        this.ticket = null;
        this.intentosFallidos = 0;
        this.proximoIntento = ahora;
    }

    private void cambiarA(EstadoResumen destino, LocalDateTime ahora) {
        if (!estado.puedePasarA(destino)) {
            throw new IllegalStateException("Un resumen " + estado + " no puede pasar a " + destino);
        }
        this.estado = destino;
        this.actualizadoEn = ahora;
    }
}
