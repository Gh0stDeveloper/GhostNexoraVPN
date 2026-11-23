#include <jni.h>

#include <array>
#include <cstdint>
#include <cstring>
#include <memory>
#include <new>
#include <string_view>

#include "bearssl.h"
#include "third_party/monocypher/monocypher.h"

extern "C" {
#include "fips202.h"
#include "kem.h"
}

uint8_t gnx4_wrapped_byte(std::size_t index);
uint8_t gnx4_mask_byte(std::size_t index);

namespace {

constexpr std::array<uint8_t, 4> magic{'G', 'N', 'X', '4'};
// This discriminator is authenticated as part of the header. The original
// GNX4 variant has a random nonce immediately after the same magic.
constexpr std::array<uint8_t, 16> multilayer{'G', 'N', 'X', '-', 'M', 'L', 'K', 'E',
                                           'M', '7', '6', '8', '-', 'X', '2', '5'};
constexpr jsize nonceSize = 24;
constexpr jsize aesNonceSize = 12;
constexpr jsize tagSize = 16;
constexpr jsize keySize = 32;
constexpr jsize coinsSize = 2 * keySize;
constexpr jsize kemSize = PQCLEAN_MLKEM768_CLEAN_CRYPTO_CIPHERTEXTBYTES;
constexpr jsize legacyHeaderSize = magic.size() + nonceSize;
constexpr jsize nonceOffset = magic.size() + multilayer.size();
constexpr jsize aesNonceOffset = nonceOffset + nonceSize;
constexpr jsize kemOffset = aesNonceOffset + aesNonceSize;
constexpr jsize publicOffset = kemOffset + kemSize;
constexpr jsize headerSize = publicOffset + keySize;
constexpr jsize overhead = headerSize + 2 * tagSize;
constexpr jsize randomSize = nonceSize + aesNonceSize + coinsSize;
constexpr jsize maxEnvelopeSize = 32 * 1024 * 1024;

template<typename T>
struct Sensitive {
    T value{};
    ~Sensitive() { crypto_wipe(&value, sizeof(value)); }
};

using Bytes32 = std::array<uint8_t, keySize>;

struct ApplicationKey {
    Sensitive<Bytes32> material;

