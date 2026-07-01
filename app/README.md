Test build of adgeistkit using
```
./gradlew :adgeistkit:assemble
```
run the exampleapp using
```
./gradlew :app:installBetaDebug
```

run unit test in exampleapp
```
./gradlew :app:assembleBetaRelease :app:assembleBetaReleaseAndroidTest --console=plain 2>&1 | tail -40
```