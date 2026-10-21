// `react-native` for the web preview: react-native-web plus the few RN 0.8x exports that
// third-party packages reference at module level but never call on web.
export * from 'react-native-web';

const missingModule = (name) => {
  throw new Error(`[web-preview] native module ${name} is not available on web`);
};
export const TurboModuleRegistry = {
  get: () => null,
  getEnforcing: (name) => new Proxy({}, { get: () => () => missingModule(name) }),
};
export function codegenNativeComponent(name) {
  return name;
}
export function codegenNativeCommands() {
  return {};
}

/**
 * Layout compensation (web only). Sheet.tsx puts `maxHeight: '88%'` on a child of an auto-height
 * KeyboardAvoidingView. Yoga resolves that percentage against the screen-height space the parent
 * is given; CSS resolves it against the parent's own (content) height, which clips every sheet
 * by 12 % and lets tall sheets overflow the top of the screen. Giving the wrapper the full
 * height and bottom-aligning its child reproduces the native result. Sheet is the only user.
 */
import { createElement } from 'react';
import { View as WebView } from 'react-native-web';

export function KeyboardAvoidingView({ behavior, keyboardVerticalOffset, contentContainerStyle, style, ...rest }) {
  return createElement(WebView, {
    ...rest,
    pointerEvents: 'box-none',
    style: [{ height: '100%', justifyContent: 'flex-end' }, style],
  });
}