    ApplicationKey() {
        for (std::size_t i = 0; i < material.value.size(); ++i) {
            material.value[i] = gnx4_wrapped_byte(i) ^ gnx4_mask_byte(i);
        }
    }
};

void deriveKey(Bytes32& output, const Bytes32& input, const Bytes32& salt, std::string_view info) {
    Sensitive<br_hkdf_context> hkdf;
    br_hkdf_init(&hkdf.value, &br_sha256_vtable, salt.data(), salt.size());
    br_hkdf_inject(&hkdf.value, input.data(), input.size());
    br_hkdf_flip(&hkdf.value);
    br_hkdf_produce(&hkdf.value, info.data(), info.size(), output.data(), output.size());
}

bool deriveLayerKeys(uint8_t* header, const uint8_t* coins, Bytes32& aes, Bytes32& xchacha) {
    const ApplicationKey root;
    Sensitive<std::array<uint8_t, 96>> expanded;
    shake256(expanded.value.data(), expanded.value.size(), root.material.value.data(), keySize);
    std::array<uint8_t, PQCLEAN_MLKEM768_CLEAN_CRYPTO_PUBLICKEYBYTES> kemPublic{};
    Sensitive<std::array<uint8_t, PQCLEAN_MLKEM768_CLEAN_CRYPTO_SECRETKEYBYTES>> kemPrivate;
    if (PQCLEAN_MLKEM768_CLEAN_crypto_kem_keypair_derand(
            kemPublic.data(), kemPrivate.value.data(), expanded.value.data()) != 0) {
        return false;
    }

    // X-Wing combiner input: ML-KEM secret, X25519 secret, ephemeral X25519
    // public key, recipient X25519 public key and the six-byte domain label.
    Sensitive<std::array<uint8_t, 4 * keySize + 6>> combined;
    auto* kemSecret = combined.value.data();
    auto* xSecret = kemSecret + keySize;
    auto* ephemeralPublic = xSecret + keySize;
    auto* recipientPublic = ephemeralPublic + keySize;
    const auto* recipientPrivate = expanded.value.data() + 2 * keySize;
    crypto_x25519_public_key(recipientPublic, recipientPrivate);

    if (coins != nullptr) {
        const auto* ephemeralPrivate = coins + keySize;
        if (PQCLEAN_MLKEM768_CLEAN_crypto_kem_enc_derand(
                header + kemOffset, kemSecret, kemPublic.data(), coins) != 0) {
            return false;
        }
        crypto_x25519_public_key(header + publicOffset, ephemeralPrivate);
        crypto_x25519(xSecret, ephemeralPrivate, recipientPublic);
    } else {
        if (PQCLEAN_MLKEM768_CLEAN_crypto_kem_dec(
                kemSecret, header + kemOffset, kemPrivate.value.data()) != 0) {
            return false;
        }
        crypto_x25519(xSecret, recipientPrivate, header + publicOffset);
    }

    constexpr Bytes32 zero{};
    if (crypto_verify32(xSecret, zero.data()) == 0) {
        return false;
    }
    std::memcpy(ephemeralPublic, header + publicOffset, keySize);
    constexpr std::array<uint8_t, 6> xWingLabel{0x5c, 0x2e, 0x2f, 0x2f, 0x5e, 0x5c};
    std::memcpy(recipientPublic + keySize, xWingLabel.data(), xWingLabel.size());

    Sensitive<Bytes32> shared;
    sha3_256(shared.value.data(), combined.value.data(), combined.value.size());
    Sensitive<br_sha256_context> hash;
    Bytes32 salt{};
    br_sha256_init(&hash.value);
    br_sha256_update(&hash.value, header, headerSize);
    br_sha256_out(&hash.value, salt.data());
    deriveKey(aes, shared.value, salt, "GhostNexoraVPN|GNX4|AES-256-GCM");
    deriveKey(xchacha, shared.value, salt, "GhostNexoraVPN|GNX4|XChaCha20-Poly1305");
    return true;
}

bool aesGcm(bool encrypt, const Bytes32& key, const uint8_t* header, uint8_t* tag,
            uint8_t* payload, jsize size) {
    Sensitive<br_aes_ct_ctr_keys> aes;
    Sensitive<br_gcm_context> gcm;
    br_aes_ct_ctr_init(&aes.value, key.data(), key.size());
    br_gcm_init(&gcm.value, &aes.value.vtable, br_ghash_ctmul32);
    br_gcm_reset(&gcm.value, header + aesNonceOffset, aesNonceSize);
    br_gcm_aad_inject(&gcm.value, header, headerSize);
    br_gcm_flip(&gcm.value);
    br_gcm_run(&gcm.value, encrypt, payload, size);
    if (encrypt) {
        br_gcm_get_tag(&gcm.value, tag);
        return true;
    }
    return br_gcm_check_tag(&gcm.value, tag) != 0;
}

jbyteArray outputBytes(JNIEnv* env, const uint8_t* bytes, jsize size) {
    jbyteArray result = env->NewByteArray(size);
    if (result != nullptr) {
        env->SetByteArrayRegion(result, 0, size, reinterpret_cast<const jbyte*>(bytes));
    }
    return result;
}

jbyteArray openLegacy(JNIEnv* env, uint8_t* buffer, jsize size) {
    auto* tag = buffer + legacyHeaderSize;
    auto* payload = tag + tagSize;
    const jsize payloadSize = size - legacyHeaderSize - tagSize;
    const ApplicationKey key;
    const int status = crypto_aead_unlock(payload, tag, key.material.value.data(),
                                          buffer + magic.size(), buffer, legacyHeaderSize,
                                          payload, payloadSize);
    jbyteArray result = status == 0 ? outputBytes(env, payload, payloadSize) : nullptr;
    crypto_wipe(payload, payloadSize);
    return result;
}

} // namespace

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_ghostnexora_vpn_security_NativeGuard_nativeSealGnx4(
    JNIEnv* env, jclass, jbyteArray input, jbyteArray randomness
) {
    if (input == nullptr || randomness == nullptr || env->GetArrayLength(randomness) != randomSize) {
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

    Sensitive<std::array<uint8_t, randomSize>> random;
    env->GetByteArrayRegion(randomness, 0, randomSize, reinterpret_cast<jbyte*>(random.value.data()));
    if (env->ExceptionCheck()) {
        return nullptr;
    }
    auto* header = buffer.get();
    std::memcpy(header, magic.data(), magic.size());
    std::memcpy(header + magic.size(), multilayer.data(), multilayer.size());
    std::memcpy(header + nonceOffset, random.value.data(), nonceSize + aesNonceSize);
    Sensitive<Bytes32> aes;
    Sensitive<Bytes32> xchacha;
    if (!deriveLayerKeys(header, random.value.data() + nonceSize + aesNonceSize,
                         aes.value, xchacha.value)) {
        return nullptr;
    }

    auto* xTag = header + headerSize;
    auto* aesTag = xTag + tagSize;
    auto* payload = aesTag + tagSize;
    env->GetByteArrayRegion(input, 0, size, reinterpret_cast<jbyte*>(payload));
    if (env->ExceptionCheck()) {
        crypto_wipe(payload, size);
        return nullptr;
    }
    aesGcm(true, aes.value, header, aesTag, payload, size);
    crypto_aead_lock(aesTag, xTag, xchacha.value.data(), header + nonceOffset,
                     header, headerSize, aesTag, size + tagSize);
    return outputBytes(env, header, size + overhead);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_ghostnexora_vpn_security_NativeGuard_nativeOpenGnx4(
    JNIEnv* env, jclass, jbyteArray input
) {
    if (input == nullptr) {
        return nullptr;
    }
    const jsize size = env->GetArrayLength(input);
    if (size <= legacyHeaderSize + tagSize || size > maxEnvelopeSize) {
        return nullptr;
    }
    std::unique_ptr<uint8_t[]> buffer(new (std::nothrow) uint8_t[size]);
    if (!buffer) {
        return nullptr;
    }
    auto* header = buffer.get();
    env->GetByteArrayRegion(input, 0, size, reinterpret_cast<jbyte*>(header));
    if (env->ExceptionCheck() || std::memcmp(header, magic.data(), magic.size()) != 0) {
        return nullptr;
    }
    if (std::memcmp(header + magic.size(), multilayer.data(), multilayer.size()) != 0) {
        return openLegacy(env, header, size);
    }
    if (size <= overhead) {
        return nullptr;
    }

    Sensitive<Bytes32> aes;
    Sensitive<Bytes32> xchacha;
    if (!deriveLayerKeys(header, nullptr, aes.value, xchacha.value)) {
        return nullptr;
    }
    auto* xTag = header + headerSize;
    auto* aesTag = xTag + tagSize;
    auto* payload = aesTag + tagSize;
    const jsize payloadSize = size - overhead;
    const int status = crypto_aead_unlock(aesTag, xTag, xchacha.value.data(),
                                          header + nonceOffset, header, headerSize,
                                          aesTag, payloadSize + tagSize);
    jbyteArray result = nullptr;
    if (status == 0 && aesGcm(false, aes.value, header, aesTag, payload, payloadSize)) {
        result = outputBytes(env, payload, payloadSize);
    }
    crypto_wipe(aesTag, payloadSize + tagSize);
    return result;
}
