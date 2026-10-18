package de.dukkan.catalog.application;

import java.util.List;

public record ProductPageView(List<ProductView> items, int page, int size, long totalItems, int totalPages) {}
