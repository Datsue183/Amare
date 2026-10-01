package hn.amare.admin;

import hn.amare.admin.Models.*;
import java.util.List;

/** Boundary for a future remote implementation. UI does not own inventory rules. */
public interface Store {
  List<Product> products(String query, boolean includeArchived);

  Product product(String id);

  String saveProduct(Product p, int openingStock);

  List<Order> orders();

  Order order(String id);

  String createOrder(String customer, String phone, String notes, List<Line> lines, long deposit);

  void reserveOrder(String id);

  void markReady(String id);

  void deliver(String id);

  void cancel(String id, boolean refundConfirmed);

  void addPayment(String id, long amount);

  List<Purchase> purchases();

  String createPurchase(String supplier, String notes, List<Line> lines);

  void receivePurchase(String id);
}
