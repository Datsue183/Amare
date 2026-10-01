package hn.amare.admin;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import hn.amare.admin.Models.*;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

/** All stock/payment mutations are transactional and use integer centavos. */
public final class LocalStore implements Store {
  static final String[] TABLES = {
    "products",
    "orders",
    "order_lines",
    "purchases",
    "purchase_lines",
    "payments",
    "movements",
    "settings"
  };
  private final Helper helper;

  public LocalStore(Context context) {
    this(context, "amare.db");
  }

  LocalStore(Context context, String name) {
    helper = new Helper(context, name);
  }

  static class Helper extends SQLiteOpenHelper {
    Helper(Context c, String name) {
      super(c, name, null, 1);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
      db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase d) {
      d.execSQL(
          "CREATE TABLE products(id TEXT PRIMARY KEY,sku TEXT UNIQUE NOT NULL,name TEXT NOT"
              + " NULL,category TEXT NOT NULL,material TEXT NOT NULL,price INTEGER NOT NULL"
              + " CHECK(price>=0 AND price<=100000000),cost INTEGER NOT NULL CHECK(cost>=0 AND"
              + " cost<=100000000),stock INTEGER NOT NULL CHECK(stock>=0 AND stock<=100000),image"
              + " TEXT NOT NULL DEFAULT '',active INTEGER NOT NULL CHECK(active IN(0,1)))");
      d.execSQL(
          "CREATE TABLE orders(id TEXT PRIMARY KEY,code TEXT UNIQUE NOT NULL,customer TEXT NOT"
              + " NULL,phone TEXT NOT NULL,notes TEXT NOT NULL,status TEXT NOT NULL CHECK(status"
              + " IN('PENDING','READY','DELIVERED','CANCELLED')),total INTEGER NOT NULL"
              + " CHECK(total>=0),created INTEGER NOT NULL)");
      d.execSQL(
          "CREATE TABLE order_lines(id TEXT PRIMARY KEY,order_id TEXT NOT NULL REFERENCES"
              + " orders(id),product_id TEXT NOT NULL REFERENCES products(id),name TEXT NOT"
              + " NULL,qty INTEGER NOT NULL CHECK(qty>0 AND qty<=100000),held INTEGER NOT NULL"
              + " CHECK(held>=0 AND held<=qty),price INTEGER NOT NULL CHECK(price>=0 AND"
              + " price<=100000000),cost INTEGER NOT NULL CHECK(cost>=0 AND"
              + " cost<=100000000),preorder INTEGER NOT NULL CHECK(preorder IN(0,1)))");
      d.execSQL(
          "CREATE TABLE purchases(id TEXT PRIMARY KEY,supplier TEXT NOT NULL,notes TEXT NOT"
              + " NULL,total INTEGER NOT NULL CHECK(total>=0),created INTEGER NOT NULL,received"
              + " INTEGER NOT NULL CHECK(received IN(0,1)))");
      d.execSQL(
          "CREATE TABLE purchase_lines(id TEXT PRIMARY KEY,purchase_id TEXT NOT NULL REFERENCES"
              + " purchases(id),product_id TEXT NOT NULL REFERENCES products(id),name TEXT NOT"
              + " NULL,qty INTEGER NOT NULL CHECK(qty>0 AND qty<=100000),price INTEGER NOT NULL"
              + " CHECK(price>=0 AND price<=100000000))");
      d.execSQL(
          "CREATE TABLE payments(id TEXT PRIMARY KEY,order_id TEXT NOT NULL REFERENCES"
              + " orders(id),amount INTEGER NOT NULL CHECK(amount<>0),created INTEGER NOT NULL)");
      d.execSQL(
          "CREATE TABLE movements(id TEXT PRIMARY KEY,product_id TEXT NOT NULL REFERENCES"
              + " products(id),qty INTEGER NOT NULL CHECK(qty<>0),reason TEXT NOT NULL,reference"
              + " TEXT NOT NULL,created INTEGER NOT NULL)");
      d.execSQL("CREATE TABLE settings(key TEXT PRIMARY KEY,value TEXT NOT NULL)");
      d.execSQL("CREATE INDEX lines_product ON order_lines(product_id)");
      d.execSQL("CREATE INDEX payments_order ON payments(order_id)");
      d.execSQL("CREATE INDEX lines_order ON order_lines(order_id)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase d, int oldV, int newV) {
      throw new IllegalStateException("Migración no disponible para esta versión.");
    }
  }

