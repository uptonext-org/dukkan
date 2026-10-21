import { createContext, use, type ReactNode } from 'react';

import type { PhotoPicker } from './PhotoPicker';

const PhotoPickerContext = createContext<PhotoPicker | null>(null);

export function PhotoPickerProvider({ picker, children }: { picker: PhotoPicker; children: ReactNode }) {
  return <PhotoPickerContext value={picker}>{children}</PhotoPickerContext>;
}

export function usePhotoPicker(): PhotoPicker {
  const picker = use(PhotoPickerContext);
  if (picker === null) {
    throw new Error('usePhotoPicker must be used inside <PhotoPickerProvider>.');
  }
  return picker;
}
