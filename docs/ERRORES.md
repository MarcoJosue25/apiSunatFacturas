# Errores y limitaciones

Aquí apunto cualquier error que aparezca mientras avanzamos, cómo se soluciona o si se deja para después. También van las limitaciones que dejamos a propósito para resolver más adelante, como los reintentos.

Cómo se lee:
- `[x]` resuelto. `[ ]` pendiente o diferido.
- **Fase** es la fase del [plan](PLAN.md) donde se resuelve o donde se decide.
- Cuando algo se resuelve, no se borra: se marca y se anota cómo quedó.

Reglas de este archivo:
- Los errores y las limitaciones se anotan **en el momento en que aparecen**, no al final de la fase.
- Aunque se arreglen, **se quedan guardados**. Solo se marcan como resueltos y se anota cómo quedaron.
- Si este archivo se vuelve muy grande, se crea otro con la misma estructura (`ERRORES-2.md`, `ERRORES-3.md`...) y se enlaza desde aquí.
- Cada fase de [`PLAN.md`](PLAN.md) cierra revisando que lo que salió esté anotado.

## Plantilla

```
### E-000 Título corto
- [ ] Estado: pendiente | resuelto | diferido
- **Fecha:** AAAA-MM-DD
- **Fase:** N
- **Qué pasó:**
- **Cómo se soluciona o por qué se deja:**
```

---

## Errores y hallazgos

### E-001 La consulta de un ticket inexistente no devuelve un error SOAP
- [ ] Estado: pendiente (se resuelve en la fase 1)
- **Fecha:** 2026-10-28
- **Fase:** 1
- **Qué pasó:** al consultar `getStatus` con un ticket falso en el beta, la SUNAT respondió HTTP 200 con `statusCode 0127` y el texto "El ticket no existe". No fue un SOAP Fault ni uno de los valores 0, 98 o 99 que dice el manual.
- **Cómo se soluciona:** el parser debe leer el `statusCode` de cada respuesta y no basarse solo en el HTTP. La respuesta real se guarda como archivo de prueba.

### E-002 Los rangos de códigos del manual no sirven para decidir qué se reintenta
- [ ] Estado: pendiente (se resuelve en la fase 5)
- **Fecha:** 2026-10-28
- **Fase:** 5
- **Qué pasó:** el manual dice que 0100-0999 son excepciones de la SUNAT, pero el Excel de Reglas de validación muestra que varios son errores del emisor (por ejemplo `0151` a `0161` y `0306`). Y 24 códigos entre 2000 y 3999 son observaciones, no rechazos.
- **Cómo se soluciona:** clasificar código por código con la tabla `codigos_sunat.csv`. Solo `0100` y `0109` son transitorios. Un código desconocido nunca se reintenta.

### E-003 Los cálculos mal redondeados ahora rechazan la factura
- [ ] Estado: pendiente (se resuelve en las fases 2 y 3)
- **Fecha:** 2026-10-28
- **Fase:** 2 y 3
- **Qué pasó:** las diferencias de cálculo (precio unitario, sumatorias, IGV) antes eran observaciones (4xxx) y ahora son errores (3xxx). Una factura rechazada consume su número.
- **Cómo se soluciona:** calcular y redondear exactamente según la guía, y validar la coherencia de los totales antes de generar el XML.

### E-004 El plazo de envío de las facturas se contradice entre páginas
- [ ] Estado: pendiente (se confirma en la fase 7)
- **Fecha:** 2026-10-28
- **Fase:** 7
- **Qué pasó:** las preguntas frecuentes dicen que desde 2023 el plazo es hasta el día calendario siguiente. La página del SEE del Contribuyente (modificada en mayo de 2025) todavía dice 3 días.
- **Cómo se soluciona:** usar 1 día como límite de reintentos, configurable en `sunat.plazo-envio-dias`, hasta confirmarlo.

### E-005 El manual del programador habla de UBL 2.0
- [x] Estado: resuelto
- **Fecha:** 2026-10-28
- **Fase:** 3
- **Qué pasó:** el manual (mayo 2021) menciona UBL 2.0, pero las guías y los XSD vigentes son 2.1.
- **Cómo quedó:** se trabaja con las guías y los XSD 2.1 que están en `docs/sunat/`.

### E-006 El JDK 21 desactiva SHA-1 para firmas
- [ ] Estado: pendiente (se confirma en la fase 4)
- **Fecha:** 2026-10-28
- **Fase:** 4
- **Qué pasó:** algunas librerías de ejemplo de la SUNAT firman con RSA-SHA1, y el JDK 21 lo rechaza por defecto.
- **Cómo se soluciona:** probar en el beta si acepta SHA-256. Si exige SHA-1, se evalúa cómo habilitarlo de forma limitada.

