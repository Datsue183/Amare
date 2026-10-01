package hn.amare.admin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

public final class Money {
  private Money() {}

  public static long parse(String text) {
    try {
      String v = text.trim();
      if (!v.matches("[0-9]+([.,][0-9]{1,2})?")) throw new IllegalArgumentException();
      long cents = new BigDecimal(v.replace(',', '.')).movePointRight(2).longValueExact();
      if (cents > 100_000_000) throw new IllegalArgumentException();
      return cents;
    } catch (RuntimeException e) {
      throw new IllegalArgumentException(
          "Escribe un monto válido, con hasta dos decimales y sin separadores de miles.");
    }
  }

  public static String format(long cents) {
    NumberFormat f = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-HN"));
    f.setMinimumFractionDigits(2);
    f.setMaximumFractionDigits(2);
    return "L " + f.format(BigDecimal.valueOf(cents, 2));
  }

  public static String input(long cents) {
    return BigDecimal.valueOf(cents, 2).setScale(2, RoundingMode.UNNECESSARY).toPlainString();
  }

  public static long total(long price, int qty) {
    if (price < 0 || price > 100_000_000 || qty < 1 || qty > 100_000)
      throw new IllegalArgumentException("Revisa precio y cantidad.");
    return Math.multiplyExact(price, qty);
  }
}
