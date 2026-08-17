# Keep the JNI bridge symbols; the Rust library looks these up by exact name.
-keepclasseswithmembernames class com.bugenzhao.mnga.LogicKt {
    native <methods>;
}
-keep class com.bugenzhao.mnga.** { *; }
