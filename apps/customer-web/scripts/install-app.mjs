// Runs before `npm run build`: installs apps/mobile (the app code and its libraries) if it is not
// installed yet. Hosts that only run `npm install && npm run build` in this folder (Hostinger)
// need this; CI and the Dockerfile install apps/mobile themselves, so it does nothing there.
import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const app = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../mobile');

if (!existsSync(path.join(app, 'node_modules/react'))) {
  console.log(`Installing ${app} (npm ci --ignore-scripts)`);
  // The app's native dependencies are only needed as JavaScript here; skip their install scripts.
  execFileSync('npm', ['ci', '--ignore-scripts', '--no-audit', '--no-fund'], { cwd: app, stdio: 'inherit' });
}
