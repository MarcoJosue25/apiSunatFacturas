# Plan de trabajo

Lo hacemos despacio y por fases. Cada fase termina en algo que funciona y se puede probar, y al final de cada una paro y espero el visto bueno antes de seguir.

## Las tres reglas

1. **Cada cosa que se implementa se prueba con tests.** No se marca un paso como hecho sin sus tests en verde.
2. **No se implementa nada que no se vaya a usar ya.** Lo que se usará después se hace después. Una carpeta o clase aparece en la fase donde alguien la necesita por primera vez.
3. **Lo hacemos así para no encontrar errores inesperados más adelante.**

## Cómo trabajamos

- Los commits son cortos y en lenguaje normal ("agrego el cliente de getStatus", "valido el XML contra el XSD").
- Un paso, sus tests, un commit.
- Todo error que aparezca y toda limitación que dejemos para después se anota en [`docs/ERRORES.md`](ERRORES.md) **en el momento en que sale**, con cómo se soluciona o por qué se deja. Aunque se arregle, queda guardado ahí. Si el archivo crece mucho se crea otro con la misma estructura.
- Los commits se hacen de rato en rato, uno por paso terminado (con sus tests en verde).
- Se avanza un paso a la vez: implementar, probar, anotar y recién entonces seguir.
- Las pruebas contra el beta de la SUNAT llevan `@Tag("beta")` y no corren en `./mvnw test` normal, para que el CI no dependa de internet. Se corren a propósito.
- Los servidores simulados se hacen sin librerías extra (`MockRestServiceServer` de Spring y, para timeouts, un servidor chico del JDK). WireMock solo si algún día hace falta.
- Todo lo marcado **(verificar)** es algo que supongo pero todavía no está confirmado en la documentación o en el beta.

## Decisiones tomadas

| Tema | Decisión |
|---|---|
| groupId | `apisunat` (el identificador del proyecto en Maven, como `saasagent` en el agente) |
| artifactId | `api-sunat` |
| Paquete base | `apisunat.api_sunat` (mismo patrón del agente) |
| Java y Maven | Java 21, Maven con wrapper |
| Spring Boot | 4.1.x, la misma línea del agente |
| Paquetes | En singular: `controller`, `dto`, `model`, `repository`, `service`, `scheduler`, `config`, `exception`, `util` |
| Servicios | Interfaz en `service/` e implementación en `service/impl/` |
| Estilo | Lombok. Entidades con `@Builder`, `@Getter`, `@NoArgsConstructor(PROTECTED)` y `@AllArgsConstructor(PRIVATE)`. DTOs con `@Data`, `@Builder` y los constructores. Mensajes de validación en español |
| Base de datos | MySQL 8.4 con Flyway, como el agente. Entra en la fase 6 |
| Integración con SUNAT | Paquete aislado `sunat/` con subpaquetes, como `whatsapp` en el agente |

---

## Fase 0: Cimientos

**Objetivo:** un proyecto vacío pero sano, con el CI verde.

- [x] Revisar el esqueleto de la rama `claude/sharp-goodall-tx30hd`: qué se aprovecha y qué no. Traer lo bueno a esta rama.
- [x] `pom.xml`: parent Spring Boot 4.1.x, Java 21, solo `webmvc`, `validation`, `lombok` y los starters de test. Sin JPA ni Flyway todavía.
- [x] Clase principal `ApiSunatApplication` y `application.yml` mínimo (nombre y puerto).
- [x] Maven wrapper (`mvnw`) en el repo.
- [x] `.gitignore`: `target`, `.env`, IDE y certificados reales (`*.p12`), con excepción de los de prueba.
- [x] CI de GitHub Actions (basado en el del agente): `./mvnw -B clean test`, en cada push a cualquier rama y en pull requests.
- [ ] README: el estado apunta a este plan.

**Tests**
- [x] El contexto de Spring arranca (`contextLoads`).
- [ ] El CI corre en verde sobre el proyecto vacío.

**Listo cuando:**
- [ ] `./mvnw clean test` pasa en local y en el CI.

---

## Fase 1: Conexión con el beta

**Objetivo:** una llamada a nuestra API llega hasta el beta de la SUNAT y vuelve ordenada. Es lo primero que se conecta, antes de tocar facturas.

Recorrido: `SunatController` → `SunatService` → `BillServiceClient` → SUNAT beta.

