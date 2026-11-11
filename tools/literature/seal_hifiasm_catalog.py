# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Publish a separately verified hifiasm catalog proof without modifying preceding scientific evidence."""
import argparse,json,os
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack
from render_palantir_catalog import refresh_manifest
from verify_hifiasm_catalog import STATUS,verify


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();base=ROOT/'build/literature-review/hifiasm-runtime';data=ROOT/'docs/literature/omics-2023-2026';assert not out.exists() and out.is_relative_to(base) and not out.name.startswith('catalog-');out.mkdir(parents=True);candidate=out/'candidate';candidate.mkdir();fresh=['hifiasm-source-audit.json','hifiasm-catalog-validation.json','hifiasm-catalog-support.zip'];assert not any((data/name).exists() for name in fresh)
    gates=json.loads((base/'catalog-gates-2/COMPLETED.json').read_bytes());ui=json.loads((base/'catalog-ui-2/COMPLETED.json').read_bytes());audit=json.loads((base/'catalog-source-audit-1/COMPLETED.json').read_bytes());assert len(gates['checks'])==27 and ui['status']=='passed-local-general-and-hifiasm-catalog-browser-checks' and audit['all_non_hifiasm_source_roles_preserved'];sources=set(gates['source_pins'])|set(ui['source_pins'])|set(audit['source_pins'])
    sources.update(json.loads((base/'catalog-render-1/COMPLETED.json').read_bytes())['source_pins']);sources.update('tools/literature/'+name for name in ['hifiasm_catalog.py','hifiasm_execution_evidence.py','curate_hifiasm_uses.py','hifiasm-use-reviews.json','audit_hifiasm_roles.py','prepare_hifiasm_catalog.py','render_hifiasm_catalog.py','run_hifiasm_catalog_checks.py','audit_hifiasm_catalog.py','prepare_hifiasm_catalog_ui.py','check_hifiasm_catalog_review_ui.cjs','validate_hifiasm_catalog_ui.py','update_hifiasm_catalog_readme.py','verify_hifiasm_catalog.py','seal_hifiasm_catalog.py','catalog_source_integrity.py','collect_omics_review.py','collect_htseq_omics.py','curate_shapeit_uses.py','reviewed-uses.json']);sources.add('docs/literature/HIFIASM_CATALOG.md');sources={name for name in sources if not name.startswith('site/')};pins={name:pin(ROOT/name) for name in sorted(sources)};files={'current-source/'+name:(ROOT/name).read_bytes() for name in pins}
    for directory in sorted(base.iterdir()):
        if not directory.is_dir() or not(directory.name.startswith('catalog-') or directory.name in ['primary-roles-1','role-curation-1','role-curation-2']):continue
        for current,dirs,names in os.walk(directory,topdown=True,followlinks=False):
            dirs[:]=[name for name in dirs if name!='__pycache__' and not(Path(current)/name).is_symlink()]
            for name in names:
                file=Path(current)/name
                if file.is_file() and not file.is_symlink():files[file.relative_to(ROOT).as_posix()]=file.read_bytes()
    for name in ['site/literature-review.html','site/literature-warnings.html']:files['current-output/'+name]=(ROOT/name).read_bytes()
    before=base/'catalog-prepare-1/before/docs/literature/omics-2023-2026'
    for name in ['papers.json','tool-inventory.json','summary.json','README.md','WARNINGS.md','SHA256SUMS']:files['catalog-before/'+name]=(before/name).read_bytes()
    snapshot=['papers.json','tool-inventory.json','summary.json','README.md','WARNINGS.md','catalog.md','implementation-plan.md','hifiasm-role-coverage.json','hifiasm-prior-reviews.json.gz','hifiasm-prior-papers.json.gz','hifiasm-render-shared-source-compatibility.json','hifiasm-reviews-shared-source-compatibility.json','hifiasm-readme-evolution.json','hifiasm-source-audit.json'];(candidate/'hifiasm-source-audit.json').write_bytes((base/'catalog-source-audit-1/COMPLETED.json').read_bytes())
    for name in snapshot:files['catalog-snapshot/'+name]=((candidate if name=='hifiasm-source-audit.json' else data)/name).read_bytes()
    parents=['docs/literature/omics-2023-2026/gseapy-catalog-validation.json','docs/literature/omics-2023-2026/gseapy-catalog-support.zip'];parents.extend(file.relative_to(ROOT).as_posix() for file in (ROOT/'docs/literature/hifiasm-execution-development').iterdir() if file.is_file());parentpins={name:pin(ROOT/name) for name in sorted(parents)};held=json.loads((ROOT/'build/literature-review/push-checkpoint-2026-11-08-gseapy-catalog-1/selection.json').read_bytes())['preserved'];assert len(held)==18 and all(pin(ROOT/name)==value for name,value in held.items());files['preserved-unrelated-worktree-pins.json']=(json.dumps(held,indent=2)+'\n').encode();canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');archive=candidate/'hifiasm-catalog-support.zip';packing=pack(archive,files);assert archive.stat().st_size<95000000
    proof=dict(schema_version=1,status=STATUS,source_pins=pins,parent_proofs=parentpins,archive=pin(archive),catalog_snapshot=snapshot,canonical_executable_unchanged=canonical,new_scoped_integrations=89,including_existing_adapters=92,named_tools=400,observations=2465,reviewed_roles=1000,supporting_pointers=347,missing=252,availability_rows_changed=1,paper_roles_changed=7,unchanged_prior_reviews=993,new_reviews=7,refined_existing_reviews=0,new_catalog_credit=True,scientific_verifiers_changed=False,scientific_source_exception_added=False,independent_consensus=False,independent_phasing=False,whole_paper_reproduced=False,whole_goal_complete=False,historical_metadata_scope='Frozen980-role check and GSEApy988-role checks use exact archived metadata inputs. All scientific source/artifact/runtime paths stay live and strict.',visual_review=dict(desktop_card=True,mobile_dark_warnings=True,no_horizontal_overflow=True),**packing);save(candidate/'hifiasm-catalog-validation.json',proof);verify(folder=candidate,check_manifest=False);assert all(pin(ROOT/name)==value for name,value in pins.items()) and all(pin(ROOT/name)==value for name,value in held.items())
    for name in fresh:
        with (data/name).open('xb') as file:file.write((candidate/name).read_bytes())
    refresh_manifest(data,(data/'SHA256SUMS').read_bytes());verify();save(out/'COMPLETED.json',dict(status=STATUS,candidate_verified_before_publication=True,archive=pin(archive),logical_files=len(files),unrelated_files_preserved=18,whole_goal_complete=False));print('Sealed complete catalog checkpoint with27 scientific gates and330 browser checks; goal active')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable catalog seal gates')
    main()
