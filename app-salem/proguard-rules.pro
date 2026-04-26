# =============================================================================
# WickedSalemWitchCityTour — ProGuard / R8 rules for release AAB
# Established S180 (2026-04-26) for v1.0.0 first paid Play Store release.
# =============================================================================

# Keep crash-report metadata so Play Console / OMEN can read stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# -----------------------------------------------------------------------------
# Kotlin metadata + coroutines + data classes
# -----------------------------------------------------------------------------
-keepclassmembers class kotlin.Metadata { *; }
-keep class kotlinx.coroutines.** { *; }
-keepnames class kotlin.coroutines.Continuation
-keepclassmembers class **$WhenMappings { <fields>; }

# -----------------------------------------------------------------------------
# Hilt / Dagger — dependency injection
# Hilt ships its own consumer ProGuard rules; this is a defensive backstop.
# -----------------------------------------------------------------------------
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.lifecycle.HiltViewModelFactory { *; }
-keep,allowobfuscation @dagger.hilt.android.HiltAndroidApp class *
-keepclassmembers class * { @javax.inject.Inject <init>(...); }

# -----------------------------------------------------------------------------
# Room — DAO interfaces + entities
# Room ships consumer rules; this preserves DAO method signatures Room relies on.
# -----------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *

# Salem content + userdata Room entities — keep all fields (Gson + Room reflect).
-keep class com.example.wickedsalemwitchcitytour.content.model.** { *; }
-keep class com.example.wickedsalemwitchcitytour.content.entity.** { *; }
-keep class com.example.wickedsalemwitchcitytour.userdata.db.** { *; }

# -----------------------------------------------------------------------------
# Gson — model classes deserialized via reflection
# Gson uses field names verbatim; obfuscation breaks JSON parsing.
# -----------------------------------------------------------------------------
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.example.locationmapapp.data.model.** { *; }
-keep class com.example.wickedsalemwitchcitytour.tour.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory { *; }
-keep class * implements com.google.gson.TypeAdapter { *; }

# -----------------------------------------------------------------------------
# osmdroid — TileSource subclasses + Overlay subclasses use reflection
# -----------------------------------------------------------------------------
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# -----------------------------------------------------------------------------
# JTS Topology Suite — used for polygon point-in-polygon
# Conservative keep; JTS uses some reflection in its operations.
# -----------------------------------------------------------------------------
-keep class org.locationtech.jts.** { *; }
-dontwarn org.locationtech.jts.**

# -----------------------------------------------------------------------------
# Lottie — splash screen animation
# -----------------------------------------------------------------------------
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# -----------------------------------------------------------------------------
# OkHttp / Retrofit / Socket.IO — KEPT in dependency graph but V1 never calls
# them (FeatureFlags.V1_OFFLINE_ONLY). Consumer rules cover obfuscation needs.
# -----------------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-dontwarn io.socket.**
-dontwarn org.codehaus.mojo.animal_sniffer.**

# -----------------------------------------------------------------------------
# Routing engine — :core + :routing-jvm bundle parsers + Dijkstra
# Pure Kotlin, no reflection — but keep public API to be safe.
# -----------------------------------------------------------------------------
-keep class com.example.locationmapapp.routing.** { *; }

# -----------------------------------------------------------------------------
# Salem Witch Trials — bundled content readers
# -----------------------------------------------------------------------------
-keep class com.example.wickedsalemwitchcitytour.witchtrials.** { *; }

# -----------------------------------------------------------------------------
# Quiet down KSP / Hilt generated code warnings
# -----------------------------------------------------------------------------
-dontwarn javax.annotation.**
-dontwarn javax.inject.**

# -----------------------------------------------------------------------------
# JVM-only deps leaking from :routing-jvm (sqlite-jdbc + its slf4j dep).
# Android uses Room/SQLite directly; the JDBC driver is only used by the
# parity test JVM module (JdbcRoutingBundleLoader). R8 sees these classes
# referenced from sqlite-jdbc bytecode but they are not called at runtime
# on Android. Per R8 missing_rules.txt suggestion.
# -----------------------------------------------------------------------------
-dontwarn java.sql.**
-dontwarn org.slf4j.**
-dontwarn org.xerial.**
-dontwarn org.sqlite.**
