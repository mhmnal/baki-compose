# kotlinx.serialization generates serializers; the plugin writes the keep rules, but be explicit.
-keepclassmembers class app.baki.** {
    *** Companion;
}
-keepclasseswithmembers class app.baki.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations

# Ktor / OkHttp internals reached reflectively
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }

# Play Services location
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**
