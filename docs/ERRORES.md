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
- **Cómo se soluciona:** clasificar código por código con la tabla `src/main/resources/sunat/codigos_sunat.csv`. Solo `0100` y `0109` son transitorios. Un código desconocido nunca se reintenta.

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

### E-017 Con cantidades muy chicas el precio unitario del XML podía romper la regla 3270
- [x] Estado: resuelto
- **Fecha:** 2026-10-28
- **Fase:** 3
- **Qué pasó:** el total de la línea se redondea a centavos y al dividirlo entre una cantidad muy chica ese redondeo se amplifica: con 0.001 unidades, medio centavo son 5 soles por unidad. En una factura aleatoria, 250099.00 × 0.001 daba un total de 250.10, y `(valor de venta + IGV) / cantidad` era 250100.00: una diferencia de 1.00, justo el límite que tolera la SUNAT (regla 3270). Lo atrapó el verificador independiente `ReglasSunatXml` con 300 facturas aleatorias.
- **Cómo quedó:** `CalculadoraIgv.calcularLinea` informa el precio que llegó, salvo que se aleje más de 0.50 del que de verdad se cobró (total / cantidad), y en ese caso informa el cobrado. Si ese precio cobrado pasa de 12 enteros, se rechaza antes de enviar. Está explicado en `docs/sunat/CALCULOS.md`.

### E-018 El JDK escribía el base64 de la firma partido en líneas con `&#13;`
- [x] Estado: resuelto
- **Fecha:** 2026-10-28
- **Fase:** 4
- **Qué pasó:** al mirar el XML firmado, el `SignatureValue` y el `X509Certificate` salían en varias líneas de 76 caracteres con `&#13;` (retorno de carro) al final de cada una. La firma verificaba igual, pero no es la forma habitual y no se sabe si el servicio de la SUNAT lo toleraría igual de bien.
- **Cómo quedó:** `XmlSigner` quita los espacios de esos dos textos después de firmar, para que queden en una sola línea. No afecta la firma porque `Reference URI=""` excluye a la propia firma y el base64 se lee sin espacios. El `DigestValue` no se toca porque sí está dentro de lo firmado. Un test comprueba que no haya `&#13;` ni saltos de línea y que la firma siga verificando.

### E-019 El decodificador base64 "mime" aceptaba una constancia con basura sin avisar
- [x] Estado: resuelto
- **Fecha:** 2026-10-28
- **Fase:** 5
- **Qué pasó:** para leer la constancia (`applicationResponse`) usé `Base64.getMimeDecoder()`, porque la SUNAT puede partir el base64 en líneas. Ese decodificador ignora en silencio los caracteres que no son base64: el texto `@@@@` se decodificaba como una constancia vacía y `esto no es base64!!` como bytes a medias, en vez de fallar. Lo descubrió el test de "constancia que no es base64".
- **Cómo quedó:** `SoapResponseParser.leerSendBill` quita los espacios y saltos de línea y decodifica con `Base64.getDecoder()` (estricto). Un texto que no es base64 lanza `SunatRespuestaInvalidaException`. Los tests cubren tanto el base64 partido en líneas (válido) como la basura (inválida).

### E-020 Interbloqueo (deadlock) al pedir el correlativo con `INSERT IGNORE` y `SELECT ... FOR UPDATE`
- [x] Estado: resuelto
- **Fecha:** 2026-10-28
- **Fase:** 6
- **Qué pasó:** la primera versión de `SerieService.siguiente` hacía `INSERT IGNORE` de la fila de la serie y luego `SELECT ... FOR UPDATE` en la misma transacción. Con 50 hilos a la vez, MySQL abortó transacciones con "Deadlock found when trying to get lock": cada `INSERT IGNORE` de una fila que ya existe deja un bloqueo compartido sobre ella, y después todos piden el exclusivo y se esperan entre sí. Lo encontró el test de 50 hilos con transacciones que fallan.
- **Cómo quedó:** la creación de la serie sale de esa transacción (`SerieService.asegurar`, que se llama antes), y `siguiente` solo hace el `SELECT ... FOR UPDATE`. El test de 50 hilos se repitió 4 veces seguidas sin fallar.

### E-021 Una transacción anidada para crear la serie agotaba las conexiones
- [x] Estado: resuelto
- **Fecha:** 2026-10-28
- **Fase:** 6
- **Qué pasó:** el primer arreglo de E-020 creaba la serie en una transacción nueva (`REQUIRES_NEW`) dentro de la del comprobante. Cada hilo necesitaba una segunda conexión mientras guardaba la primera; con 50 hilos y un pool de 10, todos esperaban conexión y ninguno la liberaba ("Connection is not available, request timed out").
- **Cómo quedó:** se descartó esa idea. `asegurar` se llama antes de abrir la transacción principal, así que nunca hace falta una segunda conexión mientras se tiene la primera.

