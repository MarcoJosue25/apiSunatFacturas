# Reglas de cálculo de la factura

Fuente: hoja `Factura2_0` de las Reglas de validación (26/08/2026) y la guía XML de factura 2.1 (`docs/sunat/`).

**Alcance de ahora:** solo operaciones **gravadas con IGV al 18%**, sin descuentos, cargos, ISC, ICBPER, anticipos ni redondeo global. Todo lo demás está en [`ERRORES.md`](../ERRORES.md) (L-002) y se agrega cuando se necesite.

## Formatos de los números

| Dato | Formato | Significado |
|---|---|---|
| Cantidad, valor unitario y precio de venta unitario | `n(12,10)` | hasta 12 enteros y hasta 10 decimales |
| Todos los montos (valor de venta, IGV, totales) | `n(12,2)` | hasta 12 enteros y hasta 2 decimales |

## Fórmulas de la SUNAT (cada una es un rechazo si no cuadra)

La mayoría compara con una **tolerancia de ±1** (así lo dice cada regla). Esa tolerancia es de la SUNAT; nosotros calculamos exacto y no dependemos de ella.

| Código | Qué se valida (caso gravado, sin descuentos ni cargos) |
|---|---|
| 3271 | Valor de venta del ítem = valor unitario × cantidad (±1) |
| 3272 | Monto base del IGV de la línea = valor de venta del ítem (**exacto**, sin tolerancia, si no hay ISC) |
| 3103 | IGV de la línea = 18% × monto base de la línea (±1) |
| 3270 | Precio de venta unitario = (valor de venta del ítem + IGV de la línea) ÷ cantidad (±1) |
| 3292 | Monto total de tributos del ítem = suma de los tributos de la línea (±1) |
| 3277 | Total de operaciones gravadas = suma de los valores de venta de los ítems gravados (±1) |
| 3291 | IGV total = 18% × suma de los montos base de las líneas gravadas (±1) |
| 3294 | Monto total de tributos del documento = suma de los subtotales de tributos (±1) |
| 3278 | Total valor de venta = suma de los valores de venta de los ítems (±1) |
| 3279 | Total precio de venta = total valor de venta + IGV (±1) |
| 3280 | Importe total = total precio de venta (más otros cargos, menos descuentos y anticipos, más redondeo; ±1) |
| 3303 | Monto de redondeo del importe total: su valor absoluto no puede pasar de 1 |
| 3462 | Todas las líneas gravadas con IGV deben tener la misma tasa |

Las observaciones `4287` a `4301` ("migran a error") son las versiones antiguas de estas mismas reglas (ver `Listado de observaciones que migran a Error`).

## Cómo calculo yo

Mi agente vende con **precios que ya incluyen IGV**, así que la entrada de cada línea es el **precio unitario con IGV** y la **cantidad**. El total que paga el cliente tiene que ser exactamente lo que se le cobró, y a partir de ahí se saca el IGV (no al revés).

Por cada línea:

1. `total de la línea = redondear(precio unitario × cantidad)` a 2 decimales.
2. `valor de venta (base) = redondear(total de la línea ÷ 1.18)` a 2 decimales.
3. `IGV de la línea = total de la línea − valor de venta`.
4. `valor unitario = precio unitario ÷ 1.18` con 10 decimales (solo para el XML).

Por el documento:

- `total gravado = suma de los valores de venta`
- `IGV total = suma de los IGV de las líneas`
- `importe total = suma de los totales de las líneas` (que es igual a `total gravado + IGV total`)

Redondeo: **mitad hacia arriba** (`HALF_UP`) sobre `BigDecimal`, nunca con `double`.

Ejemplo, un polo de S/ 35.00 por 2 unidades: total de la línea 70.00 → valor de venta 59.32 → IGV 10.68 → valor unitario 29.6610169492.

## Por qué así

- El importe total siempre es lo que pagó el cliente (precio × cantidad), sin diferencias de centavos.
- Cada línea cumple 3271, 3272, 3103 y 3270 con diferencias de menos de 1 centavo.
- La diferencia entre el IGV total y el 18% de la base crece con el redondeo de cada línea: como máximo 0.0059 por línea, y en documentos reales unos pocos centavos. Con muchas líneas iguales se acumula: con el peor precio (0.23) a partir de unas 86 líneas, y con S/ 35.00 a partir de unas 417. Ese documento lo rechazo yo antes de enviarlo (mi límite es 0.50, la mitad de la tolerancia de la SUNAT). Lo normal, juntar los productos iguales en una sola línea con su cantidad, es exacto. Ver L-015 en `ERRORES.md`.

## Pendiente de verificar en el beta

- Que el beta acepte estos cálculos (se prueba en la fase 5 con una factura real).
- Cómo valida el IGV la SUNAT cuando la base total es mayor a un millón (redondeo acumulado).
