package hn.amare.admin;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.Bitmap;
import hn.amare.admin.Models.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35})
public class LocalStoreTest {
  Context context;
  LocalStore store;
  String pid;
  MediaFiles media;

  @Before
  public void setup() {
    context = RuntimeEnvironment.getApplication();
    context.deleteDatabase("test.db");
    store = new LocalStore(context, "test.db");
    media = new MediaFiles(context);
    pid = product("AN-001", 10, 10000, 3000);
  }

  @After
  public void cleanup() {
    store.close();
    context.deleteDatabase("test.db");
  }

  private String product(String sku, int stock, long price, long cost) {
    Product p = new Product();
    p.sku = sku;
    p.name = "Anillo " + sku;
    p.category = "Anillos";
    p.material = "Acero";
    p.price = price;
    p.cost = cost;
    p.image = "";
    return store.saveProduct(p, stock);
  }

  private Line line(int qty, boolean preorder) {
    return new Line(pid, "Anillo", qty, 10000, preorder);
  }

  private String order(int qty, long deposit, boolean preorder) {
    return store.createOrder("Denisse", "99998888", "", List.of(line(qty, preorder)), deposit);
  }

  private void rejects(Runnable r) {
    assertThrows(IllegalArgumentException.class, r::run);
  }

  @Test
  public void reservationDoesNotReducePhysicalStock() {
    order(3, 10000, false);
    Product p = store.product(pid);
    assertEquals(10, p.stock);
    assertEquals(3, p.reserved);
    assertEquals(7, p.available());
  }

  @Test
  public void rejectsOversellingAndRollsBack() {
    order(8, 0, false);
    rejects(() -> order(3, 0, false));
    assertEquals(1, store.orders().size());
    assertEquals(2, store.product(pid).available());
  }

  @Test
  public void duplicateProductLinesAreAggregatedForAvailability() {
    rejects(() -> store.createOrder("A", "", "", List.of(line(6, false), line(5, false)), 0));
    assertEquals(0, store.orders().size());
    assertEquals(10, store.product(pid).available());
  }

  @Test
  public void deliveryDeductsOnceAndRemovesReservation() {
    String id = order(3, 5000, false);
    store.deliver(id);
    assertEquals(7, store.product(pid).stock);
    assertEquals(0, store.product(pid).reserved);
    assertEquals("DELIVERED", store.order(id).status);
    rejects(() -> store.deliver(id));
    assertEquals(7, store.product(pid).stock);
    assertEquals(25000, store.order(id).balance());
  }

  @Test
  public void deliveryWithDuplicateLinesKeepsOtherReservations() {
    String id = store.createOrder("A", "", "", List.of(line(2, false), line(3, false)), 0);
    order(4, 0, false);
    store.deliver(id);
    assertEquals(5, store.product(pid).stock);
    assertEquals(4, store.product(pid).reserved);
    assertEquals(1, store.product(pid).available());
  }

  @Test
  public void cancelRequiresActualRefundConfirmation() {
    String id = order(2, 8000, false);
    rejects(() -> store.cancel(id, false));
    assertEquals(8000, store.order(id).paid);
    assertEquals(2, store.product(pid).reserved);
    store.cancel(id, true);
    assertEquals(0, store.order(id).paid);
    assertEquals(0, store.product(pid).reserved);
    assertEquals(10, store.product(pid).stock);
    assertEquals(2, store.payments(id).size());
  }

  @Test
  public void deliveredSaleCannotBeCancelledAsUnfulfilledOrder() {
    String id = order(1, 0, false);
    store.deliver(id);
    rejects(() -> store.cancel(id, true));
    assertEquals(9, store.product(pid).stock);
  }

  @Test
  public void paymentValidationAndDeliveredReceivable() {
    String id = order(2, 5000, false);
    rejects(() -> store.addPayment(id, 15001));
    rejects(() -> store.addPayment(id, 0));
    store.deliver(id);
    store.addPayment(id, 15000);
    assertEquals(0, store.order(id).balance());
    rejects(() -> store.addPayment(id, 1));
  }

