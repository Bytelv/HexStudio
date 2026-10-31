# Keep kotlinx.serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class top.lvbyte.hexstudio.**$$serializer { *; }
-keepclassmembers class top.lvbyte.hexstudio.** { *** Companion; }
-keepclasseswithmembers class top.lvbyte.hexstudio.** { kotlinx.serialization.KSerializer serializer(...); }

# security-crypto / Tink 的编译期注解，运行时不需要
-dontwarn com.google.errorprone.annotations.**
