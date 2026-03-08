# Firebase Functions specific
-keep class com.google.firebase.functions.** { *; }
-dontwarn com.google.firebase.functions.**

# Keep data classes for Firebase Functions
-keepclassmembers class * {
    @com.google.firebase.database.PropertyName <fields>;
    @com.google.firebase.database.PropertyName <methods>;
}

# Additional Firebase security
-keep class com.google.firebase.auth.** { *; }
-keep class com.google.firebase.database.** { *; }
-keep class com.google.firebase.remoteconfig.** { *; }

# TAMBAHAN: Keep classes yang diperlukan untuk komunikasi server
-keep class com.ibypass.tvku.data.** { *; }
-keep class com.ibypass.tvku.model.** { *; }