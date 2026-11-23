#!/usr/bin/env python3
"""Prepare private GNX4 translation units for a signed build."""

import os
import re
from pathlib import Path
from secrets import token_bytes
import sys


def write_share(directory, name, prefix, values, multiplier, offset):
    permuted = [0] * 32
    for index, value in enumerate(values):
        permuted[(index * multiplier + offset) % 32] = value

    lines = ['#include <cstddef>', '#include <cstdint>', '', 'namespace {']
    for group in range(4):
        row = ', '.join(f'0x{value:02x}' for value in permuted[group * 8:group * 8 + 8])
        lines.extend((
            f'[[gnu::section(".gnx.{prefix}{group}"), gnu::used]]',
            f'const uint8_t share{group}[8] = {{{row}}};',
            '',
        ))
    lines.extend((
        '} // namespace',
        '',
        f'uint8_t gnx4_{name}_byte(std::size_t index) {{',
        '    const volatile uint8_t* const shares[] = {share0, share1, share2, share3};',
        f'    const std::size_t slot = (index * {multiplier} + {offset}) % 32;',
        '    return shares[slot / 8][slot % 8];',
        '}',
        '',
    ))
    (directory / f'gnx4_key_{name}.cpp').write_text('\n'.join(lines), encoding='ascii')


def main():
    if len(sys.argv) != 2:
        raise SystemExit("Usage: generate-gnx4-key-shares.py PRIVATE_OUTPUT_DIR")

    key_hex = os.environ.get("GNX4_EXPORT_KEY", "")
    if not re.fullmatch(r"[0-9a-fA-F]{64}", key_hex):
        raise SystemExit("GNX4_EXPORT_KEY must contain 32 bytes encoded as 64 hexadecimal digits.")

    os.umask(0o077)
    directory = Path(sys.argv[1])
    directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    key = bytes.fromhex(key_hex)
    mask = token_bytes(32)
    write_share(directory, 'wrapped', 'a', bytes(value ^ pad for value, pad in zip(key, mask)), 13, 7)
    write_share(directory, 'mask', 'b', mask, 11, 19)


if __name__ == "__main__":
    main()