### E-022 Seguía habiendo un interbloqueo raro aun después de E-020
- [x] Estado: resuelto (con una defensa extra)
- **Fecha:** 2026-10-28
- **Fase:** 6
- **Qué pasó:** corriendo toda la suite, el test de 50 hilos volvió a fallar una vez con "Deadlock found" en el `INSERT IGNORE` de `asegurar`, que se ejecutaba en cada petición aunque la serie ya existiera (toma un bloqueo compartido sobre la fila existente y compite con quien la tiene bloqueada).
- **Cómo quedó:** `asegurar` primero hace una lectura normal (sin bloqueos) y solo inserta si la serie no existe. El test de 50 hilos se repitió 6 veces seguidas sin fallar. Además, como MySQL puede abortar una transacción por interbloqueo aunque el código esté bien, `ComprobanteServiceImpl` repite hasta 3 veces la transacción de reserva (que se deshace entera, número incluido); dos tests lo comprueban.

### E-023 El nuevo campo obligatorio `idExterno` rompió los tests del DTO
- [x] Estado: resuelto
- **Fecha:** 2026-10-28
- **Fase:** 6
- **Qué pasó:** al agregar `idExterno` (obligatorio) a `FacturaRequest`, 9 tests de validación de `FacturaRequest` dejaron de pasar porque sus datos no lo tenían. Se vio al correr toda la suite y no solo los tests nuevos.
- **Cómo quedó:** se actualizaron los datos de prueba y se agregaron tests de la validación del `idExterno`. Moraleja: correr siempre la suite completa antes de cerrar un paso.

### E-024 La máquina de estados del resumen no permitía rechazarlo desde `GENERADO` ni `ERROR_TEMPORAL`
- [x] Estado: resuelto
- **Fecha:** 2026-10-28
- **Fase:** 8
- **Qué pasó:** al escribir el servicio del resumen apareció el caso de que la SUNAT conteste un error de datos (por ejemplo `0306`) justo al enviar, sin llegar a dar ticket. El resumen estaba en `GENERADO` o `ERROR_TEMPORAL` y la regla de estados no dejaba pasar a `RECHAZADO`.
- **Cómo quedó:** `GENERADO` y `ERROR_TEMPORAL` también pueden ir a `RECHAZADO` (12 transiciones permitidas, con test). Un resumen rechazado se corrige y se reenvía con el mismo nombre.

### E-025 La consulta de una factura mostraba también las boletas
- [x] Estado: resuelto
- **Fecha:** 2026-10-28
- **Fase:** 8
- **Qué pasó:** `GET /api/v1/facturas/{id}` y su `/xml` buscaban por el id sin mirar el tipo, así que con el id de una boleta devolvían la boleta (y el XML con nombre de factura `-01-`).
- **Cómo quedó:** el servicio de facturas solo encuentra comprobantes que no sean boletas; las boletas tienen sus propios endpoints. Test con mutación (se quita el filtro y el test falla).

### E-026 Con el log de Spring en DEBUG la clave SOL salía en el log
- [x] Estado: resuelto
- **Fecha:** 2026-10-28
- **Fase:** 9
- **Qué pasó:** el test de secretos (`SecretosNoSeFiltranTest`, que mira todo el log a nivel DEBUG/TRACE) encontró que el cliente HTTP de Spring escribe el cuerpo completo de cada petición (`Writing [<soapenv:Envelope ...`) cuando su log está en DEBUG. El sobre SOAP lleva la clave SOL en el `UsernameToken`. A nivel INFO (el normal) no salía.
- **Cómo quedó:** el cuerpo se manda como bytes (`byte[]`) y no como texto, así que Spring escribe `[B@...` y no el contenido. El test pasó de rojo a verde con ese cambio y vuelve a ponerse rojo si se deshace.

### E-027 Con el perfil prod, Flyway migraba la base antes de negarse a arrancar
- [x] Estado: resuelto
- **Fecha:** 2026-10-28
- **Fase:** 9
- **Qué pasó:** en la prueba de humo con Docker, la aplicación con la configuración del beta y el perfil `prod` se negó a arrancar, pero antes ya había creado las tablas (Flyway corre al crear la conexión y el validador era un bean más).
- **Cómo quedó:** `ArranqueProduccionConfig` hace que la base de datos dependa del validador, así que la configuración se revisa primero. Test: no aparece ningún mensaje de Hikari ni de Flyway cuando la configuración no sirve (rojo sin el cambio). En el contenedor real se comprobó que la base queda sin tablas.