**Clases que aparecen:** `config/SunatProperties`, `sunat/soap/SoapEnvelopeBuilder`, `sunat/soap/SoapResponseParser`, `sunat/soap/BillServiceClient`, `service/SunatService` + `service/impl/SunatServiceImpl`, `controller/SunatController`, `dto/EstadoTicketResponse`, `exception/SunatNoDisponibleException`, `exception/SunatRespuestaInvalidaException`, `exception/GlobalExceptionHandler`.

- [ ] `SunatProperties` con `@ConfigurationProperties("sunat")`: url del servicio, ruc, usuario, clave y timeouts. Valida al arrancar y falla rápido si falta algo.
- [ ] Credenciales por variables de entorno (`SUNAT_RUC`, `SUNAT_USUARIO`, `SUNAT_CLAVE`) y un `.env.example`. Por defecto apunta al beta con usuario `MODDATOS`.
- [ ] `SoapEnvelopeBuilder`: sobre SOAP con la cabecera WS-Security UsernameToken (`RUC` + `usuario` juntos como username) y el cuerpo de `getStatus`. Escapa los caracteres especiales de XML.
- [ ] `SoapResponseParser`: lee `statusCode` y `content` de `getStatusResponse`, y también el SOAP Fault.
- [ ] `BillServiceClient` con `RestClient`: POST con `SOAPAction: urn:getStatus`, timeout de conexión y de lectura.
- [ ] `SunatService.consultarTicket(ticket)`.
- [ ] `GET /api/v1/sunat/tickets/{ticket}`: devuelve el `statusCode` y el `content` tal cual, sin interpretarlos todavía.
- [ ] `GlobalExceptionHandler`: 502 si la SUNAT falla, 504 si no responde a tiempo, 400 si el ticket viene vacío y 500 genérico sin filtrar detalles.
- [ ] Prueba manual con `curl` contra el beta: debe devolver `0127 El ticket no existe`.

**Tests**
- [ ] Propiedades: el arranque falla si falta el RUC, la URL es inválida o un timeout es 0 o negativo.
- [ ] Sobre SOAP: XML exacto esperado, namespaces correctos, RUC y usuario concatenados, y escape de `& < > " '` en credenciales y ticket.
- [ ] Parser: la respuesta real `0127` (guardada como archivo de prueba), `statusCode` 0, 98 y 99 con y sin `content`, SOAP Fault, XML vacío, XML que no es SOAP, `statusCode` ausente y tildes en UTF-8.
- [ ] Cliente con servidor simulado: cabeceras y cuerpo correctos, 200, 500, 503, timeout de lectura, conexión rechazada, respuesta HTML de un proxy y respuesta enorme.
- [ ] Servicio con Mockito: delega bien y traduce las excepciones.
- [ ] Controller (`@WebMvcTest`): 200, ticket en blanco 400, SUNAT caída 502, timeout 504, y la respuesta no deja ver stacktrace ni credenciales.
- [ ] Las credenciales no aparecen en los logs.
- [ ] Prueba real `@Tag("beta")`: un ticket inexistente devuelve `0127`.

**Listo cuando:**
- [ ] Hago `curl` a mi API y veo la respuesta real del beta.

---

## Fase 2: Los números

**Objetivo:** cálculos exactos y blindados. Una diferencia de centavos hace que la SUNAT rechace la factura y se pierda el número, así que esta fase es de las más pesadas en tests.

**Clases que aparecen:** `util/MontoUtil`, `util/MontoEnLetras`, `util/NumeracionUtil`, y una calculadora de líneas y totales.

- [ ] Leer las reglas de cálculo (guía XML de factura 2.1, hoja `Factura2_0` de las Reglas de validación, y los códigos 3270 a 3294) y anotar las fórmulas y tolerancias en `docs/sunat/CALCULOS.md`. Sin esto no se programa nada.
- [ ] Calculadora de línea: valor unitario, precio unitario (con IGV), valor de venta del ítem e IGV de la línea. Solo operación gravada (afectación 10) al principio.
- [ ] Totales del documento: suma de valores de venta, IGV total e importe total.
- [ ] Redondeo a 2 decimales y precisión del valor unitario, según lo anotado.
- [ ] Monto en letras para la leyenda 1000 ("CUATROCIENTOS VEINTITRÉS Y 00/100 SOLES").
- [ ] Serie y correlativo: formato `F001-00000001` y validación del formato.
- [ ] `Clock` inyectable y fecha y hora de Lima.