### E-007 Hay dos ramas con esqueletos del proyecto
- [x] Estado: resuelto (la rama vieja sigue en GitHub; la puede borrar el dueño del repo)
- **Fecha:** 2026-10-28
- **Fase:** 0
- **Qué pasó:** otra sesión creó la rama `claude/sharp-goodall-tx30hd` con un esqueleto de Spring Boot. Usaba otro groupId y paquete (`com.apisunatfacturas`), `spring-boot-starter-web`, Actuator y no tenía Lombok.
- **Cómo quedó:** se revisó y no se reutilizó. Se rehízo el esqueleto en esta rama con lo acordado (`apisunat.api_sunat`, `spring-boot-starter-webmvc`, Lombok, sin Actuator porque nada lo usa todavía).

### E-008 Archivos repetidos en `docs/sunat/`
- [ ] Estado: pendiente (limpieza menor)
- **Fecha:** 2026-10-28
- **Fase:** 0
- **Qué pasó:** los dos archivos "Listado de observaciones que migran a Error" son idénticos, y la guía del Resumen de Boletas está dos veces.
- **Cómo se soluciona:** dejar una copia de cada uno.

### E-009 `cpe.sunat.gob.pe` bloquea las descargas automáticas
- [ ] Estado: diferido (no se resuelve a propósito)
- **Fecha:** 2026-10-28
- **Fase:** ninguna
- **Qué pasó:** desde la sesión de Claude, `cpe.sunat.gob.pe` responde 403 por la protección anti-bots del sitio. `www.sunat.gob.pe` y `e-beta.sunat.gob.pe` sí responden.
- **Cómo se soluciona:** no se evita la protección. Los archivos de `cpe` los baja y sube el dueño del repo.

### E-010 Comandos de PowerShell ejecutados en Git Bash
- [x] Estado: resuelto
- **Fecha:** 2026-10-28
- **Fase:** ninguna
- **Qué pasó:** los pasos para copiar los documentos estaban escritos para PowerShell, pero se corrieron en Git Bash, y no se copió nada.
- **Cómo quedó:** se repitieron con `mkdir` y `cp`, y los archivos subieron bien.

### E-011 El Maven wrapper no puede bajar Maven dentro del entorno de Claude
- [x] Estado: resuelto en el CI (afecta solo al entorno de Claude, donde se usa `mvn`)
- **Fecha:** 2026-10-28
- **Fase:** 0
- **Qué pasó:** `./mvnw` usa `wget` para bajar la distribución de Maven, y `wget` falla en el entorno de Claude por el proxy. Con `curl` la misma URL funciona.
- **Cómo se soluciona:** en el entorno de Claude se usa el `mvn` instalado (`mvn clean test`). El wrapper queda en el repo para tu máquina y para el CI. Confirmado: en GitHub Actions `./mvnw -B clean test` corre en verde (corridas 1 y 2).

### E-012 Un RUC nulo pasaba la validación de las propiedades
- [x] Estado: resuelto
- **Fecha:** 2026-10-28
- **Fase:** 1
- **Qué pasó:** `SunatProperties` validaba el RUC solo con `@Pattern`, y en Bean Validation un valor nulo pasa ese patrón. Sin la variable de entorno, la aplicación habría arrancado sin RUC. Lo atrapó el test `elRucDebeTenerExactamenteOnceDigitos` con el caso nulo.
- **Cómo quedó:** se agregó `@NotBlank` al RUC. Ojo para el resto del proyecto: un patrón o un largo no obligan a que el campo exista, hay que combinarlo con `@NotBlank` o `@NotNull`. **Se repitió** en la fase 3 con `codigoEstablecimiento` (también lo atrapó un test): por eso cada campo obligatorio lleva su caso nulo en los tests.

### E-013 Un error SOAP de la SUNAT llega con HTTP 200
- [ ] Estado: parcialmente resuelto (el parser ya lo detecta; falta que el cliente lo use, paso 1.4)
- **Fecha:** 2026-10-28
- **Fase:** 1
- **Qué pasó:** al mandar un sobre sin la cabecera de seguridad, el beta respondió HTTP **200** con un `Fault` dentro del cuerpo (`faultcode soap-env:Client.0200`). El mensaje además es engañoso: dice "El servidor remoto no se encuentra activo (codigo: 0200)" aunque el problema era la cabecera. Ejemplo guardado en `src/test/resources/sunat/beta/fault-sin-seguridad.xml`.
- **Cómo se soluciona:** el cliente nunca decide por el código HTTP: siempre pasa el cuerpo al `SoapResponseParser`, que lanza `SunatFaultException` si hay un Fault. El código de 4 dígitos se saca del `faultcode` o del texto "(codigo: NNNN)".

