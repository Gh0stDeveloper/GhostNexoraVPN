# AndroidLibXrayLite / gomobile bridge
# Estas clases son alcanzadas desde JNI/Go y no deben eliminarse o renombrarse
# en el build release aunque R8 no detecte todas las llamadas nativas.
-keep class go.** { *; }
-keep class libv2ray.** { *; }
-keep interface libv2ray.** { *; }
-dontwarn go.**
-dontwarn libv2ray.**
