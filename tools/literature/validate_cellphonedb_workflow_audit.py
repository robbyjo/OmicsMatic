# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Three actual JVM audits of separately copied safe, complete original-workflow controls; never reads pickle objects."""
import argparse,json,shutil,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--build',required=True,type=Path);parser.add_argument('--out',required=True,type=Path);args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/cellphonedb-runtime') and not out.exists();out.mkdir(parents=True);state=dict(status='FAIL2_CELLPHONEDB_WORKFLOW_NATIVE_AUDIT',implementation_credit=False,whole_goal_complete=False,processes=[])
    try:
        built=json.loads((args.build/'COMPLETED.json').read_bytes());assert built['status']=='PASS_COMPILED_CELLPHONEDB_WORKFLOW_DEVELOPMENT_ONLY';jar=ROOT/built['artifact']['path'];assert pin(jar)=={k:built['artifact'][k] for k in ['bytes','sha256']};control=ROOT/'build/literature-review/cellphonedb-runtime/safe-worker-controls-3';proof=json.loads((control/'COMPLETED.json').read_bytes());assert proof['status']=='PASS_PAIRED_SAFE_CELLPHONEDB_WORKER_CONTROLS_ONLY' and proof['runtime_unchanged'] and proof['source_inputs_unchanged'];sources={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),*control.rglob('*')] if p.is_file()};canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')
        for label,java,linux in [('windows21',str(JAVA21/'java.exe'),False),('windows25','C:/Program Files/Common Files/Oracle/Java/javapath/java.exe',False),('linux21','/usr/bin/java',True)]:
            home=out/label;home.mkdir();shutil.copytree(control/'candidate',home/'copied-originals');request=json.loads((control/'request.json').read_bytes());request.update(mode='RUN',runtime='/mnt/d/projects/JLinAlg/build/cellphonedb-workflow-case-sensitive-1/setup-1/COMPLETED.json',out=str(home/'never-run'))
            def mapped(value):
                if isinstance(value,dict):return {k:(str(ROOT/Path(v).relative_to('/mnt/d/projects/JLinAlg')) if k=='path' and isinstance(v,str) and v.startswith('/mnt/d/projects/JLinAlg/') and not linux else mapped(v)) for k,v in value.items()}
                if isinstance(value,list):return list(map(mapped,value))
                return value
            save(home/'request.json',mapped(request))
            def path(p):return '/mnt/d/projects/JLinAlg/'+p.relative_to(ROOT).as_posix() if linux else str(p)
            command=[java,'-Xmx4g','-cp',path(jar),'org.jlinalg.cli.CellPhoneDbWorkflowAuditMatrix',path(home/'request.json'),path(home/'copied-originals'),path(home/'native-audit.json')]
            if linux:command=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec',*command]
            run=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=300);(home/'stdout').write_bytes(run.stdout);(home/'stderr').write_bytes(run.stderr);record=dict(label=label,command=command,exit_code=run.returncode);save(home/'process.json',record);state['processes'].append(record);assert run.returncode==0,run.stderr.decode('utf8',errors='replace');report=json.loads((home/'native-audit.json').read_bytes());assert len(report['cases'])==19;record['audit']=report
        assert all(pin(ROOT/n)==want for n,want in sources.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical;state.update(status='PASS_THREE_JVM_NATIVE_CELLPHONEDB_WORKFLOW_CONTROL_AUDITS_ONLY',build=built['artifact'],source_pins=sources,canonical_executable_unchanged=canonical)
    except Exception as failure:state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':raise SystemExit(main())
