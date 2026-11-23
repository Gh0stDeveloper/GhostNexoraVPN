cmake_minimum_required(VERSION 3.22.1)
project(ghostguard LANGUAGES C CXX)

# Public shares are for development. Signed builds pass a private generated directory.
set(GNX4_KEY_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}" CACHE PATH "GNX4 key sources")

add_library(
    ghostguard
    SHARED
    native_guard.cpp
    gnx4_envelope.cpp
    "${GNX4_KEY_SOURCE_DIR}/gnx4_key_wrapped.cpp"
    "${GNX4_KEY_SOURCE_DIR}/gnx4_key_mask.cpp"
    # Monocypher 4.0.2, upstream tag 0d85f98c9d9b0227e42cf795cb527dff372b40a4.
    third_party/monocypher/monocypher.c
    # BearSSL 0.6, AES-GCM and HKDF-SHA256 subset.
    third_party/bearssl/src/aead/gcm.c
    third_party/bearssl/src/codec/enc32be.c
    third_party/bearssl/src/codec/dec32be.c
    third_party/bearssl/src/hash/ghash_ctmul32.c
    third_party/bearssl/src/hash/sha2small.c
    third_party/bearssl/src/kdf/hkdf.c
    third_party/bearssl/src/mac/hmac.c
    third_party/bearssl/src/symcipher/aes_ct.c
    third_party/bearssl/src/symcipher/aes_ct_ctr.c
    third_party/bearssl/src/symcipher/aes_ct_enc.c
    # PQClean 0586a824fc0d49df0b6b6e9179d8d15d06d0974f, portable ML-KEM-768.
    third_party/pqclean/common/fips202.c
    third_party/pqclean/common/randombytes.c
    third_party/pqclean/mlkem768/cbd.c
    third_party/pqclean/mlkem768/indcpa.c
    third_party/pqclean/mlkem768/kem.c
    third_party/pqclean/mlkem768/ntt.c
    third_party/pqclean/mlkem768/poly.c
    third_party/pqclean/mlkem768/polyvec.c
    third_party/pqclean/mlkem768/reduce.c
    third_party/pqclean/mlkem768/symmetric-shake.c
    third_party/pqclean/mlkem768/verify.c
)

target_include_directories(ghostguard PRIVATE
    third_party/bearssl/inc
    third_party/bearssl/src
    third_party/pqclean/common
    third_party/pqclean/mlkem768
)

target_compile_features(ghostguard PRIVATE cxx_std_20)
target_compile_options(
    ghostguard
    PRIVATE
    -fvisibility=hidden
    -fdata-sections
    -ffunction-sections
    -fstack-protector-strong
)

target_link_options(
    ghostguard
    PRIVATE
    -Wl,--gc-sections
    -Wl,-z,relro
    -Wl,-z,now
)
