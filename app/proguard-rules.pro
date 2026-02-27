# OSMDroid
-keep class org.osmdroid.** { *; }
# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
# Hilt
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
# Gson
-keep class com.google.gson.** { *; }
-keepattributes Signature
