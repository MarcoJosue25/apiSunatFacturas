# Observaciones

Aquí van los detalles que voy notando mientras avanzo y que no son un error ni una limitación: decisiones, particularidades de la SUNAT o de las herramientas, cosas raras, datos útiles para más adelante. Lo anoto en el momento, por pequeño que sea.

Lo que sí es un error o una limitación va en [`ERRORES.md`](ERRORES.md). Si algo de aquí se vuelve un problema, se pasa allá con su número.

Cómo se lee: **O-NNN** es el número, y entre paréntesis la fase en que se notó.

---

## SUNAT

### O-001 Casi todas las reglas de cálculo toleran ±1, pero una es exacta (fase 2)
Las Reglas de validación dicen "con una tolerancia ± 1" en casi todas las comparaciones de montos. La excepción es la regla `3272` (la base del IGV de la línea debe ser igual al valor de venta del ítem), que no menciona tolerancia y se trata como exacta. No sé con certeza si el 1 es un sol; lo tomo así y no dependo de ello: calculo exacto y me pongo márgenes de la mitad (0.50).

### O-002 La guía XML mezcla dos estructuras (fase 3)
La guía de factura 2.1 muestra el tipo de operación en `cbc:ProfileID` y los datos de las partes en `cac:PartyTaxScheme`, pero las Reglas de validación de 2026 piden el tipo de operación como atributo `listID` de `InvoiceTypeCode` y los datos de las partes en `cac:PartyIdentification` y `cac:PartyLegalEntity`. Sigo las **Reglas de validación**, que son las que la SUNAT aplica y las más nuevas.

### O-003 La serie de números (4 dígitos) solo existe para comprobantes impresos (fase 2)
El manual y las reglas permiten series numéricas (`[0-9]{1,4}-[0-9]{1,8}`) pero son de talonarios impresos de contingencia. Las electrónicas son `F###` (factura) y `B###` (boleta). La serie de la factura se escribe sin ceros en el correlativo: `F001-4355`.

### O-004 El WSDL del beta tiene una operación que el manual no menciona (fase 1)
Además de `sendBill`, `sendSummary`, `sendPack` y `getStatus`, el beta publica `getStatusAR`. No la uso ni sé para qué sirve; queda anotada por si aparece en la documentación.

### O-005 La SUNAT cuenta con 869 códigos, la mayoría en 2000-3999 (fase 1)
14 en 0xxx, 19 en 1xxx, 678 en 2xxx-3xxx y 158 en 4xxx+. Están en `src/main/resources/sunat/codigos_sunat.csv`.

### O-006 En el Excel de Reglas de validación, la primera hoja se llama "General " con un espacio al final (fase 1)
Al leerlo con código hay que buscar la hoja por posición, no por nombre.

### O-007 El RUC de ejemplo del manual cumple el dígito verificador (fase 3)
`20100066603` y el RUC de la propia SUNAT `20131312955` pasan la validación módulo 11. Las reglas de la SUNAT validan además que el RUC del cliente exista (`1083`), cosa que no podemos comprobar localmente.

### O-008 El XSD no admite un `ExtensionContent` vacío (fase 3)
Por eso el XML sin firmar no pasa el esquema: se valida ya firmado. El esquema además revisa la estructura interna de `ds:Signature`, así que una firma mal armada también se detecta antes de enviar.

### O-009 Las series de pruebas del beta no se validan con el RUC propio (fase 1)
El beta usa `[RUC]MODDATOS` y parece no verificar la clave (ver L-013). Falta probar si acepta mi RUC 10; se hace en la fase 5.

## Herramientas y proyecto

### O-010 Spring Boot 4 cambió varios nombres respecto de la versión 3 (fases 0 y 1)
- El starter web se llama `spring-boot-starter-webmvc` (y `...-webmvc-test`).
- `@WebMvcTest` está en `org.springframework.boot.webmvc.test.autoconfigure`.
- `@MockitoBean` está en `org.springframework.test.context.bean.override.mockito`.
- Jackson 3 vive en `tools.jackson` (`JsonMapper`), pero las anotaciones siguen en `com.fasterxml.jackson.annotation`.
- `TestRestTemplate` ya no viene en el starter de pruebas: en los tests uso `RestClient`.

