# AdGeist Kit — Example App

## Build the SDK

Test build of `adgeistkit` using:

```bash
./gradlew :adgeistkit:assemble
```

## Publish the SDK locally and test in a client app

1. Publish the SDK locally with a bumped version:

   ```bash
   ./gradlew :adgeistkit:publishToMavenLocal \
     -PpublishVariant=betaRelease \
     -PVERSION_NAME=1.1.24 -PVERSION_SUFFIX=beta \
     -PRELEASE_SIGNING_ENABLED=false
   ```

2. In the client app's `settings.gradle(.kts)`, add `mavenLocal()` first in `dependencyResolutionManagement.repositories`.

3. Bump the client app's dependency to `ai.adgeist:adgeistkit:1.1.24` (the locally published version).

## Run the example app

```bash
./gradlew :app:installBetaDebug
```

## Run unit tests in the example app

```bash
./gradlew :app:assembleBetaRelease :app:assembleBetaReleaseAndroidTest --console=plain 2>&1 | tail -40
```
