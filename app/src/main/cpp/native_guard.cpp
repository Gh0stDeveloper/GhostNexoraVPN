#include <jni.h>
#include <array>
#include <cstdint>

namespace {
constexpr std::array<std::uint8_t, 39> kDomain = {
    'G','h','o','s','t','N','e','x','o','r','a','V','P','N','|','G','N','X','2','|',
    's','e','c','u','r','e','-','c','o','n','f','i','g','|','v','2','0','2','6'
};
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_ghostnexora_vpn_security_NativeGuard_nativeDomainSeparator(
    JNIEnv* env,
    jclass
) {
    auto result = env->NewByteArray(static_cast<jsize>(kDomain.size()));
    if (result == nullptr) return nullptr;
    env->SetByteArrayRegion(
        result,
        0,
        static_cast<jsize>(kDomain.size()),
        reinterpret_cast<const jbyte*>(kDomain.data())
    );
    return result;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_ghostnexora_vpn_security_NativeGuard_nativeWipe(
    JNIEnv* env,
    jclass,
    jbyteArray buffer
) {
    if (buffer == nullptr) return;
    const jsize length = env->GetArrayLength(buffer);
    if (length <= 0) return;

    jboolean isCopy = JNI_FALSE;
    jbyte* data = env->GetByteArrayElements(buffer, &isCopy);
    if (data == nullptr) return;

    volatile jbyte* cursor = data;
    for (jsize i = 0; i < length; ++i) {
        cursor[i] = 0;
    }

    env->ReleaseByteArrayElements(buffer, data, 0);
}
