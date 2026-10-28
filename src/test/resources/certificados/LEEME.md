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
| `ruc-con-texto.p12` | el RUC viene con texto y con otra OU delante: `OU=Ventas, OU=RUC 20100066603` | 2026-01-01 a 2035-12-30 |
| `ruc-de-doce-digitos.p12` | `OU=201000666030`, doce dígitos: no es un RUC | 2026-01-01 a 2035-12-30 |
| `ec.p12` | clave EC (curva) en vez de RSA, la SUNAT solo acepta RSA | 2026-01-01 a 2035-12-30 |
| `rsa-512.p12` | clave RSA de 512 bits, por debajo del mínimo | 2026-01-01 a 2035-12-30 |
| `rsa-1024.p12` | clave RSA de 1024 bits, justo el mínimo que acepta la SUNAT | 2026-01-01 a 2035-12-30 |
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
gen ruc-con-texto.p12 emisor "CN=EMPRESA DE PRUEBAS SAC, OU=Ventas, OU=RUC 20100066603, O=EMPRESA DE PRUEBAS SAC, C=PE" "2026/01/01 00:00:00" 3650
gen ruc-de-doce-digitos.p12 emisor "CN=EMPRESA DE PRUEBAS SAC, OU=201000666030, O=EMPRESA DE PRUEBAS SAC, C=PE" "2026/01/01 00:00:00" 3650
# las de clave EC y RSA pequeña usan otro algoritmo y tamaño (keytool avisa que son inseguras, es lo que se quiere probar)
keytool -genkeypair -alias emisor -keyalg EC -groupname secp256r1 -dname "CN=EMPRESA DE PRUEBAS SAC, OU=20100066603, O=EMPRESA DE PRUEBAS SAC, C=PE" -startdate "2026/01/01 00:00:00" -validity 3650 -storetype PKCS12 -keystore ec.p12 -storepass prueba123 -keypass prueba123
for bits in 512 1024; do keytool -genkeypair -alias emisor -keyalg RSA -keysize $bits -dname "CN=EMPRESA DE PRUEBAS SAC, OU=20100066603, O=EMPRESA DE PRUEBAS SAC, C=PE" -startdate "2026/01/01 00:00:00" -validity 3650 -storetype PKCS12 -keystore rsa-$bits.p12 -storepass prueba123 -keypass prueba123; done
echo "no es un p12" > corrupto.p12
```

Los tests que dependen de la fecha usan un reloj fijo, no la fecha real, para que `valido.p12` no deje de servir cuando pase el tiempo.

## Certificado emitido por una "autoridad" (para el perfil prod)

`emitido-por-ca.p12` (RUC `20512345678`, `CN=MI EMPRESA SAC`, vigencia 2026-01-01 a 2035-12-30) NO es autofirmado: su emisor es `CN=AUTORIDAD DE PRUEBA`. Sirve para probar que el perfil `prod` acepta un certificado que no sea autofirmado. Se generó así:

```bash
keytool -genkeypair -alias ca -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -dname "CN=AUTORIDAD DE PRUEBA, O=AUTORIDAD DE PRUEBA, C=PE" -ext bc:c -startdate "2026/01/01 00:00:00" -validity 3650 -storetype PKCS12 -keystore ca.p12 -storepass prueba123 -keypass prueba123
keytool -genkeypair -alias emisor -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -dname "CN=MI EMPRESA SAC, OU=20512345678, O=MI EMPRESA SAC, C=PE" -startdate "2026/01/01 00:00:00" -validity 3650 -storetype PKCS12 -keystore emitido-por-ca.p12 -storepass prueba123 -keypass prueba123
keytool -certreq -alias emisor -keystore emitido-por-ca.p12 -storepass prueba123 -file emisor.csr
keytool -gencert -alias ca -keystore ca.p12 -storepass prueba123 -infile emisor.csr -outfile emisor.crt -startdate "2026/01/01 00:00:00" -validity 3650 -rfc
keytool -exportcert -alias ca -keystore ca.p12 -storepass prueba123 -rfc -file ca.crt
keytool -importcert -noprompt -alias ca -file ca.crt -keystore emitido-por-ca.p12 -storepass prueba123
keytool -importcert -noprompt -alias emisor -file emisor.crt -keystore emitido-por-ca.p12 -storepass prueba123
```
