#!/usr/bin/env python3
"""Fail closed if the built archive omits the data Xray's built-in rules use."""
import hashlib
import sys
from zipfile import ZipFile

PRIVATE_GIT_BLOB = "15085ec3a1ea17832c1952ed3808cdd45134fcea"
REQUIRED = ("geoip.dat", "geosite.dat", "geoip-only-cn-private.dat")

with ZipFile(sys.argv[1]) as archive:
    for name in REQUIRED:
        path = "assets/" + name
        data = archive.read(path)
        if not data:
            raise SystemExit(f"Empty required asset: {path}")
        if name == REQUIRED[-1]:
            digest = hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()
            if digest != PRIVATE_GIT_BLOB:
                raise SystemExit(f"Unexpected {path} Git blob hash: {digest}")
        print(f"Verified {path} ({len(data)} bytes)")
