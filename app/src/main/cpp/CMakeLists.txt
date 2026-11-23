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
