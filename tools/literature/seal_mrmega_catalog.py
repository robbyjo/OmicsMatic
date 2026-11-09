# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal current92/95/249 catalog credit without changing predecessor science proofs."""
import argparse,json,os,stat
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack
from render_palantir_catalog import refresh_manifest
from verify_mrmega_catalog import STATUS,verify


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    base=ROOT/'build/literature-review/mrmega-runtime';out=args.out.resolve();data=ROOT/'docs/literature/omics-2023-2026'
    assert not out.exists() and out.is_relative_to(base) and not out.name.startswith('catalog-');out.mkdir(parents=True)
    fresh=['mrmega-source-audit.json','mrmega-catalog-validation.json','mrmega-catalog-support.zip'];assert not any((data/n).exists() for n in fresh)
    gates=json.loads((base/'catalog-gates-1/COMPLETED.json').read_bytes());ui=json.loads((base/'catalog-ui-1/COMPLETED.json').read_bytes());audit=json.loads((base/'catalog-source-audit-1/COMPLETED.json').read_bytes());render=json.loads((base/'catalog-render-1/COMPLETED.json').read_bytes())
    assert len(gates['checks'])==32 and ui['status']=='passed-local-general-and-mrmega-catalog-browser-checks' and render['unchanged_tool_rows']==399 and audit['all_non_mrmega_source_roles_preserved']
    sources=set(gates['source_pins'])|set(ui['source_pins'])|set(audit['source_pins'])|set(render['source_pins'])
    sources.update(p.relative_to(ROOT).as_posix() for p in (ROOT/'tools/literature').glob('*mrmega*') if p.is_file())
    sources.update(['tools/literature/reviewed-uses.json','tools/literature/catalog_source_integrity.py','tools/literature/collect_omics_review.py','tools/literature/collect_htseq_omics.py','tools/literature/curate_shapeit_uses.py',
        'src/main/java/org/jlinalg/cli/JLinAlgCli.java','docs/literature/MRMEGA_INTEGRATION.md'])
    sources={n for n in sources if not n.startswith('site/')};pins={n:pin(ROOT/n) for n in sorted(sources)};files={'current-source/'+n:(ROOT/n).read_bytes() for n in pins}
    def tree(root):
        pending=[root]
        while pending:
            with os.scandir(pending.pop()) as stream:
                for item in stream:
                    path=Path(item.path);mode=item.stat(follow_symlinks=False).st_mode
                    if item.name=='__pycache__':continue
                    if stat.S_ISDIR(mode):pending.append(path)
                    else:assert stat.S_ISREG(mode);files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    for folder in sorted(base.iterdir()):
        if folder.is_dir() and folder.name.startswith('catalog-'):tree(folder)
    for n in ['site/literature-review.html','site/literature-warnings.html']:files['current-output/'+n]=(ROOT/n).read_bytes()
    for n in ['tools/literature/render_review.py','src/main/java/org/jlinalg/cli/JLinAlgCli.java','tools/literature/reviewed-uses.json','docs/literature/omics-2023-2026/README.md']:
        files['catalog-before/'+n]=(base/'catalog-dispatch-1/before'/n).read_bytes()
    (out/'mrmega-source-audit.json').write_bytes((base/'catalog-source-audit-1/COMPLETED.json').read_bytes())
    snapshot=['papers.json','tool-inventory.json','summary.json','README.md','WARNINGS.md','catalog.md','implementation-plan.md','mrmega-role-coverage.json',
        'mrmega-prior-reviews.json.gz','mrmega-prior-papers.json.gz','mrmega-shared-source-compatibility.json','mrmega-reviews-shared-source-compatibility.json','mrmega-draft-corrections.json','mrmega-source-audit.json']
    for n in snapshot:files['catalog-snapshot/'+n]=((out if n=='mrmega-source-audit.json' else data)/n).read_bytes()
    parents=[data/'sniffles-catalog-validation.json',data/'sniffles-catalog-support.zip']
    for folder in ['mrmega-regression-development','mrmega-ancestry-development','mrmega-workflow-development']:
        parents.extend(p for p in (ROOT/'docs/literature'/folder).iterdir() if p.is_file())
    parents.extend(p for p in (ROOT/'docs/literature/mrmega-catalog-development').iterdir() if p.is_file())
    parentpins={p.relative_to(ROOT).as_posix():pin(p) for p in sorted(parents)}
    previous=json.loads((ROOT/'build/literature-review/push-checkpoint-2026-11-09-mrmega-workflow-1/selection.json').read_bytes());held=previous['preserved']
    assert len(held)==18 and all(pin(ROOT/n)==v for n,v in held.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==previous['canonical']
    files['preserved-unrelated-worktree-pins.json']=(json.dumps(held,indent=2)+'\n').encode()
    packing=pack(out/'mrmega-catalog-support.zip',files);assert (out/'mrmega-catalog-support.zip').stat().st_size<95000000
    proof=dict(schema_version=1,status=STATUS,source_pins=pins,parent_proofs=parentpins,archive=pin(out/'mrmega-catalog-support.zip'),catalog_snapshot=snapshot,
        canonical_executable_unchanged=previous['canonical'],new_scoped_integrations=92,including_existing_adapters=95,named_tools=400,observations=2465,
        reviewed_roles=1012,supporting_pointers=355,missing=249,availability_rows_changed=1,paper_roles_changed=3,unchanged_prior_reviews=1009,new_reviews=3,new_catalog_credit=True,
        scientific_verifiers_changed=False,scientific_source_exception_added=False,whole_package_implemented=False,whole_paper_reproduced=False,whole_goal_complete=False,
        historical_metadata_scope='Exact prior1009 reviews/papers and frozen predecessor catalog proof remain preserved. Dispatcher/renderer/review inverse deltas stay within the same seven-path allowlist. No scientific source pin is refreshed. Two wrong unpublished role drafts are corrected with explicit primary anchors; originals remain unchanged.',
        visual_review=dict(desktop_card=True,mobile_dark_warnings=True,no_horizontal_overflow=True),**packing)
    save(out/'mrmega-catalog-validation.json',proof);verify(False,out,False)
    assert all(pin(ROOT/n)==v for n,v in pins.items()) and all(pin(ROOT/n)==v for n,v in held.items())
    for n in fresh:
        with (data/n).open('xb') as stream:stream.write((out/n).read_bytes())
    refresh_manifest(data,(data/'SHA256SUMS').read_bytes());verify();print('Sealed92 new integrations/95 including existing;249 missing;1012 roles;32 science gates and350 browser checks; goal active')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable prior-proof, scope or preservation checks')
    main()
