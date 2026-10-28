# Certificados de prueba

Son certificados **autofirmados** que solo sirven para los tests. No tienen validez ante la SUNAT y la clave de todos es `prueba123`.
Se generaron con `keytool` (viene con el JDK). El RUC va en `OU`, igual que en los certificados reales de la SUNAT.

| Archivo | Para qué sirve | Vigencia |
|---|---|---|
| `valido.p12` | RUC `20100066603` (el del beta), caso feliz | 2026-01-01 a 2035-12-30 |
| `vencido.p12` | mismo RUC, ya vencido | 2020-01-01 a 2020-01-31 |
| `futuro.p12` | mismo RUC, todavía no vigente | desde 2040-01-01 |
| `otro-ruc.p12` | RUC `20512345678`, distinto al emisor | 2026-01-01 a 2035-12-30 |
| `sin-ruc.p12` | sin `OU`, no se puede saber a qué RUC pertenece | 2026-01-01 a 2035-12-30 |
| `corrupto.p12` | un texto cualquiera, no es un PKCS12 | — |

## Cómo se generaron

```bash
gen() { # archivo alias dn fecha-inicio dias-de-validez
  keytool -genkeypair -alias "$2" -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -dname "$3" \
    -startdate "$4" -validity "$5" -storetype PKCS12 -keystore "$1" -storepass prueba123 -keypass prueba123
}
gen valido.p12   emisor "CN=EMPRESA DE PRUEBAS SAC, OU=20100066603, O=EMPRESA DE PRUEBAS SAC, C=PE" "2026/01/01 00:00:00" 3650
gen vencido.p12  emisor "CN=EMPRESA DE PRUEBAS SAC, OU=20100066603, O=EMPRESA DE PRUEBAS SAC, C=PE" "2020/01/01 00:00:00" 30
gen futuro.p12   emisor "CN=EMPRESA DE PRUEBAS SAC, OU=20100066603, O=EMPRESA DE PRUEBAS SAC, C=PE" "2040/01/01 00:00:00" 3650
gen otro-ruc.p12 emisor "CN=OTRA EMPRESA SAC, OU=20512345678, O=OTRA EMPRESA SAC, C=PE" "2026/01/01 00:00:00" 3650
gen sin-ruc.p12  emisor "CN=SIN RUC, O=SIN RUC, C=PE" "2026/01/01 00:00:00" 3650
echo "no es un p12" > corrupto.p12
```

Los tests que dependen de la fecha usan un reloj fijo, no la fecha real, para que `valido.p12` no deje de servir cuando pase el tiempo.
