package de.dukkan.privacy.application;

import de.dukkan.identity.application.IdentityService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * At every start: accounts in the erasure journal that the database still holds (it was restored
 * from a backup taken before they were deleted) are erased again. Normally there are none.
 */
@Component
@Order(0)
final class ErasureReplay implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(ErasureReplay.class);

  private final ErasureJournal journal;
  private final IdentityService identity;
  private final AccountService accounts;

  ErasureReplay(ErasureJournal journal, IdentityService identity, AccountService accounts) {
    this.journal = journal;
    this.identity = identity;
    this.accounts = accounts;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!journal.enabled()) {
      return;
    }
    int replayed = 0;
    for (UUID userId : journal.erasedAccounts()) {
      if (!identity.isDeleted(userId)) {
        accounts.reapplyErasure(userId);
        replayed++;
      }
    }
    if (replayed > 0) {
      LOG.warn("Erasure journal: erased {} account(s) again that a restored backup brought back", replayed);
    }
  }
}