  private SQLiteDatabase db() {
    return helper.getWritableDatabase();
  }

  private static String id() {
    return UUID.randomUUID().toString();
  }

  private static String required(String s, String label) {
    if (s == null || s.trim().isEmpty())
      throw new IllegalArgumentException("Completa " + label + ".");
    if (s.length() > 1000) throw new IllegalArgumentException(label + " es demasiado largo.");
    return s.trim();
  }

  private static String safe(String s) {
    return s == null ? "" : s.trim();
  }

  static String phone(String text) {
    String raw = safe(text);
    if (raw.isEmpty()) return "";
    if (!raw.matches("[+0-9() -]+")) throw new IllegalArgumentException("Revisa el teléfono.");
    String digits = raw.replaceAll("[^0-9]", "");
    if (digits.length() == 8) digits = "504" + digits;
    if (digits.length() < 10 || digits.length() > 15)
      throw new IllegalArgumentException("Usa el número con código de país (504 para Honduras).");
    return digits;
  }

  private interface Work<T> {
    T run();
  }

  private <T> T tx(Work<T> action) {
    SQLiteDatabase d = db();
    d.beginTransaction();
    try {
      T result = action.run();
      d.setTransactionSuccessful();
      return result;
    } finally {
      d.endTransaction();
    }
  }

  private static ContentValues values(Object... pairs) {
    ContentValues v = new ContentValues();
    for (int i = 0; i < pairs.length; i += 2) {
      Object x = pairs[i + 1];
      String k = (String) pairs[i];
      if (x instanceof Integer) v.put(k, (Integer) x);
      else if (x instanceof Long) v.put(k, (Long) x);
      else v.put(k, (String) x);
    }
    return v;
  }

  private int reserved(String productId) {
    try (Cursor c =
        db().rawQuery(
                "SELECT COALESCE(SUM(l.held),0) FROM order_lines l JOIN orders o ON o.id=l.order_id"
                    + " WHERE l.product_id=? AND o.status IN('PENDING','READY')",
                new String[] {productId})) {
      c.moveToFirst();
      return c.getInt(0);
    }
  }

  private Product readProduct(Cursor c) {
    Product p = new Product();
    p.id = c.getString(0);
    p.sku = c.getString(1);
    p.name = c.getString(2);
    p.category = c.getString(3);
    p.material = c.getString(4);
    p.price = c.getLong(5);
    p.cost = c.getLong(6);
    p.stock = c.getInt(7);
    p.image = c.getString(8);
    p.active = c.getInt(9) == 1;
    p.reserved = reserved(p.id);
    return p;
  }

  @Override
  public synchronized List<Product> products(String q, boolean all) {
    List<Product> out = new ArrayList<>();
    String term = "%" + safe(q).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    try (Cursor c =
        db().rawQuery(
                "SELECT * FROM products WHERE "
                    + (all ? "" : "active=1 AND ")
                    + "(name LIKE ? ESCAPE '\\' OR sku LIKE ? ESCAPE '\\' OR category LIKE ? ESCAPE"
                    + " '\\') ORDER BY name COLLATE NOCASE",
                new String[] {term, term, term})) {
      while (c.moveToNext()) out.add(readProduct(c));
    }
    return out;
  }

  @Override
  public synchronized Product product(String id) {
    try (Cursor c = db().rawQuery("SELECT * FROM products WHERE id=?", new String[] {id})) {
      if (!c.moveToFirst()) throw new IllegalArgumentException("El producto no existe.");
      return readProduct(c);
    }
  }

