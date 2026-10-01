# Verificación — Amare 0.1.0 beta

Fecha: 2026-09-30T18:46-06:00.

**Resultado: 66 pruebas aprobadas; 0 fallos, 0 errores y 0 pruebas omitidas.** Android lint Debug y Release: sin incidencias. Compilación de distribución: exitosa.

## Comando ejecutado

```sh
./gradlew testDebugUnitTest lintDebug lintRelease assembleRelease
```

Se usaron Gradle 8.11.1 y JDK 17. El APK de distribución se alineó y firmó posteriormente con la identidad beta; firma y alineación fueron comprobadas.

## Cobertura

| Grupo | Casos ejecutados | Fallos/errores |
|---|---:|---:|
| LocalStoreTest | 50 | 0 |
| MainActivityTest | 4 | 0 |
| MediaFilesTest | 8 | 0 |
| MoneyTest | 3 | 0 |
| UiRenderTest | 1 | 0 |

Robolectric ejecutó la lógica de SQLite, importación de fotos y navegación con APIs **26 (Android 8.0) y 35 (Android 15)**. El renderizado de las cinco pantallas usa gráficos nativos de Robolectric con API 35, a 360 × 780 dp. Las vistas usan datos ficticios exclusivamente para verificar el diseño.

Se comprobaron reservas y disponibilidad; impedir sobreventa; líneas repetidas del mismo producto; entrega y recepción idempotentes; encargos sin existencia; reserva atómica; saldo después de entregar; exceso de abonos; confirmación de devolución al cancelar; costo promedio e historial de precios; ajustes y archivos; códigos únicos; persistencia al reabrir; respaldo con fotos; rollback de datos dañados; rechazo de rutas ZIP inseguras; copia privada de imágenes, fotos grandes, orientación EXIF y rechazo de fotos corruptas.

## APK verificado

- Archivo: `dist/Amare-0.1.0-beta.apk`.
- Tamaño: 779,880 bytes.
- Package: `hn.amare.admin`.
- VersionCode: `1`; VersionName: `0.1.0-beta`.
- minSdk: 26; target/compileSdk: 35.
- Firmas APK v2 y v3: válidas. Alineación de 4 bytes: válida.
- Variante de distribución no depurable. Sin permiso de internet ni bibliotecas nativas de producción.
- SHA-256: `33e962cf09c2c27428bce6a88f46e32ad0f6c9fd3869dc994702583c9fe350a7`.

La clave privada está fuera del repositorio. Las próximas betas deben usar la misma identidad para poder actualizar sin desinstalar.

## Alcance y límites

Estas son pruebas automatizadas con Robolectric y revisión de vistas renderizadas; **no se probó en un teléfono físico ni en un emulador completo**. No permiten confirmar cada marca/modelo ni Android posterior a API 35. No se verificó un envío real por WhatsApp ni los selectores de archivos de cada fabricante. Esas comprobaciones requieren el celular del usuario.

Los XML de resultados, informes estáticos, log de compilación y verificación de firma están en `docs/verification/`.

## Prueba rápida en tu celular

1. Instala el APK y abre las cinco secciones.
2. Agrega un producto con foto, código y cinco piezas iniciales. Cierra y vuelve a abrir para comprobar la persistencia.
3. Crea un pedido de dos piezas con un abono: deben quedar cinco físicas, dos reservadas y tres disponibles.
4. Registra la entrega: deben quedar tres físicas y cero reservadas, con el saldo restante conservado.
5. Comparte el producto y un resumen por WhatsApp; revisa el mensaje antes de enviarlo.
6. Exporta un respaldo con fotos. Restaura ese mismo archivo y verifica producto, pedido, pago e imagen.
7. Registra una compra y confirma recepción una vez; comprueba el aumento de existencias.

## Referencias técnicas

- [Compatibilidad de Android Gradle Plugin 8.9](https://developer.android.com/build/releases/agp-8-9-0-release-notes).
- [Persistencia SQLite en Android](https://developer.android.com/training/data-storage/sqlite).
- [AndroidX ExifInterface](https://developer.android.com/jetpack/androidx/releases/exifinterface).
