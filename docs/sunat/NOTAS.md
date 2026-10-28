# Notas SUNAT: emisión electrónica desde sistema propio (SEE del Contribuyente)

Fuentes leídas: Manual del Programador v2.1 (mayo 2021, págs. 1-34 de 54),
pautas del servicio beta, y las páginas de cpe.sunat.gob.pe: certificado digital,
SEE del Contribuyente, contingencia, preguntas frecuentes y guías y manuales.

Todo lo marcado como **[VERIFICAR]** no está confirmado en un documento oficial
o hay fuentes que se contradicen. No darlo por cierto hasta probarlo en el beta.

## 1. Flujo según tipo de documento

| Documento | Método SOAP | Respuesta |
|---|---|---|
| Factura, nota de crédito, nota de débito | `sendBill` (síncrono) | ZIP con el CDR al instante |
| Boletas y sus notas | `sendSummary` con **Resumen Diario (RC)** (asíncrono) | ticket; luego `getStatus(ticket)` |
| Comunicación de baja (RA) | `sendSummary` (asíncrono) | ticket; luego `getStatus(ticket)` |
| Lotes (hasta 500 XML) | `sendPack` | ticket; luego `getStatus(ticket)` |
| Recuperar CDR de factura/nota | `getStatusCdr` (servicio de consulta) | CDR; solo tipos 01, 07 y 08 |

- Las boletas van por Resumen Diario, confirmado en la página oficial SEE del Contribuyente.
  El RC se envía en bloques de máximo 500 líneas, cada bloque con su correlativo.
  **[VERIFICAR]** en el beta si `sendBill` también acepta una boleta individual.
- El CDR de SUNAT es un XML ApplicationResponse, viene firmado por SUNAT en un ZIP
  y se llama `R-<nombre del archivo enviado sin extensión>.xml`.

## 2. Endpoints

- Beta (solo pruebas de estructura XML, sin pruebas de estrés):
  `https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService?wsdl`
- Producción: `https://e-factura.sunat.gob.pe/ol-ti-itcpfegem/billService?wsdl`
- Consulta de CDR y estado (producción): `https://e-factura.sunat.gob.pe/ol-it-wsconscpegem/billConsultService?wsdl`
- Validez de facturas (producción): `https://e-factura.sunat.gob.pe/ol-it-wsconsvalidcpe/billValidService?wsdl`

## 3. Autenticación

- WS-Security UsernameToken sobre SSL.
- Username = `RUC` + `usuario SOL` concatenados (por ejemplo `20100066603MODDATOS`). Password = clave SOL.
- Beta: usuario `[RUC]MODDATOS`, clave `MODDATOS`. No requiere certificado registrado en SUNAT.
- Producción: usar un **usuario secundario** de SOL, nunca la clave principal.
  El manual (2021) pide el perfil "Envío de documentos electrónicos". **[VERIFICAR]** el perfil actual.

## 4. Archivos, firma y formato

- Cada ZIP contiene un único XML (el manual menciona una carpeta `dummy` vacía) con el mismo nombre
  que el ZIP. Nombre del XML: `RUC-TT-SERIE-CORRELATIVO.xml`.
  - TT: 01 factura, 03 boleta, 07 nota de crédito, 08 nota de débito.
  - Serie: `F###` para facturas y `B###` para boletas.
  - Correlativo: de 1 a 8 dígitos.
- Resumen diario: `RUC-RC-YYYYMMDD-N`. Baja: `RUC-RA-YYYYMMDD-N`.
- XML en **ISO-8859-1**: `<?xml version="1.0" encoding="ISO-8859-1" standalone="no"?>`.
  La firma debe generarse con la misma codificación.
- Firma XMLDSig sobre el documento completo, dentro de
  `ext:UBLExtensions/ext:UBLExtension/ext:ExtensionContent`. Es el último UBLExtension, y el XML
  debe incluir ya el `cac:Signature` antes de firmar. No se puede modificar nada después de firmar.
- Certificado X.509 v3, clave privada de 1024 bits como mínimo, con el RUC en el campo OU del Subject.
  En producción hay que comunicarlo a SUNAT desde SOL ("Actualización de certificado digital"),
  y debe estar vigente y no revocado.
- El manual dice UBL 2.0, pero las guías y XSD vigentes son **UBL 2.1**. Seguir las guías 2.1.
- Validar siempre el XML contra el XSD antes de enviarlo.

## 5. Clasificación de errores (manual §4.1)

| Código | Tipo | Qué significa | Qué hacer |
|---|---|---|---|
| 0100 a 0999 | Excepción (el documento no se procesó) | Mezcla fallas de SUNAT y errores de configuración o del archivo | Solo 0100 y 0109 son transitorios; el resto no se reintenta (ver abajo) |
| 1000 a 1999 | Excepción de formato o estructura | Falla del emisor; el documento se considera **no informado** | Corregir el XML y reenviar. No es reintento automático |
| 2000 a 3999 | Rechazo | CDR con estado **rechazado**; no tiene validez | Ver reglas por tipo de documento |
| 4000 en adelante | Observación | CDR **aceptado** con advertencias; válido | Registrar las observaciones y no reenviar |

