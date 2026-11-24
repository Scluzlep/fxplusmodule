# libxposed API 102 & stability-first R8 dead-code-removal rules
-dontobfuscate
-dontoptimize
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
-dontwarn io.github.libxposed.**
-adaptresourcefilecontents META-INF/xposed/java_init.list

-keep class com.scluzlep.fxplusmodule.** { *; }
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
