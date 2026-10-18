# Phase B: Technical investigation

## Status labels

The brief asks for **Observed / Inferred / Unknown**. We could not obtain an
artifact (T-05, T-06), so this investigation has **no first-hand observations**
of the Picnic binary. To avoid presenting second-hand claims as observations,
we use one extra label:

- **Observed**: directly supported by a primary artifact (REA output or the app itself). *None in this round.*
- **Reported**: stated by the vendor (V-xx) or a secondary source (S-xx); not verified against an artifact.
- **Inferred**: a reasonable deduction from reported facts. Marked with its reasoning.
- **Unknown**: not established.

## Method

1. We installed REA 6.7.0 from npm and registered it with Claude Code. Its MCP
   server answered over stdio with 207 tools (T-01–T-04).
2. We attempted to obtain the artifacts lawfully. Google Play and the App Store
   were unreachable, and we had no device or account. We did not use
   third-party APK mirrors: they redistribute the binary outside the vendor's
   channel, and their integrity and licensing cannot be confirmed (T-05, T-06).
3. We fell back to public sources the vendor publishes (V-xx) and to press
   coverage (S-xx). We did not access any network endpoint, credential or user
   data belonging to Picnic.

## Findings

| # | Area | Status | Finding | Evidence |
|---|---|---|---|---|
| B-01 | Package identity | Reported | The Android package id is `com.picnic.android`. The iOS App Store id is 1018175041. | S-02, V-06 |
| B-02 | Package structure | Unknown | APK splits, DEX count, native `.so` libraries and the asset layout. | none (no artifact) |
| B-03 | Manifest and permissions | Unknown | We read no manifest. Our own guess at likely permissions (internet, notifications, possibly location for address search) is **not** recorded as a finding. | none |
| B-04 | Framework | Reported | React Native has been the main UI framework since the 2019–2022 migration. | V-01, V-05 |
| B-05 | Language | Reported | The frontend is written in TypeScript. | V-05 |
| B-06 | JavaScript bundle | Inferred | A React Native release APK normally ships the bundle as `assets/index.android.bundle`, usually as Hermes bytecode. Whether Picnic's app does this, and which engine it uses, is **Unknown**. REA's `inspect_artifact` and the JavaScript tools could check this once we have an APK. | B-04 + general RN packaging |
| B-07 | UI composition | Reported | A server-driven UI layer, the "Page Platform", renders low-level primitives sent by the backend. | V-02, V-03 |
| B-08 | State management | Reported | RxJS observables in a Cycle.js-inspired dataflow. Hooks and MobX were used earlier. | V-04 |
| B-09 | Local persistence | Reported | SQLite for on-device storage and caching. | V-04 |
| B-10 | Navigation | Reported (UX), Unknown (implementation) | The 2019 redesign modelled navigation on walking through a store. The navigation library is unknown. | S-05 |
| B-11 | Native Java/Kotlin components | Unknown | Not inspected. | none |
| B-12 | Network behaviour | Unknown | Not observed. We deliberately captured no traffic, so TLS pinning was never encountered and nothing was bypassed. | none |
| B-13 | Offline and caching | Inferred | SQLite caching (B-09) suggests some offline read capability. How far it goes is unknown. | B-09 |
| B-14 | Performance | Unknown | No measurements were taken. | none |
| B-15 | Backend stack (company-wide) | Reported | Java, Spring Boot, Reactor, PostgreSQL, MongoDB, RabbitMQ, Kafka, Python and Kubernetes. This describes the company's engineering in general and does **not** show which services back the app. | V-05 |
| B-16 | Release cadence pressure | Reported | Adoption of a new app version takes 2–3 weeks to reach 80% of users. That delay motivated the server-driven UI. | V-02 |

## Implications for our design (our decisions, not findings)

- B-16 and B-07 point to a long-term option: add server-driven layout for the
  home and promotion screens. We **defer** it, because it adds a whole
  platform to an MVP (see `docs/architecture/decisions/0004-no-server-driven-ui-in-mvp.md`).
- B-08 differs from what we chose (TanStack Query plus React context). Our app
  has far less client-side state, and the job of our mobile app is not to
  imitate Picnic's internals (ADR 0003).

## REA run plan, ready for when an APK is lawfully available

```text
inspect_android_package           { apk: "<abs>/investigation/picnic.apk" }      # manifest, permissions, components
decode_android_resources          { apk: ... }                                   # strings, locales, version
inspect_artifact / open_binary    { path: ... }                                  # member inventory: dex, so, assets/index.android.bundle
project_android_application_graph { apk: ... }                                   # component graph
analyze_javascript_application    { path: "<extracted bundle dir>" }             # only if the bundle is plain JS, not Hermes bytecode
```

Prerequisites: a full JDK (Temurin 25 is installed in this setup), a
`jadx-headless-mcp` 0.7.1 JAR referenced by `REA_JADX_MCP_JAR`, an Apktool
launcher for resource decoding, and procps `ps` (present). Record every result
as a P-xx entry in `evidence.md` with the artifact's SHA-256.
