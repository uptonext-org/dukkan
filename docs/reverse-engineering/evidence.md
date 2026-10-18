# Evidence register

Each finding in this folder cites one or more evidence IDs from this register.
**Collected:** 2026-10-18, from a sandboxed cloud development container.

## Evidence classes

| Class | Meaning | Weight |
|---|---|---|
| **P (primary artifact)** | Output of REA, or a direct observation of the app or its official store listing | Strong |
| **V (vendor-published)** | Text published by the vendor (engineering blog, job ads, store description), seen through a search-engine summary | Medium. Seen second-hand, not read in full |
| **S (secondary)** | Press, third-party mirrors, reviews | Weak. Often dated, and sources may contradict each other |
| **T (tooling)** | Facts about our own investigation environment | Describes the method, not the target |

Important: in this environment the web-fetch tool could not resolve `picnic.app`,
`play.google.com` or `apps.apple.com`. All **V** and **S** items below come from
web-search result summaries, not from pages we opened and read in full. The
quoted wording comes from the search tool's summary, so treat it as close to the
source but **not verbatim-verified**. Re-check each item against the original
page before relying on it for anything beyond this functional-spec exercise.

## T: tooling and environment

| ID | Observation |
|---|---|
| T-01 | `npm view rea-agents` returned 6.7.0 (`latest`). Its package metadata names `git+https://github.com/morluto/rea.git` as the repository, with an MIT license. We downloaded it from the official npm registry. |
| T-02 | `rea setup --client claude_code --dry-run` planned two actions: add the MCP registration to `/root/.claude.json` with a backup at `/root/.claude.json.rea.backup`, and install the skill at `/root/.claude/skills/reverse-engineer-anything`. We reviewed the plan, then applied it with `--yes`. |
| T-03 | `rea doctor --client claude_code` reported `healthy: true` for node 24.22.0 and the claude_code registration. It reported `missing_analysis_engine` for Hopper, Ghidra, IDA, **JADX** (`REA_JADX_MCP_JAR` unset) and Kuna. |
| T-04 | Over a stdio `rea mcp` connection, `initialize` returned server `rea 6.7.0` and `tools/list` returned **207 tools**. Those include `inspect_android_package`, `search_android_classes`, `inspect_android_class`, `inspect_android_method`, `trace_android_references`, `decode_android_resources`, `project_android_application_graph`, `open_binary` and `inspect_artifact`. |
| T-05 | The egress policy blocked `picnic.app`, `jobs.picnic.app`, `play.google.com`, `apps.apple.com`, `blog.picnic.nl`, `rea.tools`, `www.apkmirror.com` and `d.apkpure.com` (`connect_rejected`). `github.com/morluto/rea` returned HTTP 403. |
| T-06 | No Android device or emulator, no `adb`, no `apktool` and no JADX headless-MCP JAR were available. We obtained **no** Picnic APK or IPA: no lawful acquisition route (a Play Store download with a user account) existed in this sandbox, and we deliberately did not use third-party APK mirrors. |

## V: vendor-published sources

