# Keep SQLCipher native bridge classes from being obfuscated.
# SQLCipher uses JNI to call into libsqlcipher.so; obfuscating class names
# will break native lookups.
-keep,includedescriptorclasses class net.zetetic.** { *; }
-keep,includedescriptorclasses interface net.zetetic.** { *; }