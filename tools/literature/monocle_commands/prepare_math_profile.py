#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Derive a complete class manifest from the exact original JDistlib 0.10.2 archive."""
from pathlib import Path
import argparse
import hashlib
import json
import zipfile

SHA = '8b50aaa11d2cbd512525e0d74f8e609f40db9f4fc179ccb7a885a82257e0ac81'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jdistlib-jar', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    raw = args.jdistlib_jar.read_bytes()
    if hashlib.sha256(raw).hexdigest() != SHA:
        parser.error('Original JDistlib archive differs; no new numerical profile admitted')
    with zipfile.ZipFile(args.jdistlib_jar) as archive:
        names = sorted(name for name in archive.namelist() if name.startswith('jdistlib/') and name.endswith('.class'))
        if len(names) != len(set(names)) or len(names) != 933:
            parser.error('Original class population differs')
        classes = {}
        for name in names:
            data = archive.read(name)
            classes[name] = dict(bytes=len(data), sha256=hashlib.sha256(data).hexdigest())
    value = dict(schema_version=1, version='0.10.2', original_archive=dict(bytes=len(raw), sha256=SHA),
        class_population='All jdistlib/**/*.class entries, including anonymous/compiler-generated helpers', classes=classes)
    with args.out.open('x', encoding='utf-8', newline='\n') as stream:
        stream.write(json.dumps(value, indent=2)+'\n')
    print('PASS complete original933-class profile; no numerical source changed')


if __name__ == '__main__':
    main()
