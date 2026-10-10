# App-specific R8 rules for release builds (on top of proguard-android-optimize.txt). The app
# needs no keep rules of its own: Room, Compose, and OkHttp ship theirs, and CI's release build
# catches anything missing.

# Keep line numbers so crash reports from release builds (e.g. in Play Console) point at real
# lines once deobfuscated with the build's mapping.txt, and name every source file "SourceFile"
# rather than shipping the real file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
