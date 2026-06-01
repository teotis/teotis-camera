# Keep application entry points and reflective Android framework hooks.
-keep class com.opencamera.app.OpenCameraApplication { *; }
-keep class com.opencamera.app.MainActivity { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature
