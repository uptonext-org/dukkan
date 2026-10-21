package de.dukkan.shared.api;

import java.util.List;
import java.util.function.Function;

/** A page of results (contract schemas {@code *Page}). */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

  public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalItems) {
    int totalPages = size <= 0 ? 0 : (int) ((totalItems + size - 1) / size);
    return new PageResponse<>(List.copyOf(items), page, size, totalItems, totalPages);
  }

  /** Pages an in-memory list. */
  public static <T> PageResponse<T> slice(List<T> all, int page, int size) {
    int from = (int) Math.min((long) page * size, all.size());
    int to = Math.min(from + size, all.size());
    return of(all.subList(from, to), page, size, all.size());
  }

  public <R> PageResponse<R> map(Function<? super T, ? extends R> mapper) {
    return new PageResponse<>(items.stream().<R>map(mapper).toList(), page, size, totalItems, totalPages);
  }
}
