# Ghost Nexora VPN — focused R8 rules

-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# Android components referenced from the manifest.
-keep class com.ghostnexora.vpn.GhostNexoraApp { <init>(); }
-keep class com.ghostnexora.vpn.ui.MainActivity { <init>(); }
-keep class com.ghostnexora.vpn.service.GhostVpnService { <init>(); }
-keep class com.ghostnexora.vpn.service.FloatingWindowService { <init>(); }
-keep class com.ghostnexora.vpn.receiver.BootReceiver { <init>(); }

# JNI symbols encode class and method names.
-keep class com.ghostnexora.vpn.security.NativeGuard {
    private static native byte[] nativeDomainSeparator();
    private static native void nativeWipe(byte[]);
}
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Gson DTO field names are part of the legacy import format.
-keep class com.ghostnexora.vpn.util.VpnProfileDocument { <fields>; }
-keep class com.ghostnexora.vpn.util.VpnProfileJson { <fields>; }
-keep class com.ghostnexora.vpn.util.ProxyJson { <fields>; }

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# JSch crypto providers and the application-owned direct injection bridge.
-keep class com.ghostnexora.vpn.tunnel.AndroidSecureRandomProvider { public <init>(); public *; }
-keep class com.ghostnexora.vpn.tunnel.JschRuntime { public *; }
-keep class com.jcraft.jsch.AndroidRandomBridge { public *; }
-keep class com.jcraft.jsch.jce.** { *; }
-keep class com.jcraft.jsch.jcraft.** { *; }
-keep class com.jcraft.jsch.jgss.** { *; }
-keepnames class com.jcraft.jsch.**
-dontwarn com.jcraft.jsch.**

-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