  @Override
  public synchronized String saveProduct(Product p, int openingStock) {
    return tx(
        () -> {
          String name = required(p.name, "el nombre"),
              sku = required(p.sku, "el código").toUpperCase(Locale.ROOT);
          Money.total(p.price, 1);
          Money.total(p.cost, 1);
          if (openingStock < 0 || openingStock > 100000)
            throw new IllegalArgumentException("La existencia inicial debe ser de 0 a 100000.");
          boolean fresh = p.id == null;
          String pid = fresh ? id() : p.id;
          int stock = fresh ? openingStock : product(pid).stock;
          try (Cursor c =
              db().rawQuery(
                      "SELECT id FROM products WHERE sku=? AND id<>?", new String[] {sku, pid})) {
            if (c.moveToFirst())
              throw new IllegalArgumentException("Ya existe un producto con ese código.");
          }
          String image = safe(p.image);
          if (!image.isEmpty() && !image.matches("[a-zA-Z0-9_-]+\\.jpg"))
            throw new IllegalArgumentException("Imagen inválida.");
          ContentValues v =
              values(
                  "id",
                  pid,
                  "sku",
                  sku,
                  "name",
                  name,
                  "category",
                  safe(p.category),
                  "material",
                  safe(p.material),
                  "price",
                  p.price,
                  "cost",
                  p.cost,
                  "stock",
                  stock,
                  "image",
                  image,
                  "active",
                  p.active ? 1 : 0);
          if (fresh) {
            db().insertOrThrow("products", null, v);
            if (stock > 0) movement(pid, stock, "Existencia inicial", pid);
          } else db().update("products", v, "id=?", new String[] {pid});
          return pid;
        });
  }

  private void movement(String pid, int qty, String reason, String ref) {
    if (qty != 0)
      db().insertOrThrow(
              "movements",
              null,
              values(
                  "id",
                  id(),
                  "product_id",
                  pid,
                  "qty",
                  qty,
                  "reason",
                  reason,
                  "reference",
                  ref,
                  "created",
                  System.currentTimeMillis()));
  }

  public synchronized void adjustStock(String pid, int delta, String reason) {
    tx(
        () -> {
          Product p = product(pid);
          required(reason, "el motivo del ajuste");
          long newStock = (long) p.stock + delta;
          if (delta == 0 || newStock < p.reserved || newStock > 100000)
            throw new IllegalArgumentException(
                "El ajuste debe respetar las piezas reservadas y el límite de existencias.");
          db().update("products", values("stock", (int) newStock), "id=?", new String[] {pid});
          movement(pid, delta, reason, "AJUSTE");
          return null;
        });
  }

  public synchronized void archive(String pid, boolean active) {
    Product p = product(pid);
    if (!active && p.reserved > 0)
      throw new IllegalArgumentException(
          "Completa o cancela los pedidos que reservan este producto antes de archivarlo.");
    db().update("products", values("active", active ? 1 : 0), "id=?", new String[] {pid});
  }

  @Override
  public synchronized List<Order> orders() {
    List<Order> out = new ArrayList<>();
    try (Cursor c = db().rawQuery("SELECT id FROM orders ORDER BY created DESC", null)) {
      while (c.moveToNext()) out.add(order(c.getString(0)));
    }
    return out;
  }

