package de.dukkan.shoppinglist.application;

import de.dukkan.shared.ai.ModelImages;
import de.dukkan.shoppinglist.domain.ListItem;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Reads a shopping list from a transcript, photos or video frames with a language model. */
public interface ShoppingListReader {

  /** {@code language} is the customer's locale code; search terms come in German and that language. */
  record Request(@Nullable String text, List<ModelImages.Image> images, String language) {
    public Request {
      images = List.copyOf(images);
    }
  }

  boolean enabled();

  /** Empty when the model is unavailable, declines or fails. */
  Optional<List<ListItem>> read(Request request);
}
