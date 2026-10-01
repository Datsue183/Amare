package hn.amare.admin;

import static org.junit.Assert.*;

import org.junit.Test;

public class MoneyTest {
  @Test
  public void centavosAreExact() {
    assertEquals(12345, Money.parse("123.45"));
    assertEquals(12345, Money.parse("123,45"));
    assertEquals(30, Money.total(Money.parse("0.10"), 3));
    assertEquals("123.45", Money.input(12345));
  }

  @Test
  public void invalidAmountsAreRejected() {
    for (String bad : new String[] {"-1", "NaN", "1.234", "1,000.00", "", "1e3", "1000001"})
      assertThrows(IllegalArgumentException.class, () -> Money.parse(bad));
  }

  @Test
  public void invalidQuantitiesAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> Money.total(100, -1));
    assertThrows(IllegalArgumentException.class, () -> Money.total(100, 0));
    assertThrows(IllegalArgumentException.class, () -> Money.total(100, 100001));
  }
}