Cómo se manifiesta cada uno:
- **Excepción**: el servicio responde con un SOAP Fault, con código y descripción. No hay CDR.
- **Rechazo**: llega un CDR con estado rechazado.
- **Observación**: llega un CDR aceptado con advertencias.
- **Sin errores**: llega un CDR aceptado.

Estados del CDR según la página oficial: Aceptada, Aceptada con observación, Rechazada.

Reglas ante un rechazo, según el tipo de documento:
- **Factura y notas de crédito o débito**: el **número queda consumido**. Hay que emitir un documento nuevo
  con otro correlativo corrigiendo la causa. No se puede reenviar el mismo número.
- **RC, baja, reversión**: se rechaza el documento completo, sin procesamiento parcial.
  Se corrige y se reenvía con **el mismo nombre de archivo**.
- **Lotes**: cada documento se valida por separado.

Los códigos y sus descripciones están en las "Reglas de validación" (xlsx, ver §10) y extraídos en `codigos_sunat.csv`.
Ahí también está el listado de "observaciones que migran a error": códigos que antes eran
observación y ahora son error. La tabla de códigos del sistema debe cargarse desde ese xlsx y versionarse, no inventarse.

### Hallazgos del Excel de Reglas de validación (26/08/2026)

Se extrajeron los códigos de todas las hojas a [`codigos_sunat.csv`](codigos_sunat.csv)
(869 códigos únicos). Por rango: 14 en 0xxx, 19 en 1xxx, 678 en 2xxx-3xxx y 158 en 4xxx+.

**Los rangos del manual no sirven para decidir reintentos. Se clasifica código por código.**

- **Transitorios de SUNAT (reintentar):** `0100` (servicio de recepción no responde, "intente nuevamente")
  y `0109` (servicio de autenticación no disponible). Más los errores de red.
- **Configuración (no reintentar, avisar al dueño):** `0111` sin perfil para enviar, `0154` el RUC del archivo
  no corresponde al usuario, `1078` emisor no autorizado en SEE desde sistemas del contribuyente,
  `1086` sujeto sin capacidad operativa, `2325` certificado distinto al comunicado a SUNAT,
  `2326` certificado de baja, `2335` documento alterado (la firma no coincide).
- **Errores propios, aunque estén en 0xxx (corregir el código):** `0151` nombre de ZIP, `0155` a `0158` ZIP
  vacío, corrupto o con más de un archivo, `0159` a `0161` nombre del XML, `0306` XML no parseable o que
  no cumple el XSD.
- **`0127` El ticket no existe:** probado en el beta; llega como `statusCode` dentro de un HTTP 200, no como SOAP Fault.
  El cliente debe leer el `statusCode` de cada respuesta.
- **`1079` "Solo puede enviar el comprobante en un resumen diario":** es lo que devuelve `sendBill` para una boleta.
  Confirma que las boletas van por Resumen Diario.
- **`1032` y `1033`:** comprobante ya informado como anulado o rechazado, y comprobante registrado antes con
  otros datos. Al reintentar hay que reenviar exactamente el mismo XML, sin cambiar datos.
- **Observaciones dentro del rango 2000-3999:** 24 códigos son observación aunque el rango diga rechazo,
  como `2901` a `2919` (formato de nombre comercial, urbanización, provincia, distrito, dirección), `2404`,
  `2988` y `3228`. Se acepta el comprobante y se guardan las advertencias.
- **Observaciones que migraron a error:** el listado "Observaciones que migran a Error" mapea cada código
  4xxx a un 3xxx (por ejemplo `4287` a `3270`, `4293` a `3292`, `4301` a `3294`). Son diferencias aritméticas:
  precio unitario, valor de venta por ítem, base imponible, sumatorias por tipo de operación e IGV.
  Ahora **rechazan**. Hay que calcular y redondear los totales exactamente y validarlos antes de firmar,
  porque un rechazo de factura consume el número. Los dos archivos "Listado de observaciones…" del repo son idénticos.

## 6. Estado del ticket (`getStatus`)

- `0`: terminó; el `content` trae el ZIP con el CDR. El CDR puede traer aceptación o rechazo.
- `98`: en proceso; consultar de nuevo más tarde (es un reintento natural).
- `99`: terminó con errores; el `content` trae el detalle o el rechazo.

## 7. Política de reintentos (propuesta de diseño)

Estados sugeridos para cada comprobante:
`PENDIENTE`, `FIRMADO`, `ENVIANDO`, `ACEPTADO`, `ACEPTADO_OBS`, `RECHAZADO`,
`ERROR_DATOS`, `ERROR_TEMPORAL`, `VENCIDO`.
Para RC y baja se agregan `TICKET_EMITIDO` y `EN_PROCESO`.

