# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Test-harness only: AndroidJUnitRunner executes inside this minified app's
# process and resolves androidx.tracing.Trace at runtime. The app itself never
# references it, so R8 strips it and every release instrumented test dies with
# NoClassDefFoundError before running. (androidx.tracing is already on the
# runtime classpath transitively via lifecycle-runtime.)
-keep class androidx.tracing.** { *; }

# Test-harness only, same mechanism as above: the androidTest APK is compiled
# against kotlin-stdlib but AGP dedups it out of the test APK because this app
# already ships it — minified. Keep the stdlib by its original names so test
# code (kotlin.LazyKt etc.) resolves. Publishers never need this; it exists so
# release instrumented tests can run at all.
-keep class kotlin.** { *; }
-dontwarn kotlin.**

# Test-harness only, same mechanism: AdModelR8Test calls Gson directly from
# the test APK but runs against this app's minified copy of Gson. Keep Gson's
# API so the test's fromJson calls resolve. Does not affect what we're really
# testing — Gson still reflects over the R8-processed SDK model classes.
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**

# Test-harness only: the instrumented tests call these SDK members from the
# test APK. A real consumer's R8 keeps what it calls via normal tracing, but
# the app's R8 pass can't see test code, so unreferenced members (data-class
# getters, AdgeistCore.initialize) get stripped and the tests die with
# NoSuchMethodError. These keeps stand in for that tracing; the SDK's own
# consumer-rules.pro stays minimal and is what's actually under test.
-keep class com.adgeistkit.AdgeistCore { *; }
-keep class com.adgeistkit.AdgeistCore$Companion { *; }
-keepclassmembers class com.adgeistkit.data.models.** { <methods>; }
# JsBridgeR8Test constructs the bridge directly; keep the ctor signature
# stable so the test APK's call resolves (SDK-internal callers don't need it).
-keepclassmembers class com.adgeistkit.ads.JsBridge { <init>(...); }