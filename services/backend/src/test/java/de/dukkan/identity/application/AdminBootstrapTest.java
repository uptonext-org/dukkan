package de.dukkan.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminBootstrapTest {

  private static final String EMAIL = "owner@example.org";

  private final List<UUID> granted = new ArrayList<>();

  private IdentityService identity(boolean adminExists, boolean verified) {
    UserView user = new UserView(UUID.randomUUID(), EMAIL, "Ada", "Owner", List.of("CUSTOMER"), null, verified);
    return (IdentityService)
        Proxy.newProxyInstance(
            IdentityService.class.getClassLoader(),
            new Class<?>[] {IdentityService.class},
            (proxy, method, args) ->
                switch (method.getName()) {
                  case "anyActiveWithRole" -> adminExists;
                  case "findByEmail" -> EMAIL.equals(args[0]) ? Optional.of(user) : Optional.empty();
                  case "grantRole" -> {
                    granted.add((UUID) args[0]);
                    yield user;
                  }
                  default -> throw new UnsupportedOperationException(method.getName());
                });
  }

  @Test
  void grantsTheFirstAdminToAConfirmedAddress() {
    new AdminBootstrap(identity(false, true), EMAIL).run(null);
    assertThat(granted).hasSize(1);
  }

  @Test
  void anUnconfirmedAddressDoesNotBecomeAdmin() {
    new AdminBootstrap(identity(false, false), EMAIL).run(null);
    assertThat(granted).isEmpty();
  }

  @Test
  void doesNothingOnceAnAdminExists() {
    new AdminBootstrap(identity(true, true), EMAIL).run(null);
    assertThat(granted).isEmpty();
  }

  @Test
  void doesNothingWithoutTheSetting() {
    new AdminBootstrap(identity(false, true), " ").run(null);
    assertThat(granted).isEmpty();
  }
}
