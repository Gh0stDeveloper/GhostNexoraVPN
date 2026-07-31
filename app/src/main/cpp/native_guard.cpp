#include <jni.h>
#include <algorithm>
#include <array>
#include <cctype>
#include <cstdint>
#include <fstream>
#include <string>

namespace {
constexpr std::array<std::uint8_t, 39> kDomain = {
    'G','h','o','s','t','N','e','x','o','r','a','V','P','N','|','G','N','X','2','|',
    's','e','c','u','r','e','-','c','o','n','f','i','g','|','v','2','0','2','6'
};

constexpr std::array<std::uint8_t, 32> kGnx3Fragment = {
    0x6f, 0x05, 0x50, 0x38, 0x7d, 0x11, 0x64, 0x2e,
    0x17, 0x6a, 0x08, 0x5d, 0x3c, 0x21, 0x73, 0x49,
    0x0f, 0x62, 0x55, 0x14, 0x7b, 0x28, 0x03, 0x6e,
    0x19, 0x45, 0x2a, 0x72, 0x59, 0x0c, 0x67, 0x31
};

bool tracerAttached() {
    std::ifstream status("/proc/self/status");
    std::string line;
    while (std::getline(status, line)) {
        if (line.rfind("TracerPid:", 0) != 0) continue;
        const auto value = line.substr(line.find(':') + 1);
        return value.find_first_not_of(" \t0") != std::string::npos;
    }
    return false;
}

bool suspiciousMapLoaded() {
    std::ifstream maps("/proc/self/maps");
    std::string line;
    constexpr std::array<const char*, 8> indicators = {
        "frida", "gum-js-loop", "gmain", "xposed", "substrate",
        "libhooker", "riru", "zygisk"
    };
    while (std::getline(maps, line)) {
        std::transform(
            line.begin(),
            line.end(),
            line.begin(),
            [](unsigned char value) { return static_cast<char>(std::tolower(value)); }
        );
        for (const auto* indicator : indicators) {
            if (line.find(indicator) != std::string::npos) return true;
        }
    }
    return false;
}
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

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_ghostnexora_vpn_security_NativeGuard_nativeGnx3KeyFragment(
    JNIEnv* env,
    jclass
) {
    auto result = env->NewByteArray(static_cast<jsize>(kGnx3Fragment.size()));
    if (result == nullptr) return nullptr;
    env->SetByteArrayRegion(
        result,
        0,
        static_cast<jsize>(kGnx3Fragment.size()),
        reinterpret_cast<const jbyte*>(kGnx3Fragment.data())
    );
    return result;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_ghostnexora_vpn_security_NativeGuard_nativeRuntimeCompromised(
    JNIEnv*,
    jclass
) {
    return (tracerAttached() || suspiciousMapLoaded()) ? JNI_TRUE : JNI_FALSE;
}
