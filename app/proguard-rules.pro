# Keep application entry points and reflective Android framework hooks.
-keep class com.opencamera.app.OpenCameraApplication { *; }
-keep class com.opencamera.app.MainActivity { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature

# CameraX and lifecycle components use generated adapters and reflective hooks.
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**
-keep class androidx.lifecycle.** { *; }
-dontwarn androidx.lifecycle.**

# ML Kit loads native and generated model classes at runtime.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.internal.mlkit_vision_**

# Preserve coroutine metadata used across Android/Kotlin optimized builds.
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**
