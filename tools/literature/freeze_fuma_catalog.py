# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze the scoped FUMA catalog checkpoint without rewriting scientific evidence."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import sys
import zipfile

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
import fuma_catalog


def read(path):return json.loads(path.read_text(encoding='utf-8'))
def write(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')


def main():
    if not __debug__:raise RuntimeError('Never disable checkpoint assertions')
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--run',type=Path,required=True);a=parser.parse_args()
    run=a.run.resolve();assert run.is_relative_to((ROOT/'build/literature-review/fuma-runtime').resolve())
    data=ROOT/'docs/literature/omics-2023-2026';output=data/'fuma-catalog-validation.json';support=data/'fuma-catalog-support.zip'
    assert not output.exists() and not support.exists(),'Preserve existing checkpoints; choose a new checkpoint instead'
    proof,roles=fuma_catalog.verify(ROOT,data);validation=read(run/'validation-2/COMPLETED.json')
    assert validation['status']=='passed-scoped-native-FUMA-catalog-and-source-preservation'
    for name,value in validation['files'].items():fuma_catalog.exact(ROOT/name,value)
    broad=read(run/'ui-broad-4/ui-validation.json');focused=read(run/'ui-focused-2/ui-validation.json');assert broad['status']==focused['status']=='passed'
    sources=['tools/literature/'+name for name in ['fuma_catalog.py','fuma_role_coverage.py','fuma_source_closure.py','validate_fuma_catalog.py','check_fuma_review_ui.cjs','freeze_fuma_catalog.py','render_review.py','check_review_ui.cjs']]
    snapshot=[data/name for name in ['tool-inventory.json','papers.json','summary.json','WARNINGS.md','catalog.md','catalog-enrichment.json','implementation-plan.md','FUMA_SCOPED_WORKFLOWS.md','fuma-role-coverage.json','fuma-source-closure.json']]
    snapshot += sorted(data.glob('fuma-*-shared-source-compatibility.json'))
    snapshot += [ROOT/'site/literature-review.html',ROOT/'site/literature-warnings.html',ROOT/fuma_catalog.PROOF]
    closure=read(ROOT/fuma_catalog.SOURCE_CLOSURE)
    entries={}
    def add(path,name):
        assert path.is_file() and name not in entries;entries[name]=path
    for name in sources:add(ROOT/name,'source/'+name)
    for name in closure['source_files']:add(ROOT/name,'source/'+name)
    for path in snapshot:add(path,'snapshot/'+path.relative_to(ROOT).as_posix())
    for path in sorted((run/'before').rglob('*')):
        if path.is_file():add(path,'before/'+path.relative_to(run/'before').as_posix())
    for folder in ['validation-1','validation-2','ui-focused-1','ui-focused-2','ui-broad-4']:
        for path in sorted((run/folder).glob('*')):
            if path.is_file() and (folder=='ui-focused-2' or path.suffix=='.json'):add(path,'evidence/'+folder+'/'+path.name)
    for path in sorted(run.glob('*.log')):add(path,'retained-attempts/'+path.name)
    for name in ['mobile-before-fix.json','source-build-1.log']:
        path=run/name
        if path.exists() and 'retained-attempts/'+name not in entries:add(path,'retained-attempts/'+name)
    archived={}
    with zipfile.ZipFile(support,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for name,path in sorted(entries.items()):
            raw=path.read_bytes();info=zipfile.ZipInfo(name,date_time=(2026,11,2,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;archive.writestr(info,raw);archived[name]=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
    with zipfile.ZipFile(support) as archive:
        assert len(archive.infolist())==len(archived) and archive.testzip() is None
        for name,value in archived.items():
            raw=archive.read(name);assert len(raw)==value['bytes'] and hashlib.sha256(raw).hexdigest()==value['sha256']
    result=dict(schema_version=1,status='passed-scoped-native-FUMA-catalog-integration',recorded_utc=datetime.now(timezone.utc).isoformat(),
        scope='Fourteen source-reviewed FUMA roles and validated native subset with explicit supplied data, separate MAGMA fitting and genuine method gaps. Historical checkpoint counts remain labeled.',
        catalog_validation=validation,browser=dict(broad_checks=len(broad['checks']),focused_checks=focused['checks'],screenshots_visually_reviewed=['fuma-current-card-desktop.png','fuma-warnings-current-actions-mobile-dark.png']),
        scientific_checkpoint=dict(path=fuma_catalog.PROOF,**fuma_catalog.EXPECTED,archive=proof['archive']),
        source_dependency_closure=dict(path=fuma_catalog.SOURCE_CLOSURE,**fuma_catalog.SOURCE_CLOSURE_PIN,source_files=closure['source_file_count'],packaged_classes=closure['class_count'],whole_JAR_bytes_identical=True),
        scientific_analysis_reexecuted=False,source_pins={name:fuma_catalog.pin(ROOT/name) for name in sources},
        support_archive=dict(path=support.name,**fuma_catalog.pin(support),entries=len(archived)),archive_files=archived,
        claims=dict(native_subset_available=True,canonical_release_updated=False,full_hosted_tool_available=False,whole_paper_reproduced=False,whole_goal_complete=False,released_or_deployed=False),
        required_action='Use FUMA_SCOPED_WORKFLOWS.md and the current candidate identified by the scientific checkpoint. Supply declared data and inspect complete warnings/audits. Unavailable or forbidden required upstream runtime: SKIP3 and skip its dependent analysis; install only where permitted. Invalid data/integrity/numerical/capacity/cancellation failure: FAIL2; preserve diagnostics, correct the named cause and rerun completely to a new directory. Cell Ranger stays excluded. This catalog snapshot is historical when unrelated tools advance.')
    write(output,result)
    # Add only byte checksums; preserve the ordering of prior manifest entries.
    manifest={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(data.iterdir()) if p.is_file() and p.name!='SHA256SUMS'}
    order=[line.split('  ',1)[1] for line in (data/'SHA256SUMS').read_text(encoding='utf-8').splitlines() if line.split('  ',1)[1] in manifest]
    assert len(order)==len(set(order));order.extend(name for name in manifest if name not in order)
    (data/'SHA256SUMS').write_text(''.join(f'{manifest[name]}  {name}\n' for name in order),encoding='utf-8',newline='\n')
    assert all(fuma_catalog.pin(data/name)['sha256']==digest for name,digest in manifest.items())
    print(json.dumps(dict(status=result['status'],support_entries=len(archived),support_bytes=result['support_archive']['bytes'],broad_browser_checks=len(broad['checks']),focused_browser_checks=len(focused['checks']),new_scoped_integrations=validation['new_scoped_integrations'],missing=validation['missing'])))


if __name__=='__main__':main()
