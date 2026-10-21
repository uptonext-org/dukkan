/* Runs after the test framework is installed: clean up per-test React Query clients. */
import { screen } from '@testing-library/react-native';

import { hasBidiControls } from './src/shared/format/bidi';
import { disposeQueryClients } from './src/test/render';

type JsonNode = { type: string; props: Record<string, unknown>; children: (JsonNode | string)[] | null };

const SPOKEN_PROPS = ['accessibilityLabel', 'accessibilityHint', 'aria-label'] as const;

/** Every spoken prop in the rendered tree that still carries bidi isolation marks. */
function pollutedSpokenProps(node: JsonNode | JsonNode[] | string | null, found: string[] = []): string[] {
  if (node === null || typeof node === 'string') {
    return found;
  }
  if (Array.isArray(node)) {
    node.forEach((child) => pollutedSpokenProps(child, found));
    return found;
  }
  for (const prop of SPOKEN_PROPS) {
    const value = node.props[prop];
    if (typeof value === 'string' && hasBidiControls(value)) {
      found.push(`${node.type} ${prop}="${value.replace(/[\u2066-\u2069]/g, '⁅')}"`);
    }
  }
  const value = node.props.accessibilityValue as { text?: unknown } | undefined;
  if (typeof value?.text === 'string' && hasBidiControls(value.text)) {
    found.push(`${node.type} accessibilityValue.text="${value.text}"`);
  }
  (node.children ?? []).forEach((child) => pollutedSpokenProps(child, found));
  return found;
}

afterEach(() => {
  // Guard for shared/format/bidi.ts: isolation marks belong in visible text, never in what a
  // screen reader announces. Runs before Testing Library's own cleanup unmounts the tree.
  let tree: ReturnType<typeof screen.toJSON> | undefined;
  try {
    tree = screen.toJSON();
  } catch {
    tree = undefined;
  }
  disposeQueryClients();
  if (tree != null) {
    const polluted = pollutedSpokenProps(tree);
    if (polluted.length > 0) {
      throw new Error(`Bidi isolation marks in accessibility props (use forSpeech()):\n${[...new Set(polluted)].join('\n')}`);
    }
  }
});
