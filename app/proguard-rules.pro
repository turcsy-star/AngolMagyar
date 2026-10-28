# AngolMagyar – R8 szabályok (csak -Pminify buildnél hatnak)

# Natív (JNI) hidak: a metódusnevek és a callback-interfész nem nevezhető át
-keep class hu.angolmagyar.fordito.WhisperNative { *; }
-keep class hu.angolmagyar.fordito.WhisperNative$* { *; }
-keepclasseswithmembernames class * { native <methods>; }

# Vosk + JNA: reflexióval tölti a natív könyvtárat
-keep class com.sun.jna.** { *; }
-keep class org.vosk.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }
-dontwarn java.awt.**

# ONNX Runtime
-keep class ai.onnxruntime.** { *; }

# ML Kit, Play Services Wearable
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Room entitások és DAO-k
-keep class hu.angolmagyar.fordito.core.db.** { *; }

# Activity/Service osztályok a manifestből (R8 magától megőrzi, a biztonság kedvéért)
-keep class hu.angolmagyar.fordito.NightJobService { *; }
