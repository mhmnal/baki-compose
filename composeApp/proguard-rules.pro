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

# Desktop PDFBox has optional BouncyCastle/JAXB/color-management paths; the app only extracts text.
-dontwarn org.apache.pdfbox.**
-dontwarn org.bouncycastle.**
-dontwarn javax.xml.bind.**
-dontwarn sun.java2d.cmm.**

# WorkManager opens its Room database reflectively; R8 must keep the generated implementation.
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**
