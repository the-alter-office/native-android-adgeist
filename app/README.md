# AdGeist Kit — Example App

Sample consumer app for `adgeistkit`. It mirrors a real publisher integration: the release build type runs with R8 minification and resource shrinking enabled, so it also serves as the test bed for the SDK's consumer ProGuard rules.

## Flavors

The app has the same `environment` flavor dimension as the SDK: `beta`, `qa`, and `prod`. Pick the flavor matching the backend you want to hit.

## Run the example app

Install the beta-flavored debug build on a connected device/emulator:

```bash
./gradlew :app:installBetaDebug
```

## Build the instrumented tests

Instrumented tests run against the **minified release** variant (`testBuildType = "release"`), so they exercise the R8-processed SDK classes — this is what catches missing consumer keep rules (see `AdModelR8Test` and `JsBridgeR8Test`).

Compile check only (no device needed):

```bash
./gradlew :app:assembleBetaRelease :app:assembleBetaReleaseAndroidTest --console=plain 2>&1 | tail -40
```

## Run the instrumented tests on a device

```bash
./gradlew :app:connectedBetaReleaseAndroidTest
```

The release build is signed with the debug keystore, so no real keystore is required.

## Test against the published AAR

By default the app depends on the in-repo `:adgeistkit` module. To test what real publishers get — the published AAR including its bundled `consumer-rules.pro` — publish locally and switch the dependency with `-PuseAarDependency`:

```bash
./gradlew :adgeistkit:publishToMavenLocal
./gradlew :app:connectedProdReleaseAndroidTest -PuseAarDependency
```

The published artifact is the `prodRelease` variant, so use the prod flavor for this. The dependency coordinates come from `GROUP`, `POM_ARTIFACT_ID`, and `VERSION_NAME` in `gradle.properties` (see [CONTRIBUTORS.md](../CONTRIBUTORS.md) for versioning details).
