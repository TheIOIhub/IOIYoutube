# NewPipe Extractor — рефлексiя для сервiсiв
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**

# NewPipe Extractor / Rhino: java.beans + javax.script are not on the Android
# classpath; they are only reflectively referenced by the bundled JS engine.
-dontwarn java.beans.**
-dontwarn javax.script.**

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# OkHttp + Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
