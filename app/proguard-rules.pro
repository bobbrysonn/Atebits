# Readable release stack traces
-keepattributes SourceFile,LineNumberTable

# --- kotlinx.serialization ---
# The Retrofit converter resolves serializers reflectively by Type at runtime,
# so the generated serializer plumbing for our models must survive R8.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers @kotlinx.serialization.Serializable class dev.bobbrysonn.atebits.** {
    *** Companion;
}
-keepclasseswithmembers class dev.bobbrysonn.atebits.**$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class dev.bobbrysonn.atebits.**$$serializer { *; }

# --- Retrofit (R8 full-mode rules from the Retrofit README) ---
# Suspend API methods return @Serializable types via generic signatures.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepclassmembers,allowshrinking,allowobfuscation interface dev.bobbrysonn.atebits.network.HomeTimelineApi {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
