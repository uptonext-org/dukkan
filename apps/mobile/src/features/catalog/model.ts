import type { Category } from '../../shared/api';

export interface CategoryRow {
  category: Category;
  depth: number;
}

/** Flattens the flat category list (linked via parentId) into an ordered, indented tree. */
export function flattenCategoryTree(categories: readonly Category[]): CategoryRow[] {
  const byParent = new Map<string | null, Category[]>();
  const ids = new Set(categories.map((c) => c.id));
  for (const category of categories) {
    const parent = category.parentId != null && ids.has(category.parentId) ? category.parentId : null;
    const siblings = byParent.get(parent) ?? [];
    siblings.push(category);
    byParent.set(parent, siblings);
  }
  const rows: CategoryRow[] = [];
  const visit = (parent: string | null, depth: number) => {
    const children = [...(byParent.get(parent) ?? [])].sort((a, b) => a.sortOrder - b.sortOrder);
    for (const child of children) {
      rows.push({ category: child, depth });
      visit(child.id, depth + 1);
    }
  };
  visit(null, 0);
  return rows;
}
