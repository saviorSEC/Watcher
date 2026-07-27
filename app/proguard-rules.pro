# Keep TFLite models
-keep class org.tensorflow.lite.** { *; }

# Keep ML Kit
-keep class com.google.mlkit.** { *; }

# Keep data classes for Gson
-keep class com.watcher.app.models.** { *; }
-keep class com.watcher.app.detection.** { *; }
-keep class com.watcher.app.testing.** { *; }
