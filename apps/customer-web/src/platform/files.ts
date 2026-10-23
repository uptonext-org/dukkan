/** Opens the browser's file chooser; on phones `capture` opens the camera directly. Null when cancelled. */
export function chooseFile(accept: string, capture: boolean, doc: Document = document): Promise<File | null> {
  return new Promise((resolve) => {
    const input = doc.createElement('input');
    input.type = 'file';
    input.accept = accept;
    if (capture) {
      input.setAttribute('capture', 'environment');
    }
    input.style.display = 'none';
    const done = (file: File | null) => {
      input.remove();
      resolve(file);
    };
    input.addEventListener('change', () => done(input.files?.[0] ?? null), { once: true });
    input.addEventListener('cancel', () => done(null), { once: true });
    doc.body.appendChild(input);
    input.click();
  });
}

/** The size that fits `maxEdge` on the long side, never enlarged. */
export function fitWithin(width: number, height: number, maxEdge: number): { width: number; height: number } {
  const scale = Math.min(1, maxEdge / Math.max(width, height));
  return { width: Math.max(1, Math.round(width * scale)), height: Math.max(1, Math.round(height * scale)) };
}

/**
 * Draws an image or video frame into a JPEG at most `maxEdge` px on the long side. Re-encoding drops
 * EXIF metadata (location) before upload, like the app does; transparent areas become white.
 */
export function encodeJpeg(
  source: CanvasImageSource,
  width: number,
  height: number,
  maxEdge: number,
  quality: number,
): Promise<Blob> {
  const size = fitWithin(width, height, maxEdge);
  const canvas = document.createElement('canvas');
  canvas.width = size.width;
  canvas.height = size.height;
  const context = canvas.getContext('2d');
  if (context === null) {
    return Promise.reject(new Error('no 2d context'));
  }
  context.fillStyle = '#ffffff';
  context.fillRect(0, 0, size.width, size.height);
  context.drawImage(source, 0, 0, size.width, size.height);
  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => (blob === null ? reject(new Error('encode failed')) : resolve(blob)), 'image/jpeg', quality);
  });
}
