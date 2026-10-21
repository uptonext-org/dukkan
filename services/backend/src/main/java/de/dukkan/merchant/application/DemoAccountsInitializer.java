package de.dukkan.merchant.application;

import de.dukkan.address.application.AddressService;
import de.dukkan.address.application.AddressDetails;
import de.dukkan.identity.application.IdentityService;
import de.dukkan.identity.application.RegisterCommand;
import de.dukkan.identity.application.UserView;
import de.dukkan.market.application.MarketService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Creates the demo accounts in the {@code local} and {@code demo} profiles only (never via Flyway):
 * a customer with a St. Georg address and a merchant who owns two seed markets. Idempotent.
 */
@Component
@Order(10)
@Profile({"local", "demo"})
public class DemoAccountsInitializer implements ApplicationRunner {

  public static final String CUSTOMER_EMAIL = "kunde@dukkan.local";
  public static final String MERCHANT_EMAIL = "haendler@dukkan.local";
  /** Development-only password, documented in README.md. */
  public static final String DEMO_PASSWORD = "dukkan-demo-2026";

  static final List<String> MERCHANT_MARKETS = List.of("anatolia-frischmarkt", "backstube-sonnenkorn");

  private static final Logger LOG = LoggerFactory.getLogger(DemoAccountsInitializer.class);

  private final IdentityService identity;
  private final MarketService markets;
  private final AddressService addresses;

  public DemoAccountsInitializer(IdentityService identity, MarketService markets, AddressService addresses) {
    this.identity = identity;
    this.markets = markets;
    this.addresses = addresses;
  }

  @Override
  public void run(ApplicationArguments args) {
    UserView customer = ensureUser(CUSTOMER_EMAIL, "Lina", "Demir", false, "de");
    if (addresses.list(customer.id()).isEmpty()) {
      addresses.create(
          customer.id(),
          new AddressDetails("Zuhause", "Pfefferminzgang", "4", null, "20099", "Hamburg", "Bitte zweimal klingeln"),
          null,
          true);
    }
    UserView merchant = ensureUser(MERCHANT_EMAIL, "Kemal", "Aydın", true, "de");
    for (String slug : MERCHANT_MARKETS) {
      markets.findIdBySlug(slug).ifPresent(id -> markets.assignOwner(id, merchant.id()));
    }
    LOG.info("Demo accounts ready: {} and {} (password documented in README)", CUSTOMER_EMAIL, MERCHANT_EMAIL);
  }

  private UserView ensureUser(String email, String firstName, String lastName, boolean merchant, String locale) {
    return identity
        .findByEmail(email)
        .orElseGet(
            () -> identity.register(new RegisterCommand(email, DEMO_PASSWORD, firstName, lastName, merchant, locale)).user());
  }
}
