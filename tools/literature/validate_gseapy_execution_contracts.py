# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Real portable exports, unsupported-platform/missing-runtime skips and invalid-request admission contracts."""
import argparse,copy,json,subprocess,sys,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21

def linux(path):return '/mnt/d/projects/JLinAlg/'+path.relative_to(ROOT).as_posix()
def local(path):return ROOT/Path(path).relative_to('/mnt/d/projects/JLinAlg')

def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['build','fixtures','out']:p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/gseapy-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_GSEAPY_EXECUTION_CONTRACTS',checks=[],full_adapter_admitted=False,whole_goal_complete=False)
    try:
        built=json.loads((a.build/'COMPLETED.json').read_bytes());assert built['status']=='passed-compiled-additive-GSEApy-analysis-entry-sources-only';jar=ROOT/built['artifact']['path'];assert pin(jar)=={k:built['artifact'][k] for k in ['bytes','sha256']};baseline=json.loads((a.fixtures/'1.1.8.json').read_bytes());old=ROOT/'src/main/resources/org/jlinalg/omics/gseapy-workflow';new=ROOT/'src/main/resources/org/jlinalg/omics/gseapy-execution';members={p.relative_to(old).as_posix():pin(p) for p in old.rglob('*') if p.is_file()};members.update({p.relative_to(new).as_posix():pin(p) for p in new.rglob('*') if p.is_file()})
        for label,java,is_linux in [('windows21',str(JAVA21/'java.exe'),False),('windows25','C:/Program Files/Common Files/Oracle/Java/javapath/java.exe',False),('linux21','/usr/bin/java',True)]:
            home=out/label;home.mkdir();path=lambda p:linux(p) if is_linux else str(p);prefix=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec',java] if is_linux else [java]
            def call(name,cls,args,expected):
                command=prefix+['-cp',path(jar),'org.jlinalg.cli.'+cls,*args];run=subprocess.run(command,capture_output=True,cwd=ROOT,timeout=120);(home/(name+'.stdout')).write_bytes(run.stdout);(home/(name+'.stderr')).write_bytes(run.stderr);save(home/(name+'.process.json'),dict(command=command,exit_code=run.returncode));assert run.returncode==expected,(name,run.returncode,run.stderr.decode(errors='replace'));state['checks'].append(dict(host=label,contract=name,exit_code=run.returncode));return run
            kit=home/'kit';call('export','GseapyExecutionSetupCli',['--export',path(kit)],0);assert {p.relative_to(kit).as_posix():pin(p) for p in kit.rglob('*') if p.is_file()}==members;call('refuse-existing-export','GseapyExecutionSetupCli',['--export',path(kit)],2);assert {p.relative_to(kit).as_posix():pin(p) for p in kit.rglob('*') if p.is_file()}==members;call('help','GseapyWorkflowCli',['--help'],0)
            request=copy.deepcopy(baseline);destination=home/'skip';request['out']=path(destination)
            if is_linux:request['runtime']=linux(home/'missing-runtime.json')
            else:
                request['runtime']=str(local(request['runtime']))
                for case in request['analyses']:case['input']['path']=str(local(case['input']['path']))
            file=home/'skip.json';save(file,request);call('skip-platform-or-missing-runtime','GseapyWorkflowCli',['--request',path(file)],3);status=json.loads((destination/'analysis-status.json').read_bytes());assert status['status']=='SKIP3_GSEAPY_DEPENDENCIES_UNAVAILABLE' and not status['result_admitted'] and not(destination/'worker').exists() and 'SKIP3' in (destination/'WARNINGS.md').read_text()
            invalid=copy.deepcopy(request);invalid['out']=path(home/'invalid-method-out');invalid['analyses'][0]['method']='guess-from-title';file=home/'invalid-method.json';save(file,invalid);call('reject-unknown-method','GseapyWorkflowCli',['--request',path(file)],2);assert not(home/'invalid-method-out').exists()
            invalid=copy.deepcopy(request);invalid['out']=path(home/'invalid-interface-out');invalid['analyses'][0]['interface']='legacy-ignored-options';file=home/'invalid-interface.json';save(file,invalid);call('reject-ambiguous-wrapper','GseapyWorkflowCli',['--request',path(file)],2);assert not(home/'invalid-interface-out').exists()
        assert all(pin(ROOT/n)==v for n,v in built['source_pins'].items());state.update(status='passed-three-platform-export-skip-and-invalid-interface-contracts',artifact=built['artifact'],source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())},analysis_installed_software=False)
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2

if __name__=='__main__':raise SystemExit(main())
