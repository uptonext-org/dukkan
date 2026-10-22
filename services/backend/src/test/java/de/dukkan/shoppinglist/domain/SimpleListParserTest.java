package de.dukkan.shoppinglist.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimpleListParserTest {

  @Test
  void readsAmountsUnitsAndSeparatorsAsPeopleSayThem() {
    List<ListItem> items = SimpleListParser.parse("Ich brauche 2 Gurken, 1 kg Tomaten und eine Packung Milch\n500g Hackfleisch; ½ Liter Sahne bitte");

    assertThat(items).extracting(ListItem::text).containsExactly("2 Gurken", "1 kg Tomaten", "eine Packung Milch", "500g Hackfleisch", "½ Liter Sahne");
    assertThat(items).extracting(ListItem::amount)
        .containsExactly(
            new Amount(BigDecimal.valueOf(2), Amount.Unit.PIECE),
            new Amount(new BigDecimal("1000"), Amount.Unit.GRAM),
            new Amount(BigDecimal.ONE, Amount.Unit.PACK),
            new Amount(new BigDecimal("500"), Amount.Unit.GRAM),
            new Amount(new BigDecimal("500.0"), Amount.Unit.MILLILITRE));
    assertThat(items.get(0).searchTerms()).containsExactly("Gurken", "Gurke", "Gurk");
    assertThat(items.get(1).searchTerms()).startsWith("Tomaten", "Tomate");
    assertThat(items.get(2).searchTerms()).containsExactly("Milch");
  }

  @Test
  void understandsEnglishArabicDigitsAndItemsWithoutAmount() {
    assertThat(SimpleListParser.parse("two bottles of water and bread"))
        .extracting(ListItem::text, ListItem::amount)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("two bottles of water", new Amount(BigDecimal.valueOf(2), Amount.Unit.PACK)),
            org.assertj.core.groups.Tuple.tuple("bread", Amount.ONE));
    assertThat(SimpleListParser.parse("٣ كيلو بندورة")).singleElement().satisfies(item -> {
      assertThat(item.amount()).isEqualTo(new Amount(BigDecimal.valueOf(3000), Amount.Unit.GRAM));
      assertThat(item.searchTerms()).first().isEqualTo("بندورة");
    });
  }

  @Test
  void keepsGermanDecimalCommas() {
    assertThat(SimpleListParser.parse("1,5 kg Tomaten, 2 Gurken"))
        .extracting(ListItem::text, ListItem::amount)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("1,5 kg Tomaten", new Amount(new BigDecimal("1500.0"), Amount.Unit.GRAM)),
            org.assertj.core.groups.Tuple.tuple("2 Gurken", new Amount(BigDecimal.valueOf(2), Amount.Unit.PIECE)));
  }

  @Test
  void skipsEmptyPartsAndNumbersWithoutAProduct() {
    assertThat(SimpleListParser.parse(" , und 3 kg, bitte ,  ")).isEmpty();
    assertThat(SimpleListParser.parse(null)).isEmpty();
    assertThat(SimpleListParser.parse("a,".repeat(40) + "Brot")).hasSize(SimpleListParser.MAX_ITEMS);
  }
}
