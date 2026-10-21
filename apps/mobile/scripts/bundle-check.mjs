// Builds the release JS bundle for Android and iOS (Metro, minified) and compiles each to Hermes
// bytecode, like the native release builds do. Catches bundling and Hermes syntax problems without
// an Android SDK or Xcode. Output goes to a temporary directory.
import { execFileSync } from 'node:child_process';
import { existsSync, mkdtempSync, rmSync, statSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const platformDir = { linux: 'linux64-bin', darwin: 'osx-bin', win32: 'win64-bin' }[process.platform];
const hermesc = join('node_modules', 'hermes-compiler', 'hermesc', platformDir ?? 'linux64-bin', process.platform === 'win32' ? 'hermesc.exe' : 'hermesc');
const out = mkdtempSync(join(tmpdir(), 'dukkan-bundle-'));

try {
  for (const platform of ['android', 'ios']) {
    const bundle = join(out, `index.${platform}.bundle`);
    execFileSync(
      'npx',
      ['react-native', 'bundle', '--platform', platform, '--dev', 'false', '--minify', 'true', '--entry-file', 'index.js', '--bundle-output', bundle, '--assets-dest', join(out, `assets-${platform}`)],
      { stdio: ['ignore', 'ignore', 'inherit'] },
    );
    const bytecode = `${bundle}.hbc`;
    if (!existsSync(hermesc)) {
      throw new Error(`Hermes compiler not found at ${hermesc}`);
    }
    // -w: the warnings about RN globals (requestAnimationFrame …) are expected.
    execFileSync(hermesc, ['-emit-binary', '-O', '-w', '-out', bytecode, bundle], { stdio: 'inherit' });
    console.log(`${platform}: bundle ${Math.round(statSync(bundle).size / 1024)} KiB, bytecode ${Math.round(statSync(bytecode).size / 1024)} KiB`);
  }
} finally {
  rmSync(out, { recursive: true, force: true });
}
