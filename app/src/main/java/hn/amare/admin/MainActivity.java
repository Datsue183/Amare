package hn.amare.admin;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import hn.amare.admin.Models.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
  private static final int CREAM = 0xfffaf7f2,
      BROWN = 0xff684832,
      INK = 0xff352a23,
      MUTED = 0xff88786a,
      GOLD = 0xffb69a70;
  private static final int PHOTO = 10, EXPORT = 11, RESTORE = 12;
  private LocalStore store;
  private MediaFiles media;
  private LinearLayout root, body, nav;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private int tab = 0;
  private boolean busy = false, archived = false;
  private ProductForm photoForm;
  private TextView notice;

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    store = new LocalStore(this);
    media = new MediaFiles(this);
    if (saved != null) tab = saved.getInt("tab", 0);
    root = vertical();
    root.setBackgroundColor(CREAM);
    root.setFitsSystemWindows(true);
    root.setFocusableInTouchMode(true);
    root.requestFocus();
    setContentView(root);
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          v.setPadding(
              insets.getSystemWindowInsetLeft(),
              insets.getSystemWindowInsetTop(),
              insets.getSystemWindowInsetRight(),
              insets.getSystemWindowInsetBottom());
          return insets.consumeSystemWindowInsets();
        });
    LinearLayout header = horizontal();
    header.setPadding(dp(20), dp(8), dp(20), dp(8));
    header.setGravity(Gravity.CENTER_VERTICAL);
    LogoView logo = new LogoView();
    header.addView(logo, new LinearLayout.LayoutParams(dp(142), dp(60)));
    TextView admin = text("ADMIN · BETA", 11, MUTED);
    admin.setGravity(Gravity.END);
    header.addView(admin, new LinearLayout.LayoutParams(0, dp(48), 1));
    root.addView(header);
    notice = text("", 12, BROWN);
    notice.setGravity(Gravity.CENTER);
    notice.setVisibility(View.GONE);
    root.addView(notice);
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    body = vertical();
    body.setPadding(dp(20), dp(12), dp(20), dp(24));
    scroll.addView(body);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    nav = horizontal();
    nav.setBackgroundColor(0xffffffff);
    nav.setPadding(dp(4), dp(5), dp(4), dp(5));
    root.addView(nav);
    showTab(tab);
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    super.onSaveInstanceState(out);
    out.putInt("tab", tab);
  }

  @Override
  protected void onDestroy() {
    worker.shutdown();
    if (!busy && store != null) store.close();
    super.onDestroy();
  }

  private int dp(int n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  private LinearLayout vertical() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  private LinearLayout horizontal() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.HORIZONTAL);
    return l;
  }

  private TextView text(String s, int size, int color) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setPadding(0, dp(4), 0, dp(4));
    return t;
  }

  private GradientDrawable background(int color, int stroke) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(16));
    if (stroke != 0) d.setStroke(dp(1), stroke);
    return d;
  }

  private LinearLayout card(LinearLayout parent) {
    LinearLayout c = vertical();
    c.setPadding(dp(16), dp(12), dp(16), dp(12));
    c.setBackground(background(0xffffffff, 0xffeadfd2));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.bottomMargin = dp(12);
    parent.addView(c, lp);
    return c;
  }

  private void label(LinearLayout l, String title) {
    TextView t = text(title, 26, INK);
    t.setTypeface(Typeface.create("serif", Typeface.BOLD));
    l.addView(t);
  }

  private void hint(LinearLayout l, String s) {
    l.addView(text(s, 13, MUTED));
  }

  private Button button(LinearLayout parent, String name, Runnable action, boolean primary) {
    Button b = new Button(this);
    b.setText(name);
    b.setAllCaps(false);
    b.setTextColor(primary ? 0xffffffff : BROWN);
    b.setTextSize(14);
    b.setMinHeight(dp(48));
    b.setMinimumHeight(dp(48));
    b.setBackground(background(primary ? BROWN : 0xfff2ece3, 0));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48));
    lp.topMargin = dp(8);
    lp.bottomMargin = dp(3);
    parent.addView(b, lp);
    b.setOnClickListener(
        v -> {
          if (!busy) action.run();
        });
    return b;
  }

  private EditText field(LinearLayout parent, String label, String value, int type) {
    parent.addView(text(label, 13, MUTED));
    EditText e = new EditText(this);
    e.setSingleLine(
        type
            != android.text.InputType.TYPE_CLASS_TEXT
                + android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    e.setTextSize(16);
    e.setTextColor(INK);
    e.setInputType(type);
    e.setText(value);
    e.setPadding(dp(12), dp(8), dp(12), dp(8));
    e.setBackground(background(0xfffaf7f2, 0xffe2d6c7));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.bottomMargin = dp(12);
    parent.addView(e, lp);
    return e;
  }

  private EditText field(LinearLayout p, String label, String value) {
    return field(
        p,
        label,
        value,
        android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
  }

  private EditText amount(LinearLayout p, String label, long value) {
    return field(
        p,
        label,
        Money.input(value),
        android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
  }

  private String value(EditText e) {
    return e.getText().toString().trim();
  }

  private int quantity(EditText e) {
    try {
      int n = Integer.parseInt(value(e));
      if (n < 1 || n > 100000) throw new NumberFormatException();
      return n;
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("La cantidad debe ser de 1 a 100000.");
    }
  }

  private AlertDialog dialog(String title, LinearLayout content) {
    ScrollView s = new ScrollView(this);
    s.setFillViewport(false);
    content.setPadding(dp(20), dp(8), dp(20), dp(20));
    s.addView(content);
    AlertDialog d =
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setView(s)
            .setNegativeButton("Cerrar", null)
            .create();
    d.setOnShowListener(
        v -> d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE));
    d.show();
    return d;
  }

  private void error(Exception e) {
    String msg =
        e instanceof IllegalArgumentException
            ? e.getMessage()
            : "No se pudo completar la operación. Revisa el archivo o vuelve a intentarlo.";
    new AlertDialog.Builder(this)
        .setTitle("Revisa estos datos")
        .setMessage(msg)
        .setPositiveButton("Entendido", null)
        .show();
  }

  private interface Job {
    void run() throws Exception;
  }

  private void perform(Job job, Runnable success) {
    if (busy) return;
    busy = true;
    notice.setText(R.string.saving);
    notice.setVisibility(View.VISIBLE);
    worker.execute(
        () -> {
          try {
            job.run();
            runOnUiThread(
                () -> {
                  busy = false;
                  notice.setVisibility(View.GONE);
                  if (!isDestroyed()) {
                    success.run();
                  }
                });
          } catch (Exception ex) {
            runOnUiThread(
                () -> {
                  busy = false;
                  notice.setVisibility(View.GONE);
                  if (!isDestroyed()) error(ex);
                });
          }
        });
  }

  private void done(String message) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    showTab(tab);
  }

  private void confirm(String title, String message, String yes, Runnable action) {
    new AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(message)
        .setNegativeButton("Volver", null)
        .setPositiveButton(
            yes,
            (d, w) -> {
              if (!busy) action.run();
            })
        .show();
  }

  private void showTab(int n) {
    root.requestFocus();
    tab = n;
    body.removeAllViews();
    nav.removeAllViews();
    String[] names = {"Inicio", "Catálogo", "Bodega", "Pedidos", "Ajustes"};
    String[] icons = {"⌂", "◇", "▣", "≡", "⚙"};
    for (int i = 0; i < names.length; i++) {
      final int pos = i;
      TextView t = text(icons[i] + "\n" + names[i], 12, i == n ? BROWN : MUTED);
      t.setGravity(Gravity.CENTER);
      t.setMinHeight(dp(56));
      if (i == n) t.setBackground(background(0xfff2ece3, 0));
      nav.addView(t, new LinearLayout.LayoutParams(0, dp(58), 1));
      t.setOnClickListener(
          v -> {
            if (!busy) {
              ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                  .hideSoftInputFromWindow(root.getWindowToken(), 0);
              showTab(pos);
            }
          });
    }
    try {
      switch (n) {
        case 0:
          home();
          break;
        case 1:
          catalog(false);
          break;
        case 2:
          catalog(true);
          break;
        case 3:
          ordersPage();
          break;
        default:
          settings();
      }
    } catch (Exception e) {
      error(e);
    }
  }

  private void home() {
    label(body, "Tu tienda, en orden");
    hint(body, "AMARÉ JOYERÍA · Datos de este dispositivo");
    long sales = 0, cash = 0, due = 0, valuation = 0, profit = 0;
    int pending = 0, pieces = 0;
    List<Order> orders = store.orders();
    for (Order o : orders) {
      cash += o.paid;
      if (!o.status.equals("CANCELLED")) {
        due += o.balance();
        if (o.status.equals("DELIVERED")) {
          sales += o.total;
          for (Line l : o.lines) profit += (l.price - l.cost) * l.qty;
        } else pending++;
      }
    }
    for (Product p : store.products("", true)) {
      valuation += p.stock * p.cost;
      pieces += p.stock;
    }
    LinearLayout hero = card(body);
    hero.setBackground(background(BROWN, 0));
    hero.addView(text("VENTAS ENTREGADAS · TOTAL HISTÓRICO", 11, 0xffe5d3bd));
    TextView total = text(Money.format(sales), 32, 0xffffffff);
    total.setTypeface(Typeface.create("serif", Typeface.NORMAL));
    hero.addView(total);
    hero.addView(
        text(pending + " pedidos activos · " + pieces + " piezas en bodega", 14, 0xffeadfd2));
    metric("Cobros netos", Money.format(cash), "Abonos y pagos registrados, menos devoluciones.");
    metric(
        "Saldo por cobrar", Money.format(due), "Pedidos activos y entregados con saldo pendiente.");
    metric(
        "Valor de bodega",
        Money.format(valuation),
        "Existencias físicas valoradas al costo promedio.");
    metric(
        "Margen de productos",
        Money.format(profit),
        "Ventas entregadas menos costo de piezas. No incluye gastos.");
    button(body, "Crear pedido", () -> orderForm(), true);
    button(body, "Agregar producto", () -> productForm(null), false);
    if (store.products("", true).isEmpty()) {
      LinearLayout c = card(body);
      label(c, "Bienvenida a AMARÉ");
      hint(
          c,
          "Empieza agregando tu primera pieza. Después registra compras y pedidos. Tus datos se"
              + " guardan en este celular.");
    }
  }

  private void metric(String title, String total, String subtitle) {
    LinearLayout c = card(body);
    c.addView(text(title, 13, MUTED));
    c.addView(text(total, 24, BROWN));
    hint(c, subtitle);
  }

  private void catalog(boolean inventory) {
    label(body, inventory ? "Bodega" : "Catálogo");
    hint(
        body,
        inventory
            ? "Existencias físicas, reservas y disponibilidad."
            : "Tus piezas, sus fotos y precios en lempiras.");
    button(body, "+ Agregar producto", () -> productForm(null), true);
    if (inventory) {
      button(body, "Compras a proveedores", this::purchasesPage, false);
      CheckBox a = new CheckBox(this);
      a.setText(R.string.show_archived);
      a.setChecked(archived);
      body.addView(a);
      a.setOnCheckedChangeListener(
          (v, on) -> {
            archived = on;
            showTab(tab);
          });
    }
    EditText search = field(body, "Buscar por nombre, código o categoría", "");
    LinearLayout rows = vertical();
    body.addView(rows);
    Runnable render =
        () -> {
          rows.removeAllViews();
          List<Product> ps = store.products(value(search), inventory && archived);
          if (ps.isEmpty()) {
            hint(rows, "No hay productos para mostrar.");
            return;
          }
          int count = 0;
          for (Product p : ps) {
            if (count++ >= 100) {
              hint(rows, "Mostrando 100 productos. Usa la búsqueda para encontrar una pieza.");
              break;
            }
            LinearLayout c = card(rows);
            LinearLayout line = horizontal();
            line.setGravity(Gravity.CENTER_VERTICAL);
            ImageView photo = photo(p, 72);
            line.addView(photo, new LinearLayout.LayoutParams(dp(72), dp(72)));
            LinearLayout info = vertical();
            info.setPadding(dp(12), 0, 0, 0);
            info.addView(text(p.name + (p.active ? "" : " · Archivado"), 17, INK));
            info.addView(text(p.sku + " · " + p.category, 12, MUTED));
            info.addView(text(Money.format(p.price), 18, BROWN));
            line.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
            c.addView(line);
            hint(
                c,
                p.stock
                    + " físicas · "
                    + p.reserved
                    + " reservadas · "
                    + p.available()
                    + " disponibles");
            c.setOnClickListener(v -> productDetail(p.id));
            c.setContentDescription("Ver " + p.name);
          }
        };
    render.run();
    search.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int st, int count, int after) {}

          public void onTextChanged(CharSequence s, int st, int before, int count) {
            render.run();
          }

          public void afterTextChanged(Editable e) {}
        });
  }

  private ImageView photo(Product p, int size) {
    ImageView v = new ImageView(this);
    v.setBackground(background(0xfff2ece3, 0));
    v.setClipToOutline(true);
    v.setScaleType(ImageView.ScaleType.CENTER_CROP);
    v.setContentDescription("Foto de " + p.name);
    Bitmap b = media.thumbnail(p.image, dp(size));
    if (b != null) v.setImageBitmap(b);
    else {
      v.setImageResource(hn.amare.admin.R.drawable.ic_amare);
      v.setScaleType(ImageView.ScaleType.FIT_CENTER);
    }
    return v;
  }

  private void productDetail(String id) {
    Product p = store.product(id);
    LinearLayout c = vertical();
    ImageView image = photo(p, 300);
    c.addView(image, new LinearLayout.LayoutParams(-1, dp(210)));
    c.addView(text(p.name, 24, BROWN));
    hint(c, p.sku + " · " + p.category + " · " + p.material);
    c.addView(text("Precio: " + Money.format(p.price), 20, INK));
    hint(c, "Costo promedio: " + Money.format(p.cost));
    hint(
        c,
        p.stock + " físicas · " + p.reserved + " reservadas · " + p.available() + " disponibles");
    AlertDialog d = dialog("Detalle del producto", c);
    button(c, "Compartir foto y precio", () -> shareProduct(p), true);
    button(
        c,
        "Consultar al WhatsApp de AMARÉ",
        () -> contactStore("Hola, quisiera consultar por " + p.name + " (" + p.sku + ")."),
        false);
    button(
        c,
        "Editar producto",
        () -> {
          d.dismiss();
          productForm(p);
        },
        false);
    button(c, "Ajustar existencias", () -> stockForm(p, d), false);
    button(
        c,
        "Historial de movimientos",
        () -> {
          LinearLayout list = vertical();
          for (String s : store.movements(p.id)) hint(list, s);
          dialog("Movimientos de " + p.sku, list);
        },
        false);
    button(
        c,
        p.active ? "Archivar producto" : "Reactivar producto",
        () ->
            confirm(
                "Actualizar producto",
                "El historial se conservará.",
                "Confirmar",
                () ->
                    perform(
                        () -> store.archive(p.id, !p.active),
                        () -> {
                          d.dismiss();
                          done("Producto actualizado");
                        })),
        false);
  }

  private final class ProductForm {
    Product original;
    EditText name, sku, category, material, price, cost, stock;
    String image;
    ImageView preview;
    AlertDialog dialog;
  }

  private void productForm(Product p) {
    ProductForm f = new ProductForm();
    f.original = p;
    f.image = p == null ? "" : p.image;
    LinearLayout c = vertical();
    Product sample = p == null ? new Product() : p;
    sample.name = p == null ? "producto" : p.name;
    f.preview = photo(sample, 180);
    c.addView(f.preview, new LinearLayout.LayoutParams(-1, dp(160)));
    button(
        c,
        "Elegir foto del dispositivo",
        () -> {
          photoForm = f;
          Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
          i.addCategory(Intent.CATEGORY_OPENABLE);
          i.setType("image/*");
          startActivityForResult(i, PHOTO);
        },
        false);
    f.name = field(c, "Nombre *", p == null ? "" : p.name);
    f.sku = field(c, "Código único / SKU *", p == null ? "" : p.sku);
    f.category = field(c, "Categoría (anillos, aretes, collares…)", p == null ? "" : p.category);
    f.material = field(c, "Material y variante (talla, color…)", p == null ? "" : p.material);
    f.price = amount(c, "Precio de venta (L) *", p == null ? 0 : p.price);
    if (p == null) {
      f.cost = amount(c, "Costo inicial por pieza (L)", 0);
      f.stock = field(c, "Existencia inicial", "0", android.text.InputType.TYPE_CLASS_NUMBER);
    } else
      hint(
          c,
          "El costo promedio se actualiza al recibir compras. Ajusta la existencia desde el detalle"
              + " del producto.");
    f.dialog = dialog(p == null ? "Nuevo producto" : "Editar producto", c);
    button(
        c,
        "Guardar producto",
        () -> {
          try {
            Product next = new Product();
            next.id = p == null ? null : p.id;
            next.name = value(f.name);
            next.sku = value(f.sku);
            next.category = value(f.category);
            next.material = value(f.material);
            next.price = Money.parse(value(f.price));
            next.cost = p == null ? Money.parse(value(f.cost)) : p.cost;
            next.active = p == null || p.active;
            next.image = f.image;
            int initial = p == null ? Integer.parseInt(value(f.stock)) : 0;
            perform(
                () -> store.saveProduct(next, initial),
                () -> {
                  f.dialog.dismiss();
                  done("Producto guardado");
                });
          } catch (Exception ex) {
            error(ex);
          }
        },
        true);
  }

  private void stockForm(Product p, AlertDialog detail) {
    LinearLayout c = vertical();
    hint(
        c,
        "Las reservas deben mantenerse. Un ajuste corrige la existencia sin registrar una compra o"
            + " venta.");
    EditText qty =
        field(
            c,
            "Ajuste (+ para entrada, − para salida)",
            "",
            android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
    EditText reason = field(c, "Motivo *", "");
    AlertDialog d = dialog("Ajustar " + p.sku, c);
    button(
        c,
        "Guardar ajuste",
        () -> {
          try {
            int delta = Integer.parseInt(value(qty));
            perform(
                () -> store.adjustStock(p.id, delta, value(reason)),
                () -> {
                  d.dismiss();
                  detail.dismiss();
                  done("Existencia actualizada");
                });
          } catch (Exception ex) {
            error(ex);
          }
        },
        true);
  }

  private String state(String s) {
    switch (s) {
      case "READY":
        return "Listo para entregar";
      case "DELIVERED":
        return "Entregado / Venta";
      case "CANCELLED":
        return "Cancelado";
      default:
        return "Pendiente";
    }
  }

  private String date(long time) {
    return new java.text.SimpleDateFormat("dd MMM yyyy", Locale.forLanguageTag("es-HN"))
        .format(new Date(time));
  }

  private void ordersPage() {
    label(body, "Pedidos y ventas");
    hint(body, "Reservas, encargos, abonos y entregas.");
    button(body, "+ Crear pedido", this::orderForm, true);
    EditText search = field(body, "Buscar cliente, código o estado", "");
    LinearLayout rows = vertical();
    body.addView(rows);
    Runnable render =
        () -> {
          rows.removeAllViews();
          String term = value(search).toLowerCase(Locale.ROOT);
          for (Order o : store.orders()) {
            if (!(o.customer + " " + o.code + " " + state(o.status))
                .toLowerCase(Locale.ROOT)
                .contains(term)) continue;
            LinearLayout c = card(rows);
            c.addView(text(o.customer, 18, INK));
            hint(c, o.code + " · " + date(o.created));
            c.addView(text(state(o.status), 13, BROWN));
            c.addView(text(Money.format(o.total), 22, BROWN));
            hint(
                c,
                o.status.equals("CANCELLED")
                    ? "Cancelado · abonos devueltos"
                    : "Abonado " + Money.format(o.paid) + " · Saldo " + Money.format(o.balance()));
            c.setOnClickListener(v -> orderDetail(o.id));
          }
          if (rows.getChildCount() == 0) hint(rows, "Aún no hay pedidos para mostrar.");
        };
    render.run();
    search.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int st, int count, int after) {}

          public void onTextChanged(CharSequence s, int st, int before, int count) {
            render.run();
          }

          public void afterTextChanged(Editable e) {}
        });
  }

  private void orderForm() {
    LinearLayout c = vertical();
    EditText customer = field(c, "Nombre del cliente *", "");
    EditText phone =
        field(c, "WhatsApp del cliente (opcional)", "", android.text.InputType.TYPE_CLASS_PHONE);
    EditText notes = field(c, "Dirección / notas", "");
    List<Line> cart = new ArrayList<>();
    LinearLayout lines = vertical();
    c.addView(lines);
    TextView total = text("Total: L 0.00", 20, BROWN);
    c.addView(total);
    Runnable update = () -> renderCart(lines, cart, total);
    button(c, "+ Agregar pieza", () -> lineForm(false, cart, update), false);
    EditText deposit = amount(c, "Abono inicial (L)", 0);
    hint(
        c,
        "Las piezas de bodega se reservan. Las piezas por encargo requieren recibir la compra y"
            + " reservarlas antes de entregar.");
    AlertDialog d = dialog("Crear pedido", c);
    button(
        c,
        "Guardar pedido",
        () -> {
          try {
            String name = value(customer), tel = value(phone), note = value(notes);
            long paid = Money.parse(value(deposit));
            List<Line> selected = new ArrayList<>(cart);
            perform(
                () -> store.createOrder(name, tel, note, selected, paid),
                () -> {
                  d.dismiss();
                  tab = 3;
                  done("Pedido creado");
                });
          } catch (Exception e) {
            error(e);
          }
        },
        true);
  }

  private void renderCart(LinearLayout c, List<Line> cart, TextView total) {
    c.removeAllViews();
    long sum = 0;
    for (Line l : cart) {
      sum += Money.total(l.price, l.qty);
      LinearLayout row = card(c);
      row.addView(text(l.name + " × " + l.qty, 15, INK));
      hint(row, Money.format(l.price) + " / pieza" + (l.preorder ? " · Por encargo" : ""));
      button(
          row,
          "Quitar",
          () -> {
            cart.remove(l);
            renderCart(c, cart, total);
          },
          false);
    }
    total.setText(getString(R.string.cart_total, Money.format(sum)));
  }

  private void lineForm(boolean purchase, List<Line> cart, Runnable update) {
    List<Product> ps = store.products("", false);
    if (ps.isEmpty()) {
      new AlertDialog.Builder(this)
          .setMessage("Primero agrega un producto al catálogo.")
          .setPositiveButton("Entendido", null)
          .show();
      return;
    }
    LinearLayout c = vertical();
    hint(c, "Producto");
    Spinner select = new Spinner(this);
    String[] names = new String[ps.size()];
    for (int i = 0; i < ps.size(); i++) names[i] = ps.get(i).sku + " · " + ps.get(i).name;
    select.setAdapter(
        new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
    c.addView(select, new LinearLayout.LayoutParams(-1, dp(56)));
    EditText qty = field(c, "Cantidad *", "1", android.text.InputType.TYPE_CLASS_NUMBER);
    EditText price =
        amount(
            c,
            purchase ? "Costo por pieza (L)" : "Precio por pieza (L)",
            purchase ? ps.get(0).cost : ps.get(0).price);
    CheckBox preorder = new CheckBox(this);
    preorder.setText(R.string.preorder);
    if (!purchase) c.addView(preorder);
    TextView stock = text("", 13, MUTED);
    c.addView(stock);
    select.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          public void onItemSelected(AdapterView<?> a, View v, int pos, long id) {
            Product p = ps.get(pos);
            price.setText(Money.input(purchase ? p.cost : p.price));
            stock.setText(
                getResources()
                    .getQuantityString(R.plurals.available_pieces, p.available(), p.available()));
          }

          public void onNothingSelected(AdapterView<?> a) {}
        });
    AlertDialog d = dialog("Agregar producto", c);
    button(
        c,
        "Agregar",
        () -> {
          try {
            Product p = ps.get(select.getSelectedItemPosition());
            int q = quantity(qty);
            long money = Money.parse(value(price));
            cart.add(new Line(p.id, p.name, q, money, !purchase && preorder.isChecked()));
            update.run();
            d.dismiss();
          } catch (Exception ex) {
            error(ex);
          }
        },
        true);
  }

  private void orderDetail(String id) {
    Order o = store.order(id);
    LinearLayout c = vertical();
    c.addView(text(o.customer, 22, INK));
    hint(c, o.code + " · " + state(o.status) + " · " + date(o.created));
    if (!o.phone.isEmpty()) hint(c, "WhatsApp: +" + o.phone);
    if (!o.notes.isEmpty()) hint(c, o.notes);
    for (Line l : o.lines) {
      c.addView(text(l.name + " × " + l.qty, 16, INK));
      hint(
          c,
          Money.format(l.price)
              + " por pieza"
              + (o.status.equals("PENDING") || o.status.equals("READY")
                  ? " · " + l.held + " reservadas" + (l.preorder ? " · encargo" : "")
                  : ""));
    }
    c.addView(text("Total: " + Money.format(o.total), 23, BROWN));
    hint(c, "Abonado: " + Money.format(o.paid));
    if (!o.status.equals("CANCELLED")) hint(c, "Saldo: " + Money.format(o.balance()));
    for (String payment : store.payments(id)) hint(c, "Pago: " + payment);
    AlertDialog d = dialog("Detalle de pedido", c);
    if (!o.status.equals("CANCELLED") && o.balance() > 0)
      button(c, "Registrar abono", () -> paymentForm(o, d), true);
    boolean pending = o.status.equals("PENDING") || o.status.equals("READY");
    if (pending) {
      boolean missing = false;
      for (Line l : o.lines) if (l.held < l.qty) missing = true;
      if (missing)
        button(
            c,
            "Reservar piezas recibidas",
            () ->
                perform(
                    () -> store.reserveOrder(id),
                    () -> {
                      d.dismiss();
                      done("Piezas reservadas");
                      orderDetail(id);
                    }),
            false);
      if (o.status.equals("PENDING"))
        button(
            c,
            "Marcar listo para entregar",
            () ->
                perform(
                    () -> store.markReady(id),
                    () -> {
                      d.dismiss();
                      done("Pedido listo");
                      orderDetail(id);
                    }),
            false);
      button(
          c,
          "Registrar entrega / venta",
          () ->
              confirm(
                  "Confirmar entrega",
                  "Se descontarán las piezas reservadas y quedará registrada la venta."
                      + (o.balance() > 0
                          ? " Quedará por cobrar " + Money.format(o.balance()) + "."
                          : ""),
                  "Registrar entrega",
                  () ->
                      perform(
                          () -> store.deliver(id),
                          () -> {
                            d.dismiss();
                            done("Venta registrada");
                          })),
          true);
      button(
          c,
          "Cancelar pedido",
          () ->
              confirm(
                  "Cancelar pedido",
                  o.paid > 0
                      ? "Esta acción libera las reservas y registra una devolución de "
                          + Money.format(o.paid)
                          + ". Confirma solo si ya devolviste ese dinero al cliente."
                      : "Se liberarán las piezas reservadas y se conservará el historial.",
                  o.paid > 0 ? "Ya devolví; cancelar" : "Cancelar pedido",
                  () ->
                      perform(
                          () -> store.cancel(id, true),
                          () -> {
                            d.dismiss();
                            done("Pedido cancelado");
                          })),
          false);
    }
    button(c, "Compartir resumen del pedido", () -> shareText(orderMessage(o)), false);
    if (!o.phone.isEmpty())
      button(c, "Abrir WhatsApp del cliente", () -> openWhatsApp(o.phone, orderMessage(o)), false);
  }

  private void paymentForm(Order o, AlertDialog detail) {
    LinearLayout c = vertical();
    hint(c, "Saldo pendiente: " + Money.format(o.balance()));
    EditText amount = amount(c, "Abono recibido (L)", 0);
    AlertDialog d = dialog("Registrar abono", c);
    button(
        c,
        "Guardar abono",
        () -> {
          try {
            long paid = Money.parse(value(amount));
            perform(
                () -> store.addPayment(o.id, paid),
                () -> {
                  d.dismiss();
                  detail.dismiss();
                  done("Abono registrado");
                  orderDetail(o.id);
                });
          } catch (Exception ex) {
            error(ex);
          }
        },
        true);
  }

  private void purchasesPage() {
    body.removeAllViews();
    label(body, "Compras");
    hint(body, "La existencia aumenta al confirmar que recibiste las piezas.");
    button(body, "+ Registrar compra", this::purchaseForm, true);
    button(body, "Volver a bodega", () -> showTab(2), false);
    for (Purchase p : store.purchases()) {
      LinearLayout c = card(body);
      c.addView(text(p.supplier, 19, INK));
      hint(c, date(p.created) + " · " + (p.received ? "Recibida" : "Pendiente de recibir"));
      for (Line l : p.lines)
        hint(c, l.name + " × " + l.qty + " · " + Money.format(l.price) + " / pieza");
      if (!p.notes.isEmpty()) hint(c, p.notes);
      c.addView(text(Money.format(p.total), 22, BROWN));
      if (!p.received)
        button(
            c,
            "Confirmar recepción",
            () ->
                confirm(
                    "Recibir compra",
                    "Se agregarán estas piezas a la bodega y se actualizará el costo promedio.",
                    "Ya recibí las piezas",
                    () ->
                        perform(
                            () -> store.receivePurchase(p.id),
                            () -> {
                              done("Compra recibida");
                              purchasesPage();
                            })),
            true);
    }
  }

  private void purchaseForm() {
    LinearLayout c = vertical();
    EditText supplier = field(c, "Proveedor *", "");
    EditText notes = field(c, "Referencia / notas", "");
    List<Line> cart = new ArrayList<>();
    LinearLayout lines = vertical();
    c.addView(lines);
    TextView total = text("Total: L 0.00", 20, BROWN);
    c.addView(total);
    button(
        c,
        "+ Agregar pieza",
        () -> lineForm(true, cart, () -> renderCart(lines, cart, total)),
        false);
    AlertDialog d = dialog("Compra a proveedor", c);
    button(
        c,
        "Guardar compra pendiente",
        () -> {
          String vendor = value(supplier), note = value(notes);
          List<Line> selected = new ArrayList<>(cart);
          perform(
              () -> store.createPurchase(vendor, note, selected),
              () -> {
                d.dismiss();
                done("Compra guardada");
                purchasesPage();
              });
        },
        true);
  }

  private void settings() {
    label(body, "Tu AMARÉ");
    hint(body, "Administración local · versión 0.1.0 beta");
    LinearLayout c = card(body);
    label(c, "Contacto");
    EditText phone =
        field(
            c,
            "WhatsApp de AMARÉ",
            store.setting("store_phone"),
            android.text.InputType.TYPE_CLASS_PHONE);
    hint(c, "Puedes usar 8 dígitos para Honduras o el código de país completo.");
    button(
        c,
        "Guardar WhatsApp",
        () -> {
          try {
            String tel = LocalStore.phone(value(phone));
            perform(() -> store.setting("store_phone", tel), () -> done("WhatsApp guardado"));
          } catch (Exception ex) {
            error(ex);
          }
        },
        true);
    button(
        c,
        "Abrir WhatsApp de AMARÉ",
        () -> contactStore("Hola, quisiera información sobre sus piezas."),
        false);
    LinearLayout backup = card(body);
    label(backup, "Respaldo");
    hint(
        backup,
        "Exporta datos y fotos a un archivo ZIP. Guárdalo fuera de este celular antes de cambiar de"
            + " teléfono o desinstalar. Contiene información de tus clientes.");
    button(
        backup,
        "Exportar respaldo con fotos",
        () -> {
          Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
          i.addCategory(Intent.CATEGORY_OPENABLE);
          i.setType("application/zip");
          i.putExtra(
              Intent.EXTRA_TITLE,
              "Amare-respaldo-"
                  + new java.text.SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(new Date())
                  + ".zip");
          startActivityForResult(i, EXPORT);
        },
        true);
    button(
        backup,
        "Restaurar respaldo",
        () -> {
          Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
          i.addCategory(Intent.CATEGORY_OPENABLE);
          i.setType("*/*");
          startActivityForResult(i, RESTORE);
        },
        false);
    LinearLayout local = card(body);
    label(local, "En este dispositivo");
    hint(
        local,
        "El catálogo, inventario, pedidos y fotos funcionan sin internet. WhatsApp requiere"
            + " conectividad. Esta beta no sincroniza entre celulares y no procesa pagos bancarios;"
            + " registra los pagos que tú confirmas.");
    hint(local, "Android 8.0 o posterior. Sin dependencia de servicios de Google.");
    button(
        local,
        "Instagram de AMARÉ",
        () -> openUrl("https://www.instagram.com/amarejoyeriahn/"),
        false);
  }

  private String orderMessage(Order o) {
    StringBuilder b =
        new StringBuilder("AMARÉ JOYERÍA\nPedido " + o.code + "\nCliente: " + o.customer + "\n");
    for (Line l : o.lines)
      b.append(l.name)
          .append(" × ")
          .append(l.qty)
          .append(" · ")
          .append(Money.format(Money.total(l.price, l.qty)))
          .append('\n');
    b.append("Total: ")
        .append(Money.format(o.total))
        .append("\nAbonado: ")
        .append(Money.format(o.paid));
    if (!o.status.equals("CANCELLED")) b.append("\nSaldo: ").append(Money.format(o.balance()));
    return b.append("\nEstado: ").append(state(o.status)).toString();
  }

  private void shareText(String message) {
    Intent i = new Intent(Intent.ACTION_SEND);
    i.setType("text/plain");
    i.putExtra(Intent.EXTRA_TEXT, message);
    startActivity(Intent.createChooser(i, "Compartir con el cliente"));
  }

  private void shareProduct(Product p) {
    String message =
        "AMARÉ JOYERÍA\n"
            + p.name
            + "\nCódigo: "
            + p.sku
            + "\n"
            + p.material
            + "\nPrecio: "
            + Money.format(p.price)
            + "\n"
            + (p.available() > 0 ? "Disponible" : "Consulta disponibilidad / por encargo");
    if (p.image == null || p.image.isEmpty()) {
      shareText(message);
      return;
    }
    perform(
        () -> {
          File f = media.shareImage(p.image);
          Uri uri = Uri.parse("content://hn.amare.admin.share/" + f.getName());
          runOnUiThread(
              () -> {
                if (isDestroyed()) return;
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("image/jpeg");
                i.putExtra(Intent.EXTRA_STREAM, uri);
                i.putExtra(Intent.EXTRA_TEXT, message);
                i.setClipData(ClipData.newRawUri("Foto del producto", uri));
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(i, "Compartir pieza por WhatsApp"));
              });
        },
        () -> {});
  }

  private void contactStore(String message) {
    String phone = store.setting("store_phone");
    if (phone.isEmpty()) {
      new AlertDialog.Builder(this)
          .setMessage("Configura primero el WhatsApp de AMARÉ en Ajustes.")
          .setPositiveButton("Ir a Ajustes", (d, w) -> showTab(4))
          .setNegativeButton("Volver", null)
          .show();
      return;
    }
    openWhatsApp(phone, message);
  }

  private void openWhatsApp(String phone, String message) {
    openUrl("https://wa.me/" + phone + "?text=" + Uri.encode(message));
  }

  private void openUrl(String url) {
    try {
      startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    } catch (ActivityNotFoundException e) {
      new AlertDialog.Builder(this)
          .setMessage("Instala WhatsApp o un navegador para abrir este enlace.")
          .setPositiveButton("Entendido", null)
          .show();
    }
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (result != RESULT_OK || data == null || data.getData() == null) return;
    Uri uri = data.getData();
    if (request == PHOTO && photoForm != null) {
      ProductForm f = photoForm;
      photoForm = null;
      perform(
          () -> {
            String name = media.importImage(uri);
            f.image = name;
          },
          () -> {
            Bitmap b = media.thumbnail(f.image, dp(180));
            f.preview.setImageBitmap(b);
            f.preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
          });
    } else if (request == EXPORT) {
      perform(
          () -> {
            OutputStream out = getContentResolver().openOutputStream(uri, "wt");
            if (out == null) throw new IOException();
            Backup.write(store, media, out);
          },
          () -> done("Respaldo exportado con datos y fotos"));
    } else if (request == RESTORE) {
      confirm(
          "Restaurar y reemplazar",
          "Este respaldo reemplazará todos los datos actuales. Exporta primero un respaldo de este"
              + " celular si necesitas conservarlos.",
          "Restaurar y reemplazar",
          () ->
              perform(
                  () -> {
                    InputStream in = getContentResolver().openInputStream(uri);
                    if (in == null) throw new IOException();
                    Backup.restore(store, media, in, getCacheDir());
                  },
                  () -> done("Respaldo restaurado")));
    }
  }

  private final class LogoView extends View {
    private final Bitmap logo;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Rect source = new Rect();
    private final RectF destination = new RectF();

    LogoView() {
      super(MainActivity.this);
      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inSampleSize = 2;
      logo = BitmapFactory.decodeResource(getResources(), R.drawable.logo_reference, options);
      setContentDescription("AMARÉ joyería");
    }

    @Override
    protected void onDraw(Canvas c) {
      super.onDraw(c);
      if (logo != null) {
        float sx = logo.getWidth() / 1600f, sy = logo.getHeight() / 1207f;
        source.set(
            Math.round(350 * sx),
            Math.round(430 * sy),
            Math.round(1260 * sx),
            Math.round(805 * sy));
        float h = getWidth() * source.height() / (float) source.width();
        destination.set(0, (getHeight() - h) / 2f, getWidth(), (getHeight() + h) / 2f);
        c.drawBitmap(logo, source, destination, paint);
      }
    }
  }
}
