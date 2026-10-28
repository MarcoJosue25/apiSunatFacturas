package apisunat.api_sunat.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// Todo lo que hace falta para hablar con el servicio de la SUNAT. Si algo está mal, la aplicación no arranca.
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "sunat")
public class SunatProperties {

    @NotBlank(message = "La URL del servicio de la SUNAT no puede estar vacía")
    @Pattern(regexp = "^https?://\\S+$", message = "La URL del servicio de la SUNAT debe empezar con http:// o https://")
    private String url;

    @NotBlank(message = "El RUC no puede estar vacío")
    @Pattern(regexp = "^\\d{11}$", message = "El RUC debe tener exactamente 11 dígitos")
    private String ruc;

    @NotBlank(message = "El usuario SOL no puede estar vacío")
    private String usuario;

    @NotBlank(message = "La clave SOL no puede estar vacía")
    private String clave;

    // La serie con la que se numeran las facturas: 4 caracteres, empieza con F (regla 1001 de la SUNAT)
    @NotBlank(message = "La serie de las facturas no puede estar vacía")
    @Pattern(regexp = "^F[A-Z0-9]{3}$", message = "La serie de las facturas debe tener 4 caracteres, empezar con F y llevar solo mayúsculas y números")
    private String serieFactura = "F001";

    // Días calendario después del día de emisión en que todavía se puede enviar una factura. La SUNAT hoy dice 1 (preguntas
    // frecuentes) aunque páginas anteriores decían 3; pasado el plazo rechaza con el código 2108. Ver docs/ERRORES.md E-015.
    @PositiveOrZero(message = "El plazo de envío no puede ser negativo")
    @Max(value = 30, message = "El plazo de envío no puede pasar de 30 días")
    private int plazoEnvioDias = 1;

    @Positive(message = "El timeout de conexión debe ser mayor que cero")
    private int conexionTimeoutMs;

    @Positive(message = "El timeout de lectura debe ser mayor que cero")
    private int lecturaTimeoutMs;

    @Valid
    @NotNull(message = "Faltan los datos del emisor")
    private Emisor emisor = new Emisor();

    @Valid
    @NotNull(message = "Faltan los datos del certificado")
    private Certificado certificado = new Certificado();

    @Valid
    @NotNull(message = "Faltan los datos de los reintentos")
    private Reintentos reintentos = new Reintentos();

    @Valid
    @NotNull(message = "Faltan los datos de la consulta de constancias")
    private ConsultaCdr consultaCdr = new ConsultaCdr();

    // Los datos de quien emite la factura (el negocio). Lo opcional puede venir vacío.
    @Getter
    @Setter
    public static class Emisor {

        @NotBlank(message = "La razón social del emisor no puede estar vacía")
        @Size(max = 1500, message = "La razón social del emisor no puede pasar de 1500 caracteres")
        private String razonSocial;

        @Size(max = 1500, message = "El nombre comercial del emisor no puede pasar de 1500 caracteres")
        private String nombreComercial;

        @Pattern(regexp = "^(.{3,200})?$", message = "La dirección del emisor debe tener entre 3 y 200 caracteres")
        private String direccion;

        // Código del establecimiento anexo que asigna la SUNAT. El domicilio fiscal es 0000.
        @NotBlank(message = "El código de establecimiento no puede estar vacío")
        @Pattern(regexp = "^\\d{4}$", message = "El código de establecimiento debe tener 4 dígitos")
        private String codigoEstablecimiento = "0000";

        // En el archivo de configuración un dato opcional sin valor llega como texto vacío: se trata como si no existiera
        public String getNombreComercial() {
            return nombreComercial == null || nombreComercial.isBlank() ? null : nombreComercial;
        }

        public String getDireccion() {
            return direccion == null || direccion.isBlank() ? null : direccion;
        }
    }

    // El certificado digital con el que se firman las facturas (un .p12 o .pfx). La aplicación arranca sin él,
    // pero no puede firmar: el error aparece recién al intentar cargarlo.
    @Getter
    @Setter
    public static class Certificado {

        private String ruta;

        private String clave;

        // Igual que los datos opcionales del emisor: en el archivo de configuración un valor vacío llega como texto vacío
        public String getRuta() {
            return ruta == null || ruta.isBlank() ? null : ruta;
        }

        // La clave no se recorta ni se normaliza más que el vacío: un espacio puede ser parte de la clave
        public String getClave() {
            return clave == null || clave.isEmpty() ? null : clave;
        }

        // Una ruta sin clave (o al revés) es una configuración a medias: se avisa al arrancar y no al firmar
        @AssertTrue(message = "El certificado necesita la ruta y la clave juntas, o ninguna de las dos")
        public boolean isRutaYClaveCompletas() {
            return (getRuta() == null) == (getClave() == null);
        }
    }

    // El planificador que vuelve a enviar lo que quedó pendiente
    @Getter
    @Setter
    public static class Reintentos {

        // Se apaga en los tests y se puede apagar en una réplica extra
        private boolean habilitado = true;

        @Min(value = 1, message = "El intervalo de reintentos debe ser de al menos 1 segundo")
        private int intervaloSegundos = 60;

        // Cuántos comprobantes se toman en cada pasada
        @Min(value = 1, message = "El lote de reintentos debe ser de al menos 1")
        @Max(value = 500, message = "El lote de reintentos no puede pasar de 500")
        private int lote = 20;

        // Un comprobante que lleva más tiempo que este "enviándose" se da por perdido (la aplicación se cayó a mitad del envío)
        @Min(value = 1, message = "El tiempo máximo enviando debe ser de al menos 1 minuto")
        private int minutosMaximoEnviando = 10;
    }

    // Consultar a la SUNAT si ya tiene una factura antes de volver a enviarla. El servicio billConsultService existe solo en
    // producción: el beta no lo tiene (su billService solo trae getStatus, getStatusAR, sendBill, sendPack y sendSummary).
    @Getter
    @Setter
    public static class ConsultaCdr {

        private boolean habilitada = false;

        @NotBlank(message = "La URL de la consulta de constancias no puede estar vacía")
        @Pattern(regexp = "^https?://\\S+$", message = "La URL de la consulta de constancias debe empezar con http:// o https://")
        private String url = "https://e-factura.sunat.gob.pe/ol-it-wsconscpegem/billConsultService";
    }
}
