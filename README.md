# apiSunatFacturas

Una API para emitir facturas y boletas electrónicas directo con la SUNAT, sin intermediarios.

> Este proyecto está hecho con IA bajo mi guía y revisión. Lo pensé como una forma rápida, robusta y liviana de probar los comprobantes en el ambiente de pruebas (demo) de la SUNAT, sin lidiar con proveedores que cobran por consulta.

## Por qué lo hago

Estoy armando un agente de ventas para WhatsApp y otras redes, con un pequeño minicrm, y me pareció buena idea que también pueda emitir facturas y boletas. Los proveedores de facturación cobran por comprobante, y la SUNAT ofrece su propio servicio y un certificado digital tributario gratuito (hasta fines de 2027), así que prefiero hacerlo directo.

Es un servicio independiente: mi agente no sabe nada de XML ni de firmas, solo le pide "emite esta boleta" por REST y este proyecto se encarga del resto.

## Qué quiero que haga

- Armar el XML (UBL 2.1), firmarlo y enviarlo a la SUNAT.
- Leer el CDR (la constancia de respuesta) y guardar el resultado.
- Distinguir un error mío de una caída de la SUNAT, para reintentar solo cuando tiene sentido.
- Mandar las boletas por resumen diario, como pide la SUNAT.

## Estado

Recién arrancando. El plan, por etapas:

1. [ ] Consulta de prueba contra el servicio beta de la SUNAT
2. [ ] Boleta de punta a punta (XML, firma, resumen diario y CDR)
3. [ ] Factura
4. [ ] Guardar XML y CDR de cada comprobante
5. [ ] Reintentos cuando la SUNAT falla
6. [ ] Paso a producción con certificado propio

## Stack

Java 21, Spring Boot y Maven.

## Documentación

Todo lo que fui leyendo de la SUNAT está en [`docs/sunat/`](docs/sunat/). Las notas más importantes (flujo, clasificación de errores y reintentos) están en [`docs/sunat/NOTAS.md`](docs/sunat/NOTAS.md).

## Aviso

Es un proyecto personal. No tiene ninguna relación oficial con la SUNAT.
