# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Derive a compiler-selection-only historical installer; original scientific sources remain unchanged."""
import hashlib
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]


def main():
    original=ROOT/'tools/literature/setup_htseq_historical_reference.py'
    target=ROOT/'tools/literature/setup_htseq_historical_gcc_reference.py'
    before=original.read_bytes()
    old=b"'PIP_NO_INDEX':'1','PIP_DISABLE_PIP_VERSION_CHECK':'1','SWIG':'/usr/bin/false'}"
    new=b"'PIP_NO_INDEX':'1','PIP_DISABLE_PIP_VERSION_CHECK':'1','SWIG':'/usr/bin/false',\n            'CC':'/usr/bin/gcc','CXX':'/usr/bin/g++','LDSHARED':'/usr/bin/gcc -shared'}"
    assert before.count(old)==1 and not target.exists()
    after=before.replace(old,new)
    assert after.count(new)==1 and after.replace(new,old)==before
    target.write_bytes(after)
    record=ROOT/'build/literature-review/htseq-runtime/historical-gcc-source-derivation.json'
    assert not record.exists()
    record.write_text(json.dumps(dict(schema_version=1,status='exact-private-compiler-selection-only-installer-derivation',
        original=original.relative_to(ROOT).as_posix(),original_sha256=hashlib.sha256(before).hexdigest(),
        derived=target.relative_to(ROOT).as_posix(),derived_sha256=hashlib.sha256(after).hexdigest(),
        only_change='Select existing GCC/G++ and shared linker explicitly instead of absent SDK-default Clang.',
        original_HTSeq_scientific_sources_changed=False,shared_Python_changed=False),indent=2)+'\n',encoding='utf8',newline='\n')
    print('Derived historical installer with only explicit existing GCC/G++ selection; original installer unchanged')


if __name__=='__main__':main()