  @Test
  public void rejectsDepositAboveTotal() {
    rejects(() -> order(1, 10001, false));
    assertTrue(store.orders().isEmpty());
    assertEquals(0, store.product(pid).reserved);
  }

  @Test
  public void cancelledOrderDoesNotAcceptPayments() {
    String id = order(1, 0, false);
    store.cancel(id, true);
    rejects(() -> store.addPayment(id, 100));
  }

  @Test
  public void preordersRequireReceiptAndReservation() {
    String empty = product("EN-01", 0, 10000, 3000);
    String id =
        store.createOrder("A", "", "", List.of(new Line(empty, "Pieza", 2, 10000, true)), 0);
    rejects(() -> store.deliver(id));
    rejects(() -> store.markReady(id));
    String buy =
        store.createPurchase("Proveedor", "", List.of(new Line(empty, "Pieza", 2, 3000, false)));
    assertEquals(0, store.product(empty).stock);
    store.receivePurchase(buy);
    store.reserveOrder(id);
    store.markReady(id);
    store.deliver(id);
    assertEquals(0, store.product(empty).stock);
  }

  @Test
  public void failedPreorderReservationIsAtomic() {
    String empty = product("EN-02", 0, 10000, 3000);
    String id =
        store.createOrder(
            "A", "", "", List.of(line(2, true), new Line(empty, "Pieza", 1, 10000, true)), 0);
    rejects(() -> store.reserveOrder(id));
    assertEquals(0, store.product(pid).reserved);
  }

  @Test
  public void receivingPurchaseUpdatesWeightedCostAndCannotRepeat() {
    String buy =
        store.createPurchase(
            "Proveedor", "Factura 1", List.of(new Line(pid, "Anillo", 10, 5000, false)));
    store.receivePurchase(buy);
    assertEquals(20, store.product(pid).stock);
    assertEquals(4000, store.product(pid).cost);
    rejects(() -> store.receivePurchase(buy));
    assertEquals(20, store.product(pid).stock);
  }

  @Test
  public void deliveredCostIsCapturedAtDelivery() {
    String id = order(1, 0, false);
    String buy =
        store.createPurchase("Proveedor", "", List.of(new Line(pid, "Anillo", 10, 5000, false)));
    store.receivePurchase(buy);
    store.deliver(id);
    assertEquals(4000, store.order(id).lines.get(0).cost);
    store.receivePurchase(
        store.createPurchase("Otro", "", List.of(new Line(pid, "Anillo", 1, 2000, false))));
    assertEquals(4000, store.order(id).lines.get(0).cost);
  }

  @Test
  public void stockAdjustmentRespectsReservations() {
    order(8, 0, false);
    rejects(() -> store.adjustStock(pid, -3, "Merma"));
    store.adjustStock(pid, -2, "Merma");
    assertEquals(8, store.product(pid).stock);
    rejects(() -> store.adjustStock(pid, 1, ""));
  }

  @Test
  public void skuIsUniqueAndArchivedProductPreservesHistory() {
    rejects(() -> product("an-001", 0, 10000, 3000));
    String id = order(1, 0, false);
    rejects(() -> store.archive(pid, false));
    store.cancel(id, true);
    store.archive(pid, false);
    assertTrue(store.products("", false).isEmpty());
    assertEquals(1, store.products("", true).size());
    assertEquals(1, store.orders().size());
    rejects(() -> order(1, 0, false));
  }

  @Test
  public void catalogEditsDoNotChangeExistingOrderPrice() {
    String id = order(1, 0, false);
    Product p = store.product(pid);
    p.price = 20000;
    store.saveProduct(p, 999);
    assertEquals(10000, store.order(id).total);
    assertEquals(10, store.product(pid).stock);
  }

