# Keep Android components declared in the manifest
-keep class com.example.entimate.EntimateApplication { *; }
-keep class com.example.entimate.MainActivity { *; }

# Keep Room entities and data models (safety; Room also generates its own rules)
-keep class com.example.entimate.data.local.** { *; }
-keep class com.example.entimate.data.model.** { *; }

-dontwarn org.jetbrains.**
-dontwarn kotlinx.coroutines.**
