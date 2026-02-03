# UTM Tracking - Quick Start Guide

## 🚀 Quick Setup (3 Steps)

### Step 1: SDK Already Has UTM Tracking!

The AdGeist SDK now automatically tracks UTM parameters. No additional setup needed in most cases.

### Step 2: Add Deeplink Intent Filters (Optional)

If you want to track UTM from deeplinks, add this to your `AndroidManifest.xml`:

```xml
<activity
    android:name=".MainActivity"
    android:launchMode="singleTop">

    <!-- Your app link -->
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="yourapp" />
    </intent-filter>
</activity>
```

### Step 3: Handle Deeplinks in Your Activity

Add this code to your main activity:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Initialize SDK
    adGeist = AdgeistCore.initialize(applicationContext)

    // Track deeplink UTM
    intent?.data?.let { uri ->
        adGeist.trackUtmFromDeeplink(uri)
    }
}

override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    intent?.data?.let { uri ->
        adGeist.trackUtmFromDeeplink(uri)
    }
}
```

## ✅ That's It!

UTM parameters are now:

- ✅ Automatically tracked on first install
- ✅ Automatically tracked from deeplinks
- ✅ Automatically included in all analytics events

## 📊 View UTM Data

```kotlin
val utm = adGeist.getUtmParameters()
Log.d("UTM", "Campaign: ${utm?.campaign}, Source: ${utm?.source}")
```

## 🧪 Test Deeplinks

```bash
# Test with ADB
adb shell am start -W -a android.intent.action.VIEW \
  -d "yourapp://open?utm_source=test&utm_campaign=demo" \
  com.yourpackage
```

## 📱 Example Marketing URLs

**Google Ads:**

```
https://yourapp.com?utm_source=google&utm_medium=cpc&utm_campaign=summer_sale
```

**Facebook:**

```
https://yourapp.com?utm_source=facebook&utm_medium=social&utm_campaign=product_launch
```

**Email:**

```
https://yourapp.com?utm_source=newsletter&utm_medium=email&utm_campaign=weekly_digest
```

## 📖 Full Documentation

See [UTM_TRACKING.md](UTM_TRACKING.md) for complete documentation.