  @Override
  public synchronized Order order(String oid) {
    Order o = new Order();
    try (Cursor c = db().rawQuery("SELECT * FROM orders WHERE id=?", new String[] {oid})) {
      if (!c.moveToFirst()) throw new IllegalArgumentException("El pedido no existe.");
      o.id = c.getString(0);
      o.code = c.getString(1);
      o.customer = c.getString(2);
      o.phone = c.getString(3);
      o.notes = c.getString(4);
      o.status = c.getString(5);
      o.total = c.getLong(6);
      o.created = c.getLong(7);
    }
    try (Cursor c =
        db().rawQuery(
                "SELECT product_id,name,qty,held,price,cost,preorder FROM order_lines WHERE"
                    + " order_id=? ORDER BY rowid",
                new String[] {oid})) {
      while (c.moveToNext()) {
        Line l =
            new Line(c.getString(0), c.getString(1), c.getInt(2), c.getLong(4), c.getInt(6) == 1);
        l.held = c.getInt(3);
        l.cost = c.getLong(5);
        o.lines.add(l);
      }
    }
    try (Cursor c =
        db().rawQuery(
                "SELECT COALESCE(SUM(amount),0) FROM payments WHERE order_id=?",
                new String[] {oid})) {
      c.moveToFirst();
      o.paid = c.getLong(0);
    }
    return o;
  }

  @Override
  public synchronized String createOrder(
      String customer, String rawPhone, String notes, List<Line> lines, long deposit) {
    return tx(
        () -> {
          required(customer, "el nombre del cliente");
          String tel = phone(rawPhone);
          if (lines == null || lines.isEmpty() || lines.size() > 100)
            throw new IllegalArgumentException("Agrega de 1 a 100 productos al pedido.");
          long total = 0;
          Map<String, Integer> needed = new HashMap<>();
          for (Line l : lines) {
            Product p = product(l.productId);
            if (!p.active) throw new IllegalArgumentException("El producto está archivado.");
            total = Math.addExact(total, Money.total(l.price, l.qty));
            if (!l.preorder) needed.merge(p.id, l.qty, Integer::sum);
          }
          for (Map.Entry<String, Integer> e : needed.entrySet())
            if (product(e.getKey()).available() < e.getValue())
              throw new IllegalArgumentException(
                  "No hay suficientes piezas disponibles. Usa por encargo o reduce la cantidad.");
          if (deposit < 0 || deposit > total)
            throw new IllegalArgumentException("El abono no puede superar el total.");
          String oid = id();
          db().insertOrThrow(
                  "orders",
                  null,
                  values(
                      "id",
                      oid,
                      "code",
                      "AM-" + oid.substring(0, 8).toUpperCase(Locale.ROOT),
                      "customer",
                      customer.trim(),
                      "phone",
                      tel,
                      "notes",
                      safe(notes),
                      "status",
                      "PENDING",
                      "total",
                      total,
                      "created",
                      System.currentTimeMillis()));
          for (Line l : lines) {
            Product p = product(l.productId);
            db().insertOrThrow(
                    "order_lines",
                    null,
                    values(
                        "id",
                        id(),
                        "order_id",
                        oid,
                        "product_id",
                        p.id,
                        "name",
                        p.name,
                        "qty",
                        l.qty,
                        "held",
                        l.preorder ? 0 : l.qty,
                        "price",
                        l.price,
                        "cost",
                        p.cost,
                        "preorder",
                        l.preorder ? 1 : 0));
          }
          if (deposit > 0) payment(oid, deposit);
          return oid;
        });
  }

  private void pending(Order o) {
    if (!o.status.equals("PENDING") && !o.status.equals("READY"))
      throw new IllegalArgumentException("El pedido ya fue entregado o cancelado.");
  }

  @Override
  public synchronized void reserveOrder(String oid) {
    tx(
        () -> {
          Order o = order(oid);
          pending(o);
          try (Cursor c =
              db().rawQuery(
                      "SELECT id,product_id,qty,held FROM order_lines WHERE order_id=?",
                      new String[] {oid})) {
            while (c.moveToNext()) {
              String pid = c.getString(1);
              int need = c.getInt(2) - c.getInt(3);
              if (product(pid).available() < need)
                throw new IllegalArgumentException(
                    "Faltan existencias para reservar todas las piezas. Registra primero la compra"
                        + " recibida.");
              db().update(
                      "order_lines",
                      values("held", c.getInt(2)),
                      "id=?",
                      new String[] {c.getString(0)});
            }
          }
          return null;
        });
  }

