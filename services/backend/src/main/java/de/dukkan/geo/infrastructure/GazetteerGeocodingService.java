package de.dukkan.geo.infrastructure;

import de.dukkan.geo.application.GeoMath;
import de.dukkan.geo.application.GeocodingService;
import de.dukkan.geo.application.Place;
import de.dukkan.i18n.application.TextNormalizer;
import de.dukkan.shared.api.GeoPoint;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * {@link GeocodingService} backed by table {@code gazetteer_place} (Hamburg districts, postal codes
 * and streets). The table is small reference data and is held in memory.
 */
@Service
public class GazetteerGeocodingService implements GeocodingService {

  /** Reverse geocoding only answers within this distance (outside the covered area: 404). */
  private static final double MAX_REVERSE_DISTANCE_METERS = 25_000;

  /** "Lange Reihe 12a" → street "Lange Reihe", house number "12a". */
  private static final Pattern HOUSE_NUMBER = Pattern.compile("^(.*\\D)\\s+(\\d{1,4}\\s?[a-zA-Z]?)$");

  private static final Pattern POSTAL_CODE = Pattern.compile("\\b(\\d{5})\\b");

  private static final Pattern PUNCTUATION = Pattern.compile("[.,;:/\\\\'’()\\-]+");

  record Entry(
      String kind,
      @Nullable String street,
      @Nullable String postalCode,
      @Nullable String district,
      String city,
      double lat,
      double lng,
      String normalizedStreet,
      String normalizedDistrict) {}

  private final JdbcClient jdbc;
  private final AtomicReference<@Nullable List<Entry>> entries = new AtomicReference<>();

