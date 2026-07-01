# ============================================================================
# AdGeist SDK (ai.adgeist:adgeistkit) — consumer ProGuard/R8 rules.
# Bundled into the AAR via consumerProguardFiles; applied to the CONSUMER's
# R8 pass. The SDK ships unminified, so these rules are what protect SDK
# classes when the host app (e.g. PixelPlayer) runs R8 + resource shrinking.
# ============================================================================

# Keep every SDK class, its members, and its original names. This prevents R8
# in the consuming app from renaming Gson-mapped model fields (which have no
# @SerializedName) and from stripping the @JavascriptInterface WebView bridge.
-keep class com.adgeistkit.** { *; }

# TODO : add targeted skip instead of blanket disable of minification 

# Gson reads generic type info (TypeToken), annotations (@SerializedName on the
# CDP models), and inner-class relationships reflectively at runtime.
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod

# Enum values()/valueOf() are resolved reflectively by Gson (e.g. AdType).
-keepclassmembers enum com.adgeistkit.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
