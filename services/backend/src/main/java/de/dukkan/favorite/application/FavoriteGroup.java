package de.dukkan.favorite.application;

import de.dukkan.catalog.application.CategoryView;
import java.util.List;

/** Contract schema {@code FavoriteGroup}. */
public record FavoriteGroup(CategoryView category, List<FavoriteItem> items) {}
