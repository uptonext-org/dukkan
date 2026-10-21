package de.dukkan.identity.domain;

import static java.util.UUID.randomUUID;

import de.dukkan.shared.persistence.AuditedEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A registered account. Emails are stored lower-cased. */
@Entity
@Table(name = "app_user")
public class User extends AuditedEntity {

  @Id private UUID id;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(name = "first_name", nullable = false)
  private String firstName;

  @Column(name = "last_name", nullable = false)
  private String lastName;

  @Column(name = "preferred_locale")
  private @Nullable String preferredLocale;

  @CollectionTable(name = "app_user_role", joinColumns = @JoinColumn(name = "user_id"))
  @Column(name = "role", nullable = false)
  @ElementCollection(fetch = FetchType.EAGER)
  @Enumerated(EnumType.STRING)
  private Set<Role> roles = EnumSet.noneOf(Role.class);

  protected User() {}

  public User(
      String email,
      String passwordHash,
      String firstName,
      String lastName,
      Set<Role> roles,
      @Nullable String preferredLocale) {
    this.id = randomUUID();
    this.email = normalizeEmail(email);
    this.passwordHash = passwordHash;
    this.firstName = firstName.strip();
    this.lastName = lastName.strip();
    this.roles = EnumSet.copyOf(roles);
    this.preferredLocale = preferredLocale;
  }

  public static String normalizeEmail(String email) {
    return email.strip().toLowerCase(Locale.ROOT);
  }

  public void updateProfile(@Nullable String firstName, @Nullable String lastName, @Nullable String preferredLocale) {
    if (firstName != null) {
      this.firstName = firstName.strip();
    }
    if (lastName != null) {
      this.lastName = lastName.strip();
    }
    if (preferredLocale != null) {
      this.preferredLocale = preferredLocale;
    }
  }

  public void grantRole(Role role) {
    roles.add(role);
  }

  @Override
  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getFirstName() {
    return firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public @Nullable String getPreferredLocale() {
    return preferredLocale;
  }

  public Set<Role> getRoles() {
    return roles.isEmpty() ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(roles);
  }
}
