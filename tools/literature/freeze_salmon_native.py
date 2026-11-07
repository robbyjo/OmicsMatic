# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze complete original Salmon controls and the additive native decoder, without tool credit."""
import argparse
from datetime import datetime,timezone
import hashlib
import json
import os
from pathlib import Path
import stat
import sys
import zipfile

ROOT=Path(__file__).resolve().parents[2]


def read(path):return json.loads(path.read_bytes())
def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def write(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')


def main():
    if not __debug__:raise RuntimeError('Never disable complete source/population assertions')
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);a=parser.parse_args();target=a.out.resolve()
    base=ROOT/'build/literature-review/salmon-runtime';assert os.uname().sysname=='Linux' and target.is_relative_to((ROOT/'docs/literature').resolve()) and not target.exists()
    reference=read(base/'reference-protocols-3/COMPLETED.json');native=read(base/'native-development-1/matrix-1/COMPLETED.json');roles=read(base/'role-application-3/verification.json');startup=read(base/'startup-probe-2/COMPLETED.json')
    assert reference['original_quantifications']==25 and reference['complete_quant_rows']==1000 and reference['complete_bootstrap_values']==31600
    assert native['status']=='passed-three-JVM-native-Salmon-replicate-decoder-and-complete-original-values' and len(native['runs'])==3
    assert all(r['passed']==r['tests']==27 and r['original_protocols']==20 and r['original_values']==31600 for r in native['runs'])
    assert roles['applied'] and roles['reviewed_roles']==8 and roles['total_reviews']==793 and roles['supporting_pointers']==241 and roles['unrelated_prior_reviews_preserved']==785
    assert native['Salmon_catalog_status']=='missing' and not native['public_quantification_adapter_available']
    assert pin(ROOT/native['artifact']['path'])=={k:native['artifact'][k] for k in ('bytes','sha256')}
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==native['canonical_executable_unchanged']
    for path,value in native['source_pins'].items():assert pin(ROOT/path)==value,path
    assert pin(ROOT/'tools/literature/collect_salmon_reference_controls.py')==reference['source_pin']
    assert pin(ROOT/'tools/literature/probe_salmon_releases.py')==startup['script']
    post=[]
    for row in startup['releases']:
        home=Path(row['home']);actual={}
        for path in sorted(home.rglob('*')):
            info=path.lstat();name=path.relative_to(home).as_posix()
            if stat.S_ISLNK(info.st_mode):actual[name]=dict(kind='symlink',target=os.readlink(path));assert path.resolve(strict=True).is_relative_to(home)
            elif stat.S_ISDIR(info.st_mode):actual[name]=dict(kind='directory',mode=stat.S_IMODE(info.st_mode))
            else:assert stat.S_ISREG(info.st_mode);actual[name]=dict(kind='file',mode=stat.S_IMODE(info.st_mode),**pin(path))
        assert actual==row['payload'],('Original runtime payload changed',row['version'])
        post.append(dict(version=row['version'],complete_payload_unchanged=True,members=len(actual)))
    print('All five complete original runtime payloads remain unchanged after controls.',flush=True)
    sources=dict(native['source_pins'])
    for path in list((ROOT/'tools/literature').glob('*salmon*.py'))+[ROOT/'tools/literature/salmon-use-reviews.json',ROOT/'docs/literature/SALMON_DEVELOPMENT.md']:
        sources[path.relative_to(ROOT).as_posix()]=pin(path)
    entries={}
    def add(path,name):assert path.is_file() and not path.is_symlink() and name not in entries;entries[name]=path
    def tree(folder,label):
        for path in sorted(folder.rglob('*')):
            if path.is_file():add(path,label+'/'+path.relative_to(folder).as_posix())
    for path in sources:add(ROOT/path,'source/'+path)
    for folder in ['source-audit-1','source-audit-2','role-application-1','role-application-2','role-application-3',
        'startup-probe-1','startup-probe-2','reference-protocols-1','reference-protocols-2','reference-protocols-3',
        'bootstrap-diagnostics-1','bootstrap-feature-diagnostics-1','bootstrap-trace-1','p227-pipeline-source-1','native-development-1/matrix-1']:
        tree(base/folder,'evidence/'+folder)
    add(ROOT/'build/test-results/test/TEST-org.jlinalg.sequence.SalmonInferentialReplicatesTest.xml','evidence/gradle-native-tests.xml')
    data=ROOT/'docs/literature/omics-2023-2026'
    for name in ['salmon-use-shared-source-compatibility.json','salmon-role-coverage.json']:add(data/name,'catalog-relationships/'+name)
    capture=read(base/'upstream-capture-1/COMPLETED.json');public_capture=[]
    for row in capture['releases']:
        # Signed public-asset redirect URLs are transient transport metadata,
        # not scientific provenance; retain official stable URLs and byte pins.
        public_capture.append(dict(version=row['version'],official_asset_digest=row['official_asset_digest'],
            files=[{k:item[k] for k in ['url','bytes','sha256']} for item in row['files']]))
        for name in ['release.json','source.tar.gz']:add(base/'upstream-capture-1'/row['version']/name,'official-source/'+row['version']+'/'+name)
    target.mkdir();(target/'.gitattributes').write_text('* -text\n',encoding='ascii',newline='\n');archive=target/'controls.zip';census={}
    with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,path in sorted(entries.items()):
            raw=path.read_bytes();item=zipfile.ZipInfo(name,(1980,1,1,0,0,0));item.compress_type=zipfile.ZIP_DEFLATED;item.external_attr=0o100644<<16;z.writestr(item,raw);census[name]=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
    with zipfile.ZipFile(archive) as z:
        assert len(z.infolist())==len(census)==len(set(z.namelist())) and z.testzip() is None
        for name,value in census.items():raw=z.read(name);assert len(raw)==value['bytes'] and hashlib.sha256(raw).hexdigest()==value['sha256']
    assert archive.stat().st_size<100*1024*1024 and all(pin(ROOT/name)==value for name,value in sources.items())
    summary=read(data/'summary.json');assert summary['implementation_status_counts']['upstream-adapter-validated']==49 and summary['implementation_status_counts']['native-subset-validated']==15 and summary['implementation_status_counts']['missing']==256
    result=dict(schema_version=1,status='passed-native-Salmon-inferential-decoder-and-original-development-controls-public-workflow-open',recorded_utc=datetime.now(timezone.utc).isoformat(),
        source_pins=sources,artifact=native['artifact'],native_validation=native,original_reference_validation=reference,source_role_validation=roles,
        original_startup_validation=startup,post_control_runtime_payload_checks=post,official_capture=public_capture,
        transport_note='Transient signed public asset redirect URLs are omitted from published provenance; stable official URLs, official release metadata and complete archive byte hashes are retained. Runtime binaries remain in the local capture and are not embedded here; complete official source archives and all scientific control outputs are embedded.',
        archive=dict(path=archive.name,**pin(archive),entries=len(census)),archive_files=census,canonical_executable_unchanged=native['canonical_executable_unchanged'],
        retained_findings=['An earlier /tmp reference area vanished between commands; no quantification ran from that missing state. Fresh probes use owned /var/tmp areas.',
            'The four-target positional-bias control crashed in original1.0.0,1.1.0,1.5.2,1.8.0;1.9.0 was not reached there. A1.8.0 diagnostic reproduces the failure with8/32GiB and1/2threads and without bootstraps; removing position bias succeeds. A40-target reference with populated length classes passes the same scientific options on all five editions. Exact root cause remains unproven; preserve original failed output families.',
            'An optional diagnostic signal tracer was not compiled because /usr/bin/gcc is absent. No traced analysis ran. This compiler is not a dependency of the native decoder or original Salmon runtime.',
            'Synthetic reference fits remain below the original five-million-fragment burn-in setting; original observed-mapping fallback and all warnings are retained.'],
        scope='Complete original bootstrap binary64 decoding and integrity, not read mapping/abundance inference or full quantification output certification. Twenty original bootstrap-bearing protocols contribute31600 values per JVM; the same25 original quantifications contribute1000 quant.sf rows, without a native full-quantification certificate. All protocols reuse one40-target synthetic population; earlier small-input failures are distinct retained controls, not failed biological studies.',
        claims=dict(native_complete_replicate_decoder_available=True,typed_public_quantification_command_available=False,scientific_runtime_admitted=False,complete_quantification_output_audit_available=False,Salmon_catalog_promoted=False,whole_paper_reproduced=False,biological_accuracy_certified=False,whole_goal_complete=False,released_or_deployed=False),
        catalog_snapshot=dict(new_scoped_integrations=64,native=15,upstream=49,including_existing_adapters=67,missing=256,reviewed_uses=793,supporting_pointers=241,Salmon_status='missing'),
        required_action='Salmon remains missing: skip dependent project quantification until typed execution, managed runtime/index/reference/read admission and complete output auditing pass. Native decoder consumers must stage callbacks and admit nothing until the full receipt succeeds. Missing/forbidden runtime/resource/capability means SKIP3 with the named setup action. Input, integrity, process, numerical, capacity, cancellation or finalization failure means FAIL2 and no result. Preserve complete failed outputs and raw warnings/logs; use NEW paths. A startup check, quant.sf prefix or truncated bootstrap family is insufficient. Original positional-bias failures remain prominent and unresolved. Install/download only where permitted. Hashes prove integrity, not authenticity. Cell Ranger remains excluded.')
    write(target/'VALIDATION.json',result);(target/'SHA256SUMS').write_text(''.join(pin(path)['sha256']+'  '+path.name+'\n' for path in [archive,target/'VALIDATION.json']),encoding='ascii',newline='\n')
    print(json.dumps(dict(status=result['status'],archive_entries=len(census),archive_bytes=archive.stat().st_size,original_quantifications=25,native_original_values_per_JVM=31600,Salmon_catalog_promoted=False)),flush=True)


if __name__=='__main__':main()