### E-014 Un test con timeout muy corto fallaba en la primera llamada
- [x] Estado: resuelto
- **Fecha:** 2026-10-28
- **Fase:** 1
- **Qué pasó:** en la prueba de punta a punta el timeout de lectura era de 400 ms. La primera consulta, con la aplicación recién arrancada (clases sin cargar, cliente HTTP sin calentar), tardó más que eso y la API respondió 504 aunque el código estaba bien.
- **Cómo quedó:** timeout de 1500 ms y un servidor lento de 3000 ms. Se corrió la suite tres veces seguidas sin fallos. Regla para el resto del proyecto: los tests con tiempo usan márgenes holgados, porque el CI puede ser más lento que mi entorno.

### E-015 La SUNAT rechaza con 2108 las facturas enviadas fuera de plazo
- [ ] Estado: pendiente (se usa en la fase 7)
- **Fecha:** 2026-10-28
- **Fase:** 7
- **Qué pasó:** en las Reglas de validación, el código `2108` ("Presentación fuera de fecha") es un error: la diferencia entre la fecha de recepción del XML y la fecha de emisión supera el plazo máximo vigente. O sea, la propia SUNAT hace cumplir el plazo (ver E-004), y un reintento que llegue tarde no se acepta aunque el XML sea correcto.
- **Cómo se soluciona:** el reintento termina en `VENCIDO` al cumplirse el plazo y no reenvía. Si igual llega un `2108`, se clasifica como rechazo y no se reintenta. Ojo también con `1033` ("registrado previamente con otros datos"): si hay que reenviar, el XML debe ser exactamente el mismo.

### E-016 Los valores por defecto de Lombok no funcionan al leer un JSON
- [x] Estado: resuelto
- **Fecha:** 2026-10-28
- **Fase:** 3
- **Qué pasó:** `ItemRequest` tenía `@Builder.Default private String unidadMedida = "NIU"`. Al leer un JSON sin ese campo, Jackson 3 construye el objeto con el constructor de todos los argumentos y deja `null`, así que la validación fallaba con "la unidad de medida no puede estar vacía" aunque el campo se omitió a propósito. Lo atrapó el test del JSON de una factura completa.
- **Cómo quedó:** el valor por defecto va en el getter (`getUnidadMedida()` devuelve `NIU` si es nulo) y el campo solo valida el patrón cuando viene. Regla para los demás DTO: un valor por defecto se pone en el getter, no en el campo.

---

## Limitaciones que se dejan para después

### L-001 Todavía no hay reintentos
- [ ] Estado: diferido (fase 7)
- **Qué significa:** hasta la fase 7, si la SUNAT falla, el comprobante queda pendiente y no se vuelve a enviar solo.
- **Por qué se deja:** primero hay que tener el envío, el guardado y los estados funcionando, que es lo que los reintentos necesitan.

### L-002 La factura solo soporta operación gravada con IGV
- [ ] Estado: diferido (fase 3, cada variante cuando se pida)
- **Qué significa:** exonerado, inafecto, gratuito, descuento global, pago a crédito y dólares no se hacen al principio.

### L-003 Las boletas solo van por resumen diario
- [ ] Estado: diferido (fase 8)
- **Qué significa:** no se soporta el envío individual de boletas hasta confirmar en el beta qué responde la SUNAT.

### L-004 No hay notas de crédito, notas de débito ni comunicación de baja
- [ ] Estado: diferido (opcional, después de la fase 9)
- **Qué significa:** hasta entonces no se puede corregir ni anular un comprobante desde la API.

### L-005 Solo el sistema "SEE desde los sistemas del contribuyente"
- [ ] Estado: diferido (sin fecha)
- **Qué significa:** no se usa OSE, PSE ni el Facturador SUNAT.

### L-006 Fuera del alcance
- [ ] Estado: diferido (sin fecha)
- **Qué significa:** guías de remisión, retenciones, percepciones, liquidaciones de compra y recibos de servicios públicos no se implementan.

