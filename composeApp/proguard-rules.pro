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

# PDFBox references optional JPEG-2000 decoding that the app never uses (text extraction only)
-dontwarn com.gemalto.jp2.**
-dontwarn com.tom_roush.pdfbox.filter.JPXFilter
