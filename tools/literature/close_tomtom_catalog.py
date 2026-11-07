# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Close only Tomtom's verified comparison credit, preserving the cohort and every unrelated tool object."""
from collections import Counter
import hashlib
import json
from pathlib import Path
import stat
import sys
import zipfile

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from catalog_source_integrity import verify_source,reverse_delta
from catalog_fixed_cohort import verify_fixed_cohort
from tomtom_catalog import verify


def read(path):return json.loads(path.read_bytes())
def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())


def main():
    base=ROOT/'build/literature-review/meme-runtime/tomtom-workflow-development-1';before=base/'catalog-promotion-1';data=ROOT/'docs/literature/omics-2023-2026';target=data/'tomtom-catalog-validation.json';archive=data/'tomtom-catalog-support.zip';assert not target.exists() and not archive.exists()
    proof=verify(ROOT,data);papers=read(data/'papers.json');inventory=read(data/'tool-inventory.json');summary=read(data/'summary.json');assert len(papers)==250 and len(inventory)==387 and sum(len(p['tools']) for p in papers)==2442;verify_fixed_cohort(ROOT,data,papers)
    counts=dict(Counter(t['implementation_status'] for t in inventory));assert counts==summary['implementation_status_counts'] and tuple(counts[k] for k in ['native-subset-validated','upstream-adapter-validated','existing-adapter','missing','excluded-by-user'])==(15,51,3,261,1)
    old={t['id']:t for t in read(before/'before-tool-inventory.json')};new={t['id']:t for t in inventory};assert set(old)==set(new) and [name for name in old if old[name]!=new[name]]==['tomtom'];assert old['tomtom']['implementation_status']=='missing' and new['tomtom']['implementation_status']=='upstream-adapter-validated'
    roles=read(data/'meme-program-role-coverage.json');assert roles['total_reviews']==815 and roles['supporting_pointers']==248 and len(read(ROOT/'tools/literature/reviewed-uses.json')['reviews'])==815
    focused=read(base/'catalog-focused-ui-2/ui-validation.json');broad=read(base/'catalog-broad-ui-2/ui-validation.json');assert focused['status']==broad['status']=='passed' and len(focused['checks'])==5 and len(broad['checks'])==299
    assert 'BUILD SUCCESSFUL' in (before/'checkWebsite.stdout').read_text() and not (before/'checkWebsite.stderr').read_bytes()
    delta=read(data/'tomtom-catalog-shared-source-compatibility.json');assert len(delta['paths'])==1 and len(delta['paths'][0]['edits'])==6 and reverse_delta((ROOT/'tools/literature/render_review.py').read_bytes(),delta['paths'][0])==(before/'before-render_review.py').read_bytes()
    rejected=[]
    for name in ['src/main/java/org/jlinalg/cli/TomtomWorkflow.java','src/main/java/org/jlinalg/sequence/TomtomResults.java','src/main/java/org/jlinalg/cli/MemeBuildEvidence.java']:
        try:verify_source(ROOT,data,name,'0'*64)
        except ValueError:rejected.append(name)
        else:raise AssertionError('Incorrect scientific source hash accepted: '+name)
    source_names=['tools/literature/'+n for n in ['close_tomtom_catalog.py','tomtom_catalog.py','tomtom_catalog_bridge.py','tomtom_dispatch_bridge.py','check_tomtom_review_ui.cjs','render_review.py','check_review_ui.cjs','catalog_source_integrity.py','catalog_fixed_cohort.py','nextflow_current_catalog.py','salmon_native_current_catalog.py','salmon_current_catalog.py']];sources={name:pin(ROOT/name) for name in source_names};entries={}
    def add(path,name):assert path.is_file() and not path.is_symlink() and name not in entries;entries[name]=path
    def tree(folder,prefix):
        for path in sorted(folder.rglob('*')):
            if path.is_file():add(path,prefix+'/'+path.relative_to(folder).as_posix())
    for name in sources:add(ROOT/name,'source/'+name)
    for name in ['catalog-promotion-1','catalog-focused-ui-1','catalog-focused-ui-2','catalog-broad-ui-1','catalog-broad-ui-2']:tree(base/name,'evidence/'+name)
    for name in ['catalog-focused-ui-2.stdout','catalog-focused-ui-2.stderr','catalog-broad-ui-2.stdout','catalog-broad-ui-2.stderr']:add(base/name,'evidence/'+name)
    for name in ['summary.json','tool-inventory.json','meme-program-role-coverage.json','tomtom-catalog-shared-source-compatibility.json','tomtom-dispatch-shared-source-compatibility.json']:add(data/name,'catalog-snapshot/'+name)
    for name in ['literature-review.html','literature-warnings.html']:add(ROOT/'site'/name,'local-site/'+name)
    workflow=ROOT/'docs/literature/meme-tomtom-workflow-development';add(workflow/'VALIDATION.json','workflow-checkpoint/VALIDATION.json');add(workflow/'SHA256SUMS','workflow-checkpoint/SHA256SUMS');members={}
    with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,path in sorted(entries.items()):
            raw=path.read_bytes();info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=(stat.S_IFREG|0o644)<<16;z.writestr(info,raw);members[name]=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
    with zipfile.ZipFile(archive) as z:
        assert z.testzip() is None and len(z.infolist())==len(set(z.namelist()))==len(members)
        for name,value in members.items():raw=z.read(name);assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==value
    assert archive.stat().st_size<95000000 and all(pin(ROOT/name)==value for name,value in sources.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==proof['canonical_executable_unchanged']
    result=dict(schema_version=1,status='passed-scoped-original-Tomtom-catalog-and-browser-closure',source_pins=sources,archive=dict(path=archive.name,**pin(archive),entries=len(members)),archive_files=members,workflow_validation=pin(workflow/'VALIDATION.json'),workflow_archive=pin(workflow/'controls.zip'),artifact=proof['artifact'],canonical_executable_unchanged=proof['canonical_executable_unchanged'],catalog_counts=dict(papers=250,tools=387,observations=2442,reviewed_uses=815,supporting_pointers=248,new_scoped_integrations=66,including_existing=69,missing=261),preserved_unrelated_tool_objects=386,preserved_source_roles=7,focused_browser_checks=5,broad_browser_checks=299,website_check_passed=True,scientific_source_hash_rejections=rejected,generic_scientific_source_waivers_added=False,prior_provider_files_changed=False,new_scoped_runtime_implementation_credit=True,whole_paper_reproduced=False,biological_accuracy_certified=False,released=False,pushed=False,deployed=False,whole_goal_complete=False,required_action='Use the exact development Tomtom candidate and examples/tomtom/README.md; older canonical/released JARs lack tomtom-tools. Read all warnings and original logs. Complete only permitted separate setup; missing/forbidden requirements mean SKIP3 and skip dependent analysis. Integrity/process/capacity/cancellation/finalization failures mean FAIL2 with no admitted result. Preserve partials/native workers and retry corrected requests into NEW paths. Require overall COMPLETED.json and successful complete --verify. Unreported values remain NA; no silent historical edition/resource/settings substitution. Cell Ranger remains excluded;261 integrations remain missing.')
    target.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8',newline='\n');manifest={p.name:pin(p)['sha256'] for p in sorted(data.iterdir()) if p.is_file() and p.name!='SHA256SUMS'};(data/'SHA256SUMS').write_text(''.join(value+'  '+name+'\n' for name,value in manifest.items()),encoding='ascii',newline='\n');assert all(pin(data/name)['sha256']==value for name,value in manifest.items());print(json.dumps({k:result[k] for k in ['status','catalog_counts','preserved_unrelated_tool_objects','focused_browser_checks','broad_browser_checks','archive']}))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete scoped Tomtom catalog closure checks')
    main()
