package hn.amare.admin;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import hn.amare.admin.Models.*;
import java.io.*;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

/** Render the actual Android views, rather than an HTML approximation. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w360dp-h780dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class UiRenderTest {
  private TextView find(View v, String s) {
    if (v instanceof TextView && s.equals(((TextView) v).getText().toString())) return (TextView) v;
    if (v instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) v;
      for (int i = 0; i < g.getChildCount(); i++) {
        TextView t = find(g.getChildAt(i), s);
        if (t != null) return t;
      }
    }
    return null;
  }

  @Test
  public void renderScreensWithExampleRecords() throws Exception {
    Context context = RuntimeEnvironment.getApplication();
    context.deleteDatabase("amare.db");
    try {
      LocalStore store = new LocalStore(context);
      Product p = new Product();
      p.name = "Anillo Aurora · ejemplo";
      p.sku = "DEMO-01";
      p.category = "Anillos";
      p.material = "Acero inoxidable · ajustable";
      p.price = 28000;
      p.cost = 12000;
      p.image = "";
      String id = store.saveProduct(p, 5);
      store.createOrder(
          "Cliente de ejemplo",
          "99998888",
          "Datos ficticios para verificar pantallas",
          List.of(new Line(id, p.name, 1, 28000, false)),
          14000);
      store.close();
      File dir = new File("build/ui-preview");
      assertTrue(dir.exists() || dir.mkdirs());
      String[] tabs = {null, "◇\nCatálogo", "▣\nBodega", "≡\nPedidos", "⚙\nAjustes"};
      String[] files = {"inicio", "catalogo", "bodega", "pedidos", "ajustes"};
      for (int i = 0; i < tabs.length; i++) {
        // A fresh activity avoids stale display-list state in Robolectric's native renderer.
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup()) {
          View root = controller.get().getWindow().getDecorView();
          if (tabs[i] != null) find(root, tabs[i]).performClick();
          org.robolectric.shadows.ShadowLooper.idleMainLooper();
          root.forceLayout();
          root.measure(
              View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
              View.MeasureSpec.makeMeasureSpec(1560, View.MeasureSpec.EXACTLY));
          root.layout(0, 0, 720, 1560);
          assertEquals("Window stays at its origin", 0, root.getScrollY());
          Bitmap bitmap = Bitmap.createBitmap(720, 1560, Bitmap.Config.ARGB_8888);
          root.draw(new Canvas(bitmap));
          java.util.Set<Integer> colors = new java.util.HashSet<>();
          for (int y = 0; y < 1560; y += 15)
            for (int x = 0; x < 720; x += 15) colors.add(bitmap.getPixel(x, y));
          assertTrue("Screen has rendered content", colors.size() > 10);
          try (OutputStream out = new FileOutputStream(new File(dir, files[i] + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
          }
          bitmap.recycle();
        }
      }
    } finally {
      context.deleteDatabase("amare.db");
    }
  }
}
