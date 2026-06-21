# Keep all resource identifiers intact so their names match the string replacements
-keepclassmembers class **.R$* {
    public static <fields>;
}

# Prevent optimization or renaming of any drawable/mipmap/string resources
-keep class **.R { *; }