### E-028 Otro interbloqueo, ahora al crear la serie por primera vez
- [x] Estado: resuelto
- **Fecha:** 2026-10-28
- **Fase:** 9
- **Qué pasó:** corriendo toda la suite (2129 tests) falló una vez `facturasDistintasALaVezRecibenNumerosDistintosYSeguidos` con "Deadlock found" en el `INSERT IGNORE` de `asegurar`. Es el caso que E-022 no cubría: 50 hilos creando a la vez una serie que todavía no existe (la lectura previa da "no existe" en todos y todos hacen el `INSERT IGNORE`). El reintento por interbloqueo solo protegía la reserva del número, no la creación de la serie.
- **Cómo quedó:** `asegurar` también se repite hasta 3 veces ante un interbloqueo (en facturas y en boletas); al repetir, la serie ya existe. Test con un interbloqueo simulado (rojo sin el reintento).

### E-029 Dos tests de la fase 9 pasaban en mi máquina y fallaron en el CI
- [x] Estado: resuelto
- **Fecha:** 2026-10-28
- **Fase:** 9
- **Qué pasó:** (1) `RespaldoScriptTest`: con `localhost` como host, `mysqldump`/`mysql` usan el socket local (`/var/run/mysqld/mysqld.sock`) y no el puerto; en el CI la base está en un contenedor y el socket no existe ("Can't connect to local MySQL server through socket"). En mi máquina el socket sí existía. (2) `ApiKeyPuntaAPuntaTest`: probaba "con llave correcta un POST llega al controlador" con `POST /resumenes/generar`, que depende de lo que haya en la base: en el CI había boletas pendientes de otros tests y el envío falló (500).
- **Cómo quedó:** el script y el test usan `--protocol=tcp`; el test de la llave usa un `POST /resumenes/999999/reenviar` (404 que no depende de la base ni de la SUNAT). Moraleja: un test que depende del estado compartido de la base o de lo que existe en esta máquina se rompe en otro lado.

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
- [x] Estado: resuelto (fase 8)
- **Qué significa:** no se soporta el envío individual de boletas hasta confirmar en el beta qué responde la SUNAT.
- **Cómo quedó:** las boletas nunca se mandan por `sendBill`: `POST /api/v1/boletas` solo las guarda y firma, y el resumen diario las informa.

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
- [x] Estado: resuelto (fase 8)
- **Qué significa:** en lo leído no figura cuántos días hay para informar las boletas.
- **Cómo quedó:** la guía del resumen de boletas dice que se envía "el día en que se emitieron las boletas o a más tardar hasta el sétimo día calendario contado desde el día siguiente". Es `sunat.resumen.plazo-dias=7`. Pasado ese plazo la SUNAT acepta con la observación `4443` (fuera de plazo), según las reglas de validación; eso no se comprobó en el beta.

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

### L-018 El RUC del certificado se busca solo en la unidad organizativa (OU)
- [ ] Estado: diferido (se ajusta si mi certificado real lo trae en otro lugar)
- **Qué significa:** `CertificadoLoader` lee el RUC de los campos `OU` del titular (puede venir solo o con texto, como `RUC 20100066603`). Si el certificado lo trae en otro campo (por ejemplo `SERIALNUMBER`), se rechaza con el mensaje "no trae el RUC en la unidad organizativa".
- **Por qué se deja:** la documentación de la SUNAT indica que el RUC va en la OU y no tengo un certificado real para comprobar otras formas. Se confirma en la fase 9 con el certificado verdadero.

### L-019 Si el archivo trae varias claves, se usa la primera
- [ ] Estado: diferido (comportamiento decidido)
- **Qué significa:** un `.p12` puede guardar más de una clave privada. `CertificadoLoader` usa la primera que encuentra, sin importar su alias.
- **Por qué se deja:** los certificados de la SUNAT traen una sola. Si algún día hace falta elegir, se agrega una propiedad con el alias.

### L-020 El beta no valida los números de la factura
- [ ] Estado: diferido (se vuelve a probar en producción, fase 9)
- **Qué significa:** en la prueba real, una factura con el importe total cambiado (71.00 en vez de 70.00) fue **aceptada** por el beta. En producción la SUNAT sí rechaza las diferencias aritméticas (reglas `3270` y parecidas, ver `docs/sunat/CALCULOS.md`).
- **Por qué se deja:** el beta no sirve para comprobar que nuestros cálculos son correctos. La única defensa es la calculadora y el verificador de reglas con fuentes independientes (fases 2 y 3). Esa confianza se confirma con las primeras facturas reales.

