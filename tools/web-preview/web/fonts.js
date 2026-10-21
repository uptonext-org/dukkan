// @font-face for the TTFs bundled with the app. Family names equal the PostScript names the app
// uses (typography.ts: 'Inter-Regular', 'IBMPlexSansArabic-Bold', ...).
const fonts = import.meta.glob('../../../apps/mobile/assets/fonts/*.ttf', { query: '?url', import: 'default', eager: true });

const css = Object.entries(fonts)
  .map(([file, url]) => {
    const family = file.split('/').pop().replace(/\.ttf$/, '');
    return `@font-face{font-family:'${family}';src:url('${url}') format('truetype');font-display:block;}`;
  })
  .join('\n');

const style = document.createElement('style');
style.setAttribute('data-dukkan-fonts', '');
style.textContent = css;
document.head.appendChild(style);