  private void fullyReserved(Order o) {
    for (Line l : o.lines)
      if (l.held != l.qty)
        throw new IllegalArgumentException(
            "Primero recibe y reserva todas las piezas por encargo.");
  }

  @Override
  public synchronized void markReady(String oid) {
    tx(
        () -> {
          Order o = order(oid);
          pending(o);
          fullyReserved(o);
          db().update("orders", values("status", "READY"), "id=?", new String[] {oid});
          return null;
        });
  }

  @Override
  public synchronized void deliver(String oid) {
    tx(
        () -> {
          Order o = order(oid);
          pending(o);
          fullyReserved(o);
          for (Line l : o.lines) {
            Product p = product(l.productId);
            if (p.stock < l.qty) throw new IllegalArgumentException("Existencia insuficiente.");
            db().update("products", values("stock", p.stock - l.qty), "id=?", new String[] {p.id});
            movement(p.id, -l.qty, "Venta entregada", oid);
          }
          // Snapshot cost at delivery, including purchases received after the order was placed.
          for (Line l : o.lines)
            db().update(
                    "order_lines",
                    values("cost", product(l.productId).cost, "held", 0),
                    "order_id=? AND product_id=?",
                    new String[] {oid, l.productId});
          db().update("orders", values("status", "DELIVERED"), "id=?", new String[] {oid});
          return null;
        });
  }

  private void payment(String oid, long amount) {
    db().insertOrThrow(
            "payments",
            null,
            values(
                "id",
                id(),
                "order_id",
                oid,
                "amount",
                amount,
                "created",
                System.currentTimeMillis()));
  }

  @Override
  public synchronized void addPayment(String oid, long amount) {
    tx(
        () -> {
          Order o = order(oid);
          if (o.status.equals("CANCELLED"))
            throw new IllegalArgumentException("El pedido está cancelado.");
          if (amount <= 0 || amount > o.balance())
            throw new IllegalArgumentException(
                "El abono debe ser mayor que cero y no superar el saldo.");
          payment(oid, amount);
          return null;
        });
  }

  @Override
  public synchronized void cancel(String oid, boolean refundConfirmed) {
    tx(
        () -> {
          Order o = order(oid);
          pending(o);
          if (o.paid > 0 && !refundConfirmed)
            throw new IllegalArgumentException(
                "Confirma la devolución de los abonos antes de cancelar.");
          if (o.paid > 0) payment(oid, -o.paid);
          db().update("order_lines", values("held", 0), "order_id=?", new String[] {oid});
          db().update("orders", values("status", "CANCELLED"), "id=?", new String[] {oid});
          return null;
        });
  }

  @Override
  public synchronized List<Purchase> purchases() {
    List<Purchase> out = new ArrayList<>();
    try (Cursor c = db().rawQuery("SELECT * FROM purchases ORDER BY created DESC", null)) {
      while (c.moveToNext()) {
        Purchase p = new Purchase();
        p.id = c.getString(0);
        p.supplier = c.getString(1);
        p.notes = c.getString(2);
        p.total = c.getLong(3);
        p.created = c.getLong(4);
        p.received = c.getInt(5) == 1;
        try (Cursor l =
            db().rawQuery(
                    "SELECT product_id,name,qty,price FROM purchase_lines WHERE purchase_id=?",
                    new String[] {p.id})) {
          while (l.moveToNext())
            p.lines.add(new Line(l.getString(0), l.getString(1), l.getInt(2), l.getLong(3), false));
        }
        out.add(p);
      }
    }
    return out;
  }