Reglas:
1. **Error de transporte** (timeout, conexión cortada, HTTP 5xx, caída de SUNAT): reintentar con backoff
   exponencial y jitter. Antes de reenviar una factura, consultar `getStatusCdr`: si SUNAT ya la recibió,
   usar ese CDR y no reenviar (idempotencia).
2. **`0100` y `0109`** (transitorios de SUNAT): reintentar con backoff hasta el plazo de envío.
   **El resto de 0xxx** (`0111`, `0151` a `0161`, `0306`...): no reintentar; son de configuración o del XML o ZIP.
3. **Excepción 1000-1999**: no reintentar. Pasar a `ERROR_DATOS` y alertar para corregir la causa.
4. **Rechazo 2000-3999**: no reintentar el mismo documento. Una factura rechazada se reemite con un nuevo número;
   un RC o baja rechazado se corrige y se reenvía con el mismo nombre.
5. **Observación 4000+**: dar por aceptado y guardar las advertencias.
6. **Ticket**: consultar `getStatus` con backoff mientras devuelva `98`. Con `0` o `99`, leer el CDR.
7. **Plazo máximo**: el reintento termina cuando vence el plazo de envío (ver §8). Si vence, pasar a `VENCIDO` y alertar.
8. **Entrega al cliente**: enviar la factura al cliente solo después de `ACEPTADO` o `ACEPTADO_OBS`.
   La norma permite enviar antes, pero si SUNAT la rechaza no tiene validez tributaria.

## 8. Plazos y reglas de negocio

- Plazo de envío de facturas y notas vinculadas:
  - Hasta el 31/12/2022: la fecha de emisión más 3 días calendario.
  - Desde el 01/01/2023: la fecha de emisión o **hasta el día calendario siguiente**
    (preguntas frecuentes #10, R.S. 193-2020/SUNAT).
  - La página SEE del Contribuyente, modificada en mayo de 2025, todavía dice 3 días.
    **[VERIFICAR]**. Mientras tanto, usar el plazo más estricto (1 día) como límite de reintentos.
- Si no se envía a tiempo, lo enviado a SUNAT **no tiene calidad de factura electrónica**,
  aunque se haya entregado al cliente.
- Plazo del Resumen Diario de boletas: no figura en lo leído. **[VERIFICAR]**.
- Factura: solo a clientes con RUC. Boleta: va por resumen diario.
- Nota de crédito: solo sobre comprobantes con CDR aceptado.
  Excepcionalmente se puede emitir hasta el 10.º día hábil para corregir cliente o descripción.
- Forma de pago (R.S. 193-2020): contado, o crédito con monto pendiente y cuotas con su fecha de vencimiento.
- Conservar los XML, los CDR, los resúmenes y las bajas. El cliente debe poder consultar sus
  comprobantes en una página web por un año desde la emisión.
- Requisitos del emisor: Clave SOL, RUC habido, sin suspensión temporal ni baja. Se pasa a "emisor electrónico"
  indicando un certificado digital y un correo en SOL, o vinculando un PSE, y opera desde el día calendario siguiente.
- Nuevo RUS (NRUS): no está obligado a emitir electrónicamente, pero puede hacerlo de forma voluntaria.
- Contingencia: si SUNAT cae por causas ajenas, se pueden emitir comprobantes impresos autorizados y
  se informan después (hasta el 7.º día calendario). Es un procedimiento aparte; no se prevé para este proyecto.

## 9. Certificado digital tributario (CDT) gratuito

- Gratis hasta el 31/12/2027. Se pide con Clave SOL en Empresas, Comprobantes de Pago, Certificado Digital Tributario.
  Se descarga como `certificado.p12` desde el Buzón SOL y se protege con una clave privada propia.
- Requisitos relevantes: RUC habido y activo, **afecto a renta de tercera categoría**, ingresos netos
  anuales iguales o menores a S/ 1 260 000 (referencia 2019), no ser OSE ni PSE, y no tener ya un CDT vigente.
- Ojo: un RUC 10 en Nuevo RUS probablemente no califica. **[VERIFICAR]** el régimen tributario real.

## 10. Documentos oficiales por bajar (cpe.sunat.gob.pe/guias-y-manuales)

- Guías XML 2.1: Factura, Boleta, Nota de Crédito, Nota de Débito, y Resumen Diario 2.0.
  Los PDF salen de la página "Guías y Manuales".
- Archivos XSD 2.1 (zip, actualizado al 28/02/2022) y XSL 2.1.
- Reglas de validación (xlsx, actualizado al 26.08.2026) y listado de observaciones que migran a error.
- Manual de consulta integrada de validez de comprobante de pago.
- Catálogos de códigos: no aparecen en esa página; salen en las guías XML y las reglas de validación.

## 11. Pendientes

- Leer las páginas 35-54 del manual (estructura del CDR y Anexo 2).
- Verificar en el beta: `sendBill` con boleta, firma con certificado de pruebas, si acepta el RUC 10 propio,
  y si el contenido del ZIP necesita la carpeta `dummy`.
- Probar en el beta qué responde SUNAT al reenviar un comprobante ya aceptado (¿el mismo CDR o el error `1033`?).
