# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare exact metadata-only catalog dispatch changes; apply requires final calling proof."""
import argparse,hashlib,json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from curate_shapeit_uses import inverse
from catalog_source_integrity import ALLOWED_PATHS,reverse_delta


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--apply',action='store_true');a=parser.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/sniffles-runtime');out.mkdir(parents=True)
    data=ROOT/'docs/literature/omics-2023-2026';renderer=ROOT/'tools/literature/render_review.py';before=renderer.read_bytes();text=before.decode('utf8')
    summary=json.loads((data/'summary.json').read_bytes());tools=json.loads((data/'tool-inventory.json').read_bytes());reviews=json.loads((ROOT/'tools/literature/reviewed-uses.json').read_bytes())
    assert summary['papers']==250 and summary['distinct_named_tools']==400 and summary['paper_tool_observations']==2465
    assert summary['implementation_status_counts']['native-subset-validated']==17 and summary['implementation_status_counts']['upstream-adapter-validated']==73 and summary['implementation_status_counts']['missing']==251
    assert len(reviews['reviews'])==1003 and sum(len(row.get('supporting_evidence',[])) for row in reviews['reviews'])==349
    assert len(tools)==400 and next(row for row in tools if row['id']=='sniffles')['implementation_status']=='missing'
    assert ALLOWED_PATHS==frozenset({'.gitattributes','src/main/java/org/jlinalg/cli/JLinAlgCli.java','tools/literature/render_review.py',
        'tools/literature/reviewed-uses.json','tools/literature/repeat_catalog.py','tools/literature/check_review_ui.cjs','tools/literature/check_repeat_review_ui.cjs'})
    imports=text.count('from rsubread_catalog import');assert imports==8,('Unexpected prior dispatch imports',imports)
    text=text.replace('from rsubread_catalog import','from sniffles_catalog import')
    needle='from pathlib import Path\n';assert text.count(needle)==1
    text=text.replace(needle,needle+'from sniffles_catalog import sniffles_metadata, SNIFFLES_WARNING, SNIFFLES_ACTIONS\n')
    needle='        elif tool_id == "rsubread":\n            tool.update(rsubread_metadata(ROOT,DATA))'
    assert text.count(needle)==1;text=text.replace(needle,'        elif tool_id == "sniffles":\n            tool.update(sniffles_metadata(ROOT,DATA))\n'+needle)
    needle='(DATA / "WARNINGS.md").write_text(RSUBREAD_WARNING +';assert text.count(needle)==1
    text=text.replace(needle,'(DATA / "WARNINGS.md").write_text(SNIFFLES_WARNING + RSUBREAD_WARNING +')
    needle='warning_body = RSUBREAD_ACTIONS +';assert text.count(needle)==1
    text=text.replace(needle,'warning_body = SNIFFLES_ACTIONS + RSUBREAD_ACTIONS +')
    after=text.encode();compile(after,'candidate-render-review.py','exec')
    row=inverse(before,after);row['path']='tools/literature/render_review.py';assert reverse_delta(after,row)==before
    files=[renderer,ROOT/'tools/literature/reviewed-uses.json',data/'papers.json',data/'tool-inventory.json',data/'summary.json',data/'README.md',
        ROOT/'site/literature-review.html',ROOT/'site/literature-warnings.html',data/'WARNINGS.md',data/'SHA256SUMS',ROOT/'tools/literature/check_review_ui.cjs']
    input_pins={path.relative_to(ROOT).as_posix():pin(path) for path in files}
    for path in files:
        target=out/'before'/path.relative_to(ROOT);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(path.read_bytes())
    (out/'candidate-render-review.py').write_bytes(after)
    descriptor=dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='sniffles',
        scope='Add one final-proof-gated Sniffles metadata dispatch and prominent required actions, retaining all other tool dispatches. No scientific-source exception.',paths=[row])
    save(out/'sniffles-shared-source-compatibility.json',descriptor)
    provider=ROOT/'tools/literature/sniffles_catalog.py';curator=ROOT/'tools/literature/curate_sniffles_uses.py'
    source_pins={path.relative_to(ROOT).as_posix():pin(path) for path in [Path(__file__).resolve(),provider,curator,ROOT/'tools/literature/catalog_source_integrity.py']}
    assert all(pin(ROOT/name)==value for name,value in input_pins.items())
    if a.apply:
        proof_path=ROOT/'tools/literature/sniffles_execution_evidence.py'
        assert proof_path.is_file(),'ACTION: final calling proof is absent; keep this exact catalog candidate unapplied.'
        from sniffles_execution_evidence import verify
        proof,_=verify(False);assert proof['declared_calling_workflow_available'] and not proof['implementation_credit']
        destination=data/'sniffles-shared-source-compatibility.json';assert not destination.exists()
        destination.write_bytes((out/'sniffles-shared-source-compatibility.json').read_bytes());renderer.write_bytes(after)
    save(out/'COMPLETED.json',dict(status='PASS_PREPARED_SNIFFLES_METADATA_DISPATCH_ONLY',applied=a.apply,availability_credit=False,
        source_pins=source_pins,input_pins=input_pins,before_renderer=dict(bytes=len(before),sha256=hashlib.sha256(before).hexdigest()),
        candidate_renderer=pin(out/'candidate-render-review.py'),compatibility_descriptor=pin(out/'sniffles-shared-source-compatibility.json'),
        exact_prior_renderer_reconstructable=True,seven_path_allowlist_unchanged=True,prior_reviews=1003,whole_goal_complete=False))
    print('Prepared exact Sniffles metadata dispatch and warning candidate; source allowlist unchanged; applied='+str(a.apply))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact metadata/source-delta checks')
    main()
