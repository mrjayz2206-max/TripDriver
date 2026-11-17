# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepclassmembers class * extends android.app.Service {
    public *;
}
