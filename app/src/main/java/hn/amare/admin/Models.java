package hn.amare.admin;

import java.util.ArrayList;
import java.util.List;

public final class Models {
  private Models() {}

  public static class Product {
    public String id, sku, name, category, material, image;
    public long price, cost;
    public int stock, reserved;
    public boolean active = true;

    public int available() {
      return stock - reserved;
    }
  }

  public static class Line {
    public String productId, name;
    public int qty, held;
    public long price, cost;
    public boolean preorder;

    public Line(String id, String name, int qty, long price, boolean preorder) {
      productId = id;
      this.name = name;
      this.qty = qty;
      this.price = price;
      this.preorder = preorder;
    }
  }

  public static class Order {
    public String id, code, customer, phone, notes, status;
    public long total, paid, created;
    public List<Line> lines = new ArrayList<>();

    public long balance() {
      return total - paid;
    }
  }

  public static class Purchase {
    public String id, supplier, notes;
    public long total, created;
    public boolean received;
    public List<Line> lines = new ArrayList<>();
  }
}