  public GazetteerGeocodingService(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<Place> search(String query, int limit) {
    String raw = query.strip();
    String houseNumber = null;
    Matcher numbered = HOUSE_NUMBER.matcher(raw);
    if (numbered.matches()) {
      raw = numbered.group(1).strip();
      houseNumber = numbered.group(2).replace(" ", "");
    }
    Matcher postal = POSTAL_CODE.matcher(raw);
    String postalCode = postal.find() ? postal.group(1) : null;
    String text = TextNormalizer.normalize(POSTAL_CODE.matcher(raw).replaceAll(" ").replace(",", " "));

    record Scored(Entry entry, int score) {}
    List<Scored> scored = new ArrayList<>();
    for (Entry entry : entries()) {
      if (postalCode != null && !postalCode.equals(entry.postalCode())) {
        continue;
      }
      int score = score(entry, text, postalCode != null);
      if (score >= 0) {
        scored.add(new Scored(entry, score));
      }
    }
    String number = houseNumber;
    return scored.stream()
        .sorted(Comparator.comparingInt(Scored::score).thenComparing(s -> label(s.entry(), null)))
        .limit(limit)
        .map(s -> toPlace(s.entry(), "STREET".equals(s.entry().kind()) ? number : null))
        .toList();
  }

  /**
   * Lower is better; negative means no match. Matching is word based: every query word must be the
   * start of a word in the street or district name, so punctuation ("St. Georg" vs "st georg") and
   * word order do not matter and "str" finds "...strasse". Streets whose own name matches rank first;
   * otherwise districts, then their postal codes, then streets inside a matching district.
   */
  static int score(Entry entry, String text, boolean postalCodeGiven) {
    String kind = entry.kind();
    if (text.isEmpty()) {
      if (!postalCodeGiven) {
        return -1;
      }
      return switch (kind) {
        case "POSTAL_CODE" -> 0;
        case "DISTRICT" -> 1;
        default -> 2;
      };
    }
    List<String> query = words(text);
    if ("STREET".equals(kind) && matchesAll(query, words(entry.normalizedStreet()))) {
      return compact(entry.normalizedStreet()).startsWith(compact(text)) ? 0 : 5;
    }
    if (!matchesAll(query, words(entry.normalizedDistrict()))) {
      return -1;
    }
    boolean exactStart = compact(entry.normalizedDistrict()).startsWith(compact(text));
    int base =
        switch (kind) {
          case "DISTRICT" -> 10;
          case "POSTAL_CODE" -> 20;
          default -> 40;
        };
    return exactStart ? base : base + 5;
  }

  private static boolean matchesAll(List<String> query, List<String> words) {
    return query.stream().allMatch(q -> words.stream().anyMatch(w -> w.startsWith(q) || infixMatch(w, q)));
  }

  /** "hauptstr" (or "str") matches the "strasse" suffix of "hauptstrasse"; other infixes need 4 letters. */
  private static boolean infixMatch(String word, String query) {
    if (query.endsWith("str") && word.endsWith("strasse")) {
      return word.startsWith(query.substring(0, query.length() - 3));
    }
    return query.length() >= 4 && word.contains(query);
  }

  private static List<String> words(String normalized) {
    return List.of(PUNCTUATION.matcher(normalized).replaceAll(" ").strip().split("\\s+"));
  }

  private static String compact(String normalized) {
    return PUNCTUATION.matcher(normalized).replaceAll(" ").replaceAll("\\s+", " ").strip();
  }

  @Override
  public Optional<Place> reverse(double lat, double lng) {
    return entries().stream()
        .filter(e -> !"POSTAL_CODE".equals(e.kind()))
        .min(Comparator.comparingDouble(e -> GeoMath.distanceMeters(lat, lng, e.lat(), e.lng())))
        .filter(e -> GeoMath.distanceMeters(lat, lng, e.lat(), e.lng()) <= MAX_REVERSE_DISTANCE_METERS)
        .map(e -> toPlace(e, null));
  }

  @Override
  public Optional<GeoPoint> geocode(String street, String postalCode, @Nullable String city) {
    String normalizedStreet = TextNormalizer.normalize(street);
    List<Entry> all = entries();
    return all.stream()
        .filter(e -> "STREET".equals(e.kind()) && postalCode.equals(e.postalCode()))
        .filter(e -> e.normalizedStreet().equals(normalizedStreet))
        .findFirst()
        .or(() -> all.stream().filter(e -> "POSTAL_CODE".equals(e.kind()) && postalCode.equals(e.postalCode())).findFirst())
        .map(e -> GeoPoint.of(e.lat(), e.lng()));
  }

  private static Place toPlace(Entry e, @Nullable String houseNumber) {
    return new Place(
        label(e, houseNumber),
        e.street(),
        houseNumber,
        e.postalCode(),
        e.district(),
        e.city(),
        GeoPoint.of(e.lat(), e.lng()));
  }

  private static String label(Entry e, @Nullable String houseNumber) {
    return switch (e.kind()) {
      case "STREET" ->
          e.street()
              + (houseNumber == null ? "" : " " + houseNumber)
              + ", "
              + e.postalCode()
              + " "
              + e.city()
              + " ("
              + e.district()
              + ")";
      case "POSTAL_CODE" -> e.postalCode() + " " + e.city() + " (" + e.district() + ")";
      default -> e.district() + ", " + e.city();
    };
  }

  private List<Entry> entries() {
    List<Entry> cached = entries.get();
    if (cached == null) {
      cached =
          jdbc.sql("SELECT kind, street, postal_code, district, city, lat, lng FROM gazetteer_place")
              .query(
                  (rs, i) ->
                      new Entry(
                          rs.getString("kind"),
                          rs.getString("street"),
                          rs.getString("postal_code"),
                          rs.getString("district"),
                          rs.getString("city"),
                          rs.getDouble("lat"),
                          rs.getDouble("lng"),
                          TextNormalizer.normalize(rs.getString("street")),
                          TextNormalizer.normalize(rs.getString("district"))))
              .list();
      entries.set(List.copyOf(cached));
    }
    return cached;
  }
}
