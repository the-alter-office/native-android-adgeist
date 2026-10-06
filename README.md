[![Maven Central](https://img.shields.io/maven-central/v/ai.adgeist/adgeistkit.svg)](https://central.sonatype.com/artifact/ai.adgeist/adgeistkit)

---

# Adgeist Mobile Ads SDK for Android

This guide is for publishers who want to monetize an Android app with Adgeist.

Integrating the Adgeist Mobile Ads SDK into an app is the first step toward displaying ads and earning revenue. Once you've integrated the SDK, you can proceed to implement one or more of the supported ad formats.

## Prerequisites

Make sure your project meets the following minimum versions:

| Requirement | Minimum |
|---|---|
| Android minSdk | 23 (Android 6.0) |
| compileSdk | 35 |
| Android Gradle Plugin | 8.6 |
| Gradle | 8.7 |
| Kotlin | 1.8 |
| JDK (to run the build) | 17 |

> **Note:** Your app does **not** need to change its own `jvmTarget`/`compileOptions` — any level
> (including Java 8) works. The SDK ships Java 11 bytecode that D8 dexes independently of your
> app's classes. The JDK 17 row is required by AGP 8.6+ (not by this SDK): AGP 8.x needs JDK 17 to
> run the build, and compileSdk 35 already forces consumers onto AGP 8.6+, which in turn requires
> Gradle 8.7+ — see the
> [AGP compatibility matrix](https://developer.android.com/build/releases/gradle-plugin#android_gradle_plugin_and_android_studio_compatibility).
> It does not mean your app needs Java 17 language features or bytecode anywhere.

**Recommended:** Create an Adgeist publisher account and register your app

### Register your app's package id as an allowed origin

When registering your app in the Adgeist web interface, the **Package Id** field must contain the
exact effective `applicationId` your build ships. The SDK sends this `applicationId` as the request
origin, and the ad server rejects any origin not registered for your publisher app id — ads then
fail with an [`AW6`](#event-reference) event (ad request rejected).

Include any `applicationIdSuffix` (e.g. a debug build with `applicationIdSuffix = ".debug"` must be
registered as `com.example.app.debug`, not `com.example.app`) as well as any product-flavor
override. Confirm the exact id to register with:

```bash
adb shell pm list packages | grep <your-app>
```

## Configure your app

### STEP 1: Ensure Maven Central is configured

In your Gradle settings file, make sure Maven Central is included (it's typically present by default). Check your `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "My Application"
include(":app")
```

### STEP 2: Add the dependency

Add the dependencies for Adgeist Mobile Ads SDK to your app module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("ai.adgeist:adgeistkit:Tag")
}
```

_Replace `Tag` with the latest version from the [Maven Central](https://central.sonatype.com/artifact/ai.adgeist/adgeistkit/versions).

Click **Sync Now** to sync your project with Gradle files.

### STEP 3: Configure AndroidManifest.xml

Add your Adgeist publisher ID (as identified in the Adgeist web interface) to your app's `AndroidManifest.xml` file. Add `<meta-data>` tags with the following names:

- `android:name="com.adgeistkit.ads.ADGEIST_APP_ID"`

```xml
<manifest>
    <application>
        <!-- Sample Adgeist app ID: 69326f9fbb280f9241cabc94 -->
        <meta-data
            android:name="com.adgeistkit.ads.ADGEIST_APP_ID"
            android:value="YOUR_ADGEIST_ID"/>

    </application>
</manifest>
```

Replace `YOUR_ADGEIST_ID` with your actual Adgeist publisher ID. The `android:name` attribute must stay as is.

Also make sure your app requests the INTERNET permission, which `loadAd()` requires:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

### STEP 4: Initialize the Adgeist Mobile Ads SDK

Before loading ads, initialize the Adgeist Mobile Ads SDK by calling `AdgeistCore.initialize(this)`. Add this to your launcher activity, e.g. `MainActivity.kt`:

```kotlin
import com.adgeistkit.AdgeistCore

class MainActivity : AppCompatActivity() {
    private var adGeist: AdgeistCore? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize the Adgeist Mobile Ads SDK
        adGeist = AdgeistCore.initialize(applicationContext)
    }
}
```

Or from Java:

```java
import com.adgeistkit.AdgeistCore;

public class MainActivity extends AppCompatActivity {
    private AdgeistCore adGeist;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize the Adgeist Mobile Ads SDK
        adGeist = AdgeistCore.initialize(getApplicationContext());
    }
}
```

You're now ready to implement ads in your app!

## Implement Ad Formats

Once the SDK is integrated and initialized, you can implement one or more of the supported ad formats below.

### Banner and Display Ads

Banner ads are rectangular ads that occupy a portion of an app's layout. They stay on screen while users are interacting with the app, either anchored at the top or bottom of the screen or inline with content as the user scrolls.

> **Launch screen ads (Android 12+ splash screens):** On Android 12+ (targetSdk 31+), the system
> SplashScreen shown at app startup (via `installSplashScreen()`) is rendered by the OS — an icon on
> a background color — and cannot host views or ads. To use a launch-screen ad unit, your app must
> present its own splash/landing screen (an Activity or Compose screen shown after the system
> splash) and place the `AdView` there. If your app has no such screen, assign the ad unit to
> another placement instead.

#### Implement Ad Placement

Banner, display and companion ads are shown in an `AdView`. Create one, give it your ad unit ID, add it to your layout, and call `loadAd()` — the SDK loads and renders the ad content.

An adspace is either **fixed-size** or **responsive**, depending on what you chose while creating it on [adgeist.ai](https://adgeist.ai). Use the snippet below that matches yours. Each one is complete — copy it into your screen and the ad placement is done.

In the snippets, `context` is your screen's `Context`, and `adContainer` is the view in your layout that holds the ad.

**Fixed-size adspace** — you entered a width and a height when you created it:

```kotlin
import com.adgeistkit.ads.AdSize
import com.adgeistkit.ads.AdView
import com.adgeistkit.request.AdRequest

// Sample Adgeist ad unit ID: 6932a4c022f6786424ce3b84
// Sample ad size: AdSize(320, 480)

val adView = AdView(context).apply {
    adUnitId = "YOUR_ADUNIT_ID"
    setAdDimension(AdSize(YOUR_AD_WIDTH, YOUR_AD_HEIGHT))
}

adContainer.addView(adView)
adView.loadAd(AdRequest.Builder().build())
```

**Responsive adspace** — it takes its size from your layout, so it carries no dimensions:

```kotlin
import com.adgeistkit.ads.AdView
import com.adgeistkit.request.AdRequest

// adContainer is a box with a real size, for example 300dp x 250dp

val adView = AdView(context).apply {
    adUnitId = "YOUR_ADUNIT_ID"
    adIsResponsive = true
}

adContainer.addView(adView)
adView.loadAd(AdRequest.Builder().build())
```

Replace `YOUR_ADUNIT_ID` with your Adgeist Ad Unit ID, as identified in the Adgeist web interface. Each ad placement in your app requires its own ad unit ID.

To react to loads, failures, clicks and warnings, add a listener before `loadAd()` — see [Ad Events](#ad-events). To release the ad when the screen goes away, see [Destroy the Ad](#destroy-the-ad).

#### What `setAdDimension` actually does

`setAdDimension()` sets the space your layout hands the ad. The `AdView` takes that box as soon as it is laid out, before any ad has been fetched, so your screen is laid out correctly while the request is in flight.

It is **not** what decides the creative's size. That comes from the adspace you configured on adgeist.ai. When the ad arrives the SDK compares the two:

| | What happens |
|---|---|
| Your `AdSize` matches the adspace | The creative fills the box you reserved. Nothing moves. |
| They differ | The SDK resizes the `AdView` to the creative's real size, so content around it shifts. An [`AW7`](#event-reference) event fires, with the real size in `data.reason`. Change your `AdSize` to that size. |

So replace `YOUR_AD_WIDTH` and `YOUR_AD_HEIGHT` with the exact dimensions you entered on adgeist.ai when you created the adspace, and the two can never disagree.

> The SDK corrects the size for you so the creative is never cropped, but the correction happens after the ad arrives — which is the layout shift you reserved the box to avoid. Read `AW7` as "your `AdSize` is wrong, here is the right one", and treat it as a bug to fix rather than a runtime condition to handle.

This comparison applies to fixed-size ads only. A responsive ad is never resized to the creative — it keeps the size your layout gives it.

#### Destroy the Ad

When you're done with an `AdView`, call `destroyAd()` to permanently tear the ad down and release its resources. Call it from your activity's or fragment's `onDestroy()`:

```kotlin
override fun onDestroy() {
    adView?.destroyAd()
    super.onDestroy()
}
```

Calling `destroyAd()` stops any in-progress ad load, releases the underlying WebView, and sends an [`AL2`](#event-reference) (`AD_CLOSED`) event to your `AdListener`. A destroyed `AdView` should not be reused — create a new instance to show another ad.

The SDK also invokes `destroyAd()` automatically when the host screen is popped or the activity is destroyed, but calling it explicitly is recommended so cleanup happens deterministically.

#### Compose hosts

In Compose, host the `AdView` inside an `AndroidView` and connect it to your screen with
`viewModelStoreOwner`:

```kotlin
val owner = LocalViewModelStoreOwner.current

AndroidView(
    factory = { ctx ->
        AdView(ctx).apply {
            viewModelStoreOwner = owner   // must be set before loadAd()
            adUnitId = "YOUR_ADUNIT_ID"
            setAdDimension(AdSize(320, 320))
            loadAd(AdRequest.Builder().build())
        }
    },
    onRelease = { },
)
```

**Why `viewModelStoreOwner`.** The SDK ties each loaded ad to the screen showing it, so coming back
to that screen — after a rotation, or back through the navigation stack — reuses the ad instead of
spending a new request on it. In Compose, the screen's scope lives in the composition, and
`LocalViewModelStoreOwner.current` is how you hand it over. Assign it before `loadAd()`, which is
when the SDK reads it.

**Destroying the ad in Compose.** Leave `onRelease` empty, and do not call `destroyAd()` from it.
Compose calls `onRelease` whenever the `AndroidView` leaves the composition — on rotation, and when
you navigate to another screen — and `destroyAd()` discards the screen's ad, so coming back would
request a new one. You do not need to clean up yourself: the SDK releases the WebView when the
`AdView` leaves the window, and drops the stored ad when the screen is popped off the back stack.

Call `destroyAd()` only to remove the ad for good while the user stays on the screen, for example
when they close the ad placement. Keep a reference to the `AdView` from `factory`, call
`destroyAd()` on it, and stop showing the `AndroidView`:

```kotlin
val owner = LocalViewModelStoreOwner.current
var showAd by remember { mutableStateOf(true) }
var adView by remember { mutableStateOf<AdView?>(null) }

if (showAd) {
    AndroidView(
        factory = { ctx ->
            AdView(ctx).apply {
                viewModelStoreOwner = owner
                adUnitId = "YOUR_ADUNIT_ID"
                setAdDimension(AdSize(320, 320))
                loadAd(AdRequest.Builder().build())
            }.also { adView = it }
        },
        onRelease = { },
    )
}

Button(onClick = {
    adView?.destroyAd()
    showAd = false
}) {
    Text("Close ad")
}
```

---

## Extra options

The options below are not part of the snippet adgeist.ai gives you. Your ad placement works without them — reach for one only when your layout calls for it.

### `reserveSpace` — keep the slot when an ad fails

Optional. **Defaults to `true`** — leave it out entirely unless you specifically want `false`.

Not every request returns an ad. By default the `AdView` holds its box after a failed load, so the rest of your screen stays exactly where it was:

```kotlin
val adView = AdView(context).apply {
    adUnitId = "YOUR_ADUNIT_ID"
    setAdDimension(AdSize(320, 480))
    reserveSpace = true   // default, identical to leaving it out
}
```

Set it to `false` if you would rather the ad give up its place when there is nothing to show:

| `reserveSpace` | After a failed load |
|---|---|
| `true` (default) | The `AdView` keeps its full box in your layout, empty. No layout shift. |
| `false` | The `AdView` removes itself from its parent. Content below it moves up. |

**Whether there is any space to reserve depends on how the ad is sized:**

| Ad | Does reserving work? |
|---|---|
| Fixed (`AdSize` with both width and height) | Always. The box has a size of its own to hold, ad or no ad. |
| Responsive | Only on the axes that actually resolve. An axis that nothing determines measures `0`, and there is no space to hold open. See [Responsive ads and `AdSize`](#responsive-ads-and-adsize). |

This applies to **failed loads only**. `destroyAd()` always removes the `AdView` from its parent, whatever `reserveSpace` is set to.

### Responsive ads and `AdSize`

Optional. A responsive adspace normally needs no `AdSize` at all — that is the point of it. You only reach for this when your layout fixes one axis and leaves the other open.

A responsive ad takes each axis independently:

| What the parent layout gives the `AdView` on that axis | Where that axis comes from |
|---|---|
| A fixed size (`match_parent` in a bounded parent, or a fixed `dp` value) | Your layout |
| `wrap_content`, or no constraint at all (inside a `ScrollView`) | `AdSize` |

**Both axes from the layout** — the usual case, no `AdSize`:

```kotlin
// adContainer is a fixed box, for example 300dp x 250dp
val adView = AdView(context).apply {
    adUnitId = "YOUR_ADUNIT_ID"
    adIsResponsive = true
}
```

**One axis from the layout, one from you** — use `AdSize.width()` or `AdSize.height()`:

```kotlin
// Bottom banner: match_parent wide, wrap_content tall - you supply the height
val bottomBanner = AdView(context).apply {
    adUnitId = "YOUR_ADUNIT_ID"
    adIsResponsive = true
    setAdDimension(AdSize.height(50))
}

// Side rail: wrap_content wide, match_parent tall - you supply the width
val sideRail = AdView(context).apply {
    adUnitId = "YOUR_ADUNIT_ID"
    adIsResponsive = true
    setAdDimension(AdSize.width(120))
}
```

If **neither** your layout nor `AdSize` determines an axis, the `AdView` measures `0` on that axis and the ad never becomes visible. The SDK reports this with an [`AW4`](#event-reference) event rather than failing silently. The case that catches people out is a `ScrollView`: it gives its children no definite height, so a responsive ad inside one should declare `AdSize.height(...)`.

---

## Ad Events

Set an `AdListener` before calling `loadAd()`. Every event arrives in one callback, `onAdEvent()`:

```kotlin
import com.adgeistkit.ads.AdListener
import com.adgeistkit.ads.AdgeistEvent
import com.adgeistkit.ads.AdgeistEventType

adView.setAdListener(object : AdListener() {
    override fun onAdEvent(event: AdgeistEvent) {
        when (event.type) {
            AdgeistEventType.AD_LOADED -> { }
            AdgeistEventType.AD_CLICKED -> { }
            AdgeistEventType.AD_CLOSED -> { }
            AdgeistEventType.AD_NO_FILL -> { }
            AdgeistEventType.AD_NETWORK_ERROR -> { }
            AdgeistEventType.AD_WARNING -> Log.w("AdView", event.toString())
        }
    }
})
```

### Event payload

| Field | Type | Description |
|---|---|---|
| `code` | `AdgeistEventCode` | SDK reference code, e.g. `AE1` |
| `type` | `AdgeistEventType` | Event kind, e.g. `AD_NO_FILL` |
| `message` | `String` | Human-readable description |
| `data` | `AdgeistEventData?` | Extra details; `data.reason` on the events marked below, `null` otherwise |

`data.reason` is a detailed description of what went wrong. Use it for diagnostics only; match on `code` or `type`, never on the text.

Code prefixes: `AL` lifecycle, `AI` interaction, `AE` error, `AW` warning.

### Event reference

| Code | type | Meaning | When it occurs | Possible cause | Recommended action |
|---|---|---|---|---|---|
| AL1 | `AD_LOADED` | Ad loaded successfully | Creative rendered | — | — |
| AL2 | `AD_CLOSED` | Ad closed | `destroyAd()` is called, or the screen hosting the ad is destroyed | — | — |
| AI1 | `AD_CLICKED` | Ad clicked | User taps the ad | — | — |
| AE1 | `AD_NO_FILL` | No ad available | Server returns no ad | No active campaign for the ad unit | Hide the placement |
| AE2 | `AD_NETWORK_ERROR` | Ad request failed | Ad request does not complete | Device offline, timeout, server error, or connection dropped mid-response | Retry later |
| AW1 | `AD_WARNING` | SDK not initialized | On `loadAd()` | `AdgeistCore.initialize()` was not called | Initialize the SDK before `loadAd()` |
| AW2 | `AD_WARNING` | Ad unit ID is empty | On `loadAd()` | No `adUnitId` was set | Set `adUnitId` before `loadAd()` |
| AW3 | `AD_WARNING` | Ad has no size | After the ad response | Fixed-size ad with no `AdSize` | Call `setAdDimension()`, or set `adIsResponsive = true` |
| AW4 | `AD_WARNING` | Responsive ad has no width or height | During layout | Neither the layout nor `AdSize` determines an axis, so it measures `0` | Follow `data.reason`, which names the axis and the fix |
| AW5 | `AD_WARNING` | Ad is already loading | On `loadAd()` | `loadAd()` called again before the previous load finished | Wait for the previous load's event |
| AW6 | `AD_WARNING` | Ad request rejected | During the ad request | Request rejected by the server (HTTP 4xx) | Check the ad unit ID and `ADGEIST_APP_ID` in `AndroidManifest.xml` |
| AW7 | `AD_WARNING` | Ad size mismatch | After the ad response | Your `AdSize` differs from the adspace's size; the `AdView` was resized | Set `AdSize` to the size in `data.reason` |
| AW8 | `AD_WARNING` | Not enough space for a companion ad | While rendering the creative | Less than 320x320 available; the ad is collapsed and not tracked | Give the `AdView` at least 320x320 |
| AW9 | `AD_WARNING` | Ad failed to render | While rendering the creative | Web view error | Contact support with `code` and `data.reason` |
| AW10 | `AD_WARNING` | Ad response could not be read | After the ad response | SDK version mismatch | Contact support with `code` |

`data.reason` is set on AW4, AW7, AW8 and AW9.

When a load fails with AE1, AE2, AW1, AW2, AW3, AW6, AW9 or AW10, the `AdView` keeps or gives up its space according to [`reserveSpace`](#reservespace--keep-the-slot-when-an-ad-fails).

---

## Support

If you run into any difficulties while integrating or using the Adgeist Mobile Ads SDK, reach out to beast@thealteroffice.com or kishore@thealteroffice.com and we'll help you get it sorted out.
