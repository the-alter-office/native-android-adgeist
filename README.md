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
fail with `onAdFailedToLoad` reporting an invalid adspace or origin.

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

Replace `YOUR_ADGEIST_ID` with your actual Adgeist publisher ID.

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

#### Define the Ad View

Banner and display ads are displayed in `AdView` objects, so the first step toward integrating ads is to include an `AdView` in your view hierarchy. Create an `AdView` and add it to your view hierarchy programmatically:

```kotlin
val adView = AdView(this)
```

#### Set the Ad Size

For a fixed-size ad, set the `AdSize` to one of the predefined sizes or create a custom size:

```kotlin
adView.setAdDimension(AdSize(360, 360))
```

The size you request is a layout hint, not a guarantee — the creative that comes back decides the
real size. If the server returns a creative of a different size, the SDK resizes the `AdView` to
match it and reports the mismatch through [`onAdWarning()`](#ad-events). Update your layout to the
size named in that warning so the ad does not shift your content when it arrives.

#### Responsive ads

A **responsive** ad takes its size from your layout instead of from `AdSize`:

```kotlin
adView.adIsResponsive = true
```

Each axis is resolved independently:

| What the parent layout gives the `AdView` | Where that axis comes from |
|---|---|
| A fixed size (`match_parent` in a bounded parent, or a fixed `dp` value) | Your layout |
| `wrap_content`, or no constraint at all (inside a `ScrollView`) | `AdSize` |

If your layout fixes **both** axes, you need no `AdSize` at all:

```kotlin
// container is, for example, 300dp x 250dp
adView.adIsResponsive = true
```

If your layout fixes only **one** axis, supply the other with `AdSize.width()` or
`AdSize.height()`:

```kotlin
// Bottom banner: match_parent wide, wrap_content tall - you supply the height
adView.adIsResponsive = true
adView.setAdDimension(AdSize.height(50))

// Side rail: wrap_content wide, match_parent tall - you supply the width
adView.adIsResponsive = true
adView.setAdDimension(AdSize.width(120))
```

If **neither** your layout nor `AdSize` determines an axis, the `AdView` measures `0` on that axis
and the ad never becomes visible. The SDK reports this through [`onAdWarning()`](#ad-events) rather
than failing silently. The most common cause is a responsive ad placed directly inside a
`ScrollView`, which tells its children nothing about height — give it `AdSize.height(...)`.

#### Set Required Properties

Configure the following properties on your `AdView`:

**Ad Unit ID:**

```kotlin
adView.adUnitId = "YOUR_AD_UNIT_ID"
```

Replace `YOUR_AD_UNIT_ID` with the ad unit ID you created in the Adgeist dashboard.

#### Create an Ad Request

Once the `AdView` is configured with its properties (`adUnitId`, `setAdDimension`), create an ad request using the builder pattern:

```kotlin
val adRequest = AdRequest.Builder().build()
```

#### Load an Ad

Now it's time to load an ad. This is done by calling `loadAd()` on the `AdView` object:

```kotlin
adView.loadAd(adRequest)
```

#### Reserve space for failed loads

Not every request returns an ad. By default the `AdView` keeps its reserved box when a load fails,
so nothing below it moves:

```kotlin
adView.reserveSpace = true    // default
```

Set it to `false` if you would rather the slot collapse when there is nothing to show:

```kotlin
adView.reserveSpace = false
```

| `reserveSpace` | After `onAdFailedToLoad()` |
|---|---|
| `true` (default) | The `AdView` stays in your layout at its full size, empty. No layout shift. |
| `false` | The `AdView` removes itself from its parent. Content below it moves up. |

Two things to know:

- This applies to **failed loads only**. `destroyAd()` always removes the `AdView` from its parent,
  whatever `reserveSpace` is set to.
- Reserving space only works if the `AdView` has a size to hold. A fixed-size ad always does; a
  responsive ad only does when its axes resolve — see [Responsive ads](#responsive-ads).

#### Destroy the Ad

When you're done with an `AdView`, call `destroyAd()` to permanently tear the ad down and release its resources. Call it from your activity's or fragment's `onDestroy()`:

```kotlin
override fun onDestroy() {
    adView?.destroyAd()
    super.onDestroy()
}
```

Calling `destroyAd()` stops any in-progress ad load, releases the underlying WebView, and triggers the `onAdClosed()` callback on your `AdListener`. A destroyed `AdView` should not be reused — create a new instance to show another ad.

The SDK also invokes `destroyAd()` automatically when the host screen is popped or the activity is destroyed, but calling it explicitly is recommended so cleanup happens deterministically.

#### Complete Example

Here's a complete example of loading a banner ad programmatically:

```kotlin
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.adgeistkit.AdgeistCore
import com.adgeistkit.ads.AdSize
import com.adgeistkit.ads.AdView
import com.adgeistkit.request.AdRequest

class MainActivity : AppCompatActivity() {
    private var adGeist: AdgeistCore? = null
    private var adView: AdView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize SDK
        adGeist = AdgeistCore.initialize(applicationContext)

        // Create AdView
        val newAdView = AdView(this).apply {
            adUnitId = "YOUR_AD_UNIT_ID"
            setAdDimension(AdSize(320, 50))
        }
        adView = newAdView

        // Create ad request
        val adRequest = AdRequest.Builder().build()

        // Load ad
        newAdView.loadAd(adRequest)

        // Add to layout
        val container = findViewById<LinearLayout>(R.id.adContainer)
        container.addView(newAdView)
    }

    override fun onDestroy() {
        adView?.destroyAd()
        super.onDestroy()
    }
}
```

#### Responsive Ad Example

When the container fixes **both** axes, skip `setAdDimension()` entirely:

```kotlin
val adView = AdView(this).apply {
    adUnitId = "YOUR_AD_UNIT_ID"
    adIsResponsive = true
}

// adContainer is a fixed box, for example 300dp x 250dp
val container = findViewById<FrameLayout>(R.id.adContainer)
container.addView(adView)
adView.loadAd(AdRequest.Builder().build())
```

When the container fixes only **one** axis, supply the other. A bottom banner is `match_parent`
wide and `wrap_content` tall, so the width comes from the layout and you provide the height:

```kotlin
val adView = AdView(this).apply {
    adUnitId = "YOUR_AD_UNIT_ID"
    adIsResponsive = true
    setAdDimension(AdSize.height(50))
}

val container = findViewById<FrameLayout>(R.id.bottomBannerContainer)
container.addView(adView)
adView.loadAd(AdRequest.Builder().build())
```

A side rail is the mirror image — `wrap_content` wide and `match_parent` tall — so use
`AdSize.width(120)` instead.

#### Compose hosts

In Compose, host the `AdView` inside an `AndroidView` and connect it to your screen with
— `viewModelStoreOwner`:

```kotlin
val owner = LocalViewModelStoreOwner.current

AndroidView(
    factory = { ctx ->
        AdView(ctx).apply {
            viewModelStoreOwner = owner   // must be set before loadAd()
            adUnitId = "YOUR_AD_UNIT_ID"
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

#### Ad Events

You can listen for a number of events in the ad's lifecycle, including loading, impression, click, as well as open and close events. It is recommended to set the listener before loading the ad:

```kotlin
adView?.setAdListener(object : AdListener() {
    override fun onAdClicked() {
        // Code to be executed when the user clicks on an ad.
    }

    override fun onAdClosed() {
        // Code to be executed when the ad is completely removes 
        // from the screen
    }

    override fun onAdFailedToLoad(error: String) {
        // Code to be executed when an ad request fails.
        Log.e("AdView", "Ad Failed to Load: $error")
    }

    override fun onAdImpression() {
        // Code to be executed when an impression is recorded
        // for an ad.
    }

    override fun onAdLoaded() {
        // Code to be executed when an ad finishes loading.
        Log.d("AdView", "Ad Loaded Successfully!")
    }

    override fun onAdOpened() {
        // Code to be executed when an ad opens an overlay that
        // covers the screen.
    }

    override fun onAdWarning(message: String) {
        // Code to be executed when the SDK finds a problem that did not
        // stop the ad from loading.
        Log.w("AdView", message)
    }
})
```

---

## Support

If you run into any difficulties while integrating or using the Adgeist Mobile Ads SDK, reach out to beast@thealteroffice.com and we'll help you get it sorted out.