### O-011 `mvn -q` esconde los tests que pasan (fase 0)
Con `-q` un éxito no imprime nada. Para ver cuántos tests corrieron se usa sin `-q` y se filtra la salida con `grep "Tests run:"`.

### O-012 Maven Central dio un 429 una sola vez (fase 0)
Fue por límite de peticiones y se resolvió solo al reintentar. No es un problema del proyecto.

### O-013 `xmllint` está en el entorno de Claude (fase 3)
Sirve para validar un XML contra el XSD desde la terminal en segundos, y fue útil para armar el XML de referencia antes de escribir el validador en Java.

### O-014 Una anotación de Java no acepta expresiones (fase 3)
`@ValueSource(strings = {"A".repeat(200)})` no compila. Los tests con textos largos reciben la longitud (`ints`) y arman el texto adentro.

### O-015 Los mensajes del validador XSD dependen del idioma de la máquina (fase 3)
Xerces devuelve los errores en el idioma del sistema. Por eso los tests no comparan el texto del mensaje de la SUNAT o de Xerces, sino que comprueban cuántos errores hay y nombres de elementos.

### O-016 El puerto de esta API es el 8081 (fase 0)
El agente (`saas-agent`) usa el 8080, así que se pueden correr los dos a la vez.

### O-017 Verificación de que los tests de verdad detectan fallos (fases 1 a 3)
Después de escribir los tests de cada pieza importante, le provoco fallos a propósito al código (cambio un redondeo, quito una validación, abro un DTD externo) y compruebo que los tests se ponen rojos. Dos veces encontró tests que no probaban lo que decían (el prefijo del RUC y un caso de NBSP), y los arreglé.

## Decisiones de diseño

### O-018 El XML sale compacto, sin sangrías (fase 3)
El documento se firma tal como se escribe, así que no agrego espacios entre elementos. El JDK además escribe `standalone="no"` en la declaración, igual que el ejemplo del manual de la SUNAT.

### O-019 El texto se limpia con `TextoSunat` antes de entrar al XML (fase 3)
Normaliza las letras con tilde escritas en dos partes (NFC), cambia saltos de línea y espacios raros por un espacio, descarta caracteres de control y reemplaza lo que no existe en ISO-8859-1 (ver L-017).

### O-020 El monto en letras usa la RAE (fase 2)
`VEINTIUN MIL`, `UN MILLON`, `MIL UN MILLONES`, `CIEN`, `CIENTO UNO`. La guía de la SUNAT tiene ejemplos con otras formas (`CINCUENTICUATRO`, `Y 00/100`) porque el campo es texto libre. La librería de comparación (`num2words`) no apocopa (`veintiuno mil`); se normalizó solo esa diferencia.

### O-021 El ticket se acepta solo si son números de hasta 30 dígitos (fase 1)
Es una suposición mía (el ejemplo del manual tiene 15 dígitos). Si la SUNAT usa otro formato, se ajusta en `SunatController`.

### O-022 Dos movimientos del `Clock` por la regla de "no implementar lo que no se usa" (fases 2 y 3)
Pasó de la fase 2 a la 3 y de la 3 a la 5: lo usa la orquestación del envío, no el armador del XML, que recibe la fecha como parámetro.

### O-023 El peor desvío de redondeo por línea es 0.0058 (fase 2)
Revisando todos los totales de 0.01 a 2000.00, el IGV de una línea se aleja como máximo 0.0058 del 18% de su base, con el total 0.23. Está por debajo de la cota teórica de 0.0059.

