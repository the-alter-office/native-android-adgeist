# UTM Parameter Tracking

The AdGeist SDK now supports tracking UTM parameters from both first install (via Google Play Install Referrer) and deeplinks. This allows you to attribute user acquisition and track marketing campaign performance.

## Features

- ✅ Track UTM parameters from first app install via Google Play Install Referrer API
- ✅ Track UTM parameters from deeplinks (both app launches and runtime)
- ✅ Automatic persistence of UTM parameters using SharedPreferences
- ✅ Automatic inclusion of UTM data in analytics events
- ✅ Support for all standard UTM parameters: source, medium, campaign, term, content

## Setup

### 1. Dependencies

The SDK automatically includes the required dependency:

```gradle
implementation("com.android.installreferrer:installreferrer:2.2")
```

### 2. Configure Deeplink Support (Optional)

To track UTM parameters from deeplinks, add intent filters to your `AndroidManifest.xml`:

```xml
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:launchMode="singleTop">

    <!-- Your existing intent filters -->
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>

    <!-- HTTPS/HTTP Deeplinks -->
    <intent-filter android:autoVerify="true">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />

        <data
            android:scheme="https"
            android:host="yourdomain.com"
            android:pathPrefix="/app" />
    </intent-filter>

    <!-- Custom URL Scheme -->
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />

        <data android:scheme="yourapp" />
    </intent-filter>
</activity>
```

## Usage

### Automatic First Install Tracking

UTM parameters from the install referrer are automatically tracked when the SDK is initialized for the first time:

```kotlin
// Initialize the SDK (already in your code)
val adGeist = AdgeistCore.initialize(applicationContext)

// UTM parameters from install referrer are automatically tracked
```

**Example Install Referrer URL:**

```
utm_source=google&utm_medium=cpc&utm_campaign=summer_sale&utm_term=shoes&utm_content=ad_variant_a
```

### Tracking from Deeplinks

Track UTM parameters when your app is opened via a deeplink:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Initialize SDK
    adGeist = AdgeistCore.initialize(applicationContext)

    // Track UTM from intent
    handleDeeplinkUtm(intent)
}

override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleDeeplinkUtm(intent)
}

private fun handleDeeplinkUtm(intent: Intent?) {
    intent?.data?.let { uri ->
        // Track UTM parameters from the deeplink
        adGeist.trackUtmFromDeeplink(uri)

        // Optionally retrieve and use the parameters
        val utmParams = adGeist.getUtmParameters()
        utmParams?.let {
            Log.d("UTM", "Source: ${it.source}, Campaign: ${it.campaign}")
        }
    }
}
```

**Example Deeplink URLs:**

```
https://yourdomain.com/app?utm_source=facebook&utm_medium=social&utm_campaign=spring_promo

yourapp://open?utm_source=email&utm_medium=newsletter&utm_campaign=weekly_digest
```

### Retrieving UTM Parameters

Get the currently stored UTM parameters:

```kotlin
val utmParams = adGeist.getUtmParameters()

utmParams?.let {
    println("UTM Source: ${it.source}")
    println("UTM Medium: ${it.medium}")
    println("UTM Campaign: ${it.campaign}")
    println("UTM Term: ${it.term}")
    println("UTM Content: ${it.content}")
    println("Timestamp: ${it.timestamp}")
}
```

### Automatic Analytics Integration

UTM parameters are automatically included in all analytics events when you use `logEvent()`:

```kotlin
// Log an event
adGeist.logEvent(
    Event(
        eventType = "purchase_completed",
        eventProperties = mapOf(
            "item_id" to "12345",
            "price" to 29.99
        )
    )
)

