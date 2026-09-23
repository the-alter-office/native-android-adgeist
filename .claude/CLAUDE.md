# Working rules for this project

## Do not do
- Do NOT add comments to code. Explanation goes in the chat, not the diff.
- NEVER commit. NEVER push. Not even when the work is finished.

## If I do ask you to make a change
First explain it in the chat, in plain terms: what you are going to change, in which file,
and why. Only after that explanation do you write the plan or edit the files.

## Stop and wait for my approval before:
- Creating any new file
- Modifying more than 3 files in one change
- Any destructive operation (delete, drop, remove)
- Adding a new dependency

---

# Project: AdGeist Android SDK (`adgeistkit`)

A publisher-side Android SDK. A publisher drops an `AdView` into their layout; the SDK
fetches an ad from the AdGeist marketplace, renders it in a WebView, and reports
impressions, clicks and viewability back.

## Tech stack
- Kotlin 1.8.0, Java/Kotlin target 11, AGP 8.9.0
- minSdk 23, compileSdk 35
- OkHttp 4.12, Gson 2.10.1, kotlinx-coroutines 1.7.3
- AndroidX lifecycle + fragment, play-services-ads-identifier
- Gradle Kotlin DSL, build flavors: `beta` / `qa` / `prod` (each sets its own BASE_API_URL)

## Modules
- `adgeistkit` — the published library (Maven Central)
- `app` — example host app for manual testing

## Commands
- Unit tests: `./gradlew :adgeistkit:test`
- Run the example app: `./gradlew :app:installBetaDebug`
- Release build check: `./gradlew :adgeistkit:assembleRelease -PpublishVariant=prodRelease`

## Architecture, in four facts
- `AdgeistCore` is the singleton entry point: config, shared OkHttp client, shared coroutine
  scope (`ioScope`), device/targeting signals.
- `AdView` / `BaseAdView` is the public view. It builds HTML from `assets/ad_view.html` +
  `assets/adcard-beta.js` and renders it in a WebView.
- `JsBridge` is the JS-to-native bridge exposed to that WebView as `Android`. Render status,
  video play/pause/end and clicks arrive through it; `ads/tracking/` turns them into
  impression and viewability reports.
- An ad is kept per screen in `AdViewModel` (holding a `RetainedAd`), so returning to a
  screen reuses the ad instead of buying a new one and never double-counts the impression.

## When you touch the `ads` package
Read `.claude/Ad-persistence-scenarios.md` first. It lists the 10 lifecycle scenarios that
must never destroy a live ad, and the expected behavior for each.
