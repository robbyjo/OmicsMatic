#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify genuine managed evidence offline and reject physically resealed semantic contradictions."""
from pathlib import Path
import argparse,copy,hashlib,json,os,shutil,subprocess,sys,time,traceback,zipfile

ROOT=Path(__file__).resolve().parents[3]
if not __debug__:raise RuntimeError('Qualification assertions must remain enabled')

def digest(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def pin(path):return digest(path.read_bytes())
def encoded(value):return (json.dumps(value,indent=2)+'\n').encode()
def write(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as stream:json.dump(value,stream,indent=2);stream.write('\n')

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ['archive','compiled','jar','out']:parser.add_argument('--'+name,type=Path,required=True)
    parser.add_argument('--java',required=True);args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(exist_ok=False);shutil.copyfile(__file__,out/'as-run-qualifier.py')
    try:
        assert sys.platform=='win32','Use a host without the Linux producer filesystem/runtime'
        archive=args.archive/'bundle.zip';sidecar=json.loads((args.archive/'ARCHIVED.json').read_bytes());before=pin(archive);assert before==sidecar['archive']
        compiled=json.loads((args.compiled/'COMPILED.json').read_bytes());assert compiled['adapter_class_source']=='byte-pinned-jar';assert pin(args.jar)=={key:compiled['baseline_jar'][key] for key in ['bytes','sha256']}
        for name,expected in compiled['classes'].items():assert pin(args.compiled/'classes'/name)==expected
        with zipfile.ZipFile(archive) as source:
            assert len(source.namelist())==len(set(source.namelist()))
            original={name:source.read(name) for name in source.namelist() if not name.endswith('/')};directories={name[:-1] for name in source.namelist() if name.endswith('/')}
        assert {name:digest(raw) for name,raw in original.items()}==sidecar['files'] and directories==set(sidecar['directories'])
        assert json.loads(original['COMPLETED.json'])['kind']=='declared-managed-workflow'
        producer=json.loads(original['invocation.json'])['owned_root'];assert not Path(producer).exists()
        cases=['genuine','reencoded-manifest','reordered-policy-set','wrong-selected-mode','partial-dag-resealed-binding','wrong-command-resealed-request','borrowed-terminal-mutation','wrong-software-roots','missing-software-root-node','inconsistent-shared-length','unowned-controller-resealed-binding','live-owned-process-resealed-binding','missing-closed-policy','missing-original-job-finish-resealed-binding','changed-output-contract','removed-warning','extra-object','extra-directory','false-admission','foreign-runtime-file','wrong-provider-version-resealed-context','missing-helper']
        rows=[];classpath=str(args.compiled.resolve()/'classes')+os.pathsep+str(args.jar.resolve())
        for name in cases:
            case=out/name;case.mkdir();files=original.copy();dirs=directories.copy();accepted=name in cases[:3]
            def read(member):return json.loads(files[member])
            def put(member,value):files[member]=encoded(value)
            def update_binding(controller=False,process=False):
                bound=read('managed-process-binding.json')
                if controller:bound['controllerReceipt']=digest(files['CONTROLLER.json'])
                if process:bound['processLedger']=digest(files['process/process.json'])
                put('managed-process-binding.json',bound);seal=read('SUPERVISED-POLICY.json');seal['bound']=bound;put('SUPERVISED-POLICY.json',seal)
            if name=='reordered-policy-set':
                bound=read('managed-process-binding.json');assert len(bound['policy']['processes'])>=2;bound['policy']['processes'].reverse();put('managed-process-binding.json',bound);update_binding()
            elif name=='wrong-selected-mode':
                request=read('REQUEST.json');request['execution']['deployment']='apptainer';put('REQUEST.json',request);source=read('request-source.json');source['identity']['pin']=digest(files['REQUEST.json']);put('request-source.json',source)
            elif name=='partial-dag-resealed-binding':
                controller=read('CONTROLLER.json');controller['effective_policy']['until']=['make'];put('CONTROLLER.json',controller);bound=read('managed-process-binding.json');bound['parserPolicy']['effective']['until']=['make'];put('managed-process-binding.json',bound);update_binding(controller=True)
            elif name=='wrong-command-resealed-request':
                command=read('COMMAND.json');command['argv'][-1]='different-target';put('COMMAND.json',command);supervised=read('supervised-invocation.json');supervised['request']=digest(files['COMMAND.json']);put('supervised-invocation.json',supervised)
            elif name=='borrowed-terminal-mutation':
                snapshot=read('software-after.json');borrowed=read('invocation.json')['borrowed_roots'];assert borrowed
                key=next(iter(borrowed));node=next(value for value in snapshot['roots'][key]['nodes'].values() if value['kind']=='file');node['mode']^=1;put('software-after.json',snapshot)
            elif name=='wrong-software-roots':
                snapshot=read('software-before.json');snapshot['roots']['payload']['source']='/uncaptured/payload';put('software-before.json',snapshot)
            elif name=='missing-software-root-node':
                snapshot=read('software-before.json');snapshot['roots']['environments']['nodes'].pop('');snapshot['members']-=1;put('software-before.json',snapshot)
            elif name=='inconsistent-shared-length':
                snapshot=read('software-before.json');node=next(value for tree in snapshot['roots'].values() for value in tree['nodes'].values() if value['kind']=='file');node['content']['bytes']+=1;snapshot['bytes']+=1;put('software-before.json',snapshot)
            elif name in ['unowned-controller-resealed-binding','live-owned-process-resealed-binding']:
                process=read('process/process.json');controller=read('CONTROLLER.json')['controller_pid']
                if name.startswith('unowned'):process['owned_processes']=[value for value in process['owned_processes'] if value['pid']!=controller]
                else:process['owned_live_after_cleanup']=[controller]
                put('process/process.json',process);update_binding(process=True)
            elif name=='missing-closed-policy':
                member=next(key for key in files if key.startswith('policy-records/') and not key.endswith('.OPENED.json'));files.pop(member)
            elif name=='missing-original-job-finish-resealed-binding':
                controller=read('CONTROLLER.json');member='events/events-'+str(controller['controller_pid'])+'.jsonl';events=[json.loads(line) for line in files[member].splitlines()];removed=[event for event in events if event.get('record',{}).get('event')=='job_finished'];assert removed
                events=[event for event in events if event not in removed]
                for sequence,event in enumerate(events,1):event['sequence']=sequence
                files[member]=b''.join((json.dumps(event)+'\n').encode() for event in events);controller['collector_streams'][member[7:]]=digest(files[member]);put('CONTROLLER.json',controller);update_binding(controller=True)
            elif name=='changed-output-contract':
                outputs=read('outputs.json');output=next(value for value in outputs if value['root']=='published');raw=b'deliberately different declared result\n';value=digest(raw);files['objects/'+value['sha256']]=raw;output['members']['']['pin']=value;output['bytes']=len(raw);put('outputs.json',outputs);blobs=read('blob-index.json');blobs[value['sha256']]=value;put('blob-index.json',blobs)
            elif name=='removed-warning':files['BUNDLE_WARNINGS.md']=b'Warnings removed\n'
            elif name=='extra-object':
                raw=b'unreferenced software object\n';value=digest(raw);files['objects/'+value['sha256']]=raw;blobs=read('blob-index.json');blobs[value['sha256']]=value;put('blob-index.json',blobs)
            elif name=='extra-directory':dirs.add('unlisted-empty-directory')
            elif name=='foreign-runtime-file':files['runtime/foreign.txt']=b'Unexpected imported runtime evidence\n'
            elif name=='wrong-provider-version-resealed-context':
                installed=read('deployment/CONDA-INSTALLED.json');installed['version']='conda 0.0.0\n';put('deployment/CONDA-INSTALLED.json',installed)
                adopted=read('deployment/ADOPTED.json');adopted['components']['conda']['version']='conda 0.0.0';adopted['metadata']['CONDA-INSTALLED.json']=digest(files['deployment/CONDA-INSTALLED.json']);put('deployment/ADOPTED.json',adopted)
                context=read('deployment-context.json');context['providers']['conda']['version']='conda 0.0.0';context['metadata']['CONDA-INSTALLED.json']=digest(files['deployment/CONDA-INSTALLED.json']);context['metadata']['ADOPTED.json']=digest(files['deployment/ADOPTED.json']);put('deployment-context.json',context);put('provider-context.json',context)
            elif name=='missing-helper':files.pop('helpers/managed_parser_policy.py')
            manifest=read('manifest.json');manifest['files']={member:digest(raw) for member,raw in files.items() if member not in ['manifest.json','COMPLETED.json']};manifest['directories']=sorted(dirs);put('manifest.json',manifest)
            marker=read('COMPLETED.json');marker['manifest']=digest(files['manifest.json'])
            if name=='false-admission':marker['user_analysis_admitted']=True
            put('COMPLETED.json',marker);target=case/'bundle.zip'
            with zipfile.ZipFile(target,'x',allowZip64=True) as archive_out:
                for member in sorted(dirs):archive_out.writestr(member+'/',b'')
                for member,raw in sorted(files.items()):archive_out.writestr(member,raw,compress_type=zipfile.ZIP_STORED if member.startswith('objects/') else zipfile.ZIP_DEFLATED,compresslevel=None if member.startswith('objects/') else 3)
            write(case/'REQUEST.json',dict(archive=str(target)));command=[args.java,'-cp',classpath,compiled['probe'],'verify-zip',str(case/'REQUEST.json')];env=os.environ.copy();env['PATH']='';env['PYTHONPATH']='';start=time.monotonic()
            with (case/'stdout').open('xb') as so,(case/'stderr').open('xb') as se:result=subprocess.run(command,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=180)
            row=dict(case=name,command=command,exit_code=result.returncode,expected_acceptance=accepted,seconds=round(time.monotonic()-start,3),archive=pin(target));write(case/'PROCESS.json',row);rows.append(row)
            assert (result.returncode==0)==accepted,(name,'Inspect retained independent portable verifier diagnostics');print(name+' PASS',flush=True)
        assert pin(archive)==before
        assert pin(args.jar)=={key:compiled['baseline_jar'][key] for key in ['bytes','sha256']}
        write(out/'COMPLETED.json',dict(status='PASS_TWENTY_TWO_PORTABLE_MANAGED_BUNDLE_AND_RESEALED_SEMANTIC_CONTROLS',cases=rows,source_archive=before,engine_executed=False,path_empty=True,producer_paths_available=False,original_archive_unchanged=True,user_analysis_admitted=False,new_catalog_credit=0,whole_goal_complete=False));print('PASS_TWENTY_TWO_PORTABLE_MANAGED_BUNDLE_CONTROLS',flush=True)
    except BaseException as error:
        write(out/'FAILURE.json',dict(error=repr(error),traceback=traceback.format_exc(),required_action='Retain every genuine and altered archive and its diagnostics. Resolve the reader or fixture discrepancy and retry a NEW qualification directory; do not refresh previous receipts.'));raise

if __name__=='__main__':main()
