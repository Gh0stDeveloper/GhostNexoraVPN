#include <cstddef>
#include <cstdint>

namespace {
[[gnu::section(".gnx.a0"), gnu::used]]
const uint8_t share0[8] = {0xb9, 0xf5, 0xf4, 0x15, 0xae, 0xb4, 0xc9, 0x81};

[[gnu::section(".gnx.a1"), gnu::used]]
const uint8_t share1[8] = {0xa6, 0xda, 0x18, 0xed, 0x20, 0xba, 0xf1, 0xb7};

[[gnu::section(".gnx.a2"), gnu::used]]
const uint8_t share2[8] = {0x24, 0x7e, 0xb3, 0x93, 0xba, 0xdd, 0x37, 0xaf};

[[gnu::section(".gnx.a3"), gnu::used]]
const uint8_t share3[8] = {0x41, 0xa8, 0xa5, 0x1a, 0x39, 0x2e, 0x13, 0xae};

} // namespace

uint8_t gnx4_wrapped_byte(std::size_t index) {
    // Volatile reads keep the two shares separate under optimization.
    const volatile uint8_t* const shares[] = {share0, share1, share2, share3};
    const std::size_t slot = (index * 13 + 7) % 32;
    return shares[slot / 8][slot % 8];
}