**Tests**
- [ ] Redondeo: 0.005, 0.015, 0.004999, 0.01, montos grandes y los límites.
- [ ] Líneas: cantidades con decimales, precios con muchos decimales, descuentos y cantidad cero (rechazada).
- [ ] Coherencia: el total siempre es la suma de las líneas y el IGV de la línea cuadra con su base. Con muchos casos generados con semilla fija.
- [ ] Ida y vuelta: del precio con IGV al valor unitario y de regreso no pierde centavos.
- [ ] Monto en letras: 0, 1, 15, 21, 100 ("CIEN"), 101, 1000, 1001, 21000, 1000000 ("UN MILLÓN"), centavos, y negativos rechazados.
- [ ] Serie y correlativo: `F001`, `F999`, `B001`, serie inválida, correlativo 0 o de más de 8 dígitos.
- [ ] Fecha: el reloj controlado devuelve la hora de Lima aunque el servidor esté en otra zona.

**Listo cuando:**
- [ ] Los cálculos son puros, no dependen de nada externo y tienen sus casos límite cubiertos.

---

## Fase 3: XML de factura

**Objetivo:** generar una factura válida (sin firmar) en UBL 2.1.

**Clases que aparecen:** `dto/FacturaRequest` (+ `ItemRequest`, `ClienteRequest`), `sunat/xml/FacturaXmlBuilder`, `sunat/xml/XsdValidator`, y los enums de catálogo que se usen.

- [ ] Extraer los XSD 2.1 (`docs/sunat/Archivos XSD (1).zip`) a `src/main/resources/xsd/`, con una nota de qué versión son.
- [ ] `XsdValidator`: valida un XML contra los XSD y devuelve los errores legibles.
- [ ] Datos del emisor en `sunat.emisor.*` (razón social, nombre comercial, ubigeo y dirección).
- [ ] DTO de entrada de la factura (se reutiliza en la fase 6 para el endpoint).
- [ ] `FacturaXmlBuilder` con el DOM del JDK: versión UBL, tipo de documento 01, moneda PEN, leyenda 1000, emisor, cliente con RUC, forma de pago contado, ítems, impuestos, totales, `cac:Signature` y un `ext:UBLExtension` vacío para la firma.
- [ ] Codificación ISO-8859-1 declarada y real.
- [ ] Validación de coherencia antes de generar: los totales cuadran con las líneas.
- [ ] Solo los catálogos que se usan (tipo de documento de identidad, unidad de medida, afectación del IGV).
- [ ] Variantes, cada una como sub-paso y solo si decides que las necesitas: exonerado, inafecto, gratuito, descuento global, pago a crédito y dólares.

**Tests**
- [ ] El XML generado pasa el XSD.
- [ ] Comparación contra el ejemplo de la guía de la SUNAT.
- [ ] Cada campo obligatorio ausente falla con un mensaje claro.
- [ ] Caracteres especiales: ñ, tildes, `&`, `<`, comillas.
- [ ] Caracteres que no existen en ISO-8859-1 (emoji, guion largo): se define qué se hace y se prueba.
- [ ] 1, 100 y 1000 líneas.
- [ ] Cliente sin RUC en una factura: rechazado antes de llegar a la SUNAT.
- [ ] RUC del cliente con longitud o dígito verificador inválido.
- [ ] Totales que no cuadran: rechazados localmente.
- [ ] El nombre del archivo (`RUC-01-F001-1`) coincide con el contenido del XML.

**Listo cuando:**
- [ ] Genero una factura que pasa el XSD y cuyos totales son consistentes.

---

## Fase 4: Firma digital

**Objetivo:** firmar el XML como exige la SUNAT.

**Clases que aparecen:** `sunat/firma/CertificadoLoader`, `sunat/firma/XmlSigner`, y las propiedades del certificado.

