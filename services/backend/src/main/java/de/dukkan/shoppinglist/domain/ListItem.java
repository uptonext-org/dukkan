package de.dukkan.shoppinglist.domain;

import java.util.List;

/**
 * One line of a shopping list: the words the customer used, what to look for in the catalog (best
 * term first) and how much.
 */
public record ListItem(String text, List<String> searchTerms, Amount amount) {

  public ListItem {
    searchTerms = List.copyOf(searchTerms);
  }
}
