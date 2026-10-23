package de.dukkan.notification.application;

import de.dukkan.order.application.OrderEvent;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Notification texts in German, English and Arabic. Amounts keep the German number format in every
 * language (product decision 3), like the app.
 */
final class NotificationTexts {

  /** Kind (stored), title and body of one notification. */
  record Text(String kind, String title, String body) {}

  private NotificationTexts() {}

  /** For the market's merchants: a new or cancelled order. */
  static Optional<Text> forMerchant(String type, OrderEvent event, @Nullable String locale) {
    String lang = lang(locale);
    String amount = euro(event.totalCents());
    String fulfilment = fulfilment(event.fulfilmentType(), lang);
    if (OrderEvent.PLACED.equals(type)) {
      String title = switch (lang) {
        case "en" -> "New order";
        case "ar" -> "طلب جديد";
        default -> "Neue Bestellung";
      };
      return Optional.of(new Text("NEW_ORDER", title, event.orderNumber() + " · " + amount + " · " + fulfilment));
    }
    if ("CANCELLED".equals(event.status()) && "CUSTOMER".equals(event.actor())) {
      String title = switch (lang) {
        case "en" -> "Order cancelled";
        case "ar" -> "تم إلغاء الطلب";
        default -> "Bestellung storniert";
      };
      return Optional.of(new Text("ORDER_CANCELLED", title, event.orderNumber() + " · " + amount));
    }
    return Optional.empty();
  }

  /** For the customer: what the merchant did with the order. */
  static Optional<Text> forCustomer(String type, OrderEvent event, @Nullable String locale) {
    String lang = lang(locale);
    if (OrderEvent.ITEMS_ADJUSTED.equals(type)) {
      return Optional.of(itemsAdjusted(event, lang));
    }
    if (!"MERCHANT".equals(event.actor()) || !OrderEvent.STATUS_CHANGED.equals(type)) {
      return Optional.empty();
    }
    String title =
        switch (event.status()) {
          case "ACCEPTED" -> pick(lang, "Bestellung angenommen", "Order accepted", "تم قبول الطلب");
          case "REJECTED" -> pick(lang, "Bestellung abgelehnt", "Order declined", "تم رفض الطلب");
          case "READY_FOR_PICKUP" -> pick(lang, "Bereit zur Abholung", "Ready for pickup", "جاهز للاستلام");
          case "OUT_FOR_DELIVERY" -> pick(lang, "Unterwegs zu dir", "On its way", "في الطريق إليك");
          case "COMPLETED" -> pick(lang, "Bestellung abgeschlossen", "Order completed", "اكتمل الطلب");
          default -> null;
        };
    return title == null ? Optional.empty() : Optional.of(new Text("ORDER_" + event.status(), title, event.marketName() + " · " + event.orderNumber()));
  }

  /** "2 Artikel nicht (ganz) verfügbar · neuer Betrag 12,40 € statt 15,30 €". */
  private static Text itemsAdjusted(OrderEvent event, String lang) {
    int shortItems = event.shortItems() == null ? 0 : event.shortItems();
    String title = pick(lang, "Bestellung angepasst", "Order updated", "تم تعديل الطلب");
    String amount = euro(event.totalCents());
    String before = event.previousTotalCents() == null ? null : euro(event.previousTotalCents());
    String body =
        switch (lang) {
          case "en" ->
              (shortItems == 1 ? "1 item is" : shortItems + " items are") + " not (fully) available · new total " + amount
                  + (before == null ? "" : " instead of " + before);
          case "ar" ->
              "بعض المنتجات غير متوفرة (" + shortItems + ") · المبلغ الجديد " + amount + (before == null ? "" : " بدلًا من " + before);
          default ->
              shortItems + " Artikel nicht (ganz) verfügbar · neuer Betrag " + amount + (before == null ? "" : " statt " + before);
        };
    return new Text("ORDER_ADJUSTED", title, event.marketName() + " · " + event.orderNumber() + " · " + body);
  }

  private static String fulfilment(String type, String lang) {
    return "DELIVERY".equals(type) ? pick(lang, "Lieferung", "Delivery", "توصيل") : pick(lang, "Abholung", "Pickup", "استلام");
  }

  private static String pick(String lang, String de, String en, String ar) {
    return switch (lang) {
      case "en" -> en;
      case "ar" -> ar;
      default -> de;
    };
  }

  private static String lang(@Nullable String locale) {
    return locale == null ? "de" : locale;
  }

  static String euro(long cents) {
    NumberFormat format = NumberFormat.getNumberInstance(Locale.GERMANY);
    format.setMinimumFractionDigits(2);
    format.setMaximumFractionDigits(2);
    return format.format(cents / 100.0) + " €";
  }
}
