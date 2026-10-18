# Reverse engineering report: Picnic (Online Grocery Shopping)

**Date:** 2026-10-18. **Investigator:** Claude Code session on branch `feature/reverse-engineering`.
**Scope:** publicly observable features and technical characteristics of the
Picnic Android app (primary target) and iOS app (secondary). The goal is to
inform an **independent** implementation, Dukkan.

## 1. Summary

- We installed REA 6.7.0 from the official npm package and checked that it
  works: its MCP server answers with 207 tools, including the Android APK
  family (T-01–T-04).
- **No Picnic artifact was analysed.** We had no lawful way to get one in
  this sandbox: the Play Store and App Store were blocked by the network
  policy, and we had no device or account. We deliberately did not use
  third-party APK mirrors. So **no finding in this report is a first-hand
  observation** (T-05, T-06).
- Public sources the vendor publishes say the app is **React Native with
  TypeScript** (since a 2019–2022 migration), uses **RxJS** state management,
  **SQLite** local storage and an in-house **server-driven UI** ("Page
  Platform") (V-01–V-05).
- Reported product capabilities: a shopping list, delivery-area check and
  waitlist, choice of delivery day or slot with a 20-minute window on delivery
  day, weekly deals, recipes whose ingredients go to the list in one click,
  cashless in-app payment and live van tracking (V-06, S-01–S-04).
- Most of the detail a functional spec needs (exact flows, fields, rules,
  error handling) is **Unknown**. The Dukkan specification is an independent
  design and is labelled as such.

## 2. Methods

| Method | Used? | Notes |
|---|---|---|
| REA static APK analysis (JADX provider) | Set up, not run | No APK and no JADX MCP JAR (T-03, T-06) |
| REA resource decoding (Apktool) | No | No APK and no Apktool |
| REA device inspection (adb) | No | No device or emulator |
| REA browser observation of the web shop | No | `picnic.app` blocked (T-05) |
| Network interception | **Deliberately not used** | Not needed for a functional spec. It risks touching private APIs and would require bypassing TLS pinning, which the brief forbids |
| Public documentation and press | Yes | Through search summaries only, because the fetch tool was blocked (see evidence classes) |

## 3. Findings

- Product and UX capabilities: [features.md § Phase A](features.md#phase-a-application-discovery)
- Technical characteristics: [technical-analysis.md](technical-analysis.md)
- Flows: [user-flows.md](user-flows.md)
- Sources: [evidence.md](evidence.md)

## 4. Limitations

1. No binary, no traffic and no hands-on use of the app, so the internal
   architecture **cannot** be determined. Even the vendor's statements describe
   intent, not the shipped build.
2. The V and S sources were read through search-engine summaries. Quotes may be
   paraphrased.
3. Several secondary sources are 5–7 years old (2016–2019). Business figures such as
   the minimum order value contradict each other.
4. iOS was not investigated beyond its store-listing summary.

## 5. Unknowns that matter most for product design

1. Registration fields and when the address is captured (before or after browsing).
2. Slot granularity, fee tiers and how the 20-minute window is derived.
3. Search behaviour (suggestions, filters, typo tolerance).
4. Rules for editing an order after it is placed.
5. Payment methods and when payment is captured.
6. Push notification types.

## 6. Legal and ethical boundaries we kept to

- We did not bypass any authentication, encryption, certificate pinning,
  anti-tampering or access control. None of these was even encountered.
- We used no Picnic API, credentials, personal data or proprietary assets.
- The Dukkan code, names, colours, icons and seed data are original. The
  Picnic name appears only in this documentation, as the subject of the
  analysis.

## 7. Next steps to complete the investigation

See "What would upgrade this evidence" in [evidence.md](evidence.md) and the REA run plan in
[technical-analysis.md](technical-analysis.md#rea-run-plan-ready-for-when-an-apk-is-lawfully-available).
