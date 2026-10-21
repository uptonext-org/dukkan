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

  /** Lower is better; negative means no match. Streets rank before districts and postal codes. */
  private static int score(Entry entry, String text, boolean postalCodeGiven) {
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
    return switch (kind) {
      case "STREET" -> {
        if (entry.normalizedStreet().startsWith(text)) {
          yield 0;
        }
        if (entry.normalizedStreet().contains(text)) {
          yield 10;
        }
        yield entry.normalizedDistrict().startsWith(text) ? 40 : -1;
      }
      case "DISTRICT" -> {
        if (entry.normalizedDistrict().startsWith(text)) {
          yield 2;
        }
        yield entry.normalizedDistrict().contains(text) ? 12 : -1;
      }
      default -> entry.normalizedDistrict().startsWith(text) ? 31 : -1;
    };
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
