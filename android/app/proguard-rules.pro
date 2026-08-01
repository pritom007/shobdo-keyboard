# Keep Compose-generated code paths (usually handled by default rules, but
# explicit for clarity).
-keep class androidx.compose.** { *; }

# Strip debug/verbose logs in release. Content must never leak via logs (§26).
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}
