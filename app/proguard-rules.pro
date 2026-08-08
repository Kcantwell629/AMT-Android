# Add project specific ProGuard rules here.
# The app ships with minification disabled by default (see app/build.gradle.kts),
# so this file only matters if you later flip isMinifyEnabled to true.

# Keep the JavaScript bridge methods callable from the WebView.
-keepclassmembers class com.amtandroid.quotebuilder.MainActivity$AndroidBridge {
    public *;
}
