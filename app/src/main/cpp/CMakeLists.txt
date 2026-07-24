cmake_minimum_required(VERSION 3.22.1)
project(ghostguard LANGUAGES CXX)

add_library(
    ghostguard
    SHARED
    native_guard.cpp
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
