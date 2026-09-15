# ===================================================================
# ProGuard rules for OpenTelemetry
#
# These rules are essential for the agent-based auto-instrumentation
# and SDK to function correctly in a minified application.
# ===================================================================

# Keep all public and protected members of the OpenTelemetry API and SDK.
-keep public class io.opentelemetry.api.** { *; }
-keep public class io.opentelemetry.sdk.** { *; }

# The Android agent uses reflection and service loaders extensively.
-keep class io.opentelemetry.android.** { *; }
-keep class io.opentelemetry.android.instrumentation.** { *; }

# Keep context propagation and exporter classes.
-keep public class io.opentelemetry.context.** { *; }
-keep public class io.opentelemetry.exporter.otlp.** { *; }

# Keep annotation classes used by OpenTelemetry.
-keep @interface io.opentelemetry.instrumentation.annotations.WithSpan

# Keep classes that are used by the service provider mechanism (SPI).
# This is crucial for discovering instrumentations and exporters at runtime.
-keepnames class io.opentelemetry.sdk.autoconfigure.spi.**

-keepclassmembers class * implements io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider {
    public <init>();
}
-keepclassmembers class * implements io.opentelemetry.sdk.autoconfigure.spi.ConfigurableSpanExporterProvider {
    public <init>();
}
-keepclassmembers class * implements io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider {
    public <init>();
}


# ===================================================================
# ProGuard rules for OkHttp
#
# Required for OpenTelemetry's OkHttp instrumentation and general
# OkHttp usage with R8.
# ===================================================================

-dontwarn okhttp3.**
-dontwarn okio.**

# Our io.opentelemetry:opentelemetry-exporter-otlp includes multiple transports
# but we're only using HTTP (OtlpHttpLogRecordExporter).
-dontwarn com.fasterxml.jackson.databind.ObjectMapper
-dontwarn io.grpc.*
-dontwarn io.grpc.stub.ClientCalls

# Keep necessary parts of OkHttp that are accessed via reflection.
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }
-keep interface okio.** { *; }


# ===================================================================
# ProGuard rules for common dependencies (like Gson)
#
# Often used with Retrofit/OkHttp.
# ===================================================================

# Keep GSON specific classes needed for reflection.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# This generic rule keeps fields for classes that are serialized/deserialized by Gson.
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
