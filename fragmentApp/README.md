# AdGeist Kit — Example App

Sample consumer app for `adgeistkit`. It mirrors a real publisher integration: the release build type runs with R8 minification and resource shrinking enabled, so it also serves as the test bed for the SDK's consumer ProGuard rules.

## Flavors

The app has the same `environment` flavor dimension as the SDK: `beta`, `qa`, and `prod`. Pick the flavor matching the backend you want to hit.

## Run the example app

Install the beta-flavored debug build on a connected device/emulator:

```bash
./gradlew :fragmentApp:installBetaDebug
```

## Build the minified release

The release build runs R8 like a real publisher's app. It is signed with the debug keystore, so no real keystore is required:

```bash
./gradlew :fragmentApp:installBetaRelease
```

## Test against the published AAR

By default the app depends on the in-repo `:adgeistkit` module. To test what real publishers get — the published AAR including its bundled `consumer-rules.pro` — publish locally and switch the dependency with `-PuseAarDependency`:

```bash
./gradlew :adgeistkit:publishToMavenLocal
./gradlew :fragmentApp:installProdRelease -PuseAarDependency
```

The published artifact is the `prodRelease` variant, so use the prod flavor for this. The dependency coordinates come from `GROUP`, `POM_ARTIFACT_ID`, and `VERSION_NAME` in `gradle.properties` (see [CONTRIBUTORS.md](../CONTRIBUTORS.md) for versioning details).