### O-024 Cada comprobación de una regla usa dos fuentes (fases 2 y 3)
Los valores esperados de los cálculos, los RUC y los números en letras salen de programas independientes (Python con `Decimal`, `num2words`) y no de la misma fórmula del código. El verificador `ReglasSunatXml` (en los tests) recalcula las reglas de la SUNAT leyendo los números del XML, sin usar la calculadora.

### O-025 El primer test lento de un arranque no es un error del código (fase 1)
Con la aplicación recién arrancada, la primera consulta tarda más (ver E-014). Los tests con tiempo usan márgenes amplios.

### O-026 El orden de los `xmlns` de la raíz lo decide el JDK (fase 3)
Al escribir el XML, el JDK pone `xmlns:cac`, `xmlns:cbc`, `xmlns:ds`, `xmlns:ext` y al final el `xmlns` por defecto. El orden no importa para la SUNAT ni para la firma (la canonicalización lo normaliza). Los tests solo comprueban que cada espacio de nombres se declare una vez.

### O-027 Un catálogo con un solo valor no se vuelve enum todavía (fase 3)
El tipo de documento de identidad es siempre `6` (RUC), la afectación del IGV es siempre `10` y la unidad de medida se valida con un patrón. Los enums se crean cuando haya un segundo valor que los justifique (por ejemplo, DNI en las boletas, en la fase 8).

### O-028 El detalle de la guía se contrasta con las Reglas, no con la guía (fase 3)
Para saber qué elementos son obligatorios sirve más la lista de errores de las Reglas de validación (`No existe el Tag UBL...`) que la guía, porque es lo que la SUNAT realmente exige. Ejemplos que solo se ven ahí: la forma de pago (`FormaPago`, error `3244`) y el atributo `listID` del tipo de operación (error `3205`).


### O-029 El `Clock` pasó de la fase 5 a la fase 4 (fase 4)
El `Clock` con la zona de Lima se iba a crear en la fase 5, pero la validación de la vigencia del certificado (fase 4) también necesita saber la fecha actual. Se creó antes (`config/ClockConfig`) para no usar `Instant.now()` suelto, que no se puede probar con fechas controladas.

### O-030 Lima no tiene horario de verano (fase 4)
`America/Lima` es siempre UTC-5. Aun así se usa la zona con nombre y no un desplazamiento fijo, para que el código diga claramente que son fechas de Perú. Un test comprueba los 12 meses.

### O-031 El certificado es opcional al arrancar, pero ruta y clave van juntas (fase 4)
La aplicación arranca sin certificado (para poder consultar tickets, que no firman). Si se pone la ruta sin la clave, o la clave sin la ruta, no arranca: es una configuración a medias y conviene enterarse al arrancar y no al firmar la primera factura. El error de "no hay certificado" al firmar lo da el `CertificadoLoader` (paso siguiente).

### O-032 La clave del certificado no se recorta (fase 4)
La ruta vacía o con solo espacios cuenta como "sin ruta", pero de la clave solo se trata como ausente la cadena vacía: un espacio podría ser parte de una clave real. Un test lo fija.

### O-033 Los certificados de prueba no deben vencer a mitad del proyecto (fase 4)
`valido.p12` vence en 2035, pero aun así los tests de vigencia usan un reloj fijo y no la fecha real. Así no se rompen solos con el paso del tiempo (como pasa con los tests que dependen de `now()`).

### O-034 `keytool -startdate` permite crear certificados vencidos y futuros (fase 4)
No hace falta esperar ni manipular el reloj del sistema para tener un certificado vencido: `-startdate "2020/01/01 00:00:00" -validity 30` lo genera ya vencido.

### O-035 Solo se guarda el certificado del emisor, sin la cadena (fase 4)
El plan hablaba de "la cadena" de certificados. Para firmar basta la clave privada y el certificado del emisor, que es el que va dentro del `KeyInfo` de la firma. La cadena se agregará solo si la SUNAT la pide.

