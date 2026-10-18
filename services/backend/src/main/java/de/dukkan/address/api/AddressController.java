package de.dukkan.address.api;

import de.dukkan.address.application.AddressService;
import de.dukkan.shared.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/v1/addresses")
@RestController
final class AddressController {

  private final AddressService addresses;

  AddressController(AddressService addresses) {
    this.addresses = addresses;
  }

  @GetMapping
  List<AddressResponse> list(@CurrentSecurityContext SecurityContext security) {
    return addresses.list(CurrentUser.id(security)).stream().map(AddressResponse::from).toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  AddressResponse create(@CurrentSecurityContext SecurityContext security, @RequestBody @Valid AddressRequest request) {
    return AddressResponse.from(addresses.create(CurrentUser.id(security), request.toDetails(), request.makeDefault()));
  }

  @PutMapping("/{addressId}")
  AddressResponse update(
      @CurrentSecurityContext SecurityContext security, @PathVariable UUID addressId, @RequestBody @Valid AddressRequest request) {
    return AddressResponse.from(
        addresses.update(CurrentUser.id(security), addressId, request.toDetails(), request.makeDefault()));
  }

  @DeleteMapping("/{addressId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@CurrentSecurityContext SecurityContext security, @PathVariable UUID addressId) {
    addresses.delete(CurrentUser.id(security), addressId);
  }
}
