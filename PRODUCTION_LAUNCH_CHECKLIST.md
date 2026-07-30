# AdGeist Android SDK — Production Launch Master Checklist

Everything required to take `ai.adgeist:adgeistkit` (currently `1.1.31-beta`) from beta to a production SDK that publishers will trust in their host apps — benchmarked against how Google (AdMob/Firebase), AppLovin, and Meta prepare SDKs for launch. Compiled July 2026 from Google's official SDK guidelines, Maven Central requirements, IAB/TAG certification programs, and major-vendor practices; sources linked per item.

## How to read this doc

| Status | Meaning |
|---|---|
| ✅ | Done — verified present in this repo |
| 🟡 | Partial — exists but incomplete or unverified |
| ❌ | Missing |

| Priority | Meaning |
|---|---|
| **P0** | Blocks GA — do not remove the `-beta` suffix before these |
| **P1** | Pre-GA / launch window — needed to credibly pitch publishers |
| **P2** | Post-GA — scale, trust-building, competitive parity |

> **Code-level fixes:** the 36 open findings in [adgeist-sdk-code-review.md](adgeist-sdk-code-review.md) (4 Critical: consent flag not enforced, `adSize!!` NPE, unhandled coroutine exceptions, no-op `logEvent()`) are collectively **one P0 item** here and are not restated below.

---

## 1. Code & Technical

