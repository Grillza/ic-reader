# kotlinx.serialization: keep generated serializers for @Serializable models.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class io.github.grillza.icreader.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
