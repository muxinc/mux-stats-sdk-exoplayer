# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/mattward/Library/Android/sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:
-printmapping mapping.txt

-dontwarn com.google.ads.**

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

-keep public class com.mux.** {
  public * ;
  protected * ;
  interface * ;
}

-repackageclasses com.mux.stats.sdk

-keepattributes Exceptions,Deprecated,*Annotation*,Signature,InnerClasses,EnclosingMethod

-keeppackagenames com.mux.**

-dontoptimize