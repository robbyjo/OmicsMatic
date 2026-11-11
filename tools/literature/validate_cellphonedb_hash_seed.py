# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Three actual JVM audits of fresh original workflows and effective-startup controls."""
import argparse,json,shutil,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21
from prepare_cellphonedb_hash_seed import verify

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/cellphonedb-runtime') and not out.exists();out.mkdir(parents=True);state=dict(status='FAIL2_CELLPHONEDB_HASH_SEED_NATIVE_AUDITS',implementation_credit=False,whole_goal_complete=False,processes=[])
    try:
        verify();built=json.loads((a.build/'COMPLETED.json').read_bytes());assert built['status']=='PASS_COMPILED_CELLPHONEDB_HASH_SEED_DEVELOPMENT_ONLY';jar=ROOT/built['artifact']['path'];assert pin(jar)=={k:built['artifact'][k] for k in ['bytes','sha256']};control=ROOT/'build/literature-review/cellphonedb-runtime/hash-seed-controls-1';proof=json.loads((control/'COMPLETED.json').read_bytes());assert proof['status']=='PASS_FRESH_EFFECTIVE_SEED_CELLPHONEDB_CONTROLS_ONLY' and proof['runtime_unchanged'] and proof['source_inputs_unchanged'];pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),*control.rglob('*')] if p.is_file()};canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')
        for label,java,linux in [('windows21',str(JAVA21/'java.exe'),False),('windows25','C:/Program Files/Common Files/Oracle/Java/javapath/java.exe',False),('linux21','/usr/bin/java',True)]:
            home=out/label;home.mkdir();shutil.copytree(control/'candidate',home/'copied-originals');request=json.loads((control/'request.json').read_bytes());request.update(mode='RUN',runtime='/mnt/d/projects/JLinAlg/build/cellphonedb-workflow-case-sensitive-1/setup-1/COMPLETED.json',out=str(home/'never-run'))
            def mapped(value):
                if isinstance(value,dict):return {k:(str(ROOT/Path(v).relative_to('/mnt/d/projects/JLinAlg')) if k=='path' and isinstance(v,str) and v.startswith('/mnt/d/projects/JLinAlg/') and not linux else mapped(v)) for k,v in value.items()}
                if isinstance(value,list):return list(map(mapped,value))
                return value
            save(home/'request.json',mapped(request))
            def path(p):return '/mnt/d/projects/JLinAlg/'+p.relative_to(ROOT).as_posix() if linux else str(p)
            def execute(name,arguments):
                command=[java,'-Xmx4g','-cp',path(jar),*arguments]
                if linux:command=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec',*command]
                run=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=300);(home/(name+'.stdout')).write_bytes(run.stdout);(home/(name+'.stderr')).write_bytes(run.stderr);record=dict(label=label,name=name,command=command,exit_code=run.returncode);save(home/(name+'.process.json'),record);state['processes'].append(record);assert run.returncode==0,run.stderr.decode('utf8',errors='replace')
            execute('workflows',['org.jlinalg.cli.CellPhoneDbWorkflowAuditMatrix',path(home/'request.json'),path(home/'copied-originals'),path(home/'native-audit.json')]);audit=json.loads((home/'native-audit.json').read_bytes());assert len(audit['cases'])==19
            for i in [0,2,4]:
                value=proof['startup_receipts'][i];receipt=ROOT/Path(value['path']).relative_to('/mnt/d/projects/JLinAlg');seed=value['receipt']['requested_seed'];name='startup-'+str(seed);execute(name,['org.jlinalg.cli.CellPhoneDbPythonStartupMatrix',path(receipt),str(seed),value['receipt']['python_executable'],path(home/(name+'-cases')),path(home/(name+'.json'))]);report=json.loads((home/(name+'.json')).read_bytes());assert len(report['rejections'])==22 and report['missing_receipt_rejected'] and report['valid']['independent_native_seed0_hashes']==(seed==0)
        assert all(pin(ROOT/n)==want for n,want in pins.items()) and all(pin(ROOT/n)==want for n,want in built['source_pins'].items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical;state.update(status='PASS_THREE_JVM_EFFECTIVE_SEED_AND_FRESH_WORKFLOW_AUDITS_ONLY',source_pins=pins,artifact=built['artifact'],workflow_cases_per_jvm=19,seeds_per_jvm=[0,123,4294967295],corrupt_receipt_rejections_per_seed=22,missing_receipt_rejected_per_seed=True,canonical_executable_unchanged=canonical)
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':raise SystemExit(main())
