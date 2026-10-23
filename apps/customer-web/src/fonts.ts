// @font-face for the app's bundled TTFs, under the family names the styles use
// (typography.ts: 'Inter-Regular', 'IBMPlexSansArabic-Bold', ...).
const fonts = import.meta.glob<string>('../../mobile/assets/fonts/*.ttf', { query: '?url', import: 'default', eager: true });

export function fontFaces(files: Record<string, string>): string {
  return Object.entries(files)
    .map(([file, url]) => {
      const family = (file.split('/').pop() ?? '').replace(/\.ttf$/, '');
      return `@font-face{font-family:'${family}';src:url('${url}') format('truetype');font-display:swap;}`;
    })
    .join('\n');
}

const style = document.createElement('style');
style.textContent = fontFaces(fonts);
document.head.appendChild(style);
