#!/usr/bin/env python3
"""Extract the original JPEG embedded in an OCWM (OpenCamera Watermark) archived JPEG.

Usage:
    python3 scripts/extract_ocwm_original.py input-watermarked.jpg output-original.jpg

No third-party dependencies. Uses only the Python 3 standard library.
"""

import argparse
import hashlib
import json
import struct
import sys
from pathlib import Path

OCWM_MAGIC = b"OCWM\0"
HEADER_FMT = ">B B I I Q I"  # version, flags, chunkIndex, chunkCount, totalPayloadLength, manifestLength
HEADER_SIZE = struct.calcsize(HEADER_FMT)  # 22 bytes after magic


def parse_jpeg_segments(data: bytes):
    """Yield (marker, payload) for each JPEG marker segment before SOS."""
    if len(data) < 2 or data[0:2] != b"\xff\xd8":
        return
    i = 2
    while i < len(data) - 1:
        if data[i] != 0xFF:
            break
        marker = data[i + 1]
        if marker == 0xDA:  # SOS
            break
        if marker == 0xD9:  # EOI
            break
        if i + 3 >= len(data):
            break
        seg_len = (data[i + 2] << 8) | data[i + 3]
        if seg_len < 2:
            break
        payload = data[i + 4 : i + 2 + seg_len]
        yield marker, payload
        i += 2 + seg_len


def extract_ocwm_chunks(data: bytes):
    """Extract OCWM chunks from APP15 segments."""
    chunks = []
    for marker, payload in parse_jpeg_segments(data):
        if marker != 0xEF:  # APP15
            continue
        if len(payload) < 5 + HEADER_SIZE:
            continue
        if payload[:5] != OCWM_MAGIC:
            continue
        version, flags, chunk_idx, chunk_count, total_len, manifest_len = struct.unpack(
            HEADER_FMT, payload[5 : 5 + HEADER_SIZE]
        )
        if version != 1:
            print(f"error: unsupported ocwm version {version}", file=sys.stderr)
            sys.exit(1)
        offset = 5 + HEADER_SIZE
        manifest_bytes = None
        if manifest_len > 0:
            manifest_bytes = payload[offset : offset + manifest_len]
            offset += manifest_len
        payload_slice = payload[offset:]
        chunks.append(
            {
                "chunkIndex": chunk_idx,
                "chunkCount": chunk_count,
                "totalPayloadLength": total_len,
                "manifestBytes": manifest_bytes,
                "payloadSlice": payload_slice,
            }
        )
    return chunks


def main():
    parser = argparse.ArgumentParser(description="Extract original JPEG from OCWM archive")
    parser.add_argument("input", help="Path to watermarked JPEG with OCWM archive")
    parser.add_argument("output", help="Path to write extracted original JPEG")
    args = parser.parse_args()

    data = Path(args.input).read_bytes()

    if len(data) < 2 or data[0:2] != b"\xff\xd8":
        print("error: not a valid jpeg", file=sys.stderr)
        sys.exit(1)

    chunks = extract_ocwm_chunks(data)
    if not chunks:
        print("error: ocwm archive not found", file=sys.stderr)
        sys.exit(1)

    chunk_count = chunks[0]["chunkCount"]
    if len(chunks) != chunk_count:
        print("error: ocwm chunk missing", file=sys.stderr)
        sys.exit(1)

    chunks.sort(key=lambda c: c["chunkIndex"])
    for i, chunk in enumerate(chunks):
        if chunk["chunkIndex"] != i:
            print("error: ocwm chunk missing", file=sys.stderr)
            sys.exit(1)

    # Parse manifest from chunk 0
    if chunks[0]["manifestBytes"] is None:
        print("error: ocwm manifest missing from chunk 0", file=sys.stderr)
        sys.exit(1)
    manifest = json.loads(chunks[0]["manifestBytes"])
    if manifest.get("schema") != "org.opencamera.reversible-watermark":
        print(f"error: unsupported schema: {manifest.get('schema')}", file=sys.stderr)
        sys.exit(1)
    if manifest.get("version") != 1:
        print(f"error: unsupported manifest version: {manifest.get('version')}", file=sys.stderr)
        sys.exit(1)

    # Reassemble payload
    total_len = chunks[0]["totalPayloadLength"]
    parts = [c["payloadSlice"] for c in chunks]
    payload = b"".join(parts)

    if len(payload) != total_len:
        print("error: ocwm payload length mismatch", file=sys.stderr)
        sys.exit(1)

    # Verify SHA-256
    expected_hash = manifest.get("payloadSha256", "")
    actual_hash = hashlib.sha256(payload).hexdigest()
    if actual_hash != expected_hash:
        print("error: ocwm payload sha256 mismatch", file=sys.stderr)
        sys.exit(1)

    Path(args.output).write_bytes(payload)
    print(f"extracted ocwm original: {args.output}")
    print(f"payload-sha256: {actual_hash}")


if __name__ == "__main__":
    main()