  @Test
  public void databaseSurvivesReopening() {
    String id = order(1, 1000, false);
    store.close();
    store = new LocalStore(context, "test.db");
    assertEquals(1000, store.order(id).paid);
    assertEquals(1, store.product(pid).reserved);
  }

  @Test
  public void jsonRoundTripPreservesAllRecords() throws Exception {
    String id = order(2, 8000, false);
    String buy = store.createPurchase("Proveedor", "", List.of(line(1, false)));
    store.setting("store_phone", "50499998888");
    JSONObject data = store.exportData();
    store.cancel(id, true);
    store.receivePurchase(buy);
    store.importData(data);
    assertEquals(8000, store.order(id).paid);
    assertEquals(2, store.product(pid).reserved);
    assertFalse(store.purchases().get(0).received);
    assertEquals("50499998888", store.setting("store_phone"));
  }

  @Test
  public void invalidRestoreRollsBackCurrentData() throws Exception {
    String id = order(1, 5000, false);
    JSONObject bad = store.exportData();
    bad.getJSONObject("tables").getJSONArray("products").getJSONObject(0).put("stock", 0);
    rejects(() -> store.importData(bad));
    assertEquals(10, store.product(pid).stock);
    assertEquals(5000, store.order(id).paid);
  }

  @Test
  public void unknownBackupColumnsAreRejected() throws Exception {
    JSONObject bad = store.exportData();
    bad.getJSONObject("tables").getJSONArray("products").getJSONObject(0).put("extra", "x");
    rejects(() -> store.importData(bad));
    assertEquals(10, store.product(pid).stock);
  }

  @Test
  public void backupZipIncludesPhotosAndRestores() throws Exception {
    String image = "test-photo.jpg";
    try (OutputStream out = new FileOutputStream(media.file(image))) {
      Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
          .compress(Bitmap.CompressFormat.JPEG, 90, out);
    }
    Product p = store.product(pid);
    p.image = image;
    store.saveProduct(p, 0);
    String id = order(1, 5000, false);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Backup.write(store, media, out);
    store.cancel(id, true);
    Backup.restore(
        store, media, new ByteArrayInputStream(out.toByteArray()), context.getCacheDir());
    assertEquals(5000, store.order(id).paid);
    assertNotEquals(image, store.product(pid).image);
    assertTrue(media.file(store.product(pid).image).isFile());
  }

  @Test
  public void zipTraversalCannotOverwriteData() throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream z = new ZipOutputStream(bytes)) {
      z.putNextEntry(new ZipEntry("../amare.db"));
      z.write("bad".getBytes());
      z.closeEntry();
    }
    assertThrows(
        IOException.class,
        () ->
            Backup.restore(
                store,
                media,
                new ByteArrayInputStream(bytes.toByteArray()),
                context.getCacheDir()));
    assertEquals(10, store.product(pid).stock);
  }

  @Test
  public void missingBackupPhotoPreservesCurrentRecords() throws Exception {
    Product p = store.product(pid);
    p.image = "missing.jpg";
    JSONObject data = store.exportData();
    data.getJSONObject("tables")
        .getJSONArray("products")
        .getJSONObject(0)
        .put("image", "missing.jpg");
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream z = new ZipOutputStream(bytes)) {
      z.putNextEntry(new ZipEntry("data.json"));
      z.write(data.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
      z.closeEntry();
    }
    assertThrows(
        IOException.class,
        () ->
            Backup.restore(
                store,
                media,
                new ByteArrayInputStream(bytes.toByteArray()),
                context.getCacheDir()));
    assertEquals("", store.product(pid).image);
  }

  @Test
  public void phoneNormalization() {
    assertEquals("50499998888", LocalStore.phone("9999-8888"));
    assertEquals("50499998888", LocalStore.phone("+504 9999 8888"));
    assertEquals("", LocalStore.phone(""));
    rejects(() -> LocalStore.phone("abc"));
  }
}
