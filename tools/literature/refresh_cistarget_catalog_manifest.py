# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Refresh only the catalog's complete file-integrity index after a coherent render."""
import argparse,json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--render',type=Path,required=True);a=p.parse_args();out=a.out.resolve()
    assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/cistarget-runtime');out.mkdir(parents=True);data=ROOT/'docs/literature/omics-2023-2026'
    rendered=json.loads((a.render/'COMPLETED.json').read_bytes());assert rendered['status']=='PASS_CISTARGET_RENDER_WITH_399_UNRELATED_TOOL_OBJECTS_IDENTICAL'
    assert all(pin(ROOT/n)==v for n,v in rendered['rendered_files'].items());path=data/'SHA256SUMS';before=path.read_bytes();(out/'SHA256SUMS.before').write_bytes(before)
    entries={p.name:pin(p) for p in sorted(data.iterdir()) if p.is_file() and p.name!='SHA256SUMS'}
    raw=''.join(v['sha256']+'  '+name+'\n' for name,v in entries.items()).encode();(out/'SHA256SUMS.after').write_bytes(raw);path.write_bytes(raw)
    save(out/'COMPLETED.json',dict(status='PASS_COMPLETE_CISTARGET_CATALOG_FILE_INDEX_REFRESH',file_count=len(entries),all_current_files_hashed=True,files=entries,before_manifest=pin(out/'SHA256SUMS.before'),after_manifest=pin(path),
        source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))},scientific_verifier_hashes_refreshed=False,whole_goal_complete=False));print('Refreshed complete catalog byte-integrity index; scientific verification bindings unchanged')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable coherent-render/index checks')
    main()