### L-021 El beta acepta una boleta por `sendBill`
- [ ] Estado: diferido (se vuelve a probar en producción, fase 9)
- **Qué significa:** una boleta (`B001`, tipo `03`) enviada por `sendBill` fue aceptada por el beta; las notas esperaban el error `1079` ("solo puede enviar el comprobante en un resumen diario"), que sí está en las Reglas de validación. En producción habrá que usar siempre el resumen diario.
- **Por qué se deja:** esta API solo emite facturas por `sendBill`; las boletas van por resumen diario (fase 8). No hay que apoyarse en el beta para detectar el error.

### L-022 El beta acepta fechas de emisión viejas y futuras
- [ ] Estado: diferido (se vuelve a probar en producción, fase 9)
- **Qué significa:** facturas con fecha de hace 30 días, de hace 2 días y de mañana fueron aceptadas. En producción el plazo de envío es corto (ver E-015, código `2108`).
- **Por qué se deja:** esta API siempre pone la fecha y hora actuales de Lima (`FacturaServiceImpl`), así que no se puede enviar una fecha vieja por accidente. El plazo real se valida en producción.

### L-023 La reserva del número es en serie: una a la vez
- [ ] Estado: diferido (se mide si algún día hace falta)
- **Qué significa:** mientras una petición arma y firma el XML, tiene bloqueada la fila de la serie; las demás peticiones esperan. Cada reserva dura decenas de milisegundos, así que el máximo ronda las decenas de facturas por segundo.
- **Por qué se deja:** es lo que garantiza que no haya saltos ni repetidos, y una tienda por WhatsApp está muy lejos de ese límite. Si hiciera falta, se reservarían bloques de números.

### L-024 Una sola serie para las facturas
- [ ] Estado: diferido
- **Qué significa:** `sunat.serie-factura` es una sola serie (por defecto `F001`). La tabla `series` ya admite varias, pero la API no permite elegir.
- **Por qué se deja:** hasta ahora no hace falta más de una.

### L-025 La petición `POST` es síncrona
- [ ] Estado: diferido
- **Qué significa:** el `POST` espera la respuesta de la SUNAT (1 a 3 segundos en el beta; el timeout de lectura es 30 s). Si la SUNAT está lenta, mi agente espera.
- **Por qué se deja:** es lo más simple y deja el resultado a la vista. Si molestara, el POST podría responder 202 al instante y dejar el envío al planificador de la fase 7.

### L-026 Una factura vencida deja un hueco en la numeración de la SUNAT
- [ ] Estado: diferido (se resuelve en la fase 9 con la SUNAT real)
- **Qué significa:** si una factura pasa a `VENCIDO` sin haber llegado nunca a la SUNAT, su número queda sin informar y la siguiente ya usa el siguiente. No se vuelve a emitir con el mismo número porque su fecha ya es vieja.
- **Por qué se deja:** no sé aún si la SUNAT exige informar los números saltados (por ejemplo con una comunicación de baja). Se confirma en la fase 9 y, si hace falta, se agrega la baja en la parte opcional del plan.

### L-027 `getStatusCdr` solo está probado con respuestas simuladas
- [ ] Estado: diferido (se confirma en la fase 9)
- **Qué significa:** el servicio `billConsultService` solo existe en producción. La estructura del mensaje y los códigos de respuesta (`0001` aceptado, `0002` rechazado, `0011` no existe...) salen del manual y de la guía de consulta integrada, y no se pudieron comprobar contra la SUNAT. Por eso `sunat.consulta-cdr.habilitada` está apagado por defecto.
- **Por qué se deja:** el beta no lo tiene. Mientras esté apagado, un reintento reenvía directamente, que en el beta es seguro porque devuelve la misma constancia.

### L-028 Los reintentos corren en la misma aplicación
- [ ] Estado: diferido
- **Qué significa:** el planificador es un `@Scheduled` dentro de la aplicación, sin cola ni servicio aparte. Si hubiera varias copias, el control de versiones (`@Version`) y los estados evitan enviar el mismo comprobante dos veces, pero se desperdicia algo de trabajo en las pasadas simultáneas.
- **Por qué se deja:** una sola copia alcanza para este volumen.

