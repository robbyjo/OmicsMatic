# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Connect MTAG metadata, prominent warnings and current README refresh with an exact renderer-only inverse."""
import argparse
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_mtag_reference import BASE
from curate_shapeit_uses import inverse
from catalog_source_integrity import reverse_delta,verify_source


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir(parents=True)
    name='tools/literature/render_review.py';path=ROOT/name;before=path.read_bytes();assert b'from mtag_catalog import' not in before and before.count(b'from genesis_catalog import')>=10
    after=before.replace(b'from genesis_catalog import',b'from mtag_catalog import')
    after=b'from refresh_mtag_catalog_metadata import refresh as refresh_mtag_metadata\nfrom mtag_catalog import mtag_metadata, MTAG_WARNING, MTAG_ACTIONS\n'+after
    edits=[
        (b'        elif tool_id == "genesis":\n',b'        elif tool_id == "mtag":\n            tool.update(mtag_metadata(ROOT,DATA))\n        elif tool_id == "genesis":\n'),
        (b'if tool_id not in {"genesis",',b'if tool_id not in {"mtag", "genesis",'),
        (b'write_text(GENESIS_WARNING + ',b'write_text(MTAG_WARNING + GENESIS_WARNING + '),
        (b'warning_body = GENESIS_ACTIONS + ',b'warning_body = MTAG_ACTIONS + GENESIS_ACTIONS + '),
        (b'page.replace(\'<p id="overview"></p>\', GENESIS_ACTIONS + ',b'page.replace(\'<p id="overview"></p>\', MTAG_ACTIONS + GENESIS_ACTIONS + '),
        (b'    refresh_genesis_metadata(ROOT, DATA)\n',b'    refresh_genesis_metadata(ROOT, DATA)\n    refresh_mtag_metadata(ROOT, DATA)\n')]
    for old,new in edits:assert after.count(old)==1,old;after=after.replace(old,new)
    row=inverse(before,after);row['path']=name;assert reverse_delta(after,row)==before
    data=ROOT/'docs/literature/omics-2023-2026';descriptor=data/'mtag-renderer-shared-source-compatibility.json';assert not descriptor.exists()
    save(descriptor,dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='mtag',scope='Only the MTAG metadata provider, card, warnings and current README hook. Predecessor metadata is viewed through exact archived root+DATA inputs; no scientific source or verifier changes.',paths=[row]))
    (out/'renderer.before.py').write_bytes(before);(out/'renderer.after.py').write_bytes(after);assert path.read_bytes()==before;path.write_bytes(after);verify_source(ROOT,data,name,row['before_sha256'])
    save(out/'COMPLETED.json',dict(status='PASS_MTAG_RENDERER_EXACT_METADATA_INVERSE',renderer=pin(path),descriptor=pin(descriptor),producer=pin(Path(__file__)),new_catalog_credit=False,whole_goal_complete=False))
    print('PASS_MTAG_RENDERER_EXACT_METADATA_INVERSE')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact renderer-only metadata inverse checks')
    main()