### L-007 La contingencia no se implementa
- [ ] Estado: diferido (sin fecha)
- **Qué significa:** el procedimiento de comprobantes impresos cuando la SUNAT no está disponible por causas ajenas no está previsto. Si la SUNAT cae, se reintenta (fase 7).

### L-008 Las pruebas contra el beta no corren en el CI
- [ ] Estado: diferido (se decide en la fase 9)
- **Qué significa:** las pruebas con `@Tag("beta")` se corren a mano, para que el CI no dependa de internet ni de la disponibilidad de la SUNAT.

### L-009 El beta no prueba todo
- [ ] Estado: diferido (se ve en la fase 9)
- **Qué significa:** el beta solo valida la estructura de los XML. Lo que pasa en producción, como la idempotencia al reenviar o la validación del certificado registrado, solo se puede probar con la SUNAT real.

### L-010 El manual del programador no está leído completo
- [ ] Estado: pendiente (antes de la fase 5)
- **Qué significa:** faltan las páginas 35 a 54 (estructura del CDR y Anexo 2).

### L-011 El plazo de envío del resumen diario no está confirmado
- [ ] Estado: pendiente (fase 8)
- **Qué significa:** en lo leído no figura cuántos días hay para informar las boletas.

### L-012 El certificado gratuito puede no corresponder a mi régimen
- [ ] Estado: pendiente (se confirma antes de la fase 9)
- **Qué significa:** el certificado gratuito exige estar afecto a renta de tercera categoría. Si el RUC 10 está en Nuevo RUS, probablemente no califica y habría que cambiar de régimen o usar otra vía.

### L-013 El beta no valida la clave
- [ ] Estado: diferido (se ve en la fase 9)
- **Qué significa:** con una clave incorrecta y con el ticket vacío, el beta respondió exactamente igual que con la clave buena (`0127`). Los errores de autenticación y permisos (`0109`, `0111`) no se pueden reproducir en el beta.
- **Por qué se deja:** esos casos se prueban con respuestas simuladas, y su comportamiento real se confirma recién con la SUNAT de producción.

### L-014 El beta es lento
- [ ] Estado: diferido (se tiene en cuenta en timeouts y reintentos)
- **Qué significa:** en la prueba manual, una consulta `getStatus` al beta tardó entre 1.2 y 2.5 segundos. Por eso el timeout de lectura por defecto es de 30 s y los tests con tiempo usan márgenes holgados. Un envío de comprobante (que firma y comprime) probablemente tarde más.
- **Por qué se deja:** no hay nada que arreglar, pero hay que recordarlo al decidir los timeouts de las fases 5 y 7.

### L-015 Las facturas con cientos de líneas iguales se rechazan localmente
- [ ] Estado: diferido (se mejora solo si algún día hace falta)
- **Qué significa:** el redondeo de cada línea se acumula. Con 35.00 repetido, cada línea se desvía 0.0012 del 18%, así que unas 417 líneas iguales superan el margen de 0.50 que me puse (la SUNAT tolera 1). Con el peor precio (0.23) bastan unas 86. `CalculadoraIgv.calcularTotales` lanza un error antes de que el documento llegue a la SUNAT.
- **Por qué se deja:** una venta por WhatsApp no tiene cientos de líneas, y juntar los productos iguales en una sola línea con su cantidad es exacto. Si algún día hace falta, se puede repartir el redondeo entre las líneas (difusión del error) para que el total siempre cuadre.

### L-016 El emisor no envía ubigeo, departamento, provincia ni distrito
- [ ] Estado: diferido (se agrega si hace falta)
- **Qué significa:** en el XML, el domicilio fiscal del emisor lleva solo la dirección y el código de establecimiento. El ubigeo, departamento, provincia y distrito son opcionales para la SUNAT y no se envían todavía.
- **Por qué se deja:** la SUNAT valida el ubigeo contra su lista, y no tenemos esa lista. Si una factura lo necesita, se agrega con una validación del código.

### L-017 Los caracteres que no existen en ISO-8859-1 se reemplazan por "?"
- [ ] Estado: diferido (comportamiento decidido)
- **Qué significa:** el XML va en ISO-8859-1. Un emoji o una letra de otro alfabeto en el nombre de un producto o un cliente se escribe como `?` (uno por carácter). Las comillas y guiones elegantes, los puntos suspensivos y el euro se cambian por su parecido en ASCII (`TextoSunat`).
- **Por qué se deja:** es lo más seguro para que la SUNAT no rechace el XML. Quien necesite el texto exacto lo tiene en la base de datos, no en el XML.

