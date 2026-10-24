package de.dukkan.notification.application;

import de.dukkan.address.application.AddressView;
import de.dukkan.legal.application.PlatformSettings;
import de.dukkan.market.application.MarketDetail;
import de.dukkan.order.application.OrderView;
import de.dukkan.shared.domain.FulfilmentType;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Plain-text order e-mails to the customer in their language: the confirmation that the order
 * arrived (§ 312i Abs. 1 Nr. 3 BGB) with the order on a durable medium (§ 312f Abs. 2 BGB), the
 * changed order after missing items, and the news that a market declined or the customer cancelled.
 * Each mail names the market with its imprint, the payment and the platform's legal pages.
 */
public final class OrderMails {

  /** Which mail. */
  public enum Kind {
    PLACED,
    ADJUSTED,
    REJECTED,
    CANCELLED
  }

  public record Mail(String subject, String body) {}

  /** What the mail is built from. {@code webUrl} is the web app (links), null when unknown. */
  public record Input(
      Kind kind,
      String locale,
      String firstName,
      OrderView order,
      MarketDetail market,
      PlatformSettings platform,
      @Nullable String webUrl) {}

  private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

  private record Words(
      Locale locale,
      String hello,
      Map<Kind, String> subject,
      Map<Kind, String> intro,
      String orderNumber,
      String placedAt,
      String pickup,
      String delivery,
      String deliveryTo,
      String items,
      String instead,
      String subtotal,
      String discount,
      String deliveryFee,
      String total,
      String originalTotal,
      String payOnSite,
      String simulated,
      String seller,
      String register,
      String phone,
      String view,
      String terms,
      String privacy,
      String help,
      String footer,
      String datePattern) {}

