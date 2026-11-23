#include <cstddef>
#include <cstdint>

namespace {
[[gnu::section(".gnx.b0"), gnu::used]]
const uint8_t share0[8] = {0x07, 0x08, 0xae, 0xc3, 0xca, 0x7f, 0x37, 0xff};

[[gnu::section(".gnx.b1"), gnu::used]]
const uint8_t share1[8] = {0xbb, 0x85, 0xa2, 0x45, 0xb0, 0x05, 0x47, 0x49};

[[gnu::section(".gnx.b2"), gnu::used]]
const uint8_t share2[8] = {0x77, 0x6b, 0x71, 0x32, 0x53, 0x88, 0x42, 0xd0};

[[gnu::section(".gnx.b3"), gnu::used]]
const uint8_t share3[8] = {0x68, 0x1b, 0xc4, 0x67, 0x53, 0x6f, 0x9f, 0xda};

} // namespace

uint8_t gnx4_mask_byte(std::size_t index) {
    // Volatile reads keep the two shares separate under optimization.
    const volatile uint8_t* const shares[] = {share0, share1, share2, share3};
    const std::size_t slot = (index * 11 + 19) % 32;
    return shares[slot / 8][slot % 8];
}
