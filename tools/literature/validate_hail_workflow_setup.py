# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify setup export from an actual candidate on three JVMs; exercise separate permitted capture/install/denial in NEW private paths."""
import argparse,json,os,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21,linux

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',required=True,type=Path);p.add_argument('--out',required=True,type=Path);p.add_argument('--mode',choices=['export','capture139','capture130','install139','deny-install139'],required=True);p.add_argument('--prefix',type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/hail-runtime') and not out.exists();out.mkdir(parents=True);state=dict(status='FAIL2_HAIL_SETUP_VALIDATION_INCOMPLETE',implementation_credit=False,whole_goal_complete=False);(out/'validator-at-attempt.py').write_bytes(Path(__file__).read_bytes())
    try:
        proof=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/proof['artifact']['path'];assert pin(jar)=={k:proof['artifact'][k] for k in ['bytes','sha256']};sources=dict(proof['source_pins']);sources[Path(__file__).resolve().relative_to(ROOT).as_posix()]=pin(Path(__file__));assert all(pin(ROOT/name)==value for name,value in sources.items());runtime={'windows21':[str(JAVA21/'java.exe')],'windows25':['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'],'linux21':['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/env','-u','JAVA_TOOL_OPTIONS','-u','_JAVA_OPTIONS','-u','JDK_JAVA_OPTIONS','/usr/bin/java']};env=dict(os.environ)
        for key in ['JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS']:env.pop(key,None)
        exports={}
        for name,java in runtime.items():
            target=out/(name+'-export');command=[*java,'-cp',linux(jar) if name=='linux21' else str(jar),'org.jlinalg.cli.HailWorkflowSetupCli','--export-runtime-setup',linux(target) if name=='linux21' else str(target)]
            with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,env=env,stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL,timeout=120,creationflags=subprocess.CREATE_NO_WINDOW)
            save(out/(name+'.process.json'),dict(command=command,exit_code=r.returncode));assert r.returncode==0;manifest=json.loads((target/'setup-kit.json').read_bytes());assert manifest['analysis_executed'] is False and manifest['software_installed'] is False and len(manifest['files'])==6;assert {p.relative_to(target).as_posix() for p in target.rglob('*') if p.is_file()}==set(manifest['files'])|{'setup-kit.json'}
            for path,want in manifest['files'].items():assert pin(target/path)==want,path
            exports[name]=manifest['files']
        assert exports['windows21']==exports['windows25']==exports['linux21'];state['exported_files']=exports['linux21']
        if a.mode!='export':
            edition='0.2.130' if a.mode=='capture130' else '0.2.139';prefix=a.prefix.resolve() if a.prefix else ROOT/('build/hl130-env-2' if edition=='0.2.130' else 'build/hl139-env-2');prepare=out/'preparation';command=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/python3','-I','-B',linux(out/'linux21-export/setup/prepare.py'),'--mode','INSTALL' if 'install' in a.mode else 'CAPTURE','--edition',edition,'--prefix',linux(prefix),'--java-home',linux(ROOT/'build/hl130-env-2/lib/jvm'),'--out',linux(prepare)]
            if 'install' in a.mode:
                assert a.prefix and not prefix.exists();command+=['--base-python','/usr/bin/python3']
                for cache in ['reference-setup-139-2/archives','pip-bootstrap-intake-1','source-intake-2/0.2.139']:command+=['--archive-cache',linux(ROOT/'build/literature-review/hail-runtime'/cache)]
                if a.mode=='install139':command.append('--allow-install')
            with (out/'preparation.stdout').open('xb') as stdout,(out/'preparation.stderr').open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,env=env,stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL,creationflags=subprocess.CREATE_NO_WINDOW)
            save(out/'preparation.process.json',dict(command=command,exit_code=r.returncode));report=json.loads((prepare/'COMPLETED.json').read_bytes());assert report['analysis_executed'] is False and report['cloud_batch_submitted'] is False
            if a.mode=='deny-install139':assert r.returncode==3 and report['status']=='SKIP3_HAIL_RUNTIME_UNAVAILABLE' and not prefix.exists() and 'Installation flag absent' in report['error'] and 'Required action:' in (prepare/'WARNINGS.md').read_text()
            else:assert r.returncode==0 and report['status']=='PASS_BOUND_ORIGINAL_HAIL_WORKFLOW_REFERENCE_ONLY' and report['runtime_trees_unchanged'] is True and report['shared_runtime_unchanged'] is True and all(p['owned_live_after_cleanup']==[] for p in report['processes']);state['runtime_binding']=dict(path=(prepare/'COMPLETED.json').relative_to(ROOT).as_posix(),**pin(prepare/'COMPLETED.json'))
        assert all(pin(ROOT/name)==value for name,value in sources.items()) and pin(jar)=={k:proof['artifact'][k] for k in ['bytes','sha256']};state.update(status='PASS_HAIL_WORKFLOW_SETUP_'+a.mode.upper().replace('-','_')+'_ONLY',source_pins=sources,artifact=proof['artifact']);print(state['status'])
    except Exception as error:state.update(error=repr(error),required_action='No new runtime or dependent analysis is admitted. Preserve exports, failed prefixes, archive copies and process logs; inspect error.traceback and preparation/WARNINGS.md. Correct the named export/permission/source/runtime mismatch and retry NEW paths. Required forbidden/unavailable installs keep analysis skipped.');(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8');(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state['required_action']+'\n\n'+repr(error)+'\n',encoding='utf8');print(state['status'],repr(error))
    save(out/'COMPLETED.json',state);return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact export/preparation checks')
    raise SystemExit(main())
