[![Maven Central](https://img.shields.io/maven-central/v/ai.adgeist/adgeistkit.svg)](https://central.sonatype.com/artifact/ai.adgeist/adgeistkit)

---

# Adgeist Mobile Ads SDK for Android

This guide is for publishers who want to monetize an Android app with Adgeist.

Integrating the Adgeist Mobile Ads SDK into an app is the first step toward displaying ads and earning revenue. Once you've integrated the SDK, you can proceed to implement one or more of the supported ad formats.

## Prerequisites

Make sure that your app's build file uses the following values:

- Minimum SDK version of 23 or higher
- Compile SDK version of 35 or higher
- **Recommended:** Create an Adgeist publisher account and register your app

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
val adView = AdView(this).apply {
    layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )
}
```

#### Set the Ad Size

For a fixed-size ad, set the `AdSize` to one of the predefined sizes or create a custom size:

```kotlin
adView.setAdDimension(AdSize(360, 360))
```

For a **responsive ad** that sizes itself to fit its parent container, skip `setAdDimension()` entirely and set `adIsResponsive` instead:

```kotlin
adView.adIsResponsive = true
```

#### Set Required Properties

Configure the following properties on your `AdView`:

**Ad Unit ID:**

```kotlin
adView.adUnitId = "YOUR_AD_UNIT_ID"
```

Replace `YOUR_AD_UNIT_ID` with the ad unit ID you created in the Adgeist dashboard.

**Ad Type:**

```kotlin
adView.adType = AdType.BANNER  // or AdType.DISPLAY, AdType.COMPANION
```

Replace with the ad type you created in the Adgeist dashboard:
- `AdType.BANNER` - Small rectangular banner ads
- `AdType.DISPLAY` - Standard display ads  
- `AdType.COMPANION` - Companion ads (requires minimum 320x320 dimensions)

#### Create an Ad Request

Once the `AdView` is configured with its properties (`adUnitId`, `adType`, etc.), create an ad request using the builder pattern:

```kotlin
val adRequest = AdRequest.Builder().build()
```

#### Load an Ad

Now it's time to load an ad. This is done by calling `loadAd()` on the `AdView` object:

```kotlin
adView.loadAd(adRequest)
```

#### Destroy the Ad

When you're done with an `AdView`, call `destroy()` to permanently tear the ad down and release its resources. Call it from your activity's or fragment's `onDestroy()`:

```kotlin
override fun onDestroy() {
    adView?.destroy()
    super.onDestroy()
}
```

Calling `destroy()` stops any in-progress ad load, releases the underlying WebView, and triggers the `onAdClosed()` callback on your `AdListener`. A destroyed `AdView` should not be reused — create a new instance to show another ad.

The SDK also invokes `destroy()` automatically when the host screen is popped or the activity is destroyed, but calling it explicitly is recommended so cleanup happens deterministically.

#### Complete Example

Here's a complete example of loading a banner ad programmatically:

```kotlin
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.adgeistkit.AdgeistCore
import com.adgeistkit.ads.AdSize
import com.adgeistkit.ads.AdType
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
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            adUnitId = "YOUR_AD_UNIT_ID"
            adType = AdType.BANNER
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
        adView?.destroy()
        super.onDestroy()
    }
}
```

#### Responsive Ad Example

For an ad that fills its parent container instead of a fixed size, skip `setAdDimension()` and set `adIsResponsive = true`:

```kotlin
val adView = AdView(this).apply {
    layoutParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
    )
    adUnitId = "YOUR_AD_UNIT_ID"
    adType = AdType.BANNER
    adIsResponsive = true
}

val container = findViewById<FrameLayout>(R.id.adContainer)
container.addView(adView)

adView.loadAd(AdRequest.Builder().build())
```

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
})
```

---

## Support

If you run into any difficulties while integrating or using the Adgeist Mobile Ads SDK, reach out to beast@thealteroffice.com and we'll help you get it sorted out.