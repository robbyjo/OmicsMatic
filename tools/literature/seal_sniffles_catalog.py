# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Publish a separately verified91/94/250 checkpoint and preserve predecessor proofs."""
import argparse,json,os,stat
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack
from render_palantir_catalog import refresh_manifest
from verify_sniffles_catalog import STATUS,verify


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    out=a.out.resolve();base=ROOT/'build/literature-review/sniffles-runtime';data=ROOT/'docs/literature/omics-2023-2026'
    assert not out.exists() and out.is_relative_to(base) and not out.name.startswith('catalog-');out.mkdir(parents=True)
    fresh=['sniffles-source-audit.json','sniffles-catalog-validation.json','sniffles-catalog-support.zip'];assert not any((data/name).exists() for name in fresh)
    gates=json.loads((base/'catalog-gates-2/COMPLETED.json').read_bytes());ui=json.loads((base/'catalog-ui-3/COMPLETED.json').read_bytes())
    audit=json.loads((base/'catalog-source-audit-1/COMPLETED.json').read_bytes());render=json.loads((base/'catalog-render-1/COMPLETED.json').read_bytes())
    assert len(gates['checks'])==31 and ui['status']=='passed-local-general-and-sniffles-catalog-browser-checks'
    assert audit['all_non_sniffles_source_roles_preserved'] and render['unchanged_tool_rows']==399
    sources=set(gates['source_pins'])|set(ui['source_pins'])|set(audit['source_pins'])|set(render['source_pins'])
    sources.update(path.relative_to(ROOT).as_posix() for path in (ROOT/'tools/literature').glob('*sniffles*') if path.is_file() and 'catalog' in path.name)
    sources.update('tools/literature/'+name for name in ['curate_sniffles_uses.py','sniffles-use-reviews.json','reviewed-uses.json',
        'catalog_source_integrity.py','collect_omics_review.py','collect_htseq_omics.py','curate_shapeit_uses.py'])
    sources={name for name in sources if not name.startswith('site/')};pins={name:pin(ROOT/name) for name in sorted(sources)}
    files={'current-source/'+name:(ROOT/name).read_bytes() for name in pins}
    def tree(root):
        pending=[root]
        while pending:
            parent=pending.pop()
            with os.scandir(parent) as stream:
                for item in stream:
                    path=Path(item.path);mode=item.stat(follow_symlinks=False).st_mode
                    if item.name=='__pycache__':continue
                    if stat.S_ISDIR(mode):pending.append(path)
                    else:assert stat.S_ISREG(mode);files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    for directory in sorted(base.iterdir()):
        if directory.is_dir() and (directory.name.startswith('catalog-') or directory.name in ['primary-roles-2','role-curation-apply-1']):tree(directory)
    for name in ['site/literature-review.html','site/literature-warnings.html']:files['current-output/'+name]=(ROOT/name).read_bytes()
    before=base/'catalog-dispatch-apply-1/before'
    for name in ['tools/literature/render_review.py','tools/literature/reviewed-uses.json','docs/literature/omics-2023-2026/README.md']:
        files['catalog-before/'+name]=(before/name).read_bytes()
    (out/'sniffles-source-audit.json').write_bytes((base/'catalog-source-audit-1/COMPLETED.json').read_bytes())
    snapshot=['papers.json','tool-inventory.json','summary.json','README.md','WARNINGS.md','catalog.md','implementation-plan.md',
        'sniffles-role-coverage.json','sniffles-prior-reviews.json.gz','sniffles-prior-papers.json.gz','sniffles-shared-source-compatibility.json',
        'sniffles-reviews-shared-source-compatibility.json','sniffles-source-audit.json']
    for name in snapshot:files['catalog-snapshot/'+name]=((out if name=='sniffles-source-audit.json' else data)/name).read_bytes()
    parents=[data/'rsubread-catalog-validation.json',data/'rsubread-catalog-support.zip']
    parents.extend(path for path in (ROOT/'docs/literature/sniffles-workflows').iterdir() if path.is_file())
    parentpins={path.relative_to(ROOT).as_posix():pin(path) for path in sorted(parents)}
    previous=json.loads((ROOT/'build/literature-review/push-checkpoint-2026-11-09-sniffles-installation-1/selection.json').read_bytes())
    held=previous['preserved'];assert len(held)==18 and all(pin(ROOT/name)==value for name,value in held.items())
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==previous['canonical']
    files['preserved-unrelated-worktree-pins.json']=(json.dumps(held,indent=2)+'\n').encode()
    packing=pack(out/'sniffles-catalog-support.zip',files);assert (out/'sniffles-catalog-support.zip').stat().st_size<95000000
    proof=dict(schema_version=1,status=STATUS,source_pins=pins,parent_proofs=parentpins,archive=pin(out/'sniffles-catalog-support.zip'),
        catalog_snapshot=snapshot,canonical_executable_unchanged=previous['canonical'],new_scoped_integrations=91,including_existing_adapters=94,
        named_tools=400,observations=2465,reviewed_roles=1009,supporting_pointers=353,missing=250,availability_rows_changed=1,
        paper_roles_changed=6,unchanged_prior_reviews=1003,new_reviews=6,new_catalog_credit=True,
        scientific_verifiers_changed=False,scientific_source_exception_added=False,independent_SV_calling=False,
        whole_paper_reproduced=False,whole_goal_complete=False,
        historical_metadata_scope='Predecessor catalog proofs retain exact archived metadata inputs and strict live scientific paths. Exact renderer/review inverse deltas stay within the existing seven-path allowlist. No scientific source pin is refreshed.',
        visual_review=dict(desktop_card=True,mobile_dark_warnings=True,no_horizontal_overflow=True),**packing)
    save(out/'sniffles-catalog-validation.json',proof);verify(False,out,False)
    assert all(pin(ROOT/name)==value for name,value in pins.items()) and all(pin(ROOT/name)==value for name,value in held.items())
    for name in fresh:
        with (data/name).open('xb') as stream:stream.write((out/name).read_bytes())
    refresh_manifest(data,(data/'SHA256SUMS').read_bytes());verify()
    print('Sealed91 scoped integrations,1009 roles,31 scientific gates and342 browser checks; goal active')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source, catalog or unrelated-work preservation checks')
    main()
