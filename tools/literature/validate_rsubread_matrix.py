# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay complete original R count matrices and cbind results on three JVMs without whole-tool credit."""
import argparse,json,os,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21


def linux(path):return '/mnt/d/projects/JLinAlg/'+path.relative_to(ROOT).as_posix()


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['build','controls','out']:p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/rsubread-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_RSUBREAD_NATIVE_MATRIX_VALIDATION',analysis_admitted=False,implementation_credit=False,whole_goal_complete=False,runs={})
    try:
        build=json.loads((a.build/'COMPLETED.json').read_bytes());assert build['status']=='passed-additive-native-Rsubread-count-matrix-component-only';jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};controls=a.controls.resolve();proof=json.loads((controls/'COMPLETED.json').read_bytes());assert proof['status']=='passed-sixteen-complete-original-Rsubread2260-counting-and-ten-original-cbind-controls-only';original=controls/'original';inputpins={q.relative_to(controls).as_posix():pin(q) for q in controls.rglob('*') if q.is_file()};source=ROOT/'tools/literature/java/org/jlinalg/cli/RsubreadMatrixAudit.java';classes=out/'classes';classes.mkdir();command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-cp',str(jar),'-d',str(classes),str(source)];run=subprocess.run(command,capture_output=True,timeout=120);(out/'compile.stdout').write_bytes(run.stdout);(out/'compile.stderr').write_bytes(run.stderr);save(out/'compile.json',dict(command=command,exit_code=run.returncode));assert run.returncode==0,run.stderr.decode(errors='replace')
        runtimes=[('windows21',[str(JAVA21/'java.exe'),'-Xmx128m','-cp',str(classes)+';'+str(jar),'org.jlinalg.cli.RsubreadMatrixAudit',str(original),str(out/'windows21')]),('windows25',['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe','-Xmx128m','-cp',str(classes)+';'+str(jar),'org.jlinalg.cli.RsubreadMatrixAudit',str(original),str(out/'windows25')]),('linux21',['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java','-Xmx128m','-cp',linux(classes)+':'+linux(jar),'org.jlinalg.cli.RsubreadMatrixAudit',linux(original),linux(out/'linux21')])]
        for name,command in runtimes:
            with (out/(name+'.stdout')).open('xb') as first,(out/(name+'.stderr')).open('xb') as second:run=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=first,stderr=second,timeout=180,creationflags=subprocess.CREATE_NO_WINDOW)
            save(out/(name+'.process.json'),dict(command=command,exit_code=run.returncode));assert run.returncode==0,name;report=json.loads((out/name/'audit.json').read_bytes());assert report['status']=='passed-complete-original-R-count-matrices-and-positional-cbind-only' and report['original_count_matrices']==16 and report['original_cbind_positive']==9 and report['original_cbind_errors']==report['strict_name_mismatch_rejections']==1 and report['contract_rejections']>=35 and not report['implementation_credit'];state['runs'][name]=report
        assert all(pin(controls/name)==value for name,value in inputpins.items()) and all(pin(ROOT/name)==value for name,value in build['source_pins'].items());state.update(status='passed-three-JVM-complete-original-Rsubread-matrices-and-cbind-component-only',artifact=build['artifact'],original_controls=dict(path=(controls/'COMPLETED.json').relative_to(ROOT).as_posix(),**pin(controls/'COMPLETED.json')),original_evidence_unchanged=True,source_pins={q.relative_to(ROOT).as_posix():pin(q) for q in [Path(__file__).resolve(),source]},full_adapter_available=False)
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original/JVM/source gates')
    raise SystemExit(main())
