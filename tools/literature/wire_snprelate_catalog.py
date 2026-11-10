# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Record exact renderer/Git metadata deltas; leave every preceding scientific source live."""
import argparse
import json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from curate_shapeit_uses import inverse
from catalog_source_integrity import reverse_delta,verify_source
BASE=ROOT/'build/literature-review/snprelate-runtime'


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    data=ROOT/'docs/literature/omics-2023-2026';changes={}
    name='tools/literature/render_review.py';path=ROOT/name;before=path.read_bytes()
    expected=json.loads((data/'diamond-catalog-validation.json').read_bytes())['source_pins'][name];assert pin(path)==expected
    after=before.replace(b'from diamond_catalog import ',b'from snprelate_catalog import ')
    after=b'from snprelate_catalog import snprelate_metadata, SNPRELATE_WARNING, SNPRELATE_ACTIONS\n'+after
    anchor=b'        elif tool_id == "diamond":\n';assert after.count(anchor)==1
    after=after.replace(anchor,b'        elif tool_id == "snprelate":\n            tool.update(snprelate_metadata(ROOT,DATA))\n'+anchor)
    assert after.count(b'write_text(DIAMOND_WARNING + ')==1;after=after.replace(b'write_text(DIAMOND_WARNING + ',b'write_text(SNPRELATE_WARNING + DIAMOND_WARNING + ')
    assert after.count(b'warning_body = DIAMOND_ACTIONS + ')==1;after=after.replace(b'warning_body = DIAMOND_ACTIONS + ',b'warning_body = SNPRELATE_ACTIONS + DIAMOND_ACTIONS + ')
    assert after.count(b'page.replace(\'<p id="overview"></p>\', DIAMOND_ACTIONS + ')==1
    after=after.replace(b'page.replace(\'<p id="overview"></p>\', DIAMOND_ACTIONS + ',b'page.replace(\'<p id="overview"></p>\', SNPRELATE_ACTIONS + DIAMOND_ACTIONS + ')
    changes[name]=(before,after)
    name='.gitattributes';path=ROOT/name;before=path.read_bytes();assert b'docs/literature/snprelate-tools-development/**' not in before
    after=before+b'\n# Preserve the frozen SNPRelate workflow evidence on every checkout.\ndocs/literature/snprelate-tools-development/** -text whitespace=cr-at-eol\n'
    changes[name]=(before,after)
    for name,(before,after) in changes.items():
        row=inverse(before,after);row['path']=name;assert reverse_delta(after,row)==before
        kind='renderer' if name.endswith('.py') else 'sourcebytes';descriptor=data/f'snprelate-{kind}-shared-source-compatibility.json';assert not descriptor.exists()
        (out/(kind+'.before')).write_bytes(before);(out/(kind+'.after')).write_bytes(after)
        save(descriptor,dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='snprelate',
            scope='Explicit SNPRelate catalog provider, card and prominent required actions, or append-only evidence byte preservation. No scientific-source waiver.',paths=[row]))
        assert (ROOT/name).read_bytes()==before;(ROOT/name).write_bytes(after);verify_source(ROOT,data,name,row['before_sha256'])
    save(out/'COMPLETED.json',dict(status='PASS_SNPRELATE_RENDERER_AND_GIT_METADATA_EXACT_INVERSES',source_pins={n:pin(ROOT/n) for n in changes},whole_goal_complete=False))
    print('PASS_SNPRELATE_RENDERER_AND_GIT_METADATA_EXACT_INVERSES')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact shared-metadata inverse checks')
    main()
