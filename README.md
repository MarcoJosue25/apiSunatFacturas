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

Recién arrancando. Lo voy haciendo por fases, despacio y con tests en cada paso. El plan completo, con los pasos y su check, está en [`docs/PLAN.md`](docs/PLAN.md).

1. [ ] Cimientos
2. [ ] Conexión con el beta de la SUNAT
3. [ ] Los números (cálculos exactos)
4. [ ] XML de factura
5. [ ] Firma digital
6. [ ] Enviar la factura al beta
7. [ ] Guardar y exponer por REST
8. [ ] Reintentos
9. [ ] Boletas y resumen diario
10. [ ] Producción

Los errores que me van saliendo y las limitaciones que dejo para después están en [`docs/ERRORES.md`](docs/ERRORES.md).

## Stack

Java 21, Spring Boot y Maven. MySQL con Flyway más adelante, igual que en mi agente.

## Documentación

Todo lo que fui leyendo de la SUNAT está en [`docs/sunat/`](docs/sunat/). Las notas más importantes (flujo, clasificación de errores y reintentos) están en [`docs/sunat/NOTAS.md`](docs/sunat/NOTAS.md).

## Aviso

Es un proyecto personal. No tiene ninguna relación oficial con la SUNAT.
