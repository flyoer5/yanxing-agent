# Hilt（注入生成的类不能被裁剪）
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }
-keepclasseswithmembernames class * { @dagger.* <fields>; }
-keepclasseswithmembernames class * { @javax.inject.* <fields>; }
-keep class com.yanxing.agent.YanxingApplication { *; }
-keep class com.yanxing.agent.MainActivity { *; }

# Room（实体与数据库实现按名称反射）
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep class * implements androidx.room.Dao { *; }

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault

# kotlinx.serialization（@Serializable 类的序列化器）
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.yanxing.agent.**$$serializer { *; }
-keepclassmembers class com.yanxing.agent.** { *** Companion; }
-keepclasseswithmembers class com.yanxing.agent.** { kotlinx.serialization.KSerializer serializer(...); }

# Coroutines
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# 无障碍服务由系统按 Manifest 类名反射实例化
-keep class com.yanxing.agent.service.ScreenReaderAccessibilityService { *; }
-keep class com.yanxing.agent.service.FloatingWindowService { *; }
