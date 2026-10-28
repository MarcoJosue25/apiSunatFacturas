# Pasar a producción

Esta guía es lo que hay que hacer **a mano** para emitir comprobantes reales. Lo que se puede automatizar ya está en el código (perfil `prod`, llave de la API, Dockerfile, respaldo). Los pasos en el portal de la SUNAT los hace una persona con la Clave SOL del RUC: **no pude comprobarlos desde aquí**, así que están escritos con lo que dice la SUNAT en sus páginas (enlaces al final) y pueden haber cambiado. Donde no estoy seguro lo digo.

## 0. Antes de empezar

- [ ] El RUC está activo, con domicilio fiscal **Habido**, y tengo la Clave SOL del RUC.
- [ ] Sé a qué régimen pertenece el RUC. El certificado gratuito (CDT) pide estar **afecto al Impuesto a la Renta de tercera categoría**; la SUNAT no menciona el Nuevo RUS en ningún lado, así que **si el RUC es RUS probablemente no califica** (esto es una inferencia mía, confirmarlo con la SUNAT). Ver ERRORES L-012.
- [ ] Estoy afiliado a la emisión electrónica desde el sistema del contribuyente (SEE – Del Contribuyente) en SOL. Si no, el envío será rechazado.
- [ ] Acepto que el beta (`MODDATOS`) **no valida** la clave, los totales ni las fechas: que algo pase en el beta no prueba que pase en producción.

## 1. El certificado digital

1. Entrar a SOL con la Clave SOL: **Empresas → Comprobantes de pago → Certificado Digital Tributario (CDT) → Solicitar Certificado Digital Tributario** y aceptar los términos.
2. Requisitos que menciona la SUNAT: RUC activo, afecto a renta de tercera categoría, no estar inscrito como OSE/PSE y no tener ya un CDT vigente. Vigencia **3 años**. Es gratuito.
3. Bajar el certificado con su clave. Debe terminar en un archivo `.p12` / `.pfx` con la clave privada **y el RUC en el campo `OU`** (así lo exige `CertificadoLoader`). Si la SUNAT lo entrega en otro formato, convertirlo con `openssl pkcs12 -export` y revisar que el RUC siga en el `OU`.
4. Guardarlo **fuera del repositorio** (`.gitignore` ya ignora `*.p12`), en la carpeta `certificados/` del servidor como `certificado.p12`, con permisos solo para quien corre la aplicación, y anotar la fecha de vencimiento (en 3 años hay que renovarlo; la aplicación lo relee del disco sin reiniciar).
5. Comprobar cómo lo ve la aplicación: arrancar con el perfil `prod` (paso 4). El log dice `Certificado cargado: RUC ..., vence el ...`. No arranca si está vencido, es de otro RUC, o es autofirmado.

## 2. Usuario secundario de SOL

La API envía con un **usuario secundario de SOL** y no con el principal: si se filtra, solo puede enviar comprobantes.

1. En SOL con la Clave SOL principal: **Mi RUC / Administración de usuarios → Usuarios secundarios** (el nombre exacto del menú puede variar) → registrar el usuario: tipo y número de documento, usuario, clave.
2. Asignarle solo el perfil de **envío de comprobantes electrónicos** (la SUNAT dice que el uso del usuario secundario se limita al envío de documentos electrónicos y que esos envíos se consideran hechos por el emisor). No darle más perfiles.
3. En el `.env`: `SUNAT_USUARIO=` el usuario secundario y `SUNAT_CLAVE=` su clave. Nunca la Clave SOL principal.

## 3. Configuración (`.env`)

Copiar `.env.example` a `.env` (está en `.gitignore`) y poner:

| Variable | Qué poner |
|---|---|
| `SUNAT_URL` | `https://e-factura.sunat.gob.pe/ol-ti-itcpfegem/billService` |
| `SUNAT_RUC` | el RUC del emisor |
| `SUNAT_USUARIO` / `SUNAT_CLAVE` | el usuario secundario de SOL |
| `SUNAT_EMISOR_RAZON_SOCIAL`, `..._DIRECCION`, `..._ESTABLECIMIENTO` | tal como están en la SUNAT (código de establecimiento `0000` para el domicilio fiscal) |
| `SUNAT_CERTIFICADO_CLAVE` | la clave del `.p12` (la ruta la pone docker-compose: `/certificados/certificado.p12`) |
| `SUNAT_API_KEY` | una llave larga al azar: `openssl rand -hex 32` (mínimo 32 caracteres, solo ASCII) |
| `SUNAT_SERIE_FACTURA` / `SUNAT_SERIE_BOLETA` | `F001` / `B001`, o las series que se usen |
| `DB_CLAVE` y la clave de root de MySQL | cambiar las de ejemplo (`apisunat` / `root`) en `docker-compose.yml` y `.env` |
| `SUNAT_CONSULTA_CDR_HABILITADA` | `false` hasta probar la primera factura (ERRORES L-027) |

El perfil `prod` **no deja arrancar** si queda algo de pruebas: URL del beta, RUC `20100066603`, usuario `MODDATOS`, razón social de ejemplo, sin certificado, certificado autofirmado, o sin llave de la API (o menor a 32 caracteres). Revisa todo eso **antes de conectarse a la base**, así que un arranque fallido no toca el esquema.

## 4. Levantar el servicio

```bash
mkdir -p certificados && cp /ruta/al/certificado.p12 certificados/certificado.p12
docker compose --profile app up -d --build
docker compose logs -f api-sunat
```

