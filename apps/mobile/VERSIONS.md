# Version decisions: apps/mobile

Checked on 2026-10-18 against the public npm registry (`npm view <pkg>@<ver> peerDependencies engines`).
Direct dependencies are pinned exactly in `package.json`, and `package-lock.json` is committed.
"Verified" below means checked in this sandbox (Linux, no Android SDK or Xcode). Native builds were **not** run.

## Mandated versions

| Component | Requested | Used | Verification | Notes |
|---|---|---|---|---|
| Node.js | 24.22.0 | 24.22.0 | `node -v` → v24.22.0; `.nvmrc`; `engines.node: >=24.22.0` | RN 0.88 engines: `^22.13.0 \|\| ^24.3.0 \|\| >=26.0.0` |
| react-native | 0.88.0 | 0.88.0 | npm `time`: 2026-10-15; peer `react ^19.3.0`, `@types/react ^19.1.1` | Scaffolded from `@react-native-community/template@0.88.0` |
| react | 19.3.1 | 19.3.1 | satisfies RN peer `^19.3.0` | Template pins 19.3.0; bumped to the mandated patch |
| typescript | 6.0.3 (not 7.x) | 6.0.3 | `tsc --noEmit` passes | Template asks for `^6.0.3`; pinned exactly |
| typescript-eslint | 8.71.2 | 8.71.2 | peer `typescript >=4.8.4 <6.1.0` (**includes 6.0.3**), `eslint ^8.57 \|\| ^9 \|\| ^10` | No TS peer conflict to record |

## Scaffold / CLI

| Component | Requested | Used | Verification | Notes |
|---|---|---|---|---|
| @react-native-community/cli (init) | `@latest` | 20.2.0 | `latest` dist-tag = 20.2.0; template 0.88.0 pins `cli`, `cli-platform-android`, `cli-platform-ios` at 20.2.0 | Ran `npx @react-native-community/cli@20.2.0 init Dukkan --version 0.88.0 --skip-install --skip-git-init --directory apps/mobile --package-name app.dukkan.mobile --title Dukkan`. It needed no changes. `--package-name` set the Android namespace/applicationId and the iOS `PRODUCT_BUNDLE_IDENTIFIER` to `app.dukkan.mobile`. It also set the npm `name` to `app.dukkan.mobile`, which I renamed to `dukkan-mobile` |
| @react-native/{babel-preset,jest-preset,metro-config,typescript-config} | (template) | 0.88.0 | Same as RN | `@react-native/eslint-config` was removed (see ESLint) |
| @react-native/new-app-screen | (template) | removed | n/a | Template boilerplate |

## Runtime libraries

| Component | Requested | Used | Verification | Notes |
|---|---|---|---|---|
| @react-navigation/native | v7 | 7.5.1 | peer `react >=18.2`, `react-native *` | Released 2026-10-13 |
| @react-navigation/native-stack | v7 | 7.21.0 | peers: native ^7.5.1, screens >=4, safe-area >=4 | |
| @react-navigation/bottom-tabs | v7 | 7.21.0 | same peers | Optional peer `@react-native-masked-view/masked-view` (from elements 2.9.45) is not installed and not needed |
| react-native-screens | (nav peer) | 4.30.0 | peer `*`; README Fabric table: `4.26.0+ → RN 0.84.0+` | RN 0.88 is newer than the README table lists. Latest stable (2026-10-13). Native compile **not verified** |
| react-native-safe-area-context | (template `^5.5.2`) | 5.10.1 | peer `*` | Pinned exactly to the latest |
| @tanstack/react-query | v5 | 5.104.4 | peer `react ^18 \|\| ^19`, `@types/react ^18 \|\| ^19` | |
| react-native-keychain | prefer | 10.0.0 | no peerDependencies; TurboModule (`codegenConfig`), its devDeps were built against RN 0.77.1 | Latest release (2025-03-23). JS API verified by tsc and a jest mock. Native compile against RN 0.88 **not verified here**. It sits behind the `TokenStorage` interface, so the backend can be swapped |
| @react-native-community/netinfo | if compatible | 12.0.1 | peer `react *`, `react-native >=0.59` | Used by React Query `onlineManager` and the offline banner. The jest mock it ships is used in tests |

## Tooling

| Component | Requested | Used | Verification | Notes |
|---|---|---|---|---|
| eslint | compatible with ts-eslint 8.71.2 | 10.13.0 | ts-eslint peer allows `^10`; `eslint-plugin-react-hooks` peer allows `^10` | Template shipped eslint ^8 with legacy `.eslintrc.js` and `@react-native/eslint-config` (peer `eslint ^8 \|\| ^9`). Replaced with flat `eslint.config.mjs` |
| @eslint/js | | 10.0.1 | peer `eslint ^10` | |
| eslint-plugin-react-hooks | | 7.1.1 | peer includes `^10` | Uses the `flat['recommended-latest']` config (17 rules, including the React Compiler-era rules) |
| jest | (template `^29.6.3`) | 29.7.0 | `@react-native/jest-preset@0.88.0` depends on `babel-jest ^29.7.0` and `jest-environment-node ^29.7.0` | Jest 30.5.2 exists but RN's preset is still on 29 |
| @types/jest | | 29.5.14 | Matches Jest 29 | |
| @testing-library/react-native | v13+ | 14.1.0 | peer `react >=19`, `react-native >=0.78`, `jest >=29`, `test-renderer ^1.0.0`; engines `node ^22.13 \|\| >=24` | v14 APIs are async (`await render`, `await user.press`) |
| test-renderer | (RNTL peer) | 1.3.1 | peer `react ^19.0.0` | Replaces the deprecated `react-test-renderer` and `@types/react-test-renderer` from the template, which were both removed |
| @babel/core / preset-env / runtime | (template `^7.25`) | 7.29.7 / 7.29.7 / 7.29.10 | `@react-native/babel-preset@0.88.0` depends on `@babel/core ^7.25.2` | Babel 8.0.7 exists but RN's preset is on Babel 7 |
| @types/react | (template `^19.2.0`) | 19.3.0 | Satisfies RN peer `^19.1.1` | |
| prettier | optional | 2.8.8 | Template default | Kept from the template. It isn't part of lint |
| openapi-typescript | preferred if clean with TS 6 | **not used** | `openapi-typescript@7.13.0` peer `typescript: ^5.x` (excludes 6.0.3) | Types are hand-written in `src/shared/api/types.ts`. There is no `generate:api` script |

## npm audit (real output, 2026-10-18)

- `npm audit --omit=dev`: **20 high**. They all come from one advisory: `braces <=3.0.3`,
  [GHSA-vfj7-8cjw-p6xm](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm) (stack-exhaustion DoS). It reaches the
  tree through `micromatch` → `metro`/`@react-native/community-cli-plugin`/CLI packages. That is build and dev-server
  tooling which npm counts as a prod dependency of `react-native`. It is not shipped in the app's JS bundle. No patched
  `braces` exists on npm (3.0.3 is the latest), so there is no fix or override to apply.
- `npm audit` (all): **54 (5 moderate, 49 high)**. That is the same `braces` advisory plus
  `sprintf-js <=1.1.3` ([GHSA-hp3w-g68c-fv3c](https://github.com/advisories/GHSA-hp3w-g68c-fv3c), moderate, dev tooling).
