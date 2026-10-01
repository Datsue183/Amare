package hn.amare.admin;

import static org.junit.Assert.*;

import android.view.*;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35})
public class MainActivityTest {
  private TextView find(View v, String text) {
    if (v instanceof TextView && text.equals(((TextView) v).getText().toString()))
      return (TextView) v;
    if (v instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) v;
      for (int i = 0; i < g.getChildCount(); i++) {
        TextView r = find(g.getChildAt(i), text);
        if (r != null) return r;
      }
    }
    return null;
  }

  @Test
  public void launchesAndAllTabsOpen() {
    try (var c = Robolectric.buildActivity(MainActivity.class).setup()) {
      MainActivity a = c.get();
      View root = a.getWindow().getDecorView();
      assertNotNull(find(root, "Tu tienda, en orden"));
      String[][] tabs = {
        {"◇\nCatálogo", "Catálogo"},
        {"▣\nBodega", "Bodega"},
        {"≡\nPedidos", "Pedidos y ventas"},
        {"⚙\nAjustes", "Tu AMARÉ"},
        {"⌂\nInicio", "Tu tienda, en orden"}
      };
      for (String[] tab : tabs) {
        find(root, tab[0]).performClick();
        assertNotNull(find(root, tab[1]));
      }
    }
  }

  @Test
  public void productDialogOpens() {
    try (var c = Robolectric.buildActivity(MainActivity.class).setup()) {
      MainActivity a = c.get();
      find(a.getWindow().getDecorView(), "Agregar producto").performClick();
      assertNotNull(org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog());
      assertTrue(org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().isShowing());
    }
  }
}