- [ ] Certificados de prueba generados con `keytool` y guardados en `src/test/resources`: uno válido, uno vencido y uno con otro RUC. Documentar el comando.
- [ ] Propiedades `sunat.certificado.ruta` y `sunat.certificado.clave`.
- [ ] `CertificadoLoader`: lee el `.p12`, valida la vigencia y obtiene la clave privada y la cadena.
- [ ] `XmlSigner`: XMLDSig dentro de `ext:UBLExtensions/ext:UBLExtension/ext:ExtensionContent`, sobre todo el documento.
- [ ] Algoritmo de firma: el JDK 21 desactiva SHA-1 por defecto, así que se prueba en el beta si acepta SHA-256 **(verificar)**.
- [ ] El verificador de firmas vive solo en los tests, porque en producción no se usa.

**Tests**
- [ ] La firma verifica correctamente.
- [ ] Cambiar un solo carácter después de firmar rompe la verificación.
- [ ] La firma queda en la posición correcta y hay un único `UBLExtension` con firma.
- [ ] Con ñ y tildes la firma verifica y respeta ISO-8859-1.
- [ ] El XML firmado sigue pasando el XSD.
- [ ] La firma no altera el contenido del documento.
- [ ] Clave incorrecta, certificado vencido, `.p12` corrupto y archivo inexistente fallan con mensajes claros.
- [ ] Un XML sin el espacio para la firma da un error claro.
- [ ] El certificado con otro RUC se detecta.

**Listo cuando:**
- [ ] La factura sale firmada, verificable y válida contra el XSD.

---

## Fase 5: Enviar la factura al beta

**Objetivo:** primera factura aceptada por el beta de la SUNAT.

**Clases que aparecen:** `util/ZipUtil`, `sunat/cdr/CdrParser`, `sunat/cdr/CdrResultado`, `sunat/errores/CatalogoCodigos`, `sunat/errores/ClasificadorErrores`, `model/enums/ClaseError`, y un servicio que arma el recorrido completo.

- [ ] Nombre del archivo `RUC-01-SERIE-NUMERO` y ZIP con el XML adentro. Probar si el beta pide la carpeta `dummy` **(verificar)**.
- [ ] `sendBill` en el cliente SOAP, con el ZIP en base64. Probar si hace falta MTOM **(verificar)**.
- [ ] `CdrParser`: descomprime la respuesta y lee el `ApplicationResponse` (código, descripción y observaciones).
- [ ] Mover `codigos_sunat.csv` a `src/main/resources` y cargarlo al arrancar.
- [ ] `ClasificadorErrores` con las clases: aceptado, aceptado con observaciones, rechazo, error de datos, error de configuración y transitorio. Las reglas salen de `docs/sunat/NOTAS.md`.
- [ ] Un código desconocido nunca se reintenta.
- [ ] Servicio que arma el recorrido: calcular, XML, XSD, firmar, ZIP, enviar, leer el CDR, clasificar. Todavía sin base de datos.

**Tests**
- [ ] ZIP: contiene exactamente un XML y los nombres coinciden.
- [ ] CDR de ejemplo: aceptado, rechazado y con observaciones.
- [ ] SOAP Fault con `0111`, `0151` y `0306`.
- [ ] Cada regla de clasificación de las notas (por ejemplo `0100` y `0109` transitorios, `0306` no).
- [ ] Los 869 códigos del CSV reciben una clase sin fallar.
- [ ] Código desconocido: no se reintenta.
- [ ] Timeout o 500: transitorio.
- [ ] CDR corrupto o ZIP de respuesta vacío.
- [ ] Prueba real `@Tag("beta")`: factura válida aceptada.
- [ ] Prueba real: total mal calculado, para ver que lo que atrapa nuestro validador local es lo mismo que rechaza la SUNAT.
- [ ] Prueba real: reenviar la misma factura. Anotar si devuelve el mismo CDR o el error `1033`.
- [ ] Prueba real: una boleta por `sendBill`. Esperado `1079`.

**Listo cuando:**
- [ ] El beta acepta una factura firmada por mí.

---

## Fase 6: Guardar y exponer por REST

**Objetivo:** que mi agente pueda pedir una factura por REST y consultar su estado.

**Clases que aparecen:** `model/Comprobante`, `model/Serie`, `model/EnvioIntento`, `model/enums/EstadoComprobante`, repositorios, `service/SerieService`, `service/ComprobanteService` (+ `impl`), `controller/FacturaController`, DTOs de respuesta.

