# Protobuf generated code is reflected upon by the runtime.
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**

# 腾讯 Bugly 崩溃监控
-dontwarn com.tencent.bugly.**
-keep public class com.tencent.bugly.**{*;}
