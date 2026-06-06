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

# Remove unused resources
-keep class com.webscare.urducanvas.** { *; }
-dontwarn com.webscare.urducanvas.**


# Shrink unused code for bitmap handling
-keep class android.graphics.Bitmap { *; }
-keep class android.graphics.drawable.** { *; }

# Keep the custom view class
-keep class com.webscare.urducanvas.common.views.GradientBarView { *; }

# Inline constants for performance
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int i(...);
}

# Keep classes and methods that use reflection
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep all custom views and UI-related optimization classes
-keep class com.webscare.urducanvas.common.views.** { *; }

# Keep model fields for Gson
-keepclassmembers class com.webscare.urducanvas.common.canvas.model.** {
    <fields>;
}

# Keep enum names
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep class com.webscare.urducanvas.common.canvas.model.** { *; }
-keep class ** { *; }
# Keep annotations and generic info
-keepattributes Signature
-keepattributes *Annotation*

-keepattributes SourceFile,LineNumberTable