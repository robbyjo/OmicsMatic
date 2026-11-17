#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Controlled observation of software payload links and retained strict workflow/capacity checks."""
from pathlib import Path
import argparse,json,os,shutil,sys
if not __debug__:raise RuntimeError('Qualification assertions must remain enabled')
ROOT=Path(__file__).resolve().parents[3];HERE=Path(__file__).resolve().parent;sys.path.insert(0,str(HERE))
from setup_runtime import pin,write
from qualify_deployment import execute

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--compiled',type=Path,required=True);p.add_argument('--native-out',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();assert sys.platform=='linux';out=a.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(exist_ok=False);stage=a.native_out.absolute();assert stage==stage.resolve() and stage.parent.is_dir() and not stage.exists() and not str(stage).startswith('/mnt/');stage.mkdir();shutil.copyfile(__file__,out/'as-run-qualifier.py');compiled=json.loads((a.compiled/'COMPILED.json').read_bytes());rows=[]
    for name,expected in compiled['sources'].items():assert pin(ROOT/name)==expected,name
    names=['managed-payload-links','legacy-payload-links','workflow-link-rejected','replacement-rejected','root-rejected','cwd-rejected','data-file-count','data-byte-count']
    for name in names:
        case=stage/name;case.mkdir();owned=case/'owned';owned.mkdir();work=owned/'work';work.mkdir();data=owned/'data';data.mkdir();outside=case/'outside';outside.mkdir();(outside/'outside-file').write_bytes(b'external bytes must not be followed\n'*8192)
        command=['/usr/bin/python3','-I','-B','-c','import time; print("CONTROLLED_PROCESS"); time.sleep(0.4)']
        if name in ['managed-payload-links','legacy-payload-links']:
            (data/'rootfs/usr/bin').mkdir(parents=True);(data/'rootfs/usr/bin/awk').symlink_to('/etc/alternatives/awk');(data/'external-data-alias').symlink_to(str(outside),target_is_directory=True);(data/'missing-data-alias').symlink_to('/missing-omicsmatic-payload-target')
        elif name=='workflow-link-rejected':(work/'external-alias').symlink_to(str(outside))
        elif name=='replacement-rejected':command=['/usr/bin/python3','-I','-B','-c','from pathlib import Path; import sys,time; p=Path(sys.argv[1]); p.rmdir(); p.symlink_to(sys.argv[2],target_is_directory=True); time.sleep(2)',str(data),str(outside)]
        elif name=='data-file-count':
            for i in range(1100):(data/str(i)).write_bytes(b'')
        elif name=='data-byte-count':
            with (data/'sparse-fixture').open('xb') as f:f.truncate(134217728)
        roots=[owned] if name=='root-rejected' else [work] if name=='cwd-rejected' else [data]
        request=case/'REQUEST.json';write(request,dict(command=command,environment=dict(PATH='/usr/bin:/bin',LANG='C.UTF-8',LC_ALL='C.UTF-8',TZ='UTC'),owned=str(owned),working=str(work),wall_millis=5000,software_data=[str(p) for p in roots]));mode='process' if name=='legacy-payload-links' else 'process-data';result=execute(case,'execute',['/usr/bin/java','-cp',compiled['classpath'],compiled['probe'],mode,str(request)],dict(PATH='/usr/bin:/bin',HOME=str(case),LANG='C.UTF-8',LC_ALL='C.UTF-8',TZ='UTC'),case,90);positive=name=='managed-payload-links';assert (result['exit_code']==0)==positive,name
        ledger=owned/'process/process.json'
        if positive:
            record=json.loads(ledger.read_bytes());assert record['status']=='completed-process-only' and record['link_data_roots']==[str(data)] and record['peak_workspace_bytes']<len((outside/'outside-file').read_bytes()) and not record['owned_live_after_cleanup']
        elif name in ['root-rejected','cwd-rejected']:assert not (owned/'process').exists()
        else:
            record=json.loads(ledger.read_bytes());assert not record['owned_live_after_cleanup']
        target=out/name;target.mkdir();shutil.copyfile(request,target/request.name)
        for path in case.glob('execute.*'):shutil.copyfile(path,target/path.name)
        if (owned/'process').is_dir():shutil.copytree(owned/'process',target/'process')
        rows.append(dict(case=name,exit_code=result['exit_code'],expected_success=positive,native_case=str(case)));print(name+' PASS_EXPECTED_'+str(result['exit_code']),flush=True)
    for name,expected in compiled['sources'].items():assert pin(ROOT/name)==expected,name
    write(out/'COMPLETED.json',dict(status='PASS_EIGHT_SOFTWARE_DATA_OBSERVATION_AND_STRICT_SCOPE_CAPACITY_CONTROLS',cases=rows,compiled=pin(a.compiled/'COMPILED.json'),native_stage=str(stage),payload_links_followed=False,user_analysis_admitted=False,new_catalog_credit=0,whole_goal_complete=False))
if __name__=='__main__':main()
