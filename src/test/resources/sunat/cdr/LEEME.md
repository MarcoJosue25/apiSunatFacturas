# Constancias de recepción (CDR) de ejemplo

Salen de los ejemplos C.1 y C.2 del manual del programador de la SUNAT (páginas 47 a 50), con la firma y el certificado reemplazados
por `AAAA` para que pesen poco. No son capturas reales del beta: cuando la prueba real del beta guarde una constancia verdadera,
se agrega aquí con otro nombre.

| Archivo | Qué es |
|---|---|
| `aceptado.xml` | `ResponseCode` 0, sin notas; la descripción viene partida en dos líneas, como en el manual |
| `aceptado-con-observaciones.xml` | `ResponseCode` 0 con dos `cbc:Note` (`4031` y `4001`) |
| `rechazado.xml` | `ResponseCode` 2047 (rechazo) |

Detalle del manual que conviene recordar: la constancia de la SUNAT se firma con RSA-SHA1, aunque nosotros firmamos con SHA-256.