| ID | Source | Claim (as summarised by search) |
|---|---|---|
| V-01 | [jobs.picnic.app: Faster features, happier customers](https://jobs.picnic.app/blogs/faster-features-happier-customers-introducing-the-platform-that-transformed-our-grocery-app) | Picnic began migrating its native Android and iOS apps to React Native in 2019. By mid-2022, most core functionality ran on React Native. The Android and iOS engineers merged into one mobile team. |
| V-02 | [jobs.picnic.app: Server-driven UI in Picnic's Page Platform (mobile perspective, 2025-01-30)](https://jobs.picnic.app/en/blogs/server-driven-ui-in-picnics-page-platform) | The in-house server-driven UI framework is called the "Picnic Page Platform". It sends low-level rendering instructions (text, rectangles, images). Its motivation: it takes about 2–3 weeks for 80% of customers to adopt a new app version. |
| V-03 | [jobs.picnic.app: Beyond static pages](https://jobs.picnic.app/blogs/beyond-static-pages-bringing-ui-to-life), [Adding write functionality with self-service APIs](https://jobs.picnic.app/en/blogs/adding-write-functionality-to-pages-with-self-service-apis) | The page platform was extended to interactive flows that write to back-end systems. |
| V-04 | ["How we work" page (via a [Seedcamp mirror](https://talent.seedcamp.com/companies/picnic-3-f6b61218-13ee-41b4-8acf-5f1ef5fc4ee8/jobs/52032956-picnic-engineering-how-we-work)) | The app is React Native, with **RxJS** for state management and a Cycle.js-inspired dataflow. Earlier versions used hooks and MobX. **SQLite** is used for on-device storage and caching. |
| V-05 | Picnic job ads ([wearedevelopers](https://www.wearedevelopers.com/en/jobs/ext/6188934/java-engineer), [jobs.picnic.app](https://jobs.picnic.app/jobs/JZ1MJURH), [front-end role](https://jobs.picnic.app/en/vacancies/J6U4S0TL/engineering/front-end-engineer-react-react-native-store-and-web/amsterdam/north-holland/netherlands)) | Backend: Java 21/25, Spring 6 / Spring Boot 3.5, Reactor, Immutables, Maven, PostgreSQL, MongoDB, RabbitMQ, Kafka, Python, Kubernetes, Docker, Terraform, Helm, AWS, Datadog. TypeScript is used across the frontend. |
| V-06 | [Apple App Store DE, id1018175041](https://apps.apple.com/DE/app/id1018175041) | "Du erstellst eine Einkaufsliste, wählst einen Liefertermin und das war's!" The app shows whether delivery is available in your area. It offers weekly "Superdeals" and recipes. Delivery is free. |

## S: secondary sources

| ID | Source | Claim |
|---|---|---|
| S-01 | [freshplaza: Picnic now servicing Krefeld (2019)](https://www.freshplaza.com/article/9063604/picnic-is-now-also-servicing-krefeld/) | 20-minute delivery window. Real-time route tracking in the app. Orders until 22:00 for next-day delivery. Delivery Mon–Sat. Free delivery from €25. Electric vehicles. A waiting list for areas that are not yet served. |
| S-02 | [apkcombo listing of `com.picnic.android` (third-party mirror)](https://apkcombo.com/de/picnic-order-cook-eat/com.picnic.android/) | Package id `com.picnic.android`. Version dated 2026-07-28, 5M+ installs. "Rezepte … alle Zutaten mit einem Klick direkt in deine Einkaufsliste übernehmen". Weekly offers. *Third-party listing; we did not download any binary from it.* |
| S-03 | [RetailDetail: "Boodschappenradar" (2016)](https://www.retaildetail.eu/news/uncategorized/picnic-customer-can-track-delivery-live-boodschappenradar) | Live delivery tracking shows where the van is and when it will arrive. |
| S-04 | [mydealz Picnic](https://www.mydealz.de/gutscheine/picnic), [dealdoktor](https://www.dealdoktor.de/magazin/picnic-online-supermarkt-deutschland/) | The customer picks a day, then gets a 20-minute window on delivery day. Minimum order quoted as €45, which conflicts with S-01's €25. Cashless in-app payment. Discount codes for new customers. |
| S-05 | [androidworld.nl: new Picnic app (2019)](https://androidworld.nl/apps/picnic-nieuwe-app/) | Navigation was redesigned to resemble walking through a physical store. |
| S-06 | [awin merchant profile](https://ui.awin.com/merchant-profile/116041) | Claims "10,000+ products" and "1,000+ recipes". The partner programme uses a published postal-code list of the delivery area. |
| S-07 | [esmmagazine (2023)](https://esmmagazine.com/technology/online-grocer-picnic-enters-hesse-baden-wurttemberg-rhineland-palatinate-247681), [hamburg-business](https://hamburg-business.com/en/news/online-supermarket-picnic-launches-hamburg) | German expansion is city by city. Electric vans. |

## What would upgrade this evidence

1. Install the app from Google Play with an account the user owns, on an emulator or test device. Pull the APK with `adb`, store it under `investigation/` (git-ignored), and record its SHA-256.
2. Supply a `jadx-headless-mcp` 0.7.1 JAR and set `REA_JADX_MCP_JAR`. Then run `inspect_android_package`, `decode_android_resources` and `project_android_application_graph`, and add P-xx entries here.
3. Allow `picnic.app` and `play.google.com` in the environment's network policy, so the official pages can be read directly rather than through search summaries.
