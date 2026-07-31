# ============================================================================
# AdGeist SDK (ai.adgeist:adgeistkit) — consumer ProGuard/R8 rules.
# Bundled into the AAR via consumerProguardFiles; applied to the CONSUMER's
# R8 pass (full mode on AGP 8+). The SDK ships unminified, so these rules are
# what protect SDK classes when the host app (e.g. PixelPlayer) runs R8.
#
# Targeted keeps only — everything else in the SDK may be freely shrunk,
# renamed, and optimized by the consumer's build. Public API classes need no
# rules: consumer code references them directly, so R8 traces them.
# Regression coverage: AdModelR8Test / JsBridgeR8Test in the sample app run
# against the minified release variant on every connected test run.
# ============================================================================

# Gson binds JSON keys via @SerializedName (every deserialized model field is
# annotated — see data/models/CreativeDataModel.kt), so fields may be renamed
# by the consumer's R8 but must not be stripped. This is the standard Gson
# rule. The model classes themselves are referenced directly from SDK code,
# so R8 keeps them via normal tracing.
-keepclassmembers,allowobfuscation class com.adgeistkit.** {
    @com.google.gson.annotations.SerializedName <fields>;
}

# R8 full mode keeps no constructors implicitly — Gson instantiates models
# via Unsafe when no usable ctor survives; keep <init> as a cheap safety net.
-keepclassmembers class com.adgeistkit.data.models.** {
    <init>(...);
}

# Gson reads generic signatures (e.g. List<CreativeV1>), annotations, and
# inner-class metadata reflectively. R8 full mode strips these without rules.
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod

# WebView JS bridge: creative JS calls these by method name
# (window.Android.postMessage etc.), and WebView only exposes methods that
# still carry @JavascriptInterface at runtime.
-keepclassmembers class com.adgeistkit.ads.JsBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Inflated from consumer layout XML by fully-qualified class name. aapt2
# normally emits this keep automatically from the consumer's layouts; kept
# here as defense-in-depth for hosts that construct layouts dynamically.
-keep class com.adgeistkit.ads.AdView { <init>(...); }

# Future-proofing: no enum is Gson-mapped today, but if one lands in a model,
# Gson resolves it via values()/valueOf() reflectively.
-keepclassmembers enum com.adgeistkit.data.models.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
