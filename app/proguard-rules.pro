# ══════════════════════════════════════════════════════════════════════════
# GHOST NEXORA VPN — ProGuard / R8 Rules
# Reglas de ofuscación para builds de release
# ══════════════════════════════════════════════════════════════════════════

# ── Opciones generales ────────────────────────────────────────────────────
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# ── Mantener información de debug útil (stack traces legibles) ────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keep class com.jcraft.jsch.** { *; }
-keep class com.jcraft.jsch.jce.** { *; }
-dontwarn com.jcraft.jsch.**
# ══════════════════════════════════════════════════════════════════════════
# KOTLIN
# ══════════════════════════════════════════════════════════════════════════

-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-keep class kotlinx.** { *; }
-dontwarn kotlin.**
-dontwarn kotlinx.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ══════════════════════════════════════════════════════════════════════════
# ANDROID — Componentes del sistema
# ══════════════════════════════════════════════════════════════════════════

-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# VpnService — CRÍTICO: no ofuscar
-keep class * extends android.net.VpnService { *; }
-keep class com.ghostnexora.vpn.service.GhostVpnService { *; }
-keep class com.ghostnexora.vpn.service.FloatingWindowService { *; }
-keep class com.ghostnexora.vpn.receiver.BootReceiver { *; }

# ── Views y constructores ─────────────────────────────────────────────────
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ── Parcelable ────────────────────────────────────────────────────────────
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# ── Serializable ──────────────────────────────────────────────────────────
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ══════════════════════════════════════════════════════════════════════════
# HILT — Inyección de dependencias
# ══════════════════════════════════════════════════════════════════════════

-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ActivityComponentManager { *; }
-keepnames @dagger.hilt.android.lifecycle.HiltViewModel class * extends androidx.lifecycle.ViewModel
-keep @dagger.hilt.android.AndroidEntryPoint class *

# ══════════════════════════════════════════════════════════════════════════
# ROOM — Base de datos
# ══════════════════════════════════════════════════════════════════════════

-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-keepclassmembers @androidx.room.Entity class * { *; }
-keepclassmembers @androidx.room.Dao interface * { *; }

# Modelos de Room — NO ofuscar
-keep class com.ghostnexora.vpn.data.model.** { *; }
-keep class com.ghostnexora.vpn.data.local.** { *; }

# ══════════════════════════════════════════════════════════════════════════
# GSON — Serialización JSON
# ══════════════════════════════════════════════════════════════════════════

-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# DTOs de JSON — NO ofuscar (usados en import/export)
-keep class com.ghostnexora.vpn.util.VpnProfileDocument { *; }
-keep class com.ghostnexora.vpn.util.VpnProfileJson { *; }
-keep class com.ghostnexora.vpn.util.ProxyJson { *; }

# ══════════════════════════════════════════════════════════════════════════
# JETPACK COMPOSE
# ══════════════════════════════════════════════════════════════════════════

-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ══════════════════════════════════════════════════════════════════════════
# DATASTORE
# ══════════════════════════════════════════════════════════════════════════

-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# ══════════════════════════════════════════════════════════════════════════
# VIEWMODEL Y LIFECYCLE
# ══════════════════════════════════════════════════════════════════════════

-keep class * extends androidx.lifecycle.ViewModel {
    <init>();
}
-keep class * extends androidx.lifecycle.AndroidViewModel {
    <init>(android.app.Application);
}
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <methods>;
}

# ══════════════════════════════════════════════════════════════════════════
# NAVIGATION COMPOSE
# ══════════════════════════════════════════════════════════════════════════

-keep class androidx.navigation.** { *; }
-dontwarn androidx.navigation.**

# ══════════════════════════════════════════════════════════════════════════
# APLICACIÓN PRINCIPAL
# ══════════════════════════════════════════════════════════════════════════

# Application class
-keep class com.ghostnexora.vpn.GhostNexoraApp { *; }

# MainActivity
-keep class com.ghostnexora.vpn.ui.MainActivity { *; }

# Enums — NO ofuscar (usados en Room y UI)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ══════════════════════════════════════════════════════════════════════════
# WARNINGS A IGNORAR
# ══════════════════════════════════════════════════════════════════════════

-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
-dontwarn sun.misc.**
-dontwarn java.lang.invoke.**
