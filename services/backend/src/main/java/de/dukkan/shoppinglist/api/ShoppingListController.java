package de.dukkan.shoppinglist.api;

import de.dukkan.i18n.application.ContentLocale;
import de.dukkan.shared.ai.ModelImages;
import de.dukkan.shared.error.InvalidRequestException;
import de.dukkan.shared.security.CurrentUser;
import de.dukkan.shoppinglist.application.ShoppingListService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Shopping list by voice, photo or video (F1): items matched to a market's products, to confirm in the app. */
@RestController
final class ShoppingListController {

  private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

  private final ShoppingListService shoppingLists;

  ShoppingListController(ShoppingListService shoppingLists) {
    this.shoppingLists = shoppingLists;
  }

  record Capabilities(boolean ai, int maxImages, int maxTextLength) {}

  /** Public: the app shows the voice, photo and video options accordingly. */
  @GetMapping("/api/v1/shopping-list")
  Capabilities capabilities() {
    return new Capabilities(shoppingLists.aiAvailable(), ShoppingListService.MAX_IMAGES, ShoppingListService.MAX_TEXT);
  }

  @PostMapping(path = "/api/v1/markets/{marketId}/shopping-list", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  ShoppingListService.Result read(
      @CurrentSecurityContext SecurityContext security,
      @PathVariable UUID marketId,
      @RequestParam(name = "text", required = false) @Nullable String text,
      @RequestPart(name = "images", required = false) @Nullable List<MultipartFile> images,
      @RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale)
      throws IOException {
    List<MultipartFile> files = images == null ? List.of() : images;
    if (files.size() > ShoppingListService.MAX_IMAGES) {
      throw new InvalidRequestException("images", "Size", "at most " + ShoppingListService.MAX_IMAGES + " images");
    }
    List<ModelImages.Image> decoded = new ArrayList<>();
    for (MultipartFile file : files) {
      String type = file.getContentType() == null ? "" : file.getContentType();
      if (!IMAGE_TYPES.contains(type)) {
        throw new InvalidRequestException("images", "ContentType", "images must be JPEG, PNG or WebP");
      }
      decoded.add(new ModelImages.Image(file.getBytes(), type));
    }
    return shoppingLists.read(CurrentUser.id(security), marketId, text, decoded, locale);
  }
}