### API stability
| # | Item | Status | Priority |
|---|---|---|---|
| 1.1 | Fix all Critical + High findings in [adgeist-sdk-code-review.md](adgeist-sdk-code-review.md) | ❌ | **P0** |
| 1.2 | Enable Kotlin `explicitApi()` mode — forces explicit visibility/return types on everything public, prevents accidental API leakage ([Kotlin backward-compat guide](https://kotlinlang.org/docs/api-guidelines-backward-compatibility.html)) | ❌ | P1 |
| 1.3 | Adopt [binary-compatibility-validator](https://github.com/Kotlin/binary-compatibility-validator): checked-in `.api` dump + `apiCheck` in CI failing on unintentional public-API changes (standard at RevenueCat, Stream) | ❌ | P1 |
| 1.4 | Avoid data classes in public API; use `@Deprecated` + `ReplaceWith` before any removal | ❌ | P1 |

### Packaging & publishing
| # | Item | Status | Priority |
|---|---|---|---|
| 1.5 | Maven Central publishing: signing, POM metadata, portal upload via vanniktech plugin + CI workflow ([requirements](https://central.sonatype.org/publish/requirements/)) | ✅ | — |
| 1.6 | Sources jar is intentionally a stub (NOTICE.txt only — SDK is proprietary); javadoc jar still publishes. Confirm both appear on the Central listing after next release | ✅ | — |
| 1.7 | `LICENSE` file at repo root — proprietary AdGeist SDK License Agreement finalised; POM points to it. Remaining: **upload the text to cdn.adgeist.ai/licenses/NATIVE-ANDROID-LICENSE.txt before next release** (POM URL is live from then) | ✅ | — |
| 1.8 | ~~JitPack build~~ — N/A: JitPack dropped (`jitpack.yml` removed); distribution is Maven Central only, repo going private | ✅ | — |

### R8 / ProGuard
| # | Item | Status | Priority |
|---|---|---|---|
| 1.9 | Replace blanket `-keep class com.adgeistkit.** { *; }` in `consumer-rules.pro` with targeted rules covering only Gson models + `@JavascriptInterface` bridge — broad keeps degrade every consuming app's optimization and Google explicitly warns against them ([library optimization guide](https://developer.android.com/topic/performance/app-optimization/library-optimization)); the file's own TODO acknowledges this | 🟡 | P1 |
| 1.10 | Verify SDK survives **R8 full mode** (AGP 8+ default) in a minified consumer app — most common "works in sample, breaks in publisher app" failure | ❌ | **P0** |
| 1.11 | Longer term: reduce Gson reflection surface (add `@SerializedName` everywhere or move to codegen serialization like Moshi/kotlinx.serialization) so targeted keep rules are possible | ❌ | P2 |

### Performance engineering
| # | Item | Status | Priority |
|---|---|---|---|
| 1.12 | SDK init never blocks the main thread; support lazy/background init (GMA precedent: ~405ms median / 1.5s p95 init latency — publish yours) | 🟡 | P1 |
| 1.13 | Ship a [Baseline Profile](https://developer.android.com/topic/performance/baselineprofiles/overview) in the AAR for hot paths (init, ad load, render) — library profiles merge into the consumer's app profile | ❌ | P2 |

### Compatibility & dependencies
| # | Item | Status | Priority |
|---|---|---|---|
| 1.14 | Lower `jvmTarget`/`compileOptions` from 17 to 11 (or 8) — per code review, JVM-17 metadata forces every consumer to raise their toolchain; the repo's own sample app targets 11 | ❌ | **P0** |
| 1.15 | Publish a compatibility matrix: minSdk / compileSdk / AGP / Kotlin / Gradle versions supported ([AGP release policy](https://developer.android.com/build/releases/agp-9-0-0-release-notes)) | ❌ | P1 |
| 1.16 | Treat minSdk bumps as breaking changes reserved for major versions (AdMob precedent: v23→API 21, v24→API 23) | ❌ | P1 |
| 1.17 | Audit transitive deps (OkHttp 4.12, Gson 2.10.1, coroutines 1.7.3, play-services-ads-identifier 18.0.1) — every one becomes the host app's conflict problem; keep minimal, pinned, widely compatible. Note kotlin-bom pins Kotlin **1.8.0** (2022-era) while jvmTarget is 17 — reconcile | 🟡 | P1 |

### Versioning & release lifecycle
| # | Item | Status | Priority |
|---|---|---|---|
| 1.18 | Adopt strict semver: patch = fixes, minor = compatible features, major = breaking only ([Firebase versioning policy](https://firebase.google.com/policies/changes-to-firebase/versioning-and-maintenance)) | 🟡 | **P0** |
| 1.19 | `CHANGELOG.md` + per-version release notes (current GitHub Releases carry only a generic body); majors get a "breaking changes" section + migration guide ([AdMob model](https://developers.google.com/admob/android/rel-notes)) | ❌ | **P0** |
| 1.20 | Published deprecation/support-window policy — AdMob model: ~2 yr supported → ~1 yr deprecated → sunset, with a public version/status table ([AdMob deprecation schedule](https://developers.google.com/admob/android/deprecation)) | ❌ | P1 |
| 1.21 | Release workflow hardening: workflow tags releases *before* publish succeeds (cleanup steps mitigate); consider tag-after-publish ordering. Also decide the GA flow — `prodRelease` variant currently only publishes from unnamed branches | 🟡 | P1 |

---

## 2. Testing

| # | Item | Status | Priority |
|---|---|---|---|
| 2.1 | **CI runs no tests** — `.github/workflows/artifact-release.yaml` only runs `assembleRelease`. Add `test` + `lint` (and later `apiCheck`) as publish gates | ❌ | **P0** |
| 2.2 | Unit-test critical paths: ad response parsing, request building, consent gating, analytics batching (currently 2 test files total). No formal industry coverage number, but vendors near-fully cover parse/consent/cache logic | ❌ | **P0** |
| 2.3 | Instrumented smoke tests for must-not-break flows: init → load → render → destroy | ❌ | P1 |
| 2.4 | Device matrix via [Firebase Test Lab](https://firebase.google.com/docs/test-lab/android/get-started): 2–3 virtual devices per PR; expanded nightly matrix across API 23→latest, low-RAM devices, OEM skins (Samsung/Xiaomi). Play penalizes host apps at ≥8% crash rate on a single device model | ❌ | P1 |
| 2.5 | **Minified-consumer test**: sample app variant with `minifyEnabled = true` + R8 full mode, instrumented smoke tests run against it in CI — the only proof consumer rules work | ❌ | **P0** |
| 2.6 | Coexistence matrix: integrate SDK alongside GMA, Firebase, and older/newer OkHttp/Gson/coroutines versions; assert no duplicate-class or `NoSuchMethodError` failures | ❌ | P1 |
| 2.7 | Network chaos + fuzzing: MockWebServer fault injection (dropped connections, latency, 4xx/5xx, truncated/malformed JSON) — the ad server response is untrusted input; SDK must surface clean callbacks, never crash the host ([chaos testing](https://www.mock-server.com/mock_server/chaos_testing.html)) | ❌ | P1 |
| 2.8 | Memory: [LeakCanary instrumentation gate](https://square.github.io/leakcanary/ui-tests/) (`LeakAssertions.assertNoLeaks()`) in CI; leaked Activity/WebView contexts are the #1 host-app complaint about ad SDKs | ❌ | P1 |
| 2.9 | StrictMode-clean: run sample app with thread+VM policies; any violation from SDK code is a bug | ❌ | P1 |
| 2.10 | Register on [Google Play SDK Console](https://play.google.com/sdk-console/about/) → aggregated crash/ANR reports across all host apps, filterable by SDK version/device — this is how you see production crashes before publishers file bugs | ❌ | P1 |
| 2.11 | Treat [Android vitals thresholds](https://developer.android.com/topic/performance/vitals) as your SLO: host apps get penalized at ≥1.09% user-perceived crash / ≥0.47% ANR — the SDK must contribute ~zero | ❌ | P1 |

---

## 3. Benchmarking

| # | Item | Status | Priority |
|---|---|---|---|
| 3.1 | [Macrobenchmark](https://developer.android.com/codelabs/android-macrobenchmark-inspect): host-app cold/warm startup delta with vs. without SDK init, on release builds + physical devices — publish the number in docs | ❌ | P1 |
| 3.2 | Microbenchmark hot paths (response parsing, ad-view inflation) in CI to catch regressions | ❌ | P2 |
| 3.3 | APK size impact measured as increment vs. an empty baseline app with R8 (not raw AAR size), automated with [apkscale](https://www.twilio.com/en-us/blog/developers/tutorials/building-blocks/measuring-android-library-size-with-apkscale) or Emerge; publish per release (MoEngage/Twilio model) | ❌ | P1 |
| 3.4 | Memory/battery/network footprint numbers in docs: steady-state + peak memory, [Battery Historian](https://developer.android.com/topic/performance/power/battery-historian) profile (no wakelocks/radio abuse), bytes per ad request + per session | ❌ | P2 |
| 3.5 | Instrument ad KPIs in the SDK: time-to-first-ad (median/p95), fill rate (ads served ÷ requests; segment by geo/format), render rate (rendered ÷ won — unrendered wins are unbilled revenue), show→viewable funnel | ❌ | P1 |
| 3.6 | Competitive benchmarks for the pitch deck: eCPM/fill vs. AdMob and other networks in real publisher inventory (feeds the business case, not just the tech one) | ❌ | P1 |

---

## 4. Google Play policy & ecosystem

| # | Item | Status | Priority |
|---|---|---|---|
| 4.1 | Comply with [Play SDK Requirements](https://support.google.com/googleplay/android-developer/answer/13323374): HTTPS-only transport, all data collection disclosed and limited to disclosed purposes, no selling personal data, no persistent-ID bridging (never link AAID to IMEI/etc.), no dynamic code loading. Be ready for Google's **2-week compliance-response window** | 🟡 | **P0** |
| 4.2 | Publish a **public data-safety disclosure page** in [Google's SDK format](https://support.google.com/googleplay/android-developer/answer/10787469) — collected data types, purposes, sharing, security practices. Publishers legally need this to fill their Play Data Safety form; [AdMob's page](https://developers.google.com/admob/android/play-data-disclosure) is the model. Host apps are liable for your SDK's behavior, so document defaults and off-switches precisely | ❌ | **P0** |
| 4.3 | Register on [Play SDK Console](https://support.google.com/googleplay/android-developer/answer/12244916) (claim Maven ID via verification-file release) and get an [SDK Index](https://play.google.com/sdks) listing — publishers vet SDKs there directly from Android Studio; you can mark bad versions outdated with 90-day migration windows | ❌ | P1 |
| 4.4 | Permissions: `READ_PHONE_STATE` already replaced with `READ_BASIC_PHONE_STATE` (normal permission, API 33+) ✅ — but audit whether `ACCESS_WIFI_STATE` (used for local/WiFi IP collection per code review) is justifiable; WiFi-derived data is sensitive under Play policy | 🟡 | P1 |
| 4.5 | Advertising ID rules: verify the merged manifest carries `com.google.android.gms.permission.AD_ID` (play-services-ads-identifier should declare it); honor user reset/opt-out — when "opt out of ads personalization" is on, the SDK must not use AAID. Note: Privacy Sandbox on Android was [retired Oct 2025](https://privacysandbox.google.com/blog/update-on-plans-for-privacy-sandbox-technologies) — GAID remains the operative system; don't plan around SDK Runtime | 🟡 | **P0** |
| 4.6 | Consent enforcement (ties to code-review Critical): gate all device-data collection and network calls on consent state; support revocation re-checked each session | ❌ | **P0** |
| 4.7 | **IAB TCF v2.2 support**: read `IABTCF_TCString`/`IABTCF_gdprApplies` from default SharedPreferences and respect them; register on the IAB Global Vendor List; document that EEA/UK serving via Google demand requires a [Google-certified CMP](https://support.google.com/admob/answer/13554116) (mandatory since Jan 2024) | ❌ | P1 |
| 4.8 | Families/COPPA: add a child-directed-treatment API (equivalent of `tagForChildDirectedTreatment`); until certified under the [self-certified ads SDK program](https://support.google.com/googleplay/android-developer/answer/12918983) (intake currently closed — monitor), explicitly document the SDK as **not for child-directed apps** so publishers don't discover it during Play review | ❌ | P1 |
| 4.9 | Ads-rendering policy hygiene: ads distinguishable from content, dismissible, no [disruptive-ads](https://play.google/developer-content-policy/) violations that get *host apps* rejected | 🟡 | P1 |
| 4.10 | Keep pace with Play target-API requirements (target SDK 35 era) so the SDK never blocks a host app's compliance | ✅ | — |

---

## 5. Publisher-facing developer experience (the AdMob-parity bar)

| # | Item | Status | Priority |
|---|---|---|---|
| 5.1 | **Test ad units + test-device registration** — dedicated always-fill test adspace IDs plus a registered-test-device mode, so publishers integrate without invalid-traffic risk or real inventory ([AdMob model](https://developers.google.com/admob/android/test-ads)). Biggest DX gap vs. majors | ❌ | **P0** |
| 5.2 | In-SDK integration debugger — even a minimal analog of [AdMob Ad Inspector](https://developers.google.com/admob/android/ad-inspector) / [MAX Mediation Debugger](https://support.applovin.com/en/max/android/testing-networks/mediation-debugger): overlay showing config status, last request/response, last error, opened by gesture or API call | ❌ | P2 |
| 5.3 | Open-source per-format sample apps on GitHub (Google maintains [googleads-mobile-android-examples](https://github.com/googleads/googleads-mobile-android-examples)); the in-repo `app` module is a start but publishers expect a public, runnable, per-format examples repo | 🟡 | P1 |
| 5.4 | Docs completeness: fix README↔code mismatches (promised `AdSize` constants don't exist), document every public API (`setUserDetails`, consent, permission helpers), integration checklist, error-code reference | 🟡 | **P0** |
| 5.5 | **Mediation adapters for AdMob and/or AppLovin MAX** — lets publishers add AdGeist as one waterfall/bidding line instead of a standalone integration; the single biggest adoption lever in ad tech | ❌ | P1 |
| 5.6 | Cross-platform parity roadmap: iOS SDK, then Flutter/React Native/Unity wrappers — many publishers won't adopt Android-only | ❌ | P2 |

---

## 6. Trust & certification (ad-tech specific)

| # | Item | Status | Priority |
|---|---|---|---|
| 6.1 | Supply-path transparency: document publishers' required [app-ads.txt](https://developers.google.com/admob/android/app-ads) entries, publish your **sellers.json**, pass **SupplyChain (schain) objects** in bid requests — DSPs filter unverified supply, which directly suppresses the eCPM you're pitching | ❌ | P1 |
| 6.2 | Integrate [IAB Open Measurement SDK](https://iabtechlab.com/standards/open-measurement-sdk/) (OMID v1.5) so buyers' verification vendors (IAS, DoubleVerify, Moat) can measure viewability on your inventory; then obtain IVC certification (open to non-members, test-app based, ~2–4 weeks) | ❌ | P1 |
| 6.3 | [TAG Certified Against Fraud](https://www.tagtoday.net/) — the anti-fraud seal buyers screen for; later, MRC accreditation if you make measurement claims (viewability standard: 50% pixels ≥1s display / ≥2s video) | ❌ | P2 |
| 6.4 | SOC 2 Type I → Type II; completed CAIQ/vendor security questionnaire on file — mid/large publishers' security teams block integration without it | ❌ | P2 |
| 6.5 | Legal document set: public privacy policy (linked from SDK Index listing + Data Safety page), publisher-facing SDK ToS, DPA template for EU/UK publishers | ❌ | **P0** |

---

## 7. Business & operations

| # | Item | Status | Priority |
|---|---|---|---|
| 7.1 | Commercial terms published: revenue share, payout threshold/schedule/rails, tax paperwork (W-9/W-8BEN) — "how do I get paid" answered before "how do I integrate" | ❌ | **P0** |
| 7.2 | Self-serve publisher onboarding: signup → app registration (package-ID-as-origin flow already documented in README) → adspace IDs → live stats, without manual back-and-forth | 🟡 | P1 |
| 7.3 | Support SLA channel (email/Slack/Discord) beyond the GitHub issue templates (templates ✅); status page for `bg-services.adgeist.ai` ad-serving backend | 🟡 | P1 |
| 7.4 | Internal alerting on fill rate, crash signals, and serving errors so you detect problems before publishers report them | ❌ | P1 |
| 7.5 | **Closed beta with 3–5 partner publishers** before removing `VERSION_SUFFIX=beta`; staged rollout discipline (partners first, monitor crash/ANR trends, then GA) — Google shipped its Next-Gen GMA SDK as public beta before GA | ❌ | **P0** |
| 7.6 | Pitch assets: landing page, eCPM/fill benchmark one-pager vs. competitors, integration-effort estimate ("<1 day"), case studies from beta publishers, short demo video | ❌ | P1 |

---

## 8. Launch sequence (recommended order)

| Phase | Gate | Items |
|---|---|---|
| **1. Stabilize (P0 code)** | No known crash surface, consent enforced | 1.1 (code-review Criticals/Highs), 1.14, 4.6 |
| **2. Harden the pipeline** | Tests gate every publish | 2.1, 2.2, 2.5, 1.10, 1.18, 1.19, 1.7 |
| **3. Compliance paperwork** | A publisher's legal team can say yes | 4.1, 4.2, 4.5, 6.5, 7.1 |
| **4. Publisher DX** | A stranger can integrate unassisted in a day | 5.1, 5.4, 2.10 (SDK Console), 4.3 |
| **5. Closed beta** | 3–5 partner publishers live, vitals clean | 7.5, 3.1, 3.3, 3.5, 7.4 |
| **6. GA** | Drop `-beta`, announce | 1.20 (support policy), 7.6, remaining P1 |
| **7. Scale trust** | Compete with the majors | 5.5 (mediation adapters), 6.1–6.4, 5.2, 5.6, P2 items |

---

*Primary sources: [Play SDK Requirements](https://support.google.com/googleplay/android-developer/answer/13323374) · [Android SDK best practices](https://developer.android.com/guide/practices/sdk-best-practices) · [Maven Central requirements](https://central.sonatype.org/publish/requirements/) · [AdMob deprecation policy](https://developers.google.com/admob/android/deprecation) · [Firebase versioning policy](https://firebase.google.com/policies/changes-to-firebase/versioning-and-maintenance) · [Library optimization (R8)](https://developer.android.com/topic/performance/app-optimization/library-optimization) · [Play SDK Console](https://play.google.com/sdk-console/about/) · [IAB Tech Lab OM SDK](https://iabtechlab.com/standards/open-measurement-sdk/) · [TAG certifications](https://www.tagtoday.net/) · [Android vitals](https://developer.android.com/topic/performance/vitals)*