### O-036 Una clave incorrecta y un archivo dañado dan errores distintos (fase 4)
El JDK avisa de la clave equivocada con un `IOException` cuyo mensaje dice "password was incorrect" (o cuya causa es `UnrecoverableKeyException`); un archivo que no es PKCS12 da otro `IOException`. Se distinguen para que el mensaje diga qué arreglar. El mensaje nunca incluye la clave; un test lo comprueba con varias claves equivocadas.

### O-037 El JDK sí abre claves RSA de 512 bits, pero `keytool` avisa que son inseguras (fase 4)
Por eso la comprobación del mínimo de 1024 bits la hace nuestro código y no se deja al JDK. El archivo `rsa-512.p12` existe solo para probarla.

### O-038 `checkValidity` incluye los dos extremos (fase 4)
Un certificado se considera vigente justo en el segundo en que empieza y en el segundo en que vence; un segundo antes o después ya no. Un test lo fija con relojes de precisión de segundos.

### O-039 Una mutación mal hecha no cuenta (fase 4)
Al romper a propósito la validación de vigencia, el código ni compiló (el `catch` quedaba sin su excepción), así que no probaba nada. Se rehízo cambiando la fecha por una fija. Al hacer mutaciones se comprueba que fallen los tests, no que falle la compilación.

### O-040 La excepción del certificado todavía no tiene respuesta HTTP (fase 4)
`CertificadoInvalidoException` se lanza al firmar, que recién ocurre dentro del recorrido de la fase 5. Su mapeo en `ManejadorExcepciones` (una respuesta 500 con el mensaje, porque es un problema de configuración y no del cliente) se agrega cuando haya un endpoint que la pueda provocar.

### O-041 Firmar vuelve a escribir el XML y cambia el orden de los `xmlns` de la raíz (fase 4)
El XML se lee a un DOM, se le agrega la firma y se escribe de nuevo, y en esa segunda escritura el JDK pone los `xmlns` de la raíz en otro orden (`xmlns` por defecto primero). Fuera de la firma no cambia nada más. No importa para la SUNAT ni para la firma (la canonicalización normaliza el orden), y un test lo fija comparando sin los `xmlns`.

### O-042 La firma con RSA es siempre igual para el mismo XML (fase 4)
RSA PKCS#1 v1.5 no usa números al azar: firmar dos veces el mismo XML con el mismo certificado da exactamente los mismos bytes. Sirve para los reintentos: reenviar una factura firmada de nuevo es reenviar el mismo archivo. Un test lo comprueba.

### O-043 `XmlSigner` es genérico, no solo para facturas (fase 4)
Firma cualquier XML UBL con un único `ext:ExtensionContent` vacío, así que el resumen diario y las comunicaciones de baja (fase 8) lo reutilizan sin cambios. Por eso no se llama `FacturaSigner`.

### O-044 Un XML con un `DOCTYPE` se rechaza al firmar (fase 4)
Al leer el XML para firmarlo no se aceptan declaraciones `DOCTYPE`, así que no se puede usar una entidad externa para leer archivos del servidor (XXE). El XML que llega al firmador lo arma la propia aplicación, pero se protege igual porque es barato.

### O-045 Los tests de 999 ítems iguales volvieron a chocar con L-015 (fase 4)
Al probar la firma con 999 líneas del mismo precio, el armador rechazó la factura por el redondeo acumulado. Es la limitación ya anotada (L-015); el test usa precios distintos.

### O-046 Los mensajes de error de assertj pueden ser enormes (fase 4)
Un `isEqualTo` entre dos XML largos imprime ambos completos. Para comparar XML conviene un helper que dé una diferencia corta, o recortar la salida al leer los resultados.

### O-047 El mismo XML da siempre el mismo ZIP (fase 5)
`ZipUtil.comprimir` usa una fecha fija dentro del ZIP (2000-01-01) en vez de la hora actual. Así un reintento manda exactamente los mismos bytes que el primer intento. El ZIP guarda la hora de a 2 segundos, por eso el test espera 2.2 s entre las dos compresiones; con 1.1 s la mutación (usar la hora real) no se detectaba.

