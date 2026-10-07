# Contributing to AdgeistKit

## Branches

| Branch | Purpose | Backend | Publishes |
|---|---|---|---|
| `main` | Production releases | `qa.v2.bg-services` (`qaRelease`) | `x.y.z` to Maven Central, plus a GitHub Release and tag |
| `dev` | Beta testing | `beta.v2.bg-services` (`betaRelease`) | `x.y.z-beta-SNAPSHOT` to the Maven Central snapshot repo |
| `qa` | Staging for the next release | — | Nothing |
| `feat/*`, `fix/*` | Individual changes | — | Nothing |

Every push to `main` or `dev` triggers a publish through `.github/workflows/artifact-release.yaml`.

### Workflow

```
main ──► feat/my-change ──► dev  (beta snapshot, test here)
                    │
                    └─────► qa ──► main  (release)
```

1. **Branch from `main`.** Always start from `main`, never from `dev` or `qa`:

   ```bash
   git switch main && git pull
   git switch -c feat/my-change
   ```

2. **Open a PR into `dev`.** Once it is merged, CI publishes `x.y.z-beta-SNAPSHOT`. Test it in an integrating app (see [Testing a beta](#testing-a-beta)).
3. **Open a PR from the same feature branch into `qa`** after it passes testing on `dev`. Do not merge `dev` into `qa`. `dev` can hold changes that are still being tested and must not reach a release.
4. **Release by opening a PR from `qa` into `main`.** Merging it publishes `x.y.z` to Maven Central.

If testing finds a problem, push fixes to the same feature branch and repeat from step 2.

### Rules

- `dev` is never merged into another branch.
- `main` only receives merges from `qa`.
- Feature branches are merged into `dev` and `qa` separately, never through each other.
- Delete the feature branch after it lands in `main`.

### Branch names

- `feat/<short-description>` for new functionality
- `fix/<short-description>` for bug fixes

### Commit messages

Use [Conventional Commits](https://www.conventionalcommits.org/) prefixes, as in the existing history:

```
feat: add deep link support in ad components
fix: fixed android scroll adview torn down issue
refactor: shared preference move to data/local
chore: plugins and module upgrade
```

## Versioning

The version comes from `VERSION_NAME` in `gradle.properties` and must be plain semver `x.y.z`. CI adds the `-beta-SNAPSHOT` suffix on `dev`, so never add a suffix yourself.

- Set `VERSION_NAME` in your feature branch to the version the change will ship in, unless `qa` has already been bumped to it.
- Releases are immutable. CI fails on `main` if a GitHub Release or tag for `VERSION_NAME` already exists.
- Beta snapshots are overwritten on every push to `dev` and deleted from Maven Central after 90 days.

## Testing a beta

Add the snapshot repository and depend on the beta version:

```kotlin
repositories {
    maven("https://central.sonatype.com/repository/maven-snapshots/")
}

dependencies {
    implementation("ai.adgeist:adgeistkit:1.1.37-beta-SNAPSHOT")
}
```

Gradle caches snapshots for 24 hours. Run with `--refresh-dependencies` to pick up a newer build sooner.

The example apps in this repository (`fragmentApp` and `composeApp`) depend on the `adgeistkit` module directly, so they always use your local code. Pass `-PuseAarDependency` to make them use the published artifact instead (see [Publish the SDK locally](#publish-the-sdk-locally-and-test-in-a-client-app)).

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
   - `VERSION_NAME` is used **as-is** for the published Maven version. If you want a pre-release version like `1.1.32-beta`, include the suffix directly in `VERSION_NAME`. This `-PVERSION_NAME` override is for local testing only; the value in `gradle.properties` must stay plain `x.y.z` (see [Versioning](#versioning)).

2. In the client app's `settings.gradle(.kts)`, add `mavenLocal()` first in `dependencyResolutionManagement.repositories` so the locally published artifact is resolved before remote repositories.

3. Point the client app's dependency at the locally published version:

   ```kotlin
   implementation("ai.adgeist:adgeistkit:1.1.32-beta")
   ```
