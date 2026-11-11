# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Publish a separately verified90/93/251 catalog checkpoint without changing predecessor scientific evidence."""
import argparse,json,os
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack
from render_palantir_catalog import refresh_manifest
from verify_rsubread_catalog import STATUS,verify


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();base=ROOT/'build/literature-review/rsubread-runtime';data=ROOT/'docs/literature/omics-2023-2026';assert not out.exists() and out.is_relative_to(base) and not out.name.startswith('catalog-');out.mkdir(parents=True);candidate=out/'candidate';candidate.mkdir();fresh=['rsubread-source-audit.json','rsubread-catalog-validation.json','rsubread-catalog-support.zip'];assert not any((data/n).exists() for n in fresh)
    gates=json.loads((base/'catalog-gates-1/COMPLETED.json').read_bytes());ui=json.loads((base/'catalog-ui-1/COMPLETED.json').read_bytes());audit=json.loads((base/'catalog-source-audit-1/COMPLETED.json').read_bytes())
    assert len(gates['checks'])==30 and ui['status']=='passed-local-general-and-rsubread-catalog-browser-checks' and audit['all_non_rsubread_source_roles_preserved']
    sources=set(gates['source_pins'])|set(ui['source_pins'])|set(audit['source_pins'])|set(json.loads((base/'catalog-render-1/COMPLETED.json').read_bytes())['source_pins'])
    sources.update('tools/literature/'+n for n in ['rsubread_catalog.py','rsubread_execution_evidence.py','curate_rsubread_uses.py','rsubread-use-reviews.json','audit_rsubread_roles.py','prepare_rsubread_catalog.py','render_rsubread_catalog.py','prepare_rsubread_catalog_checks.py','run_rsubread_catalog_checks.py','audit_rsubread_catalog.py','prepare_rsubread_catalog_ui.py','check_rsubread_catalog_review_ui.cjs','validate_rsubread_catalog_ui.py','update_rsubread_catalog_readme.py','verify_rsubread_catalog.py','seal_rsubread_catalog.py','catalog_source_integrity.py','collect_omics_review.py','collect_htseq_omics.py','curate_shapeit_uses.py','reviewed-uses.json']);sources.add('docs/literature/RSUBREAD_CATALOG.md');sources={n for n in sources if not n.startswith('site/')};pins={n:pin(ROOT/n) for n in sorted(sources)};files={'current-source/'+n:(ROOT/n).read_bytes() for n in pins}
    for directory in sorted(base.iterdir()):
        if not directory.is_dir() or not(directory.name.startswith('catalog-') or directory.name in ['primary-roles-2','role-curation-3']):continue
        for current,dirs,names in os.walk(directory,topdown=True,followlinks=False):
            dirs[:]=[n for n in dirs if n!='__pycache__' and not(Path(current)/n).is_symlink()]
            for name in names:
                path=Path(current)/name
                if path.is_file() and not path.is_symlink():files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    for name in ['site/literature-review.html','site/literature-warnings.html']:files['current-output/'+name]=(ROOT/name).read_bytes()
    baseline=base/'catalog-prepare-1/before/docs/literature/omics-2023-2026'
    for name in ['papers.json','tool-inventory.json','summary.json','README.md','WARNINGS.md','SHA256SUMS']:files['catalog-before/'+name]=(baseline/name).read_bytes()
    (candidate/'rsubread-source-audit.json').write_bytes((base/'catalog-source-audit-1/COMPLETED.json').read_bytes());snapshot=['papers.json','tool-inventory.json','summary.json','README.md','WARNINGS.md','catalog.md','implementation-plan.md','rsubread-role-coverage.json','rsubread-prior-reviews.json.gz','rsubread-prior-papers.json.gz','rsubread-render-shared-source-compatibility.json','rsubread-reviews-shared-source-compatibility.json','rsubread-readme-evolution.json','rsubread-source-audit.json']
    for name in snapshot:files['catalog-snapshot/'+name]=((candidate if name=='rsubread-source-audit.json' else data)/name).read_bytes()
    parents=['docs/literature/omics-2023-2026/hifiasm-catalog-validation.json','docs/literature/omics-2023-2026/hifiasm-catalog-support.zip'];parents.extend(q.relative_to(ROOT).as_posix() for q in (ROOT/'docs/literature/rsubread-execution-development').iterdir() if q.is_file());parentpins={n:pin(ROOT/n) for n in sorted(parents)}
    previous=json.loads((ROOT/'build/literature-review/push-checkpoint-2026-11-09-rsubread-execution-1/selection.json').read_bytes());held=previous['preserved'];assert len(held)==18 and all(pin(ROOT/n)==v for n,v in held.items());canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');assert canonical==previous['canonical'];files['preserved-unrelated-worktree-pins.json']=(json.dumps(held,indent=2)+'\n').encode()
    archive=candidate/'rsubread-catalog-support.zip';packing=pack(archive,files);assert archive.stat().st_size<95000000
    proof=dict(schema_version=1,status=STATUS,source_pins=pins,parent_proofs=parentpins,archive=pin(archive),catalog_snapshot=snapshot,canonical_executable_unchanged=canonical,new_scoped_integrations=90,including_existing_adapters=93,named_tools=400,observations=2465,reviewed_roles=1003,supporting_pointers=349,missing=251,availability_rows_changed=1,paper_roles_changed=6,unchanged_prior_reviews=997,new_reviews=3,refined_existing_reviews=3,new_catalog_credit=True,scientific_verifiers_changed=False,scientific_source_exception_added=False,independent_read_counting=False,independent_junction_counting=False,whole_paper_reproduced=False,whole_goal_complete=False,historical_metadata_scope='Frozen980-role and predecessor metadata checks use exact archived metadata inputs. The prior papers.json source pin is bound to its exact archived snapshot; all live scientific source/artifact paths remain strict and the seven-path allowlist is unchanged.',visual_review=dict(desktop_card=True,mobile_dark_warnings=True,no_horizontal_overflow=True),**packing);save(candidate/'rsubread-catalog-validation.json',proof)
    verify(folder=candidate,check_manifest=False);assert all(pin(ROOT/n)==v for n,v in pins.items()) and all(pin(ROOT/n)==v for n,v in held.items())
    for name in fresh:
        with (data/name).open('xb') as target:target.write((candidate/name).read_bytes())
    refresh_manifest(data,(data/'SHA256SUMS').read_bytes());verify();save(out/'COMPLETED.json',dict(status=STATUS,candidate_verified_before_publication=True,archive=pin(archive),logical_files=len(files),unrelated_files_preserved=18,whole_goal_complete=False));print('Sealed90 scoped integrations,1003 roles,30 scientific gates and335 browser checks; goal active')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source, catalog or unrelated-work preservation checks')
    main()
