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
14 en 0xxx, 19 en 1xxx, 678 en 2xxx-3xxx y 158 en 4xxx+. Están en `docs/sunat/codigos_sunat.csv`.

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