Debe aparecer `Certificado cargado`, `Perfil prod: la configuración apunta a la SUNAT de producción` y `Started ApiSunatApplication`. La API escucha en el puerto 8081; **ponerla detrás de HTTPS** (un proxy como Caddy o nginx) si sale de la red local: la llave viaja en una cabecera.

Todas las llamadas a `/api/**` llevan `X-API-Key: <la llave>`. Sin ella (o con otra) responde 401.

## 5. Primera emisión real, controlada

Hacer esto con un monto bajo y un cliente propio (o conocido), y con el contador al tanto. **Cada comprobante informado a la SUNAT es real y no se borra.**

```bash
export API=http://localhost:8081 LLAVE=...   # la llave del .env
curl -s -X POST $API/api/v1/facturas -H "X-API-Key: $LLAVE" -H 'Content-Type: application/json' -d '{
  "idExterno": "primera-real-1",
  "cliente": {"ruc": "<RUC del cliente>", "razonSocial": "<su razón social>"},
  "items": [{"descripcion": "Prueba controlada", "cantidad": 1, "precioUnitario": 1.00}]}'
```

- [ ] La respuesta es 201 con `estado: ACEPTADO` y `codigoSunat: 0`. Si es `ERROR_DE_CONFIGURACION` o `REVISAR`, **parar**: mirar `descripcionSunat` y el log, no reintentar a ciegas.
- [ ] Bajar el XML (`GET /api/v1/facturas/{id}/xml`) y guardarlo.
- [ ] Verificar la factura en SOL (consulta de comprobantes) y que el cliente la vea.
- [ ] Repetir el mismo `POST` (mismo `idExterno`): debe devolver 200 con la misma factura, sin enviar de nuevo.
- [ ] Probar una boleta pequeña con `POST /api/v1/boletas` y luego `POST /api/v1/resumenes/generar`; consultar `GET /api/v1/resumenes/{id}` hasta `ACEPTADO` y ver la boleta en SOL.

Qué mirar de lo que **solo la producción confirma** (queda abierto en PLAN.md y ERRORES.md):

- Plazo de envío de la factura: 1 día o 3 (E-015). Ahora vale 1 (`SUNAT_PLAZO_ENVIO_DIAS`).
- Si `getStatusCdr` funciona como está escrito (L-027); si sí, activar `SUNAT_CONSULTA_CDR_HABILITADA=true`.
- Si un rechazo SOAP `2xxx` consume el número de la factura (L-026) y si hay que informar los números saltados.
- Qué pasa con una boleta/resumen fuera de plazo (observación `4443`) (L-011).

## 6. Respaldo (hay que conservar los XML y las constancias)

Los XML firmados y las constancias (CDR) están en la base. Respaldo con el script (no borra nada, comprueba que el archivo se pueda leer):

```bash
DB_HOST=127.0.0.1 DB_PUERTO=3306 DB_USUARIO=apisunat DB_CLAVE=... ./scripts/respaldo.sh
# con docker compose:
docker compose exec -T mysql mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction apisunat | gzip > respaldo.sql.gz
```

- Programarlo todos los días (cron) y copiar los archivos **fuera del servidor**.
- Cuánto tiempo guardar: la norma tributaria exige conservar los comprobantes durante el plazo de prescripción; suele decirse que **5 años**, pero eso lo confirma el contador. El script nunca borra respaldos viejos.
- Restaurar: `gunzip -c respaldo.sql.gz | mysql -h... -u... apisunat_restaurada`. Hay un test que respalda y restaura de verdad y compara los XML byte por byte.
- Probar una restauración de vez en cuando: un respaldo que nunca se restauró no es un respaldo.

## 7. Cuando algo sale mal

- **No hay conexión con la SUNAT**: el comprobante queda `ERROR_TEMPORAL` y se reintenta solo (1, 2, 5, 15, 30, 60 minutos). Si pasa el plazo queda `VENCIDO` (hay que emitir otro).
- **`REVISAR`**: lo dejó así un código desconocido o respuesta rara. Nadie avisa todavía (L-029): buscarlos con `SELECT id, id_externo, estado FROM comprobantes WHERE estado = 'REVISAR'` y hablar con la SUNAT/contador.
- **Resumen `RECHAZADO`**: leer `descripcionSunat` en `GET /api/v1/resumenes/{id}`; si fue algo pasajero, `POST /api/v1/resumenes/{id}/reenviar`.
- **Cambió el certificado**: reemplazar el archivo; se usa sin reiniciar.
- **Se filtró la llave de la API**: cambiar `SUNAT_API_KEY` y reiniciar. **Se filtró la clave SOL secundaria**: cambiarla en SOL y en `.env`.

## Fuentes

- Certificado digital tributario (SUNAT): <https://cpe.sunat.gob.pe/certificado-digital> y <https://cpe.sunat.gob.pe/certificado-digital-tributario>
- Resolución de Superintendencia 038-2020/SUNAT (CDT): <https://www.sunat.gob.pe/legislacion/superin/2020/038-2020.pdf>
- Nota de prensa sobre el CDT gratuito para mypes: <https://andina.pe/agencia/noticia-mype-obten-tu-certificado-digital-tributario-gratuito-para-emitir-facturas-electronicas-881773.aspx>
- Usuario secundario de Clave SOL: <https://lacamara.pe/?p=71999> y <https://ww3.sunat.gob.pe/legislacion/superin/2013/065-2013.pdf>
