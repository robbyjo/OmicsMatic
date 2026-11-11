# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Update only two whole-catalog UI counts; reconstruct the prior checker exactly."""
import gzip
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/literature'))
from curate_shapeit_uses import inverse
from catalog_source_integrity import reverse_delta


def main():
    path = ROOT / 'tools/literature/check_review_ui.cjs'
    before = path.read_bytes()
    after = before
    for old,new in [(b'/380 named tools/',b'/387 named tools/'),(b'/2430 tool observations/',b'/2442 tool observations/')]:
        assert after.count(old)==1 and new not in after
        after=after.replace(old,new)
    row=inverse(before,after);row['path']='tools/literature/check_review_ui.cjs'
    assert len(row['edits'])==1 and reverse_delta(after,row)==before
    record=ROOT/'docs/literature/omics-2023-2026/meme-program-ui-shared-source-compatibility.json'
    assert not record.exists()
    saved=ROOT/'build/literature-review/meme-runtime/role-curation-apply-1/check_review_ui-before.cjs.gz'
    assert not saved.exists();saved.write_bytes(gzip.compress(before,mtime=0))
    record.write_text(json.dumps(dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='meme-program-identities',
        scope='Only two overview count expectations change for seven distinct newly catalogued programs and twelve net observations. Every scientific/source-role/recovery UI assertion is unchanged, and exact old checker bytes reconstruct.',paths=[row]),indent=2)+'\n',encoding='utf-8',newline='\n')
    assert path.read_bytes()==before;path.write_bytes(after)
    print('Two overview counts updated; every previous checker byte reconstructs.')


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable exact UI compatibility checks')
    main()
