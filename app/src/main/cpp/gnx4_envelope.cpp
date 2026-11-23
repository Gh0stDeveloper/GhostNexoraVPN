#include <jni.h>

#include <array>
#include <cstdint>
#include <cstring>
#include <memory>
#include <new>

#include "third_party/monocypher/monocypher.h"

uint8_t gnx4_wrapped_byte(std::size_t index);
uint8_t gnx4_mask_byte(std::size_t index);

namespace {

constexpr std::array<uint8_t, 4> magic{'G', 'N', 'X', '4'};
constexpr jsize nonceSize = 24;
constexpr jsize tagSize = 16;
constexpr jsize headerSize = magic.size() + nonceSize;
constexpr jsize overhead = headerSize + tagSize;
constexpr jsize maxEnvelopeSize = 32 * 1024 * 1024;

struct Key {
    std::array<uint8_t, 32> bytes{};

    Key() {
        for (std::size_t i = 0; i < bytes.size(); ++i) {
            bytes[i] = gnx4_wrapped_byte(i) ^ gnx4_mask_byte(i);
        }
    }

    ~Key() { crypto_wipe(bytes.data(), bytes.size()); }
};

jbyteArray outputBytes(JNIEnv* env, const uint8_t* bytes, jsize size) {
    jbyteArray result = env->NewByteArray(size);
    if (result != nullptr) {
        env->SetByteArrayRegion(result, 0, size, reinterpret_cast<const jbyte*>(bytes));
    }
    return result;
}

} // namespace

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_ghostnexora_vpn_security_NativeGuard_nativeSealGnx4(
    JNIEnv* env, jclass, jbyteArray input, jbyteArray nonce
) {
    if (input == nullptr || nonce == nullptr || env->GetArrayLength(nonce) != nonceSize) {
        return nullptr;
    }
    const jsize size = env->GetArrayLength(input);
    if (size <= 0 || size > maxEnvelopeSize - overhead) {
        return nullptr;
    }
    std::unique_ptr<uint8_t[]> buffer(new (std::nothrow) uint8_t[size + overhead]);
    if (!buffer) {
        return nullptr;
    }

    std::memcpy(buffer.get(), magic.data(), magic.size());
    env->GetByteArrayRegion(nonce, 0, nonceSize,
                            reinterpret_cast<jbyte*>(buffer.get() + magic.size()));
    auto* tag = buffer.get() + headerSize;
    auto* payload = tag + tagSize;
    env->GetByteArrayRegion(input, 0, size, reinterpret_cast<jbyte*>(payload));
    if (env->ExceptionCheck()) {
        crypto_wipe(buffer.get(), size + overhead);
        return nullptr;
    }
    {
        const Key key;
        crypto_aead_lock(payload, tag, key.bytes.data(), buffer.get() + magic.size(),
                         buffer.get(), headerSize, payload, size);
    }
    return outputBytes(env, buffer.get(), size + overhead);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_ghostnexora_vpn_security_NativeGuard_nativeOpenGnx4(
    JNIEnv* env, jclass, jbyteArray input
) {
    if (input == nullptr) {
        return nullptr;
    }
    const jsize size = env->GetArrayLength(input);
    if (size <= overhead || size > maxEnvelopeSize) {
        return nullptr;
    }
    std::unique_ptr<uint8_t[]> buffer(new (std::nothrow) uint8_t[size]);
    if (!buffer) {
        return nullptr;
    }
    env->GetByteArrayRegion(input, 0, size, reinterpret_cast<jbyte*>(buffer.get()));
    if (env->ExceptionCheck() || std::memcmp(buffer.get(), magic.data(), magic.size()) != 0) {
        return nullptr;
    }

    const auto* tag = buffer.get() + headerSize;
    auto* payload = buffer.get() + overhead;
    const jsize payloadSize = size - overhead;
    int status;
    {
        const Key key;
        status = crypto_aead_unlock(payload, tag, key.bytes.data(), buffer.get() + magic.size(),
                                    buffer.get(), headerSize, payload, payloadSize);
    }
    jbyteArray result = status == 0 ? outputBytes(env, payload, payloadSize) : nullptr;
    crypto_wipe(payload, payloadSize);
    return result;
}
