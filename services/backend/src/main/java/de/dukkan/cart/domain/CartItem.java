package de.dukkan.cart.domain;

import static java.util.UUID.randomUUID;

import de.dukkan.shared.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/** One product line in a cart. */
@Entity
@Table(name = "cart_item")
public class CartItem extends AuditedEntity {

  @Id private UUID id;

  @JoinColumn(name = "cart_id", nullable = false, updatable = false)
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @SuppressWarnings("UnusedVariable") // Owning side of the JPA association; written, read by Hibernate.
  private Cart cart;

  @Column(name = "product_id", nullable = false, updatable = false)
  private UUID productId;

  @Column(nullable = false)
  private int quantity;

  protected CartItem() {}

  CartItem(Cart cart, UUID productId, int quantity) {
    this.id = randomUUID();
    this.cart = cart;
    this.productId = productId;
    this.quantity = quantity;
  }

  @Override
  public UUID getId() {
    return id;
  }

  public UUID getProductId() {
    return productId;
  }

  public int getQuantity() {
    return quantity;
  }

  void setQuantity(int quantity) {
    this.quantity = quantity;
  }
}
