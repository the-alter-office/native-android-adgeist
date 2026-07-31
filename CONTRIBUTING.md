# Contributing to AdgeistKit

## Build the SDK

Test build of `adgeistkit` using:

```bash
./gradlew :adgeistkit:assemble
```

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