  private static final Map<String, Words> WORDS =
      Map.of(
          "de",
          new Words(
              Locale.GERMANY,
              "Hallo %s,",
              Map.of(
                  Kind.PLACED, "Deine Bestellung %s bei %s",
                  Kind.ADJUSTED, "Deine Bestellung %s wurde angepasst",
                  Kind.REJECTED, "%s kann deine Bestellung %s nicht annehmen",
                  Kind.CANCELLED, "Deine Bestellung %s ist storniert"),
              Map.of(
                  Kind.PLACED,
                  "danke für deine Bestellung. Diese E-Mail bestätigt, dass sie eingegangen ist. Der Markt sieht sie jetzt und bestätigt sie in Kürze; du bekommst dann Bescheid in der App.",
                  Kind.ADJUSTED,
                  "beim Packen fehlten leider einige Artikel. Hier ist deine Bestellung mit dem neuen Betrag. Du bezahlst nur, was du bekommst.",
                  Kind.REJECTED,
                  "der Markt kann deine Bestellung leider nicht annehmen. Es wird nichts berechnet. Vielleicht passt ein anderes Zeitfenster oder ein anderer Markt.",
                  Kind.CANCELLED,
                  "deine Bestellung ist storniert. Es wird nichts berechnet."),
              "Bestellnummer",
              "Bestellt am",
              "Abholung",
              "Lieferung",
              "Lieferadresse",
              "Artikel",
              "statt",
              "Zwischensumme",
              "Rabatt",
              "Liefergebühr",
              "Gesamt (inkl. MwSt.)",
              "Ursprünglich",
              "Bezahlung: direkt beim Markt, bei der Abholung bzw. Lieferung.",
              "Testbetrieb: Diese Bestellung ist simuliert, es wird nichts bezahlt.",
              "Verkäufer",
              "Register",
              "Telefon",
              "Bestellung ansehen",
              "AGB",
              "Datenschutz",
              "Hilfe",
              "Diese E-Mail bekommst du, weil du bei Dukkan bestellt hast.",
              "EEE, dd.MM.yyyy"),
          "en",
          new Words(
              Locale.UK,
              "Hello %s,",
              Map.of(
                  Kind.PLACED, "Your order %s at %s",
                  Kind.ADJUSTED, "Your order %s was adjusted",
                  Kind.REJECTED, "%s cannot accept your order %s",
                  Kind.CANCELLED, "Your order %s is cancelled"),
              Map.of(
                  Kind.PLACED,
                  "thank you for your order. This e-mail confirms that it has arrived. The market can see it now and will confirm it shortly; the app will let you know.",
                  Kind.ADJUSTED,
                  "unfortunately some items were missing when your order was packed. Here is your order with the new amount. You only pay for what you get.",
                  Kind.REJECTED,
                  "unfortunately the market cannot accept your order. You will not be charged. Another time slot or another market may work.",
                  Kind.CANCELLED,
                  "your order is cancelled. You will not be charged."),
              "Order number",
              "Ordered on",
              "Pickup",
              "Delivery",
              "Delivery address",
              "Items",
              "instead of",
              "Subtotal",
              "Discount",
              "Delivery fee",
              "Total (incl. VAT)",
              "Originally",
              "Payment: directly to the market, at pickup or delivery.",
              "Test operation: this order is simulated, nothing is charged.",
              "Seller",
              "Register",
              "Phone",
              "View order",
              "Terms",
              "Privacy",
              "Help",
              "You receive this e-mail because you ordered on Dukkan.",
              "EEE d MMM yyyy"),
          "ar",
          new Words(
              Locale.forLanguageTag("ar"),
              "مرحباً %s،",
              Map.of(
                  Kind.PLACED, "طلبك %s من %s",
                  Kind.ADJUSTED, "تم تعديل طلبك %s",
                  Kind.REJECTED, "لا يستطيع %s قبول طلبك %s",
                  Kind.CANCELLED, "تم إلغاء طلبك %s"),
              Map.of(
                  Kind.PLACED,
                  "شكراً لطلبك. يؤكد هذا البريد أن طلبك قد وصل. يرى المتجر الطلب الآن وسيؤكده قريباً، وستصلك رسالة في التطبيق.",
                  Kind.ADJUSTED,
                  "للأسف كانت بعض المنتجات غير متوفرة أثناء التجهيز. هذا طلبك بالمبلغ الجديد. تدفع فقط ثمن ما تستلمه.",
                  Kind.REJECTED,
                  "للأسف لا يستطيع المتجر قبول طلبك. لن يتم احتساب أي مبلغ. ربما يناسبك موعد آخر أو متجر آخر.",
                  Kind.CANCELLED,
                  "تم إلغاء طلبك. لن يتم احتساب أي مبلغ."),
              "رقم الطلب",
              "تاريخ الطلب",
              "الاستلام",
              "التوصيل",
              "عنوان التوصيل",
              "المنتجات",
              "بدلاً من",
              "المجموع الفرعي",
              "الخصم",
              "رسوم التوصيل",
              "الإجمالي (شامل الضريبة)",
              "في الأصل",
              "الدفع: مباشرة للمتجر عند الاستلام أو التوصيل.",
              "تشغيل تجريبي: هذا الطلب تجريبي، لا يتم دفع أي مبلغ.",
              "البائع",
              "السجل التجاري",
              "الهاتف",
              "عرض الطلب",
              "الشروط",
              "الخصوصية",
              "المساعدة",
              "تصلك هذه الرسالة لأنك طلبت عبر دكان.",
              "EEEE dd.MM.yyyy"));

  private OrderMails() {}

