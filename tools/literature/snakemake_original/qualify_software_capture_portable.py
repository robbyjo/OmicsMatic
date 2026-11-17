#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read genuine native software payloads on another host and reject contradictory/damaged copies."""
from pathlib import Path
import argparse,copy,hashlib,json,os,shutil,subprocess,time
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2]
if not __debug__:raise RuntimeError('Qualification assertions must remain enabled')
def write(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')
def pin(path):return dict(bytes=path.stat().st_size,sha256=hashlib.sha256(path.read_bytes()).hexdigest())
def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['source','compiled','jar','out']:p.add_argument('--'+name,type=Path,required=True)
    p.add_argument('--java',required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(exist_ok=False);shutil.copyfile(__file__,out/'as-run-qualifier.py');source=a.source/'literal-specials';genuine=json.loads((source/'SOFTWARE.json').read_bytes());compiled=json.loads((a.compiled/'COMPILED.json').read_bytes());assert compiled['adapter_class_source']=='byte-pinned-jar';classpath=str(a.compiled.resolve()/'classes')+os.pathsep+str(a.jar.resolve());rows=[]
    assert pin(a.jar)=={key:compiled['baseline_jar'][key] for key in ['bytes','sha256']}
    for name,expected in compiled['classes'].items():assert pin(a.compiled/'classes'/name)==expected
    before={p.relative_to(source).as_posix():pin(p) for p in source.rglob('*') if p.is_file()}
    mutations={
        'missing-root-member':lambda s:s['roots']['software']['nodes'].pop(''),
        'missing-parent':lambda s:s['roots']['software']['nodes'].__setitem__('absent/child',s['roots']['software']['nodes'].pop('value')),
        'unknown-kind':lambda s:s['roots']['software']['nodes']['value'].__setitem__('kind','unknown'),
        'file-with-target':lambda s:s['roots']['software']['nodes']['value'].__setitem__('target','/outside'),
        'symlink-with-content':lambda s:s['roots']['software']['nodes']['payload-link'].__setitem__('content',copy.deepcopy(s['roots']['software']['nodes']['value']['content'])),
        'fifo-with-content':lambda s:s['roots']['software']['nodes']['fifo'].__setitem__('content',copy.deepcopy(s['roots']['software']['nodes']['value']['content'])),
        'file-with-device':lambda s:s['roots']['software']['nodes']['value'].__setitem__('device',259),
        'invalid-mode':lambda s:s['roots']['software']['nodes']['value'].__setitem__('mode',4096),
        'bad-total-bytes':lambda s:s.__setitem__('bytes',s['bytes']+1),
        'bad-total-members':lambda s:s.__setitem__('members',s['members']+1),
        'path-traversal':lambda s:s['roots']['software']['nodes'].__setitem__('../escape',s['roots']['software']['nodes'].pop('value')),
        'absolute-member':lambda s:s['roots']['software']['nodes'].__setitem__('/absolute',s['roots']['software']['nodes'].pop('value')),
        'widened-scope-claim':lambda s:s.__setitem__('scope','Complete dependency seal and analysis admission'),
        'missing-roots':lambda s:s.__setitem__('roots',{}),
        'contradictory-shared-length':lambda s:(s['roots']['software']['nodes']['alias']['content'].__setitem__('bytes',s['roots']['software']['nodes']['alias']['content']['bytes']+1),s.__setitem__('bytes',s['bytes']+1)),
    }
    for name in ['genuine','reencoded','changed-blob','missing-blob',*mutations]:
        case=out/name;case.mkdir();shutil.copytree(source/'objects',case/'objects');snapshot=copy.deepcopy(genuine);accepted=name in ['genuine','reencoded']
        if name in mutations:mutations[name](snapshot)
        elif name in ['changed-blob','missing-blob']:
            file=next((case/'objects').iterdir())
            if name=='changed-blob':file.write_bytes(b'changed\n')
            else:file.rename(case/'retained-missing-blob')
        metadata=case/'SOFTWARE.json';write(metadata,snapshot);request=case/'REQUEST.json';write(request,dict(owned=str(case),metadata=str(metadata),files=1000,bytes=16777216,file_bytes=16777216));cmd=[a.java,'-cp',classpath,compiled['probe'],'software-check',str(request)];env=os.environ.copy();env['PATH']='';env['PYTHONPATH']='';start=time.monotonic()
        with (case/'stdout').open('xb') as so,(case/'stderr').open('xb') as se:r=subprocess.run(cmd,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=60)
        row=dict(case=name,command=cmd,exit_code=r.returncode,expected_accept=accepted,seconds=round(time.monotonic()-start,3));write(case/'PROCESS.json',row);rows.append(row);assert (r.returncode==0)==accepted,(name,'Inspect preserved portable diagnostics')
    assert json.loads((source/'SOFTWARE.json').read_bytes())==genuine and before=={p.relative_to(source).as_posix():pin(p) for p in source.rglob('*') if p.is_file()};assert pin(a.jar)=={key:compiled['baseline_jar'][key] for key in ['bytes','sha256']};write(out/'COMPLETED.json',dict(status='PASS_NINETEEN_PORTABLE_LITERAL_SOFTWARE_PAYLOAD_AND_DAMAGE_CONTROLS',cases=rows,engine_executed=False,path_empty=True,host_links_and_devices_never_followed=True,jar=pin(a.jar),original_source_unchanged=True,user_analysis_admitted=False,new_catalog_credit=0,whole_goal_complete=False));print('PASS_NINETEEN_PORTABLE_SOFTWARE_CAPTURE_CONTROLS',flush=True)
if __name__=='__main__':main()