// The event will automatically include UTM data:
// {
//   "event_type": "purchase_completed",
//   "event_properties": {
//     "item_id": "12345",
//     "price": 29.99,
//     "utm_data": {
//       "utm_source": "google",
//       "utm_medium": "cpc",
//       "utm_campaign": "summer_sale",
//       "utm_term": "shoes",
//       "utm_content": "ad_variant_a",
//       "utm_timestamp": 1234567890
//     }
//   }
// }
```

### Clearing UTM Parameters

If needed, you can clear stored UTM parameters:

```kotlin
adGeist.clearUtmParameters()
```

## UTM Parameter Structure

The `UtmParameters` data class includes:

| Parameter   | Description                 | Example                      |
| ----------- | --------------------------- | ---------------------------- |
| `source`    | Traffic source              | google, facebook, newsletter |
| `medium`    | Marketing medium            | cpc, email, social, organic  |
| `campaign`  | Campaign name               | summer_sale, product_launch  |
| `term`      | Paid keywords               | running+shoes, best+deals    |
| `content`   | Ad variant or content       | ad_variant_a, banner_top     |
| `timestamp` | When tracked (milliseconds) | 1234567890                   |

## Testing

### Testing Install Referrer (Development)

Testing the install referrer requires using ADB:

```bash
# Install your app
adb install app-debug.apk

# Simulate install referrer
adb shell am broadcast -a com.android.vending.INSTALL_REFERRER \
  -n com.examplenativeandroidapp/com.android.installreferrer.api.InstallReferrerReceiver \
  --es "referrer" "utm_source=google&utm_medium=cpc&utm_campaign=test"
```

### Testing Deeplinks

Using ADB:

```bash
# Test HTTPS deeplink
adb shell am start -W -a android.intent.action.VIEW \
  -d "https://example.com/app?utm_source=test&utm_medium=deeplink&utm_campaign=demo" \
  com.examplenativeandroidapp

# Test custom URL scheme
adb shell am start -W -a android.intent.action.VIEW \
  -d "adgeistdemo://open?utm_source=custom&utm_medium=scheme&utm_campaign=test" \
  com.examplenativeandroidapp
```

Using browser or other apps:

1. Create an HTML file with a link: `<a href="https://example.com/app?utm_source=test&utm_medium=web&utm_campaign=demo">Open App</a>`
2. Click the link on your device
3. Choose your app when prompted

## Best Practices

1. **First-touch Attribution**: The SDK tracks the first UTM parameters and keeps them until manually cleared
2. **Override on Deeplink**: When a user opens a deeplink with UTM parameters, they override the stored values
3. **Persistence**: UTM parameters persist across app sessions until cleared
4. **Privacy**: UTM parameters are stored locally on the device and only sent with analytics events
5. **URL Encoding**: UTM values are automatically URL-decoded by the SDK

## Example Marketing URLs

### Google Ads

```
https://yourdomain.com/app?utm_source=google&utm_medium=cpc&utm_campaign=brand_keywords&utm_term=your+app+name&utm_content=text_ad_v1
```

### Facebook Ads

```
https://yourdomain.com/app?utm_source=facebook&utm_medium=social&utm_campaign=retargeting&utm_content=carousel_ad
```

### Email Newsletter

```
https://yourdomain.com/app?utm_source=newsletter&utm_medium=email&utm_campaign=weekly_digest&utm_content=header_cta
```

### QR Code

```
https://yourdomain.com/app?utm_source=qr_code&utm_medium=offline&utm_campaign=conference_2024&utm_content=booth_display
```

## Troubleshooting

### UTM Parameters Not Tracked

1. **Check permissions**: Ensure your app has internet permission
2. **Check timing**: Install referrer is fetched asynchronously on first launch
3. **Check logs**: Look for `UtmTracker` logs in Logcat
4. **Verify intent filters**: Ensure deeplink intent filters are properly configured

### Deeplinks Not Working

1. **Verify intent filters** in AndroidManifest.xml
2. **Check launchMode**: Use `singleTop` to handle deeplinks when app is already running
3. **Test with ADB** first before testing from other sources
4. **Check domain verification** for HTTPS links (App Links)

## API Reference

### AdgeistCore

```kotlin
// Track UTM from deeplink
fun trackUtmFromDeeplink(uri: Uri)

// Get stored UTM parameters
fun getUtmParameters(): UtmParameters?

// Clear stored UTM parameters
fun clearUtmParameters()
```

### UtmParameters

```kotlin
data class UtmParameters(
    val source: String? = null,
    val medium: String? = null,
    val campaign: String? = null,
    val term: String? = null,
    val content: String? = null,
    val timestamp: Long? = null
)

// Check if has any data
fun hasData(): Boolean

// Convert to map for analytics
fun toMap(): Map<String, Any?>
```

## Support

For issues or questions about UTM tracking, please contact the AdGeist SDK support team.
