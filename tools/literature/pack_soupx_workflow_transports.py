# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Preserve actual original worker transports for portable Java auditing and semantic-corruption tests."""
import json,zipfile
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import byte_pin


def main():
    target=ROOT/'src/test/resources/org/jlinalg/singlecell/soupx-workflow';assert not target.exists();target.mkdir(parents=True);files={};origins={}
    for version in ['1.6.2','1.3.0']:
        root=ROOT/f'build/literature-review/soupx-runtime/workflow-probe-{version}-1/result';report=json.loads((root/'worker/worker-report.json').read_bytes());assert report['status']=='completed-with-warnings' and report['source_bodies_and_formals_checked_before_and_after'] and len(report['cases'])==6;state=json.loads((root/'analysis-status.json').read_bytes());origins[version]=dict(original_worker_completed=True,original_Java_attempt_admitted=state['result_admitted'],original_Java_status=state['status'],worker_report=pin(root/'worker/worker-report.json'),source_request=pin(root/'source-request.json'))
        files[version+'/source-request.json']=(root/'source-request.json').read_bytes()
        for folder in ['inputs','worker']:
            for p in (root/folder).rglob('*'):
                relative=p.relative_to(root)
                if not p.is_file() or p.suffix not in ['.json','.tsv','.mtx'] or 'scratch' in relative.parts:continue
                files[version+'/'+relative.as_posix()]=p.read_bytes()
        for p in (root/'inputs').glob('*'):
            if p.is_file():files[version+'/inputs/'+p.name]=p.read_bytes()
    index={name:byte_pin(raw) for name,raw in sorted(files.items())};files['INDEX.json']=(json.dumps(dict(schema_version=1,files=index,origins=origins),sort_keys=True,indent=2)+'\n').encode('utf8');path=target/'original-worker-transports-v1.zip'
    with zipfile.ZipFile(path,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for name,raw in sorted(files.items()):
            info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;archive.writestr(info,raw)
    (target/'.gitattributes').write_text('* -text\n',encoding='utf8',newline='\n');save(target/'ORIGINS.json',dict(status='preserved-original-worker-transports-not-execution-adapter-admission',archive=pin(path),logical_files=len(index),origins=origins,scope='Both original R workers completed6 cases. The historical Java attempt remained unadmitted because its old diagnostic decoder rejected a multiline upstream message. These unchanged source outputs support tests of the corrected Java decoder/auditor; they do not rewrite the historical attempt status or authenticate execution.',implementation_credit=False,whole_goal_complete=False));print('Preserved',len(index),'original transport files;',pin(path)['bytes'],'archive bytes')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original transport checks')
    main()
