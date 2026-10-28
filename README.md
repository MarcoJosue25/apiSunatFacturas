# apiSunatFacturas

Una API para emitir facturas y boletas electrónicas directo con la SUNAT, sin intermediarios.

> Este proyecto está hecho con IA bajo mi guía y revisión. Lo pensé como una forma rápida, robusta y liviana de probar los comprobantes en el ambiente de pruebas (demo) de la SUNAT, sin lidiar con proveedores que cobran por consulta.

## Por qué lo hago

Estoy armando un agente de ventas para WhatsApp y otras redes, con un pequeño minicrm, y me pareció buena idea que también pueda emitir facturas y boletas. Los proveedores de facturación cobran por comprobante, y la SUNAT ofrece su propio servicio y un certificado digital tributario gratuito (hasta fines de 2027), así que prefiero hacerlo directo.

Es un servicio completamente independiente: solo se le pide "emite esta boleta" por REST y este proyecto se encarga del resto.

## Qué quiero que haga

- Armar el XML (UBL 2.1), firmarlo y enviarlo a la SUNAT.
- Leer el CDR (la constancia de respuesta) y guardar el resultado.
- Distinguir un error mío de una caída de la SUNAT, para reintentar solo cuando tiene sentido.
- Mandar las boletas por resumen diario, como pide la SUNAT.

## Estado

Las 9 primeras fases están hechas y probadas contra el beta de la SUNAT; la décima (producción) tiene el código listo y espera los pasos manuales. Lo fui haciendo por fases, despacio y con tests en cada paso. El plan completo, con los pasos y su check, está en [`docs/PLAN.md`](docs/PLAN.md).

1. [x] Cimientos
2. [x] Conexión con el beta de la SUNAT
3. [x] Los números (cálculos exactos)
4. [x] XML de factura
5. [x] Firma digital
6. [x] Enviar la factura al beta
7. [x] Guardar y exponer por REST
8. [x] Reintentos
9. [x] Boletas y resumen diario
10. [~] Producción: el código está listo; faltan los pasos manuales de `docs/PRODUCCION.md` (certificado real, usuario secundario de SOL y la primera factura real)

Los errores que me van saliendo y las limitaciones que dejo para después están en [`docs/ERRORES.md`](docs/ERRORES.md). Los detalles y decisiones que voy notando están en [`docs/OBSERVACIONES.md`](docs/OBSERVACIONES.md).

## Stack

Java 21, Spring Boot 4 y Maven. MySQL con Flyway, igual que en mi agente.

## Cómo correrlo

Necesitas Java 21 y MySQL (`docker compose up -d` levanta uno). Por defecto apunta al servicio de pruebas (beta) de la SUNAT con las credenciales públicas de pruebas y necesita un certificado para firmar (ver [`.env.example`](.env.example)).

```bash
docker compose up -d
./mvnw spring-boot:run
curl http://localhost:8081/api/v1/sunat/tickets/201100000011227
```

Con un ticket que no existe, el beta responde `{"statusCode":"0127","content":"El ticket no existe"}`.

### La API (todo bajo `/api/v1`, con `X-API-Key` si hay llave configurada)

| Método y ruta | Qué hace |
|---|---|
| `POST /facturas` | emite y envía una factura (201 nueva, 202 guardada pero la SUNAT no respondió, 200 si ya existía ese `idExterno`) |
| `GET /facturas/{id}` y `/xml` | la factura y su XML firmado |
| `POST /boletas` | guarda y firma una boleta (queda pendiente de su resumen diario) |
| `GET /boletas/{id}` y `/xml` | la boleta y su XML firmado |
| `POST /resumenes/generar` | arma y envía ya el resumen diario de las boletas pendientes (si no, lo hace solo a las 22:00 de Lima) |
| `GET /resumenes/{id}` | estado del resumen |
| `POST /resumenes/{id}/reenviar` | reenvía un resumen rechazado |

### Producción

Con el perfil `prod` (`docker compose --profile app up -d --build`) la aplicación se niega a arrancar si queda algo de pruebas. Los pasos para emitir de verdad (certificado, usuario secundario de SOL, primera factura, respaldos) están en [`docs/PRODUCCION.md`](docs/PRODUCCION.md).

## Pruebas

```bash
./mvnw test            # todas las pruebas, sin internet
./mvnw test -Pbeta     # solo las que llaman al beta de la SUNAT de verdad
```

## Documentación

Todo lo que fui leyendo de la SUNAT está en [`docs/sunat/`](docs/sunat/). Las notas más importantes (flujo, clasificación de errores y reintentos) están en [`docs/sunat/NOTAS.md`](docs/sunat/NOTAS.md).

## Aviso

Es un proyecto personal. No tiene ninguna relación oficial con la SUNAT.