### O-048 La constancia (CDR) se lee con un tope de 5 MB descomprimidos (fase 5)
Una constancia real pesa unos pocos KB. `ZipUtil.leerXmlUnico` corta si lo descomprimido pasa de 5 MB sumando todos los archivos del ZIP, para que una respuesta rara (un "zip bomb": 6 MB de ceros caben en pocos KB comprimidos) no llene la memoria. Un test usa justo el tope y otro lo pasa.

### O-049 No hacía falta revisar si una entrada del ZIP es carpeta (fase 5)
Una carpeta como `dummy/` termina en `/`, no en `.xml`, así que ya se descarta por el nombre. Se probó quitando la comprobación y los tests siguieron igual: era código de más y se quitó.

### O-050 La `dummy/` de la SUNAT se ignora al leer, pero al enviar no se agrega (fase 5)
Algunos ejemplos antiguos de la SUNAT mandan el ZIP con una carpeta `dummy/` vacía. Por ahora se manda solo el XML; si el beta lo rechazara por eso se agrega (queda como punto a verificar en el plan).

### O-051 El cliente SOAP ahora es genérico por operación (fase 5)
`BillServiceClient.llamar(operacion, sobre, lector)` concentra lo común (HTTP, timeouts, errores SOAP con HTTP 500, tope de tamaño) y cada operación (`getStatus`, `sendBill`) solo dice qué sobre manda y cómo lee la respuesta. Los 28 tests de `getStatus` siguieron pasando sin tocarlos al hacer el cambio.

### O-052 `sendBill` nunca reintenta por su cuenta (fase 5)
El cliente hace una sola llamada. Si hay un timeout no se sabe si la SUNAT recibió la factura o no; decidir qué hacer (volver a enviar o consultar la constancia) es trabajo de la fase 7. Un test comprueba que ante un 503 hay una sola llamada.

### O-053 Java acepta bits sobrantes en el relleno de base64 (fase 5)
`PK==` decodifica sin error aunque los bits del último carácter no sean cero. Para el parser alcanza con que rechace los caracteres inválidos y el relleno mal puesto (`PK=`, `AAAA=A`); no se intentó ser más estricto que el JDK.

### O-054 El nombre del ZIP en el sobre solo admite letras, números y guiones (fase 5)
`SoapEnvelopeBuilder.sendBill` valida el nombre antes de ponerlo en el XML sin escapar. Todos los nombres reales (`20100066603-01-F001-1.zip`) cumplen. Rechazar el resto evita meter texto raro en el sobre.

### O-055 La estructura de la constancia (CDR) sale del manual del programador, no de una captura real (fase 5)
`CdrParser` se hizo con los ejemplos C.1 y C.2 del manual (páginas 47 a 50), guardados en `src/test/resources/sunat/cdr/` con la firma recortada. Cuando la prueba real del beta guarde una constancia verdadera hay que compararla con estas y agregarla como fixture.

### O-056 Las observaciones de una constancia son los `cbc:Note` de la raíz, con el formato "código - texto" (fase 5)
El manual (B.10) dice que las notas son advertencias de recepción que no rechazan el documento. `CdrParser` solo lee los `cbc:Note` hijos directos de la raíz y separa el código de cuatro dígitos del texto; si la nota no empieza con un código de cuatro dígitos, el código queda nulo y se guarda el texto completo.

### O-057 El rechazo puede llegar como constancia o como SOAP Fault (fase 5)
Según el manual, una constancia con `ResponseCode` distinto de cero es un rechazo con su propio `ApplicationResponse`; las NOTAS del proyecto dicen que lo habitual en `sendBill` es un SOAP Fault. `CdrParser` entiende la constancia y `SoapResponseParser` el Fault; el servicio del recorrido completo tendrá que tratar los dos. Qué hace el beta de verdad se anota tras la prueba real.

