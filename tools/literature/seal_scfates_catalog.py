# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal a candidate catalog proof, verify it, then publish fresh additive evidence paths."""
import argparse,json,os,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack
from render_palantir_catalog import refresh_manifest
from verify_scfates_catalog import STATUS,verify

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',required=True,type=Path);a=parser.parse_args();out=a.out.resolve()
    base=ROOT/'build/literature-review/scfates-runtime';data=ROOT/'docs/literature/omics-2023-2026'
    assert out.is_relative_to(base) and not out.exists();out.mkdir(parents=True);candidate=out/'candidate';candidate.mkdir()
    fresh=['scfates-source-audit.json','scfates-catalog-validation.json','scfates-catalog-support.zip'];assert all(not(data/n).exists() for n in fresh)
    gates=json.loads((base/'catalog-gates-1/COMPLETED.json').read_bytes());assert len(gates['checks'])==25
    ui=json.loads((base/'catalog-ui-2/COMPLETED.json').read_bytes());assert ui['status']=='passed-local-general-and-scfates-catalog-browser-checks'
    audit=json.loads((base/'catalog-source-audit-2/COMPLETED.json').read_bytes());assert not audit['registry_or_reviews_changed']
    sources=set(gates['source_pins'])|set(ui['source_pins'])|set(audit['source_pins'])
    sources.update('tools/literature/'+n for n in ['scfates_catalog.py','prepare_scfates_catalog.py','render_scfates_catalog.py','run_scfates_catalog_checks.py','audit_scfates_catalog.py','prepare_scfates_catalog_ui.py','check_scfates_review_ui.cjs','validate_scfates_catalog_ui.py','update_scfates_catalog_readme.py','verify_scfates_catalog.py','seal_scfates_catalog.py','catalog_source_integrity.py','collect_omics_review.py','collect_htseq_omics.py','curate_shapeit_uses.py','reviewed-uses.json'])
    sources.add('docs/literature/SCFATES_CATALOG.md');sources={n for n in sources if not n.startswith('site/')};pins={n:pin(ROOT/n) for n in sorted(sources)}
    files={'current-source/'+n:(ROOT/n).read_bytes() for n in pins}
    for directory in sorted(base.iterdir()):
        if not directory.is_dir() or not directory.name.startswith('catalog-'):continue
        for current,dirs,names in os.walk(directory,topdown=True,followlinks=False):
            dirs[:]=[n for n in dirs if n!='__pycache__' and not(Path(current)/n).is_symlink()]
            for name in names:
                path=Path(current)/name
                if path.is_file() and not path.is_symlink():files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    for name in ['create_catalog_check_runner.py','create_current_catalog_audit.py','correct_catalog_checks.py']:
        path=base/name
        if path.exists():files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    for previous in sorted(base.glob('seal-catalog-*')):
        if previous==out:continue
        if not(previous/'FAILURE.md').exists():continue
        for name in ['FAILURE.md','candidate/scfates-catalog-validation.json','candidate/scfates-catalog-support.zip']:
            path=previous/name
            if path.exists():files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    for name in ['site/literature-review.html','site/literature-warnings.html']:files['current-output/'+name]=(ROOT/name).read_bytes()
    before=base/'catalog-prepare-1/before/docs/literature/omics-2023-2026'
    for name in ['papers.json','tool-inventory.json','summary.json','README.md','WARNINGS.md','SHA256SUMS']:files['catalog-before/'+name]=(before/name).read_bytes()
    snapshot=['papers.json','tool-inventory.json','summary.json','README.md','WARNINGS.md','catalog.md','implementation-plan.md','scfates-render-shared-source-compatibility.json','scfates-readme-evolution.json','palantir-role-coverage.json','scfates-source-audit.json']
    (candidate/'scfates-source-audit.json').write_bytes((base/'catalog-source-audit-2/COMPLETED.json').read_bytes())
    for name in snapshot:files['catalog-snapshot/'+name]=((candidate if name=='scfates-source-audit.json' else data)/name).read_bytes()
    parents=['docs/literature/omics-2023-2026/palantir-catalog-validation.json','docs/literature/omics-2023-2026/palantir-catalog-support.zip']
    for folder in ['simpleppt-core-development','scfates-workflow-development']:
        parent=ROOT/'docs/literature'/folder;parents.extend(p.relative_to(ROOT).as_posix() for p in parent.iterdir() if p.is_file() and (p.name=='VALIDATION.json' or p.suffix=='.zip'))
    parentpins={n:pin(ROOT/n) for n in sorted(parents)}
    held=json.loads((ROOT/'build/literature-review/push-checkpoint-2026-11-08-scfates-workflow-1/selection.json').read_bytes())['preserved']
    assert len(held)==18 and all(pin(ROOT/n)==v for n,v in held.items());files['preserved-unrelated-worktree-pins.json']=(json.dumps(held,indent=2)+'\n').encode()
    canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');archive=candidate/'scfates-catalog-support.zip';packing=pack(archive,files);assert archive.stat().st_size<95000000
    save(candidate/'scfates-catalog-validation.json',dict(schema_version=1,status=STATUS,source_pins=pins,parent_proofs=parentpins,archive=pin(archive),catalog_snapshot=snapshot,canonical_executable_unchanged=canonical,new_scoped_integrations=87,including_existing_adapters=90,named_tools=400,observations=2465,reviewed_roles=988,supporting_pointers=340,missing=254,availability_rows_changed=1,paper_roles_changed=0,new_catalog_credit=True,scientific_verifiers_changed=False,scientific_source_exception_added=False,independent_trajectory_refit=False,whole_paper_reproduced=False,whole_goal_complete=False,visual_review=dict(desktop_card=True,mobile_dark_warnings=True,no_horizontal_overflow=True),**packing))
    verify(folder=candidate,check_manifest=False)
    assert all(pin(ROOT/n)==v for n,v in pins.items()) and all(pin(ROOT/n)==v for n,v in held.items())
    for name in fresh:
        with (data/name).open('xb') as stream:stream.write((candidate/name).read_bytes())
    refresh_manifest(data,(data/'SHA256SUMS').read_bytes());verify()
    assert all(pin(ROOT/n)==v for n,v in held.items())
    save(out/'COMPLETED.json',dict(status=STATUS,candidate_verified_before_publication=True,archive=pin(archive),logical_files=len(files),unrelated_files_preserved=18,whole_goal_complete=False))
    print('Sealed',len(files),'logical files;',archive.stat().st_size,'bytes;25 scientific gates,320 browser checks; goal remains open')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable evidence gates')
    main()
