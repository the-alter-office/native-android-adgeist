# Contributing to AdgeistKit

## Branching

### Branches

| Branch | Purpose |
| --- | --- |
| `main` | Production.  Branch all feature work from here.  |
| `qa` | Release candidate.|
| `dev` | Beta testing. Integration only. |
| `feat/*`, `fix/*`, `chore/*` | Your work. |

### Flow

1. Branch from `main`.

   ```bash
   git fetch origin
   git switch -c feat/my-change origin/main
   ```

2. Open a PR into `dev`. After review, merge to ship it to beta.
3. Test in beta.
4. Open a PR from the same feature branch into `qa`.
5. `qa` → `main` releases to production.

Never merge `dev` into a feature branch — it carries other people's unreleased work. Rebase on `main` instead.

## Build the SDK

Test build of `adgeistkit` using:

```bash
./gradlew :adgeistkit:assemble
```

## Versioning policy

Raising `minSdk` (`adgeistkit/build.gradle.kts`) breaks the build for any consumer app whose own
`minSdk` is lower than the new value — this is a hard, non-optional break, not a soft deprecation.
Treat any `minSdk` increase as a **breaking change reserved for a major version bump** (`X.0.0`),
never a minor or patch release. Call it out explicitly in the changelog/release notes for that
major version.

## Consumer ProGuard/R8 rules

`adgeistkit/consumer-rules.pro` runs on the **consumer's** R8 pass, not ours — the SDK ships
unminified. Get these rules wrong and you either bloat every publisher's app (over-keeping) or
silently break the SDK at runtime in their release builds (under-keeping), and the breakage often
won't show up until a real minified build ships.

**Do:**
- Only add a keep rule for something R8 can't already see is being used. R8 decides what's safe
  to strip or rename by tracing normal method calls and field references in the code — if
  something is called directly, R8 already knows to keep it, no rule needed. What it *can't* see
  is anything reached through reflection: Gson reading a model's fields by name, the WebView JS
  bridge invoking a method by name from JavaScript, or a class built from an XML layout by its
  class-name string. Those need explicit rules (`@SerializedName`, `@JavascriptInterface`,
  XML-inflated constructors) — nothing else does.
- Use `-keepclassmembers,allowobfuscation` over plain `-keepclassmembers` wherever renaming is
  safe — it protects a member from being *stripped* while still letting R8 rename it, so
  consumers still get shrinking/obfuscation benefits.
- Leave a one-line comment on every rule explaining *why* it exists (what breaks without it) —
  the next person touching this file has no other way to know it's safe to remove.
- Add coverage in `AdModelR8Test`/`JsBridgeR8Test` for anything new that depends on reflection —
  these run against the actual minified `prodRelease` build, which is the only way to catch this
  class of bug before a publisher does.

**Don't:**
- Add a blanket `-keep class com.adgeistkit.** { *; }` — it defeats shrinking/obfuscation for the
  entire SDK in every consuming app, and Google explicitly warns against this pattern in their
  library optimization guide.
- Keep a class/member "just in case" without a comment explaining what actually needs it — an
  unexplained keep rule is indistinguishable from dead weight six months later.
- Assume a field is safe without `@SerializedName` because "the name already matches the JSON
  key" — R8 can still rename it, and only the annotation survives obfuscation to tell Gson the
  real key.

## Publish the SDK locally and test in a client app

1. Publish the SDK to your local Maven repository (`~/.m2/repository`) with a bumped version:

   ```bash
   ./gradlew :adgeistkit:publishToMavenLocal -PpublishVariant=betaRelease -PVERSION_NAME=1.1.32-beta
   ```

   - `publishVariant` selects which flavor/build-type variant is published (`betaRelease`, `qaRelease`, or `prodRelease`). Each flavor bakes in its own `BASE_API_URL`. Defaults to `prodRelease` when omitted.
   - `VERSION_NAME` is used **as-is** for the published Maven version. If you want a pre-release version like `1.1.32-beta`, include the suffix directly in `VERSION_NAME` — the `VERSION_SUFFIX` property does *not* affect the published coordinates (it is only used for the debug `BuildConfig.VERSION_NAME`).
   - Signing is skipped automatically for `publishToMavenLocal`, so no GPG key is needed.

2. In the client app's `settings.gradle(.kts)`, add `mavenLocal()` first in `dependencyResolutionManagement.repositories` so the locally published artifact is resolved before remote repositories.

3. Point the client app's dependency at the locally published version:

   ```kotlin
   implementation("ai.adgeist:adgeistkit:1.1.32-beta")
   ```
