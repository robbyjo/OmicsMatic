# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal a separately verified current catalog proof while preserving all historical scientific evidence."""
import argparse,json,os
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack,unpack
from render_palantir_catalog import refresh_manifest
from verify_gseapy_catalog import STATUS,verify


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();base=ROOT/'build/literature-review/gseapy-runtime';data=ROOT/'docs/literature/omics-2023-2026';assert not out.exists() and out.is_relative_to(base) and not out.name.startswith('catalog-');out.mkdir(parents=True);candidate=out/'candidate';candidate.mkdir();fresh=['gseapy-source-audit.json','gseapy-catalog-validation.json','gseapy-catalog-support.zip'];assert not any((data/n).exists() for n in fresh)
    gates=json.loads((base/'catalog-gates-2/COMPLETED.json').read_bytes());ui=json.loads((base/'catalog-ui-1/COMPLETED.json').read_bytes());audit=json.loads((base/'catalog-source-audit-1/COMPLETED.json').read_bytes());assert len(gates['checks'])==26 and ui['status']=='passed-local-general-and-gseapy-catalog-browser-checks' and audit['all_non_gseapy_source_roles_preserved'];sources=set(gates['source_pins'])|set(ui['source_pins'])|set(audit['source_pins'])
    sources.update('tools/literature/'+n for n in ['gseapy_catalog.py','gseapy_current_evidence.py','curate_gseapy_uses.py','gseapy-use-reviews.json','prepare_gseapy_catalog.py','render_gseapy_catalog.py','run_gseapy_catalog_checks.py','audit_gseapy_catalog.py','prepare_gseapy_catalog_ui.py','check_gseapy_review_ui.cjs','validate_gseapy_catalog_ui.py','update_gseapy_catalog_readme.py','verify_gseapy_catalog.py','seal_gseapy_catalog.py','catalog_source_integrity.py','collect_omics_review.py','collect_htseq_omics.py','curate_shapeit_uses.py','reviewed-uses.json','run_scfates_catalog_checks.py']);sources.add('docs/literature/GSEAPY_CATALOG.md');sources={n for n in sources if not n.startswith('site/')};pins={n:pin(ROOT/n) for n in sorted(sources)};files={'current-source/'+n:(ROOT/n).read_bytes() for n in pins}
    for directory in sorted(base.iterdir()):
        if not directory.is_dir() or not directory.name.startswith('catalog-'):continue
        for current,dirs,names in os.walk(directory,topdown=True,followlinks=False):
            dirs[:]=[n for n in dirs if n!='__pycache__' and not(Path(current)/n).is_symlink()]
            for n in names:
                file=Path(current)/n
                if file.is_file() and not file.is_symlink():files[file.relative_to(ROOT).as_posix()]=file.read_bytes()
    for failed in sorted(base.glob('seal-catalog-*')):
        if failed==out or not(failed/'FAILURE.md').is_file():continue
        prior=json.loads((failed/'candidate/gseapy-catalog-validation.json').read_bytes());oldarchive=failed/'candidate/gseapy-catalog-support.zip';assert pin(oldarchive)==prior['archive'];prefix='failed-seal/'+failed.name+'/'
        files[prefix+'VALIDATION.json']=(failed/'candidate/gseapy-catalog-validation.json').read_bytes();files[prefix+'FAILURE.md']=(failed/'FAILURE.md').read_bytes()
        for n,raw in unpack(oldarchive,prior).items():files[prefix+'logical/'+n]=raw
    for n in ['site/literature-review.html','site/literature-warnings.html']:files['current-output/'+n]=(ROOT/n).read_bytes()
    before=base/'catalog-prepare-1/before/docs/literature/omics-2023-2026'
    for n in ['papers.json','tool-inventory.json','summary.json','README.md','WARNINGS.md','SHA256SUMS']:files['catalog-before/'+n]=(before/n).read_bytes()
    snapshot=['papers.json','tool-inventory.json','summary.json','README.md','WARNINGS.md','catalog.md','implementation-plan.md','gseapy-role-coverage.json','gseapy-prior-reviews.json.gz','gseapy-prior-papers.json.gz','gseapy-render-shared-source-compatibility.json','gseapy-reviews-shared-source-compatibility.json','gseapy-readme-evolution.json','gseapy-source-audit.json'];(candidate/'gseapy-source-audit.json').write_bytes((base/'catalog-source-audit-1/COMPLETED.json').read_bytes())
    for n in snapshot:files['catalog-snapshot/'+n]=((candidate if n=='gseapy-source-audit.json' else data)/n).read_bytes()
    parents=['docs/literature/omics-2023-2026/scfates-catalog-validation.json','docs/literature/omics-2023-2026/scfates-catalog-support.zip']
    for folder in ['gseapy-statistics-development','gseapy-workflow-foundation','gseapy-execution-development']:
        parents.extend(p.relative_to(ROOT).as_posix() for p in (ROOT/'docs/literature'/folder).iterdir() if p.is_file() and (p.name=='VALIDATION.json' or p.suffix=='.zip'))
    parentpins={n:pin(ROOT/n) for n in sorted(parents)};held=json.loads((ROOT/'build/literature-review/push-checkpoint-2026-11-08-gseapy-execution-1/selection.json').read_bytes())['preserved'];assert len(held)==18 and all(pin(ROOT/n)==v for n,v in held.items());files['preserved-unrelated-worktree-pins.json']=(json.dumps(held,indent=2)+'\n').encode();canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');archive=candidate/'gseapy-catalog-support.zip';packing=pack(archive,files);assert archive.stat().st_size<95000000
    proof=dict(schema_version=1,status=STATUS,source_pins=pins,parent_proofs=parentpins,archive=pin(archive),catalog_snapshot=snapshot,canonical_executable_unchanged=canonical,new_scoped_integrations=88,including_existing_adapters=91,named_tools=400,observations=2465,reviewed_roles=993,supporting_pointers=344,missing=253,availability_rows_changed=1,paper_roles_changed=6,unchanged_prior_reviews=987,new_reviews=5,refined_existing_reviews=1,new_catalog_credit=True,scientific_verifiers_changed=False,scientific_source_exception_added=False,independent_permutation_generation=False,independent_gsva_refit=False,whole_paper_reproduced=False,whole_goal_complete=False,historical_metadata_scope='Unchanged execution gates use four exact archived988-role catalog inputs; frozen980-role gate uses its exact two metadata inputs. All scientific source/runtime/numerical paths remain live and strict.',visual_review=dict(desktop_card=True,mobile_dark_warnings=True,no_horizontal_overflow=True),**packing);save(candidate/'gseapy-catalog-validation.json',proof);verify(folder=candidate,check_manifest=False);assert all(pin(ROOT/n)==v for n,v in pins.items()) and all(pin(ROOT/n)==v for n,v in held.items())
    for n in fresh:
        with (data/n).open('xb') as file:file.write((candidate/n).read_bytes())
    refresh_manifest(data,(data/'SHA256SUMS').read_bytes());verify();save(out/'COMPLETED.json',dict(status=STATUS,candidate_verified_before_publication=True,archive=pin(archive),logical_files=len(files),unrelated_files_preserved=18,whole_goal_complete=False));print('Sealed',len(files),'logical files;',archive.stat().st_size,'bytes;26 scientific gates and325 browser checks; goal active')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable catalog seal gates')
    main()
