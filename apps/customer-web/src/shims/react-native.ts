// `react-native` in the web build: react-native-web plus what the app and its libraries use that
// react-native-web leaves out or implements as a no-op (Alert, Share without the share sheet).
import { createElement, type ReactNode } from 'react';
import { View as WebView, useWindowDimensions as useWebWindowDimensions } from 'react-native-web';

export * from 'react-native-web';

/** The app column on wide screens (WebApp.tsx); layout code sees this as the window width. */
export const FRAME_MAX_WIDTH = 520;

interface AlertButton {
  text?: string;
  style?: 'default' | 'cancel' | 'destructive';
  onPress?: () => void;
}

/**
 * Alert.alert with the browser's dialogs: one button → alert, more → confirm (OK runs the last
 * non-cancel button, which is the action in every dialog of the app).
 */
export const Alert = {
  alert(title: string, message?: string, buttons?: AlertButton[]): void {
    const text = message !== undefined && message !== '' ? `${title}\n\n${message}` : title;
    const list = buttons ?? [];
    if (list.length <= 1) {
      window.alert(text);
      list[0]?.onPress?.();
      return;
    }
    const cancel = list.find((b) => b.style === 'cancel');
    const action = [...list].reverse().find((b) => b.style !== 'cancel');
    if (window.confirm(text)) {
      action?.onPress?.();
    } else {
      cancel?.onPress?.();
    }
  },
  prompt(): void {
    // Not used by the app.
  },
};

export const sharedAction = 'sharedAction';
export const dismissedAction = 'dismissedAction';

/** The system share sheet where the browser has one, otherwise a download of the content. */
export const Share = {
  sharedAction,
  dismissedAction,
  async share(content: { title?: string; message?: string; url?: string }): Promise<{ action: string }> {
    const text = content.message ?? content.url ?? '';
    if (typeof navigator.share === 'function') {
      try {
        await navigator.share({ ...(content.title !== undefined ? { title: content.title } : {}), text });
        return { action: sharedAction };
      } catch (error) {
        if (error instanceof DOMException && error.name === 'AbortError') {
          return { action: dismissedAction };
        }
      }
    }
    const json = text.trimStart().startsWith('{') || text.trimStart().startsWith('[');
    const blob = new Blob([text], { type: json ? 'application/json' : 'text/plain' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `dukkan${json ? '.json' : '.txt'}`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 10_000);
    return { action: sharedAction };
  },
};

export function useWindowDimensions() {
  const window = useWebWindowDimensions();
  return { ...window, width: Math.min(window.width, FRAME_MAX_WIDTH) };
}

/**
 * Sheet.tsx puts `maxHeight: '88%'` on a child of an auto-height KeyboardAvoidingView. Yoga resolves
 * that percentage against the screen; CSS against the parent's content height, which clipped every
 * sheet. A full-height wrapper with a bottom-aligned child gives the native result.
 */
export function KeyboardAvoidingView({
  behavior: _behavior,
  keyboardVerticalOffset: _offset,
  contentContainerStyle: _content,
  style,
  ...rest
}: {
  behavior?: unknown;
  keyboardVerticalOffset?: unknown;
  contentContainerStyle?: unknown;
  style?: unknown;
  children?: ReactNode;
}) {
  return createElement(WebView, {
    ...rest,
    pointerEvents: 'box-none',
    style: [{ height: '100%', justifyContent: 'flex-end' }, style],
  } as never);
}

// Referenced at module level by native libraries (react-native-screens, svg) but not called on the web.
export const TurboModuleRegistry = {
  get: () => null,
  getEnforcing: (name: string) =>
    new Proxy(
      {},
      {
        get: () => () => {
          throw new Error(`native module ${name} is not available on the web`);
        },
      },
    ),
};
export function codegenNativeComponent(name: string): string {
  return name;
}
export function codegenNativeCommands(): Record<string, never> {
  return {};
}
