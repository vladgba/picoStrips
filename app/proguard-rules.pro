# zxing-cpp: JNI creates/reads these classes by name
-keep class zxingcpp.** { *; }
-dontwarn androidx.camera.**

# Honeywell DataCollection.jar: talks to the scanner service over AIDL / JSON-RPC by class name
-keep class com.honeywell.** { *; }
-dontwarn com.honeywell.**