### O-058 La constancia de la SUNAT se firma con RSA-SHA1 (fase 5)
En los ejemplos del manual la firma de la SUNAT usa `rsa-sha1` y `sha1`. Nosotros no la verificamos (no es necesario para saber si nos aceptaron) y firmamos con SHA-256. Si algún día se verifica, hay que activar SHA-1 solo para ese caso.

### O-059 Las descripciones del CDR vienen partidas en líneas y se normalizan (fase 5)
El ejemplo del manual trae `La Factura numero FA01-981, ha sido\n aceptada` (con salto de línea). `CdrParser` deja un solo espacio entre palabras para que el mensaje se pueda mostrar y comparar.

### O-060 Lombok `@Singular` adivina mal el singular de "observaciones" (fase 5)
Generó `observacione(...)`, que no compiló en el uso. Se indica explícitamente `@Singular("observacion")`.

### O-061 La clase se llama `ClaseResultado` y no `ClaseError` (fase 5)
El enum incluye ACEPTADO y ACEPTADO_CON_OBSERVACIONES, que no son errores, así que `ClaseError` engañaba. Se cambió el nombre en el plan y en el código.

### O-062 Dos clases más que las del plan: `YA_REGISTRADO` y `DESCONOCIDO` (fase 5)
El plan listaba seis. `1033` (comprobante registrado antes con otros datos) no es un rechazo común: si una factura se envía dos veces porque el primer intento terminó en timeout, el segundo puede devolver `1033` y la factura **sí está registrada**. Merece su propia clase para que la fase 7 consulte la constancia en vez de darla por perdida. `DESCONOCIDO` hace explícita la regla "lo que no está en la tabla nunca se reintenta".

### O-063 Los códigos de ejemplo del manual (2047, 4001, 4031) ya no existen en las reglas de 2026 (fase 5)
Los ejemplos de constancia del manual usan `2047`, `4001` y `4031`, pero la tabla extraída de las Reglas de validación del 26/08/2026 no los tiene. Por eso el clasificador los trata como desconocidos. Un test lo deja escrito. Lo que importa de verdad se confirma con constancias reales del beta.

### O-064 `1080` se clasificó como configuración (fase 5)
"Debe enviar su comprobante por el SEE-Empresas supervisadas" no estaba en la lista de las notas, pero es del mismo tipo que `1078` y `1086`: depende de cómo está inscrito el emisor, no del documento. Es una decisión mía; si en la práctica resultara ser otra cosa, se mueve a datos.

### O-065 Una constancia con código de observación como respuesta se deja como desconocida (fase 5)
Las observaciones llegan como notas de una constancia aceptada (código `0`). Si un código de observación (por ejemplo `4287`) apareciera como `ResponseCode`, no sería una aceptación; se deja `DESCONOCIDO` para que alguien lo revise, en vez de inventar una regla.

### O-066 Una respuesta SOAP ilegible no se clasifica como transitoria (fase 5)
`SunatNoDisponibleException` (sin conexión, timeout, HTTP sin detalle) sí es transitoria, pero `SunatRespuestaInvalidaException` (una página HTML con HTTP 200, un XML cortado) no tiene clasificación todavía: no se sabe si la SUNAT llegó a procesar el comprobante. La decisión de qué hacer con ella queda para la fase 7.

### O-067 La tabla de códigos se movió a `src/main/resources/sunat/` (fase 5)
Ahora hay una sola copia (la que carga la aplicación) y los enlaces de `NOTAS.md` y de las observaciones apuntan ahí. Si la aplicación la encuentra mal formada (encabezado, columnas, código repetido, tipo desconocido, comillas sin cerrar) no arranca.

### O-068 Dos fallos reales del catálogo que encontraron los tests (fase 5)
`Map.copyOf(...).get(null)` lanza `NullPointerException`, y un tipo desconocido en la tabla lanzaba `IllegalArgumentException` en vez del error de arranque con el número de línea. Ambos arreglados.

