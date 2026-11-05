-dontwarn org.w3c.dom.**
-dontwarn javax.xml.stream.**
-dontwarn java.awt.**
-dontwarn com.graphbuilder.**
-dontwarn org.osgi.**
-dontwarn aQute.**
-dontwarn org.slf4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.apache.commons.**
-dontwarn org.maplibre.**
-dontwarn com.mapbox.**

# MapLibre / 高德 原生回调
-keep class org.maplibre.android.** { *; }
-keep class com.mapbox.** { *; }
-keep class com.amap.api.** { *; }
-keep class com.loc.** { *; }
-keep interface com.amap.api.** { *; }

# 数据模型（Moshi / 序列化 / Room）
-keep class com.kunming.powerline.data.local.entity.** { *; }
-keep class com.kunming.powerline.data.remote.model.** { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class ** {
    @com.squareup.moshi.Json <fields>;
}
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking class kotlinx.serialization.** { *; }

# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# 反射 / 枚举
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepnames class * implements android.os.Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
