# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay both original R reference families with only the native API JAR and a JDK."""
import argparse,json,os,shutil,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from prepare_rsubread_standalone import verify as source_derivation


def linux(path):return '/mnt/d/projects/JLinAlg/'+path.relative_to(ROOT).as_posix()


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['api','controls2260','controls2232','out']:p.add_argument('--'+name,type=Path,required=True)
    p.add_argument('--javac',default='javac');p.add_argument('--java',default='java');p.add_argument('--three-jvms',action='store_true');a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/rsubread-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_RSUBREAD_STANDALONE_REPLAY',analysis_admitted=False,implementation_credit=False,full_adapter_available=False,whole_goal_complete=False,runs={})
    try:
        source_derivation();jar=a.api.resolve();artifact=dict(path=jar.relative_to(ROOT).as_posix(),**pin(jar));source=ROOT/'tools/literature/java/org/jlinalg/cli/RsubreadStandaloneMatrixAudit.java';classes=out/'classes';classes.mkdir();javac=Path(shutil.which(a.javac) or a.javac).resolve();command=[str(javac),'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-cp',str(jar),'-d',str(classes),str(source)];run=subprocess.run(command,capture_output=True,timeout=120);(out/'compile.stdout').write_bytes(run.stdout);(out/'compile.stderr').write_bytes(run.stderr);save(out/'compile.process.json',dict(command=command,exit_code=run.returncode));assert run.returncode==0,run.stderr.decode(errors='replace');profiles=[('2260',a.controls2260.resolve(),'passed-sixteen-complete-original-Rsubread2260-counting-and-ten-original-cbind-controls-only'),('2232-R441',a.controls2232.resolve(),'passed-sixteen-complete-original-Rsubread2232-R441-counting-and-ten-original-cbind-controls-only')];references={};allpins={}
        for edition,controls,status in profiles:
            proof=json.loads((controls/'COMPLETED.json').read_bytes());assert proof['status']==status;references[edition]=dict(path=(controls/'COMPLETED.json').relative_to(ROOT).as_posix(),**pin(controls/'COMPLETED.json'));allpins[edition]={q.relative_to(controls).as_posix():pin(q) for q in controls.rglob('*') if q.is_file()}
            if a.three_jvms:
                java21='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe';runtimes=[('windows21',[java21]),('windows25',['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe']),('linux21',['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'])]
            else:runtimes=[('current',[str(Path(shutil.which(a.java) or a.java).resolve())])]
            state['runs'][edition]={}
            for runtime,prefix in runtimes:
                home=out/(edition+'-'+runtime);islinux=runtime=='linux21';classpath=linux(classes)+':'+linux(jar) if islinux else str(classes)+os.pathsep+str(jar);command=prefix+['-Xmx128m','-cp',classpath,'org.jlinalg.cli.RsubreadStandaloneMatrixAudit',linux(controls/'original') if islinux else str(controls/'original'),linux(home) if islinux else str(home)]
                with (out/(edition+'-'+runtime+'.stdout')).open('xb') as first,(out/(edition+'-'+runtime+'.stderr')).open('xb') as second:run=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=first,stderr=second,timeout=180,creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
                save(out/(edition+'-'+runtime+'.process.json'),dict(command=command,exit_code=run.returncode));assert run.returncode==0,(edition,runtime);report=json.loads((home/'audit.json').read_bytes());assert report['status']=='passed-complete-original-R-count-matrices-and-positional-cbind-only' and (report['original_count_matrices'],report['original_cbind_positive'],report['original_cbind_errors'],report['strict_name_mismatch_rejections'],report['contract_rejections'],report['numeric_values_replayed'],report['names_replayed'])==(16,9,1,1,42,265,179);state['runs'][edition][runtime]=report
            assert all(pin(controls/name)==value for name,value in allpins[edition].items())
        assert artifact==dict(path=jar.relative_to(ROOT).as_posix(),**pin(jar));state.update(status='passed-JDK-only-both-original-Rsubread-matrix-reference-families',artifact=artifact,references=references,source_pins={q.relative_to(ROOT).as_posix():pin(q) for q in [Path(__file__).resolve(),source,ROOT/'tools/literature/prepare_rsubread_standalone.py']},original_evidence_unchanged=True,prior_project_JAR_on_classpath=False,R_runtime_required=False)
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete original-family/JDK-only replay gates')
    raise SystemExit(main())