### O-069 El manual escribe el ZIP en mayúsculas (`.ZIP`) y nosotros en minúsculas (fase 5)
Los ejemplos del manual (`20100066603-01-F001-1.ZIP` y `.XML`) están en mayúsculas. Se usa minúscula, que es lo que usan las librerías conocidas; si el beta rechazara el nombre por eso (códigos `0151` o `0159`), se cambia. Queda como punto a confirmar en la prueba real.

### O-070 El servicio siempre usa la hora de Lima, aunque el `Clock` esté en otra zona (fase 5)
Un test con un reloj en UTC encontró que `LocalDateTime.now(clock)` usaba la zona del reloj y ponía la fecha del día siguiente a las 22:30 de Lima. Ahora el servicio hace `clock.withZone(ZONA_LIMA)`, así que la fecha de emisión no depende de cómo se configure el reloj.

### O-071 El servicio devuelve los errores SOAP como resultado, pero deja pasar los de comunicación (fase 5)
Un `SunatFaultException` (la SUNAT contestó con un error) se convierte en un `ResultadoEmision` clasificado: es una respuesta definitiva. Un `SunatNoDisponibleException` o una respuesta ilegible se dejan subir como excepción: no se sabe si la SUNAT recibió la factura, y no se debe decir que se rechazó. La fase 7 decide cómo reintentar y consultar la constancia.

### O-072 El servicio no guarda nada todavía (fase 5)
`ResultadoEmision` trae el XML firmado pero no el ZIP de la constancia. La fase 6 (base de datos) lo agregará cuando haya dónde guardarlo.

### O-073 Si el XML armado no cumple el XSD es un error del programa (fase 5)
El servicio lanza `IllegalStateException` (no `IllegalArgumentException`): el cliente no tiene la culpa, el XML lo armamos nosotros. Llegará como un 500 por el manejador general.

### O-074 ¡El beta aceptó la primera factura firmada por nosotros! (fase 5)
Con el certificado de prueba autofirmado (`valido.p12`, RUC 20100066603), firma RSA-SHA256, ZIP con solo el XML (sin carpeta `dummy`), nombre en minúsculas (`.zip`) y base64 normal (sin MTOM), el beta contestó "La Factura numero F001-N, ha sido aceptada" en unos 1.5 s. Con eso quedan resueltos los puntos a verificar sobre `dummy`, MTOM, SHA-256 y mayúsculas/minúsculas del nombre (O-050, O-069).

### O-075 Lo que el beta SÍ valida (fase 5)
Firma presente (`2335`, "No signature in message!"), firma que coincide con el contenido (`2335`, "Incorrect reference digest value"), nombre del XML igual al del ZIP (`0161`), RUC del nombre igual al del XML (`1034`) y datos obligatorios del XML (`2075`, falta `UBLVersionID`). No verifica que el certificado esté registrado (ver L-013 y E-012).

### O-076 Los rechazos del beta llegan como SOAP Fault, también los códigos 2xxx (fase 5)
`2335` y `2075` están en el rango de "rechazos" (2000-3999) y se esperaba una constancia con ese código, pero llegaron como SOAP Fault. El clasificador los trata igual (por código), pero queda por confirmar en producción si un Fault con código 2xxx consume el número de la factura como un rechazo con constancia.

### O-077 Reenviar una factura ya aceptada devuelve la misma constancia (fase 5)
El mismo XML enviado dos veces devolvió dos veces la misma constancia aceptada (no el error `1033`). En una exploración, reenviar con el mismo número un XML distinto (otra hora) también devolvió la constancia aceptada. Para la fase 7 significa que, en el beta, reintentar después de un timeout es seguro; en producción puede aparecer `1033` (de ahí la clase `YA_REGISTRADO`).

### O-078 La consola de Maven muestra los acentos de los logs como "?" (fase 5)
Es solo la codificación de la salida del terminal del entorno; los textos en memoria están bien (los tests con tildes pasan).

### O-079 Cada prueba real usa un correlativo nuevo (fase 5)
Los números salen de la hora actual más un contador, para no repetir un número ya usado en corridas anteriores del beta.
