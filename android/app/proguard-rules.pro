# Keep kotlinx.serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class top.lvbyte.studio.**$$serializer { *; }
-keepclassmembers class top.lvbyte.studio.** { *** Companion; }
-keepclasseswithmembers class top.lvbyte.studio.** { kotlinx.serialization.KSerializer serializer(...); }