- [ ] MySQL 8.4 y Flyway: dependencias, `docker-compose.yml` y servicio de MySQL en el CI (como el agente).
- [ ] Migración V1: `series`, `comprobantes` y `envio_intentos`. Se guarda el XML firmado y el CDR; los ítems viven dentro del XML.
- [ ] Entidades con el estilo del agente, `@Version` y cambios de estado dentro de la propia entidad: pendiente, enviando, aceptado, aceptado con observaciones, rechazado, error de datos, error de configuración, error temporal y vencido.
- [ ] Repositorios.
- [ ] `SerieService`: siguiente correlativo atómico, sin saltos ni repetidos.
- [ ] Recorrido: reservar el número, guardar pendiente, generar y firmar, guardar el XML, enviar, guardar el resultado. Un rechazo consume el número. Un error temporal deja el comprobante pendiente para la fase 7.
- [ ] `POST /api/v1/facturas` con el DTO validado y `GET /api/v1/facturas/{id}` con el estado.
- [ ] Un `idExterno` único del agente para que si reintenta una petición no se duplique la factura.
- [ ] `GET /api/v1/facturas/{id}/xml`, porque el cliente debe poder descargar su comprobante **(decidir)**.

**Tests**
- [ ] Las migraciones aplican sobre un MySQL limpio.
- [ ] Correlativo con 50 hilos a la vez: sin saltos ni repetidos.
- [ ] Un rechazo consume el número.
- [ ] Transiciones de estado permitidas y prohibidas, en una prueba parametrizada.
- [ ] Validación del POST: campos faltantes, RUC mal, monto 0 e ítems vacíos.
- [ ] Idempotencia: el mismo `idExterno` dos veces, y también a la vez, da una sola factura.
- [ ] GET de un id inexistente da 404.
- [ ] Flujo completo con SUNAT simulada: aceptada, rechazada y caída.
- [ ] La respuesta no expone credenciales ni detalles internos.
- [ ] Prueba real `@Tag("beta")` de punta a punta.

**Listo cuando:**
- [ ] Hago un `POST` y la factura queda aceptada y guardada.

---

## Fase 7: Reintentos

**Objetivo:** que una caída de la SUNAT no pierda comprobantes.

**Clases que aparecen:** `scheduler/ReintentoScheduler`, una política de backoff y la consulta `getStatusCdr` en el cliente.

- [ ] Plazo de envío: fecha de emisión más 1 día calendario, configurable en `sunat.plazo-envio-dias` **(verificar si son 1 o 3 días)**.
- [ ] Política de backoff (1, 2, 5, 15, 30 y 60 minutos, con tope).
- [ ] `ReintentoScheduler` con `@Scheduled`: toma los pendientes cuyo próximo intento ya llegó.
- [ ] Antes de reenviar, consultar `getStatusCdr` para ver si la SUNAT ya la recibió. Revisar si el servicio existe en el beta **(verificar)**.
- [ ] Pasar a `VENCIDO` al cumplirse el plazo y dejar un aviso en los logs.
- [ ] Que dos procesos no tomen el mismo pendiente.
- [ ] Configuración del intervalo y apagado del scheduler en los tests, como en el agente.
- [ ] Guardar cada intento con su código y fecha.

**Tests**
- [ ] Con reloj controlado, sin esperar tiempo real.
- [ ] Secuencia falla, falla, funciona.
- [ ] No reintenta error de datos, rechazo ni error de configuración.
- [ ] Pasa a vencido al cumplirse el plazo, y no antes.
- [ ] Backoff: valores exactos y tope.
- [ ] `getStatusCdr` dice que ya está aceptada: no se reenvía y se guarda el CDR.
- [ ] Dos hilos no envían el mismo comprobante dos veces.
- [ ] Sobrevive a un reinicio, porque el estado está en la base.
- [ ] Cada intento queda registrado.

**Listo cuando:**
- [ ] Apago la conexión a la SUNAT, emito, la restauro y el comprobante se envía solo.

---

## Fase 8: Boletas y resumen diario

**Objetivo:** boletas informadas a la SUNAT por resumen diario.

**Clases que aparecen:** `dto/BoletaRequest`, `sunat/xml/BoletaXmlBuilder`, `sunat/xml/ResumenDiarioXmlBuilder`, `service/ResumenDiarioService`, `scheduler/ResumenDiarioScheduler`, `scheduler/ConsultaTicketScheduler`, `model/ResumenDiario`, `controller/BoletaController`.

