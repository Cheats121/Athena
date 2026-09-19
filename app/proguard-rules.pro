# =============================================================
# ATHENA PROGUARD / R8 RULES
# =============================================================

# Athena primarily uses Android framework classes, AndroidX,
# JSONObject / JSONArray, and standard cryptographic APIs.
#
# Most dependencies provide their own consumer R8 rules,
# so broad package-level keep rules are intentionally avoided.


# =============================================================
# DEBUGGING INFORMATION
# =============================================================

# Preserve line numbers for useful release crash traces.
-keepattributes SourceFile,LineNumberTable

# Hide original source filenames while retaining line numbers.
-renamesourcefileattribute SourceFile


# =============================================================
# GENERIC ATTRIBUTES
# =============================================================

# Preserve generic type information when libraries require it.
-keepattributes Signature

# Preserve runtime annotations used by Android/framework libraries.
-keepattributes *Annotation*


# =============================================================
# ENUMS
# =============================================================

# Preserve enum helper methods.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}