  @Override
  public synchronized String createPurchase(String supplier, String notes, List<Line> lines) {
    return tx(
        () -> {
          String vendor = required(supplier, "el proveedor");
          if (lines == null || lines.isEmpty() || lines.size() > 100)
            throw new IllegalArgumentException("Agrega productos a la compra.");
          long total = 0;
          for (Line l : lines) {
            product(l.productId);
            total = Math.addExact(total, Money.total(l.price, l.qty));
          }
          String pid = id();
          db().insertOrThrow(
                  "purchases",
                  null,
                  values(
                      "id",
                      pid,
                      "supplier",
                      vendor,
                      "notes",
                      safe(notes),
                      "total",
                      total,
                      "created",
                      System.currentTimeMillis(),
                      "received",
                      0));
          for (Line l : lines)
            db().insertOrThrow(
                    "purchase_lines",
                    null,
                    values(
                        "id",
                        id(),
                        "purchase_id",
                        pid,
                        "product_id",
                        l.productId,
                        "name",
                        product(l.productId).name,
                        "qty",
                        l.qty,
                        "price",
                        l.price));
          return pid;
        });
  }

  @Override
  public synchronized void receivePurchase(String pid) {
    tx(
        () -> {
          try (Cursor c =
              db().rawQuery("SELECT received FROM purchases WHERE id=?", new String[] {pid})) {
            if (!c.moveToFirst()) throw new IllegalArgumentException("La compra no existe.");
            if (c.getInt(0) == 1)
              throw new IllegalArgumentException("Esta compra ya fue recibida.");
          }
          try (Cursor c =
              db().rawQuery(
                      "SELECT product_id,qty,price FROM purchase_lines WHERE purchase_id=?",
                      new String[] {pid})) {
            while (c.moveToNext()) {
              Product p = product(c.getString(0));
              int qty = c.getInt(1);
              long price = c.getLong(2);
              int next = p.stock + qty;
              if (next > 100000)
                throw new IllegalArgumentException("Se supera el límite de existencias.");
              long weighted = (p.stock * p.cost + Money.total(price, qty) + next / 2) / next;
              db().update(
                      "products",
                      values("stock", next, "cost", weighted),
                      "id=?",
                      new String[] {p.id});
              movement(p.id, qty, "Compra recibida", pid);
            }
          }
          db().update("purchases", values("received", 1), "id=?", new String[] {pid});
          return null;
        });
  }

  public synchronized List<String> movements(String pid) {
    List<String> out = new ArrayList<>();
    try (Cursor c =
        db().rawQuery(
                "SELECT qty,reason,created FROM movements WHERE product_id=? ORDER BY created DESC",
                new String[] {pid})) {
      while (c.moveToNext())
        out.add(
            (c.getInt(0) > 0 ? "+" : "")
                + c.getInt(0)
                + " · "
                + c.getString(1)
                + " · "
                + new java.text.SimpleDateFormat("dd/MM/yy", Locale.ROOT)
                    .format(new Date(c.getLong(2))));
    }
    return out;
  }

  public synchronized List<String> payments(String oid) {
    List<String> out = new ArrayList<>();
    try (Cursor c =
        db().rawQuery(
                "SELECT amount,created FROM payments WHERE order_id=? ORDER BY created",
                new String[] {oid})) {
      while (c.moveToNext())
        out.add(
            Money.format(c.getLong(0))
                + " · "
                + new java.text.SimpleDateFormat("dd/MM/yy HH:mm", Locale.ROOT)
                    .format(new Date(c.getLong(1))));
    }
    return out;
  }

  public synchronized String setting(String key) {
    try (Cursor c = db().rawQuery("SELECT value FROM settings WHERE key=?", new String[] {key})) {
      return c.moveToFirst() ? c.getString(0) : "";
    }
  }

  public synchronized void setting(String key, String value) {
    db().insertWithOnConflict(
            "settings",
            null,
            values("key", key, "value", safe(value)),
            SQLiteDatabase.CONFLICT_REPLACE);
  }

