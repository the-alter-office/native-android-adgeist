![JitPack](https://jitpack.io/v/the-alter-office/native-android-adgeist.svg)

---

# Adgeist Mobile Ads SDK for Android

This guide is for publishers who want to monetize an Android app with Adgeist.

Integrating the Adgeist Mobile Ads SDK into an app is the first step toward displaying ads and earning revenue. Once you've integrated the SDK, you can proceed to implement one or more of the supported ad formats.

## Prerequisites

Make sure that your app's build file uses the following values:

- Minimum SDK version of 23 or higher
- Compile SDK version of 35 or higher
- **Recommended:** Create an Adgeist publisher account and register your app

## Configure your app

### STEP 1: Add the JitPack repository

In your Gradle settings file, include Google's Maven repository and Maven Central (if not already present), then add the JitPack repository in `settings.gradle.kts`:

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
        maven { url = uri("https://jitpack.io") }
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

_Replace `Tag` with the latest version from the JitPack badge above._

Click **Sync Now** to sync your project with Gradle files.

### STEP 3: Configure AndroidManifest.xml

Add your Adgeist publisher ID, API key (as identified in the Adgeist web interface) to your app's `AndroidManifest.xml` file. Add `<meta-data>` tags with the following names:

- `android:name="com.adgeistkit.ads.ADGEIST_APP_ID"`
- `android:name="com.adgeistkit.ads.ADGEIST_API_KEY"`

```xml
<manifest>
    <application>
        <!-- Sample Adgeist app ID: 69326f9fbb280f9241cabc94 -->
        <!-- Sample Adgeist API key: b4e33bb73061d4e33670f229033f14bf770d35b15512dc1f106529e38946e49c -->

        <meta-data
            android:name="com.adgeistkit.ads.ADGEIST_APP_ID"
            android:value="ADGEIST_APP_ID"/>

        <meta-data
            android:name="com.adgeistkit.ads.ADGEIST_API_KEY"
            android:value="ADGEIST_API_KEY"/>

    </application>
</manifest>
```

Replace `ADGEIST_APP_ID`, `ADGEIST_API_KEY` with your actual Adgeist credentials.

## Initialize the Adgeist Mobile Ads SDK

Before loading ads, initialize the Adgeist Mobile Ads SDK by calling `AdgeistCore.initialize(this)`.

```kotlin
import com.adgeistkit.AdgeistCore

class MainActivity : AppCompatActivity() {
    private lateinit var adGeist: AdgeistCore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize the Adgeist Mobile Ads SDK
        adGeist = AdgeistCore.initialize(applicationContext)
    }
}
```

You're now ready to implement ads in your app!

---

## Banner and Display Ads

Banner ads are rectangular ads that occupy a portion of an app's layout. They stay on screen while users are interacting with the app, either anchored at the top or bottom of the screen or inline with content as the user scrolls.

### Define the Ad View

Banner and display ads are displayed in `AdView` objects, so the first step toward integrating ads is to include an `AdView` in your view hierarchy. This can be done either in XML or programmatically.

#### Option 1: Using XML Layout

Add the `AdView` to your layout file:

```xml
<com.adgeistkit.ads.AdView
    android:id="@+id/adView"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    ads:adUnitId="YOUR_AD_UNIT_ID"/>
```

#### Option 2: Programmatic Implementation

An `AdView` can also be instantiated directly. The following example creates an `AdView` programmatically:

```kotlin
val adView = AdView(this).apply {
    layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )
}
```

### Set the Ad Size

Set the `AdSize` to one of the predefined sizes or create a custom size:

```kotlin
adView.setAdDimension(AdSize(360, 360))
```

### Set Required Properties

Configure the following properties on your `AdView`:

**Ad Unit ID:**

```kotlin
adView.adUnitId = "YOUR_AD_UNIT_ID"
```

Replace `YOUR_AD_UNIT_ID` with the ad unit ID you created in the Adgeist dashboard.

**Ad Type:**

```kotlin
adView.adType = "banner"  // or "display"
```

Replace with the ad type you created in the Adgeist dashboard (e.g., `"banner"`, `"display"`).

### Create an Ad Request

Once the `AdView` is configured with its properties (`adUnitId`, `adType`, etc.), create an ad request using the builder pattern:

```kotlin
val adRequest = AdRequest.Builder()
    .setTestMode(false)
    .build()
```

### Always Test with Test Ads

When building and testing your apps, make sure you use test ads rather than live, production ads. Failure to do so can lead to suspension of your account.

The easiest way to load test ads is to set `testMode` to `true` when building your ad request:

```kotlin
val adRequest = AdRequest.Builder()
    .setTestMode(true)
    .build()
```

**Important:** Make sure you set `testMode` to `false` before publishing your app.

### Load an Ad

Now it's time to load an ad. This is done by calling `loadAd()` on the `AdView` object:

```kotlin
adView.loadAd(adRequest)
```

### Complete Example

Here's a complete example of loading a banner ad programmatically:

```kotlin
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.adgeistkit.AdgeistCore
import com.adgeistkit.ads.AdSize
import com.adgeistkit.ads.AdView
import com.adgeistkit.ads.network.AdRequest

class MainActivity : AppCompatActivity() {
    private lateinit var adGeist: AdgeistCore
    private var adView: AdView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize SDK
        adGeist = AdgeistCore.initialize(applicationContext)

        // Create AdView
        adView = AdView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Configure AdView
        adView.adUnitId = "YOUR_AD_UNIT_ID"
        adView.adType = "banner"
        setAdDimension(AdSize(320, 50))

        // Create ad request
        val adRequest = AdRequest.Builder()
            .setTestMode(true)
            .build()

        // Load ad
        adView?.loadAd(adRequest)

        // Add to layout
        val container = findViewById<LinearLayout>(R.id.adContainer)
        container.addView(adView)
    }

    override fun onDestroy() {
        adView?.destroy()
        super.onDestroy()
    }
}
```

### Ad Events

You can listen for a number of events in the ad's lifecycle, including loading, impression, click, as well as open and close events. It is recommended to set the listener before loading the ad:

```kotlin
adView?.setAdListener(object : AdListener() {
    override fun onAdClicked() {
        // Code to be executed when the user clicks on an ad.
    }

    override fun onAdClosed() {
        // Code to be executed when the user is about to return
        // to the app after tapping on an ad.
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

## Next Steps

Now that you've integrated the Adgeist Mobile Ads SDK and implemented banner/display ads, you can:

- Explore additional ad formats (if available)
- Review your ad performance in the Adgeist dashboard
- Optimize your ad placements for better revenue

For support, please visit the Adgeist documentation or contact support.