### L-029 Un comprobante `REVISAR` espera a una persona
- [ ] Estado: diferido
- **Qué significa:** los códigos desconocidos, `1033` y las respuestas raras de la consulta de constancias dejan el comprobante en `REVISAR`. Nadie los avisa todavía: solo quedan en el log y en la base.
- **Por qué se deja:** hay que saber primero cuáles aparecen de verdad. Se puede agregar un aviso (por ejemplo al agente) más adelante.

### L-030 Un resumen rechazado se reenvía igual, no se puede corregir una boleta
- [ ] Estado: diferido
- **Qué significa:** si la SUNAT rechaza el resumen, `POST /api/v1/resumenes/{id}/reenviar` manda el mismo XML con el mismo nombre. Sirve cuando el rechazo fue por algo pasajero o de configuración, pero no hay forma de sacar una boleta con problemas del resumen o cambiarle datos: las boletas quedan `EN_RESUMEN` con la explicación anotada. El rechazo por boleta tampoco se modela (la SUNAT rechaza o acepta el resumen entero).
- **Por qué se deja:** hasta ver rechazos reales no se sabe qué corrección hace falta. Las boletas se validan antes de guardarlas (documento, monto, RUC de la serie), así que el rechazo debería ser raro.

### L-031 Las observaciones se asignan a la boleta por el texto
- [ ] Estado: diferido
- **Qué significa:** cuando la SUNAT acepta el resumen con observaciones, cada nota se asigna a la boleta cuyo número (por ejemplo `B001-12`) aparezca en el texto. Las notas que no nombran ninguna boleta se agregan a la descripción del resumen. Es lo mejor posible sin ver una constancia real con observaciones de un resumen.
- **Por qué se deja:** no se pudo provocar una observación en el beta.

### L-032 Un ticket sin respuesta se consulta para siempre
- [ ] Estado: diferido
- **Qué significa:** mientras la SUNAT conteste "en proceso" (`98`) o no responda, el resumen sigue `ENVIADO` y se vuelve a consultar con espera creciente (hasta 60 minutos). No hay un tope de intentos ni se sabe cuánto dura un ticket en la SUNAT.
- **Por qué se deja:** el beta resolvió todos los tickets en segundos. Se ve con la SUNAT real (fase 9).

### L-033 El resumen solo informa boletas nuevas
- [ ] Estado: diferido (opcional, con las notas de crédito y la baja)
- **Qué significa:** las líneas del resumen llevan siempre el estado `1` (adicionar). Modificar (`2`) o anular (`3`) una boleta ya informada necesita notas de crédito/débito o baja, que no están hechas.

### L-034 Las boletas de después de la hora del resumen esperan al día siguiente
- [ ] Estado: aceptado
- **Qué significa:** el resumen se arma a la hora de `sunat.resumen.cron` (22:00 de Lima por defecto). Una boleta emitida después se informa en la siguiente corrida, con el nombre del día de su emisión (`RC-AAAAMMDD-N`), dentro del plazo de 7 días. `POST /api/v1/resumenes/generar` permite armar el resumen a mano cuando se quiera.

### L-035 Hay una sola llave de API, sin usuarios ni permisos
- [ ] Estado: aceptado
- **Qué significa:** quien tiene la llave puede emitir, consultar y reenviar todo. No hay llaves distintas por agente, ni vencimiento, ni límite de peticiones, ni registro de quién emitió qué (solo el `idExterno` que manda quien llama).
- **Por qué se deja:** hay un solo cliente (el agente). Rotar la llave = cambiar `SUNAT_API_KEY` y reiniciar.

### L-036 La API habla HTTP plano
- [ ] Estado: aceptado
- **Qué significa:** la aplicación no trae HTTPS. La llave de la API y los datos de los clientes viajan sin cifrar si se expone el puerto 8081 directamente.
- **Por qué se deja:** se pone detrás de un proxy con HTTPS (`docs/PRODUCCION.md`, sección 4) o solo en red local.

### L-037 El respaldo es manual o por cron, y no incluye el certificado
- [ ] Estado: diferido
- **Qué significa:** `scripts/respaldo.sh` respalda la base (XML y constancias). Programarlo, guardarlo fuera del servidor y respaldar el `.p12` y el `.env` (que no están en la base) lo hace quien administre el servidor.

### L-038 La imagen de Docker no tiene healthcheck
- [ ] Estado: diferido
- **Qué significa:** no hay un endpoint de salud (no se usa Actuator) y `docker compose` no sabe si la API responde; solo si el proceso vive (`restart: unless-stopped`).