  public synchronized JSONObject exportData() throws Exception {
    JSONObject root = new JSONObject();
    root.put("version", 1);
    root.put("app", "hn.amare.admin");
    JSONObject tables = new JSONObject();
    for (String table : TABLES) {
      JSONArray rows = new JSONArray();
      try (Cursor c = db().rawQuery("SELECT * FROM " + table, null)) {
        while (c.moveToNext()) {
          JSONObject row = new JSONObject();
          for (int i = 0; i < c.getColumnCount(); i++)
            row.put(
                c.getColumnName(i),
                c.getType(i) == Cursor.FIELD_TYPE_INTEGER ? c.getLong(i) : c.getString(i));
          rows.put(row);
        }
      }
      tables.put(table, rows);
    }
    root.put("tables", tables);
    return root;
  }

  public synchronized void importData(JSONObject root) {
    tx(
        () -> {
          try {
            if (root.getInt("version") != 1 || !root.getString("app").equals("hn.amare.admin"))
              throw new IllegalArgumentException("Respaldo incompatible.");
            JSONObject tables = root.getJSONObject("tables");
            for (int i = TABLES.length - 1; i >= 0; i--) db().delete(TABLES[i], null, null);
            for (String table : TABLES) {
              Set<String> columns = new HashSet<>();
              try (Cursor c = db().rawQuery("SELECT * FROM " + table + " LIMIT 0", null)) {
                columns.addAll(Arrays.asList(c.getColumnNames()));
              }
              JSONArray rows = tables.getJSONArray(table);
              if (rows.length() > 100000)
                throw new IllegalArgumentException("Respaldo demasiado grande.");
              for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                Set<String> keys = new HashSet<>();
                row.keys().forEachRemaining(keys::add);
                if (!keys.equals(columns))
                  throw new IllegalArgumentException("Estructura de respaldo inválida.");
                ContentValues v = new ContentValues();
                for (String k : keys) {
                  Object value = row.get(k);
                  if (value instanceof Number) v.put(k, ((Number) value).longValue());
                  else if (value instanceof String) v.put(k, (String) value);
                  else throw new IllegalArgumentException("Valor de respaldo inválido.");
                }
                db().insertOrThrow(table, null, v);
              }
            }
            validateData();
            return null;
          } catch (Exception e) {
            throw new IllegalArgumentException("No se restauró el respaldo: " + e.getMessage(), e);
          }
        });
  }

  private void validateData() {
    for (Product p : products("", true)) {
      if (p.name.trim().isEmpty()
          || p.sku.trim().isEmpty()
          || p.reserved > p.stock
          || (!p.image.isEmpty() && !p.image.matches("[a-zA-Z0-9_-]+\\.jpg")))
        throw new IllegalArgumentException("Producto o existencia inválida.");
      try (Cursor c =
          db().rawQuery(
                  "SELECT COALESCE(SUM(qty),0) FROM movements WHERE product_id=?",
                  new String[] {p.id})) {
        c.moveToFirst();
        if (c.getLong(0) != p.stock)
          throw new IllegalArgumentException("El historial de existencias no coincide.");
      }
    }
    for (Order o : orders()) {
      long sum = 0;
      for (Line l : o.lines) sum = Math.addExact(sum, Money.total(l.price, l.qty));
      if (o.lines.isEmpty()
          || o.customer.trim().isEmpty()
          || sum != o.total
          || o.paid < 0
          || o.paid > o.total) throw new IllegalArgumentException("Pedido o pagos inválidos.");
      if (o.status.equals("READY")) fullyReserved(o);
      if (o.status.equals("DELIVERED") || o.status.equals("CANCELLED"))
        for (Line l : o.lines)
          if (l.held != 0) throw new IllegalArgumentException("Reserva inválida.");
      if (o.status.equals("CANCELLED") && o.paid != 0)
        throw new IllegalArgumentException("Devolución inválida.");
    }
    for (Purchase p : purchases()) {
      long sum = 0;
      for (Line l : p.lines) sum = Math.addExact(sum, Money.total(l.price, l.qty));
      if (p.lines.isEmpty() || sum != p.total)
        throw new IllegalArgumentException("Compra inválida.");
    }
  }

  public synchronized void close() {
    helper.close();
  }
}
