#!/usr/bin/env python3
"""Build paindu's malware signature file from a list of SHA-256 hashes.

Input:  any text, CSV or ZIP export that contains SHA-256 hashes, such as the
        abuse.ch MalwareBazaar "full data dump" (CC0). Every 64-character hex
        string in it is treated as a hash.
Output: the binary file the Android app downloads. Format (see HashDb.kt):
        b"PAINDUH1", a big-endian 64-bit count, then that many big-endian
        64-bit values, each the first 8 bytes of one SHA-256, sorted.

Usage:  build_signatures.py INPUT [INPUT ...] OUTPUT
"""
import io
import re
import struct
import sys
import zipfile

HASH = re.compile(rb"(?<![0-9a-fA-F])[0-9a-fA-F]{64}(?![0-9a-fA-F])")


def hashes_in(data: bytes):
    for m in HASH.finditer(data):
        yield int(m.group(0)[:16], 16)


def read(path: str):
    with open(path, "rb") as f:
        data = f.read()
    if data[:2] == b"PK":
        with zipfile.ZipFile(io.BytesIO(data)) as z:
            for name in z.namelist():
                yield from hashes_in(z.read(name))
    else:
        yield from hashes_in(data)


def main(argv):
    if len(argv) < 3:
        print(__doc__)
        return 2
    *inputs, output = argv[1:]
    prefixes = set()
    for path in inputs:
        prefixes.update(read(path))
    if len(prefixes) < 1000:
        print(f"Only {len(prefixes)} hashes found; refusing to publish a near-empty database.")
        return 1
    with open(output, "wb") as f:
        f.write(b"PAINDUH1")
        f.write(struct.pack(">q", len(prefixes)))
        for p in sorted(prefixes):
            f.write(struct.pack(">Q", p))
    print(f"Wrote {len(prefixes):,} signatures to {output}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
