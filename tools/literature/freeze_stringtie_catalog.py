# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze guarded catalog promotion and browser evidence; preserve all scientific checkpoints and failed attempts."""
import argparse
import hashlib
import json
from pathlib import Path,PurePosixPath
import stat
import zipfile
from catalog_fixed_cohort import verify_fixed_cohort
from catalog_source_integrity import verify_source
from stringtie_catalog import verify as verify_stringtie

ROOT=Path(__file__).resolve().parents[2]
def read(p):return json.loads(p.read_bytes())
def pin(p):
    with p.open('rb') as f:return dict(bytes=p.stat().st_size,sha256=hashlib.file_digest(f,'sha256').hexdigest())
def rawpin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--validation',type=Path,required=True);args=parser.parse_args();matrix=args.validation.resolve();base=ROOT/'build/literature-review/stringtie-runtime';assert matrix.is_relative_to(base.resolve())
    data=ROOT/'docs/literature/omics-2023-2026';target=data/'stringtie-catalog-validation.json';archive=data/'stringtie-catalog-support.zip';assert not target.exists() and not archive.exists()
    result=read(matrix/'COMPLETED.json');assert result['status']=='passed-StringTie-catalog-provider-source-preservation-and-browser-integration' and not(matrix/'INCOMPLETE.json').exists() and not(matrix/'FAILURE.json').exists()
    assert result['catalog_counts']==dict(new_scoped_integrations=68,including_existing=71,missing=259,upstream_adapters=52,native=16,reviewed_uses=827,supporting_pointers=253)
    assert result['only_tool_metadata_changed']=='stringtie' and result['source_papers']==250 and result['observations']==2442 and result['broad_browser_checks']>=299 and result['stringtie_browser_checks']>=12
    assert result['actual_dark_theme_control_tested'] and result['dark_warning_theme_persisted_after_navigation'] and result['dark_background']=='rgb(13, 20, 16)'
    assert result['literature_pages_still_withheld'] and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==result['canonical_executable_unchanged'] and pin(ROOT/'.github/workflows/pages.yml')==result['pages_workflow_unchanged']
    if (matrix/'owned-server-closed.json').exists():assert read(matrix/'owned-server-closed.json')['only_owned_server_terminated']
    proof=verify_stringtie(ROOT,data);assert result['root_artifact']==proof['artifact']
    papers=read(data/'papers.json');verify_fixed_cohort(ROOT,data,papers);assert len(papers)==250 and sum(len(p['tools']) for p in papers)==2442
    inventory=read(data/'tool-inventory.json');assert len(inventory)==387 and next(t for t in inventory if t['id']=='stringtie')['implementation_status']=='upstream-adapter-validated'
    roles=read(data/'stringtie-role-coverage.json');assert roles['total_reviews']==827 and roles['supporting_pointers']==253 and roles['applied']
    for name in ['stringtie-dispatch-shared-source-compatibility.json','stringtie-use-shared-source-compatibility.json','stringtie-catalog-shared-source-compatibility.json']:
        document=read(data/name);assert document['status']=='passed-dispatch-catalog-only-source-deltas' and document['tool_id']=='stringtie'
        for row in document['paths']:verify_source(ROOT,data,row['path'],row['before_sha256'])
    sums=dict(line.split('  ',1)[::-1] for line in (data/'SHA256SUMS').read_text().splitlines());assert set(sums)=={p.name for p in data.iterdir() if p.is_file() and p.name!='SHA256SUMS'} and all(pin(data/n)['sha256']==h for n,h in sums.items())
    names=['tools/literature/stringtie_catalog.py','tools/literature/curate_stringtie_uses.py','tools/literature/wire_stringtie_catalog.py','tools/literature/validate_stringtie_catalog.py','tools/literature/freeze_stringtie_catalog.py','tools/literature/check_stringtie_review_ui.cjs','tools/literature/render_review.py','tools/literature/check_review_ui.cjs','tools/literature/reviewed-uses.json','tools/literature/tool-registry.json','site/assets/styles.css','site/assets/app.js']
    sources=dict(proof['source_pins']);sources.update({n:pin(ROOT/n) for n in names});entries={}
    def add(path,name):
        p=PurePosixPath(name);assert path.is_file() and not path.is_symlink() and name not in entries and not p.is_absolute() and '..' not in p.parts and '\\' not in name;entries[name]=path
    for n in sources:add(ROOT/n,'source/'+n)
    for checkpoint in ['stringtie-main-workflow','stringtie-typed-workflow']:
        for n in ['VALIDATION.json','SHA256SUMS','controls.zip']:add(ROOT/'docs/literature'/checkpoint/n,checkpoint+'/'+n)
    for n in ['papers.json','tool-inventory.json','summary.json','catalog-enrichment.json','WARNINGS.md','catalog.md','implementation-plan.md','SHA256SUMS','fixed-250-paper-cohort.json.gz','stringtie-role-coverage.json','stringtie-dispatch-shared-source-compatibility.json','stringtie-use-shared-source-compatibility.json','stringtie-catalog-shared-source-compatibility.json']:add(data/n,'catalog-snapshot/'+n)
    for n in ['literature-review.html','literature-warnings.html']:add(ROOT/'site'/n,'rendered-site/'+n)
    for folder,prefix in [(matrix,'current-catalog-validation'),(base/'catalog-validation-1','retained-failed-catalog-attempt'),(base/'catalog-validation-2','retained-superseded-mobile-theme-validation'),(base/'role-curation-1','source-role-application')]:
        assert folder.is_dir()
        for p in sorted(folder.rglob('*')):
            assert not p.is_symlink()
            if p.is_file() and (p.suffix!='.png' or 'stringtie-ui' in p.relative_to(folder).parts):add(p,prefix+'/'+p.relative_to(folder).as_posix())
    assert (base/'catalog-validation-1/FAILURE.json').exists() and (base/'catalog-validation-1/INCOMPLETE.json').exists() and (base/'catalog-validation-2/SUPERSEDED-MOBILE-THEME.json').exists()
    members={}
    with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for n,p in sorted(entries.items()):
            raw=p.read_bytes();info=zipfile.ZipInfo(n,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=(stat.S_IFREG|0o644)<<16;z.writestr(info,raw);members[n]=rawpin(raw)
    with zipfile.ZipFile(archive) as z:assert z.testzip() is None and len(z.namelist())==len(set(z.namelist()))==len(members) and all(rawpin(z.read(n))==p for n,p in members.items())
    assert archive.stat().st_size<95000000 and all(pin(ROOT/n)==p for n,p in sources.items())
    result.update(schema_version=1,status='passed-scoped-StringTie-catalog-and-root-integration',archive=dict(path=archive.name,**pin(archive),entries=len(members)),archive_files=members,source_pins=sources,core_root_checkpoint=pin(ROOT/'docs/literature/stringtie-main-workflow/VALIDATION.json'),core_root_archive=pin(ROOT/'docs/literature/stringtie-main-workflow/controls.zip'),standalone_checkpoint=pin(ROOT/'docs/literature/stringtie-typed-workflow/VALIDATION.json'),standalone_archive=pin(ROOT/'docs/literature/stringtie-typed-workflow/controls.zip'),source_role_coverage=pin(data/'stringtie-role-coverage.json'),scientific_checkpoint_sources_unchanged=True,implementation_promoted=True,broad_UI_summary_and_process_logs_archived=True,repeated_broad_UI_screenshots_not_archived=True,focused_UI_screenshots_archived=True,first_card_missing_adjacent_tool_wording_failure_retained=True,incorrect_initial_mobile_dark_filename_claim_superseded_and_retained=True,actual_mobile_dark_appearance_verified_after_navigation=True,whole_paper_reproduced=False,
        required_action='Use STRINGTIE_TOOLS.md and the exact development JAR with stringtie-tools. Linux x86_64 Java is required for fitting; copied verification and setup export also work on Windows. Run permitted install/download only through the standalone kit. SKIP3 means skip dependent analysis until the exact named requirement is permitted and available; FAIL2 means no result, retained diagnostics and correction into NEW paths. Read warnings, source/input/output audits and original process logs. Preserve every omission and source ambiguity; no biological or full-paper certificate is claimed. Cell Ranger remains excluded. Literature pages stay withheld, and no push/release/deployment or whole-goal completion is claimed.')
    target.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8',newline='\n')
    order=list(sums);order.extend([archive.name,target.name]);assert len(order)==len(set(order)) and set(order)=={p.name for p in data.iterdir() if p.is_file() and p.name!='SHA256SUMS'}
    (data/'SHA256SUMS').write_text(''.join(pin(data/n)['sha256']+'  '+n+'\n' for n in order),encoding='ascii',newline='\n')
    assert all(pin(data/n)['sha256']==h for n,h in sums.items()) and all(pin(ROOT/n)==p for n,p in sources.items())
    print(json.dumps(dict(status=result['status'],archive=result['archive'],source_pins=len(sources),catalog_files=len(order),counts=result['catalog_counts'])))

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete catalog freeze checks')
    main()
