package de.dukkan.identity.domain;

import static java.util.UUID.randomUUID;

import de.dukkan.shared.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.UUID;

/** A registered customer account. Emails are stored lower-cased. */
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

  protected User() {}

  public User(String email, String passwordHash, String firstName, String lastName) {
    this.id = randomUUID();
    this.email = normalizeEmail(email);
    this.passwordHash = passwordHash;
    this.firstName = firstName.strip();
    this.lastName = lastName.strip();
  }

  public static String normalizeEmail(String email) {
    return email.strip().toLowerCase(Locale.ROOT);
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
}
