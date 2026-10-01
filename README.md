# Amare — administración de AMARÉ Joyería

Beta Android **0.1.0**, solo para administración, en español y lempiras hondureños.

## Instalar

Descarga `dist/Amare-0.1.0-beta.apk` desde GitHub y ábrelo en el teléfono. Si Android lo solicita, permite la instalación desde la aplicación con la que descargaste el archivo. No necesitas Android Studio ni una cuenta de Google.

Compatibilidad declarada: Android 8.0 (API 26) o posterior, teléfonos y tabletas; no requiere Google Play services ni contiene bibliotecas nativas por arquitectura. Esto no equivale a pruebas en cada marca/modelo. Consulta `docs/VALIDATION.md` para las comprobaciones realizadas.

## Primer uso

1. En **Ajustes**, configura el WhatsApp de AMARÉ.
2. En **Catálogo**, agrega una pieza con código único, nombre, categoría, material/variante, foto, precio, costo y existencia inicial. Para distintas tallas usa códigos distintos.
3. En **Bodega → Compras**, registra una compra al proveedor. Confirma su recepción cuando las piezas lleguen; recién entonces aumentará la existencia.
4. En **Pedidos**, registra cliente, teléfono, dirección/notas, productos y abono inicial. Cada pedido puede contener varios productos.
5. Para piezas disponibles, el pedido reserva la cantidad. Para piezas por encargo, recibe primero la compra y selecciona **Reservar piezas recibidas**.
6. Marca el pedido listo y registra su entrega para convertirlo en venta y descontar las existencias. Los saldos pendientes se pueden cobrar incluso después de entregar.
7. Exporta un respaldo con fotos desde **Ajustes** y guárdalo fuera del teléfono.

## Reglas

- **Físicas**: piezas presentes en la bodega. **Reservadas**: piezas apartadas para pedidos. **Disponibles**: físicas menos reservadas.
- La recepción de una compra y la entrega de un pedido solo se registran una vez. Los cambios de existencias y estados son transaccionales.
- El costo del inventario es promedio ponderado. El precio del pedido se fija al crearlo; el costo de las piezas vendidas se fija al entregar. Editar el catálogo no cambia las ventas anteriores.
- Los abonos no pueden superar el saldo. La cancelación libera las reservas. Si hubo abonos, exige confirmar que realmente devolviste el dinero y registra la devolución. No cancela una venta ya entregada; devoluciones de ventas quedan para una siguiente versión.
- El resumen presenta totales históricos: ventas entregadas, cobros netos, saldo por cobrar, valor de bodega y margen de productos. El margen no incluye envío, impuestos ni gastos operativos.
- Una foto por producto. Las imágenes se copian al almacenamiento privado y se reducen a un máximo de 1600 píxeles para controlar tamaño y memoria.
- Un ajuste de existencias exige motivo y respeta las reservas. Archivar conserva el historial y evita nuevos pedidos de ese producto.
- El catálogo comienza vacío. Los datos ficticios de las pruebas nunca se incluyen en el APK.

## Datos y respaldo

Base SQLite y fotos en el almacenamiento privado de esta aplicación. No se solicitan permisos generales de fotos/archivos: el selector de Android concede acceso a los archivos elegidos. La aplicación no tiene permiso de internet ni envía datos a un servidor. WhatsApp e Instagram se abren en aplicaciones externas.

El ZIP de respaldo incluye todos los registros, configuraciones y fotos referenciadas. Restaurarlo reemplaza los datos actuales; primero exporta lo que necesites conservar. La restauración valida estructura, relaciones, reservas, pagos y movimientos, y rechaza archivos dañados. Un error de importación conserva los datos anteriores.

Desinstalar o borrar los datos de Android elimina los registros locales. Los respaldos contienen información de clientes: guárdalos en una ubicación privada. No hay sincronización, acceso de clientes, procesamiento bancario ni protección adicional por PIN en esta beta; usa el bloqueo de tu dispositivo. Formularios sin guardar no se recuperan después de que Android termine el proceso.

## Desarrollo

Java 17, vistas Android nativas, SQLite y AndroidX ExifInterface para leer la orientación de fotos. Sin servicios de Google. `Store` es la frontera para introducir almacenamiento remoto en una futura versión. Supabase/servidores y sincronización aún no están implementados.

Herramientas fijadas: Android Gradle Plugin 8.9.2, Gradle 8.11.1, compile/target SDK 35, min SDK 26.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

La acción de GitHub repite esas comprobaciones y conserva informes y un APK de depuración. El archivo instalado por el usuario en `dist/` tiene una firma beta estable y distinta de la firma de depuración del CI. No instales el APK del CI sobre la beta distribuida. La clave privada permanece fuera del repositorio; las próximas betas deben firmarse con la misma identidad y un `versionCode` mayor.

Los tests usan Robolectric sobre APIs 26 y 35, incluyendo SQLite, pagos, existencias, respaldo y navegación. Las imágenes en `docs/screens/` representan vistas reales de Android renderizadas con datos de ejemplo.
