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
    private static native byte[] nativeGnx3KeyFragment();
    private static native boolean nativeRuntimeCompromised();
}
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Gson DTO field names are part of the legacy import format.
-keep class com.ghostnexora.vpn.util.VpnProfileDocument { <fields>; }
-keep class com.ghostnexora.vpn.util.VpnProfileJson { <fields>; }
-keep class com.ghostnexora.vpn.util.ProxyJson { <fields>; }
-keep class com.ghostnexora.vpn.util.Gnx3ProfileDocument { <fields>; }
# The local locked-profile envelope must remain readable after an app update.
# Field names stay stable while the classes themselves may still be obfuscated.
-keepclassmembers,allowoptimization class com.ghostnexora.vpn.data.model.VpnProfile { <fields>; }
-keepclassmembers,allowoptimization class com.ghostnexora.vpn.data.model.ProxyConfig { <fields>; }

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Stable support classes: diagnostics, support reports and CI inspect these names directly.
-keep class com.ghostnexora.vpn.diagnostics.ConnectionDiagnosticsEngine { *; }
-keep class com.ghostnexora.vpn.tunnel.ConnectionErrorCatalog { *; }
-keep class com.ghostnexora.vpn.tunnel.TlsTransport { *; }
-keep class com.ghostnexora.vpn.tunnel.VpnFailure { *; }
-keep class com.ghostnexora.vpn.data.model.AppRoutingPreferences { *; }
-keep class com.ghostnexora.vpn.data.model.TlsVerificationMode { *; }
-keep class com.ghostnexora.vpn.util.PayloadEngine { *; }
-keep class com.ghostnexora.vpn.util.ProtocolLinkParser { *; }
# Retain these runtime paths but permit R8 to optimize and rename them. CI
# checks both survival and actual obfuscation in mapping.txt.
-keep,allowoptimization,allowobfuscation class com.ghostnexora.vpn.security.Gnx3ConfigCodec { *; }
-keep,allowoptimization,allowobfuscation class com.ghostnexora.vpn.security.LockedProfileVault { *; }
-keep,allowoptimization,allowobfuscation class com.ghostnexora.vpn.security.HtmlNoteSanitizer { *; }
-keep,allowoptimization,allowobfuscation class com.ghostnexora.vpn.security.AppManagedConfigKeyProvider { *; }

# JSch crypto providers and the application-owned direct injection bridge.
-keep class com.ghostnexora.vpn.tunnel.AndroidSecureRandomProvider { public <init>(); public *; }
-keep class com.ghostnexora.vpn.tunnel.JschRuntime { public *; }
-keep class com.jcraft.jsch.AndroidRandomBridge { public *; }
# JSch carga KEX, cifrados, MAC y autenticadores por Class.forName desde su
# tabla de configuración. Conservar solo los nombres permite que R8 elimine la
# clase completa; el Release debe retener las implementaciones y sus miembros.
-keep class com.jcraft.jsch.** { *; }
-keep class com.jcraft.jsch.jce.** { *; }
-keep class com.jcraft.jsch.jcraft.** { *; }
-keep class com.jcraft.jsch.jgss.** { *; }
-keepnames class com.jcraft.jsch.**
-dontwarn com.jcraft.jsch.**

-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
