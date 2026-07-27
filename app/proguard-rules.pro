# Ghost Nexora VPN — R8 rules
# Mantener estas reglas deliberadamente pequeñas: reglas demasiado amplias
# reducen la ofuscación y evitan que R8 elimine código no usado.

-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# Componentes Android se referencian desde el manifest.
-keep class com.ghostnexora.vpn.GhostNexoraApp { <init>(); }
-keep class com.ghostnexora.vpn.ui.MainActivity { <init>(); }
-keep class com.ghostnexora.vpn.service.GhostVpnService { <init>(); }
-keep class com.ghostnexora.vpn.service.FloatingWindowService { <init>(); }
-keep class com.ghostnexora.vpn.receiver.BootReceiver { <init>(); }

# JNI: los símbolos exportados codifican nombre de clase y método.
-keep class com.ghostnexora.vpn.security.NativeGuard {
    private static native byte[] nativeDomainSeparator();
    private static native void nativeWipe(byte[]);
}
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# DTOs Gson usados para compatibilidad con JSON legado. Conservan nombres de
# campos externos, pero el resto de la aplicación puede ser renombrado por R8.
-keep class com.ghostnexora.vpn.util.VpnProfileDocument { <fields>; }
-keep class com.ghostnexora.vpn.util.VpnProfileJson { <fields>; }
-keep class com.ghostnexora.vpn.util.ProxyJson { <fields>; }

# Room/Hilt/KSP generan referencias directas; solo conservamos metadatos y los
# nombres de enum serializados explícitamente por la aplicación.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# JSch registra implementaciones criptográficas mediante nombres de clase y
# Class.forName(). `-keepnames` no es suficiente: R8 puede conservar el nombre
# pero eliminar por completo una implementación que no ve referenciada.
# Mantener las implementaciones reflectivas evita ClassNotFoundException como
# com.jcraft.jsch.jce.Random en APK Release minificados.
-keep class com.jcraft.jsch.jce.** { *; }
-keep class com.jcraft.jsch.jcraft.** { *; }
-keep class com.jcraft.jsch.jgss.** { *; }
-keepnames class com.jcraft.jsch.**
-dontwarn com.jcraft.jsch.**

# Bouncy Castle/implementaciones TLS opcionales.
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**