- [ ] Confirmar en el beta que una boleta por `sendBill` da `1079`.
- [ ] XML de boleta reutilizando lo de la factura. Cliente anónimo en las boletas de poco monto y documento obligatorio por encima de S/ 700 **(verificar)**.
- [ ] XML del Resumen Diario (`SummaryDocuments`, guía de resumen 2.0), firmado, con una línea por boleta.
- [ ] Bloques de máximo 500 líneas, cada uno con su correlativo `RC-AAAAMMDD-N`.
- [ ] `sendSummary` y guardar el ticket.
- [ ] Consulta del ticket con `getStatus`: 0 terminó, 98 en proceso (volver a consultar), 99 con errores.
- [ ] `ResumenDiarioScheduler`: a una hora configurable arma el resumen de las boletas del día.
- [ ] Estados de la boleta: pendiente de resumen, en resumen, aceptada y rechazada.
- [ ] `POST /api/v1/boletas` y `GET /api/v1/boletas/{id}`.
- [ ] Un resumen rechazado se corrige y se reenvía con el mismo nombre.

**Tests**
- [ ] `sendBill` con una boleta devuelve el esperado `1079`.
- [ ] Resumen con 499, 500, 501 y 1000 líneas.
- [ ] Resumen sin boletas: no se envía nada.
- [ ] Boletas de días distintos no se mezclan en el mismo resumen.
- [ ] Ticket en proceso (98) que luego termina (0).
- [ ] Resumen rechazado y reenvío con el mismo nombre.
- [ ] Boleta sobre el monto límite sin documento: rechazada localmente.
- [ ] Las observaciones y rechazos del CDR del resumen se asignan a la boleta correcta.
- [ ] Prueba real `@Tag("beta")`: boleta y resumen aceptados.

**Listo cuando:**
- [ ] Las boletas se informan solas cada día y quedan aceptadas en el beta.

---

## Fase 9: Producción

**Objetivo:** apuntarlo a la SUNAT real con mi certificado, con seguridad básica.

**Clases que aparecen:** un filtro de autenticación por llave de API, la configuración de perfiles, y un `Dockerfile`.

- [ ] Perfiles `beta` y `prod`. En `prod` las URLs de producción son obligatorias, y no arranca si apunta al beta o si usa un certificado de prueba.
- [ ] Autenticación de la API con una llave en una cabecera, comparada en tiempo constante. Sin llave, 401.
- [ ] Secretos solo por variables de entorno, sin que aparezcan en logs ni en las respuestas de error.
- [ ] Certificado real: pedir el Certificado Digital Tributario gratuito y registrarlo en SOL. Pasos manuales documentados en `docs/`.
- [ ] Usuario secundario de SOL para el envío. Pasos documentados.
- [ ] `Dockerfile` liviano (multi-stage con JRE) y `docker-compose` con MySQL.
- [ ] Logs útiles (idExterno, id del comprobante y código de la SUNAT) sin datos sensibles.
- [ ] Primera emisión real de prueba, controlada: monto bajo y un cliente propio.
- [ ] Respaldo de los XML y CDR, porque hay que conservarlos.

**Tests**
- [ ] Sin llave y con llave incorrecta: 401. Con llave correcta: 200.
- [ ] El perfil `prod` no arranca con configuración del beta o con un certificado de prueba.
- [ ] Los secretos no aparecen en logs ni en los errores.
- [ ] La imagen de Docker arranca (prueba de humo manual).

**Listo cuando:**
- [ ] Una factura real, controlada, queda aceptada por la SUNAT.

---

## Opcional: notas de crédito, notas de débito y baja

Se necesitan si algún día hay que corregir o anular un comprobante. No se construyen hasta que lo pidas.

- [ ] Decidir si se hace y en qué orden.

---

## Cosas por confirmar en el camino

- [ ] Algoritmo de firma: SHA-1 o SHA-256 (fase 4).
- [ ] Si el ZIP necesita la carpeta `dummy` y si hace falta MTOM (fase 5).
- [ ] Qué responde la SUNAT al reenviar un comprobante ya aceptado (fase 5).
- [ ] Plazo de envío de facturas: 1 día o 3 días (fase 7).
- [ ] Si `getStatusCdr` existe en el beta (fase 7).
- [ ] Plazo de envío del resumen diario de boletas (fase 8).
- [ ] Si el beta acepta mi RUC 10 y mi régimen permite el certificado gratuito (fase 9).
