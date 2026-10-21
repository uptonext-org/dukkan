/**
 * Jest runs on Node, whose FormData and Blob have more API than React Native's global typings
 * declare (no `get`/`has`, Blob parts only string | Blob). These helpers bridge the two in tests.
 */
export interface FormDataReader {
  get(name: string): unknown;
  has(name: string): boolean;
}

export function readForm(body: unknown): FormDataReader {
  return body as FormDataReader;
}

export function blobOf(content: Uint8Array | string, type: string): Blob {
  const NodeBlob = Blob as unknown as new (parts: (Uint8Array | string)[], options: { type: string }) => Blob;
  return new NodeBlob([content], { type });
}
