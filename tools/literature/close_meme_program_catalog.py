# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze source-role corrections and browser evidence; no new implementation credit."""
from collections import Counter
import gzip
import hashlib
import json
from pathlib import Path
import stat
import sys
import zipfile

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from catalog_fixed_cohort import verify_fixed_cohort
from catalog_source_integrity import verify_source


def read(path):
    return json.loads(path.read_bytes())


def pin(path):
    with path.open('rb') as stream:
        return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())


def main():
    data=ROOT/'docs/literature/omics-2023-2026';base=ROOT/'build/literature-review/meme-runtime'
    target=data/'meme-program-catalog-validation.json';archive=data/'meme-program-catalog-support.zip'
    assert not target.exists() and not archive.exists()
    papers=read(data/'papers.json');inventory=read(data/'tool-inventory.json');summary=read(data/'summary.json')
    assert len(papers)==250 and len(inventory)==387 and sum(len(p['tools']) for p in papers)==2442
    assert (summary['papers'],summary['distinct_named_tools'],summary['paper_tool_observations'])==(250,387,2442)
    counts=dict(Counter(t['implementation_status'] for t in inventory));assert counts==summary['implementation_status_counts']
    assert tuple(counts[k] for k in ['native-subset-validated','upstream-adapter-validated','existing-adapter','missing','excluded-by-user'])==(15,50,3,262,1)
    verify_fixed_cohort(ROOT,data,papers)
    coverage=read(data/'meme-program-role-coverage.json');assert coverage['applied'] and not coverage['implementation_promoted']
    assert (coverage['total_reviews'],coverage['supporting_pointers'],coverage['new_reviews'])==(815,248,22)
    old=json.loads(gzip.decompress((base/'role-curation-apply-1/before-papers.json.gz').read_bytes()))
    old_reviews=json.loads(gzip.decompress((base/'role-curation-apply-1/before-reviewed-uses.json.gz').read_bytes()))
    reviews=read(ROOT/'tools/literature/reviewed-uses.json');assert len(reviews['reviews'])==815 and reviews['reviews'][:793]==old_reviews['reviews']
    affected={'meme','bwa','meme-chip','dreme','centrimo','tomtom','streme','hyphy','bwa-meme'}
    for a,b in zip(old,papers):
        assert {k:v for k,v in a.items() if k!='tools'}=={k:v for k,v in b.items() if k!='tools'}
        assert [t for t in a['tools'] if t['tool_id'] not in affected]==[t for t in b['tools'] if t['tool_id'] not in affected]
    prior={t['id']:t for t in json.loads(gzip.decompress((base/'role-curation-apply-1/before-tool-inventory.json.gz').read_bytes()))}
    preserved=[t for t in inventory if t['id'] in prior and t['id'] not in {'meme','bwa'}]
    assert len(preserved)==378 and all(t==prior[t['id']] for t in preserved)
    focused=read(base/'role-ui-1/ui-validation.json');broad=read(base/'role-broad-ui-1/ui-validation.json')
    assert focused['status']==broad['status']=='passed' and len(focused['checks'])==3 and len(broad['checks'])==299
    rejected=[]
    for name in ['src/main/java/org/jlinalg/cli/NextflowRecipe.java','src/main/java/org/jlinalg/cli/SalmonQuantWorkflow.java']:
        try:
            verify_source(ROOT,data,name,'0'*64)
        except ValueError:
            rejected.append(name)
        else:
            raise AssertionError('Incorrect scientific source hash accepted: '+name)
    filenames=['close_meme_program_catalog.py','audit_meme_roles.py','prepare_meme_program_registry.py','prepare_meme_program_reviews.py','meme-program-use-reviews.json','curate_meme_programs.py','bootstrap_meme_program_inventory.py','meme_program_ui_bridge.py','derive_current_catalog_providers.py','catalog_fixed_cohort.py','nextflow_current_catalog.py','salmon_native_current_catalog.py','salmon_current_catalog.py','check_meme_program_review_ui.cjs','check_review_ui.cjs','render_review.py']
    sources=['tools/literature/'+name for name in filenames]+['docs/literature/MEME_DEVELOPMENT.md']
    pins={name:pin(ROOT/name) for name in sources};entries={}
    def add(path,name):
        assert path.is_file() and not path.is_symlink() and name not in entries
        entries[name]=path
    for name in sources:
        add(ROOT/name,'source/'+name)
    for area,label in [(base/'role-curation-apply-1','curation'),(base/'role-curation-dry-run-1','dry-run'),(base/'role-ui-1','focused-browser'),(base/'role-broad-ui-1','broad-browser')]:
        for path in sorted(area.rglob('*')):
            if path.is_file():
                add(path,'evidence/'+label+'/'+path.relative_to(area).as_posix())
    for name in ['meme-program-role-coverage.json','meme-program-provider-scope-validation.json','meme-program-use-shared-source-compatibility.json','meme-program-ui-shared-source-compatibility.json','meme-program-provider-shared-source-compatibility.json','fixed-250-paper-cohort.json.gz','summary.json','tool-inventory.json','papers.json']:
        add(data/name,'catalog-snapshot/'+name)
    for area in ['build-tools-1','build-tools-2','build-tools-3','linux-build-1','linux-build-2','linux-build-3','original-controls-1','original-controls-2']:
        for name in ['COMPLETED.json','FAILURE.json','WARNINGS.md']:
            if (base/area/name).is_file():
                add(base/area/name,'development-context/'+area+'/'+name)
    census={}
    with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,path in sorted(entries.items()):
            raw=path.read_bytes();item=zipfile.ZipInfo(name,(1980,1,1,0,0,0));item.compress_type=zipfile.ZIP_DEFLATED;item.external_attr=(stat.S_IFREG|0o644)<<16
            z.writestr(item,raw);census[name]=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
    with zipfile.ZipFile(archive) as z:
        assert len(z.infolist())==len(set(z.namelist()))==len(census) and z.testzip() is None
        for name,value in census.items():
            raw=z.read(name);assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==value
    assert archive.stat().st_size<95_000_000 and all(pin(ROOT/name)==value for name,value in pins.items())
    canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');assert canonical==read(data/'salmon-workflow-catalog-validation.json')['canonical_executable_unchanged']
    result=dict(schema_version=1,status='passed-MEME-program-primary-source-and-browser-catalog-correction',source_pins=pins,archive=dict(path=archive.name,**pin(archive),entries=len(census)),archive_files=census,
        catalog_counts=dict(papers=250,tools=387,observations=2442,reviewed_uses=815,supporting_pointers=248,new_scoped_integrations=65,including_existing=68,missing=262),
        preserved_prior_reviews=793,preserved_unrelated_tool_objects=378,new_programs=7,removed_misassignments=coverage['removed_misassignments'],focused_browser_checks=3,broad_browser_checks=299,
        cohort_identity_exact=True,scientific_source_hash_rejections=rejected,frozen_provider_files_changed=False,generic_scientific_source_waivers_added=False,canonical_executable_unchanged=canonical,
        new_runtime_implementation_credit=False,scientific_runtime_admitted=False,released=False,pushed=False,deployed=False,whole_goal_complete=False,
        required_action='Read MEME_DEVELOPMENT.md and all source-role notes. Build/version/exit0 controls do not admit project analyses. Complete numerical/output/runtime/Java/packaged/example gates before new credit. Missing/forbidden dependencies mean SKIP dependent analysis with named cause and permitted recovery. Preserve partials and every attempt; use NEW paths. Cell Ranger remains excluded.')
    target.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8',newline='\n')
    manifest={p.name:pin(p)['sha256'] for p in sorted(data.iterdir()) if p.is_file() and p.name!='SHA256SUMS'}
    (data/'SHA256SUMS').write_text(''.join(value+'  '+name+'\n' for name,value in manifest.items()),encoding='ascii',newline='\n')
    assert all(pin(data/name)['sha256']==value for name,value in manifest.items())
    print(json.dumps({k:result[k] for k in ['status','catalog_counts','preserved_prior_reviews','new_programs','focused_browser_checks','broad_browser_checks']}))


if __name__=='__main__':
    if not __debug__:
        raise RuntimeError('Never disable source correction closure checks')
    main()
