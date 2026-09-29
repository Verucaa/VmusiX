# Wajib dari NewPipeExtractor (lihat 03_VERSION_MATRIX.md bagian 4)
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
# Rhino (JS engine bawaan extractor) referensi API desktop yang tidak ada di Android
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn org.mozilla.classfile.**
# Guava (transitif NewPipe) — R8 hanya butuh diam, consumer-rules bawaan yang jaga keep
-dontwarn com.google.common.**