  public static Mail build(Input in) {
    Words w = WORDS.getOrDefault(in.locale(), WORDS.get("de"));
    OrderView order = in.order();
    String marketName = in.market().summary().name();
    String subject =
        switch (in.kind()) {
          case PLACED -> w.subject().get(Kind.PLACED).formatted(order.orderNumber(), marketName);
          case REJECTED -> w.subject().get(Kind.REJECTED).formatted(marketName, order.orderNumber());
          default -> w.subject().get(in.kind()).formatted(order.orderNumber());
        };

    StringBuilder b = new StringBuilder();
    b.append(w.hello().formatted(in.firstName())).append("\n\n");
    b.append(w.intro().get(in.kind())).append("\n\n");

    DateTimeFormatter day = DateTimeFormatter.ofPattern(w.datePattern(), w.locale()).withZone(BERLIN);
    DateTimeFormatter time = DateTimeFormatter.ofPattern("HH:mm", w.locale()).withZone(BERLIN);
    line(b, w.orderNumber(), order.orderNumber());
    line(b, w.placedAt(), day.format(order.placedAt()) + ", " + time.format(order.placedAt()));
    String window =
        day.format(order.slot().startsAt()) + ", " + time.format(order.slot().startsAt()) + "–" + time.format(order.slot().endsAt());
    line(b, order.fulfilmentType() == FulfilmentType.DELIVERY ? w.delivery() : w.pickup(), window);
    AddressView address = order.deliveryAddress();
    if (address != null) {
      String addition = address.addition() == null ? "" : ", " + address.addition();
      line(b, w.deliveryTo(), address.street() + " " + address.houseNumber() + addition + ", " + address.postalCode() + " " + address.city());
    }

    b.append("\n").append(w.items()).append(":\n");
    for (OrderView.Item item : order.items()) {
      Integer fulfilled = item.fulfilledQuantity();
      String quantity = fulfilled == null ? String.valueOf(item.quantity()) : fulfilled + " (" + w.instead() + " " + item.quantity() + ")";
      b.append("  ").append(quantity).append(" × ").append(item.name()).append("  ").append(money(item.lineTotalCents(), w)).append('\n');
    }
    b.append('\n');
    line(b, w.subtotal(), money(order.subtotalCents(), w));
    if (order.discountTotalCents() > 0) {
      line(b, w.discount(), "−" + money(order.discountTotalCents(), w));
    }
    if (order.fulfilmentType() == FulfilmentType.DELIVERY) {
      line(b, w.deliveryFee(), money(order.deliveryFeeCents(), w));
    }
    line(b, w.total(), money(order.totalCents(), w));
    Long original = order.originalTotalCents();
    if (original != null && original != order.totalCents()) {
      line(b, w.originalTotal(), money(original, w));
    }
    if (in.kind() == Kind.PLACED || in.kind() == Kind.ADJUSTED) {
      b.append('\n').append(in.platform().payment() == PlatformSettings.PaymentMode.ON_SITE ? w.payOnSite() : w.simulated()).append('\n');
    }

    MarketDetail.Imprint imprint = in.market().imprint();
    b.append('\n').append(w.seller()).append(":\n");
    b.append("  ").append(imprint.legalName()).append('\n');
    b.append("  ").append(imprint.addressLine()).append('\n');
    if (imprint.registerInfo() != null) {
      b.append("  ").append(w.register()).append(": ").append(imprint.registerInfo()).append('\n');
    }
    if (in.market().phone() != null) {
      b.append("  ").append(w.phone()).append(": ").append(in.market().phone()).append('\n');
    }

    b.append('\n');
    if (in.webUrl() != null) {
      line(b, w.view(), in.webUrl() + "/orders/" + order.id());
    }
    PlatformSettings platform = in.platform();
    if (platform.termsUrl() != null) {
      line(b, w.terms(), platform.termsUrl());
    }
    if (platform.privacyUrl() != null) {
      line(b, w.privacy(), platform.privacyUrl());
    }
    if (platform.supportEmail() != null) {
      line(b, w.help(), platform.supportEmail());
    }
    b.append('\n').append(w.footer()).append('\n');
    return new Mail(subject, b.toString());
  }

  private static void line(StringBuilder b, String label, String value) {
    b.append(label).append(": ").append(value).append('\n');
  }

  private static String money(long cents, Words w) {
    // Euro amounts as in Germany (13,40 €) for every language; English uses its own notation.
    NumberFormat format = NumberFormat.getCurrencyInstance(Locale.UK.equals(w.locale()) ? Locale.UK : Locale.GERMANY);
    format.setCurrency(Currency.getInstance("EUR"));
    return format.format(BigDecimal.valueOf(cents, 2));
  }
}
