// Points a store build at the production API, or checks that it does.
//
//   node scripts/release-config.mjs https://dukkan.de/api/v1   # writes apiBaseUrl into app.json
//   node scripts/release-config.mjs --check                     # fails unless apiBaseUrl is https
//
// Without apiBaseUrl the app talks to a local backend (src/shared/config/env.ts), which is right
// for development and wrong for every store build.
import { readFileSync, writeFileSync } from 'node:fs';

const file = new URL('../app.json', import.meta.url);
const app = JSON.parse(readFileSync(file, 'utf8'));
const [arg] = process.argv.slice(2);

function valid(url) {
  try {
    const parsed = new URL(url);
    return parsed.protocol === 'https:' && parsed.pathname.replace(/\/+$/, '').endsWith('/api/v1');
  } catch {
    return false;
  }
}

if (arg === undefined || arg === '--check') {
  if (!valid(app.apiBaseUrl ?? '')) {
    console.error(`app.json apiBaseUrl is ${JSON.stringify(app.apiBaseUrl)}: a store build needs https://<domain>/api/v1`);
    process.exit(1);
  }
  console.log(`apiBaseUrl ${app.apiBaseUrl}`);
} else {
  if (!valid(arg)) {
    console.error(`Not an https API URL ending in /api/v1: ${arg}`);
    process.exit(1);
  }
  app.apiBaseUrl = arg.replace(/\/+$/, '');
  writeFileSync(file, `${JSON.stringify(app, null, 2)}\n`);
  console.log(`app.json apiBaseUrl set to ${app.apiBaseUrl}`);
}
