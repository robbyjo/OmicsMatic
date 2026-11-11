# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay all complete native audits on retained data on three JVMs; never relabel a failed batch as admitted."""
import argparse,json,os,subprocess,sys,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21

def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['build','original','out']:p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();original=a.original.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/gseapy-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_GSEAPY_COMPLETE_NATIVE_REPLAY',runs=[],original_batch_relabelled=False,whole_goal_complete=False)
    try:
        built=json.loads((a.build/'COMPLETED.json').read_bytes());assert built['status']=='passed-compiled-additive-GSEApy-analysis-entry-sources-only';jar=ROOT/built['artifact']['path'];assert pin(jar)=={k:built['artifact'][k] for k in ['bytes','sha256']};before={p.relative_to(original).as_posix():pin(p) for p in original.rglob('*') if p.is_file()};save(out/'original-file-pins.json',before);driver=ROOT/'tools/literature/java/org/jlinalg/cli/GseapyExecutionReplay.java';classes=out/'classes';classes.mkdir();command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-encoding','UTF-8','-cp',str(jar),'-d',str(classes),str(driver)];run=subprocess.run(command,capture_output=True,timeout=90);(out/'compile.stdout').write_bytes(run.stdout);(out/'compile.stderr').write_bytes(run.stderr);save(out/'compile.json',dict(command=command,exit_code=run.returncode));assert run.returncode==0,run.stderr.decode(errors='replace')
        for label,java,linux,major in [('windows21',str(JAVA21/'java.exe'),False,'21'),('windows25','C:/Program Files/Common Files/Oracle/Java/javapath/java.exe',False,'25'),('linux21','/usr/bin/java',True,'21')]:
            home=out/label;home.mkdir();path=lambda p:'/mnt/d/projects/JLinAlg/'+p.relative_to(ROOT).as_posix() if linux else str(p);prefix=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec',java] if linux else [java];version=subprocess.run(prefix+['-version'],capture_output=True,timeout=60);(home/'version.stderr').write_bytes(version.stderr);assert version.returncode==0 and ('"'+major+'.' in (version.stdout+version.stderr).decode(errors='replace') or '"'+major+'"' in (version.stdout+version.stderr).decode(errors='replace'));command=prefix+['-Xmx2g','-cp',path(classes)+(':' if linux else ';')+path(jar),'org.jlinalg.cli.GseapyExecutionReplay',path(original),path(home/'replay')]
            with (home/'replay.stdout').open('xb') as first,(home/'replay.stderr').open('xb') as second:run=subprocess.run(command,stdout=first,stderr=second,cwd=ROOT,timeout=900)
            save(home/'process.json',dict(command=command,exit_code=run.returncode));assert run.returncode==0,'Read retained '+str(home/'replay.stderr');report=json.loads((home/'replay/COMPLETED.json').read_bytes());assert report['status']=='passed-complete-retained-native-audit-replay-only' and report['failed_cases']==0 and len(report['cases'])==90 and not report['original_execution_admitted'];state['runs'].append(dict(jvm=label,cases=len(report['cases']),report=pin(home/'replay/COMPLETED.json')));print(label,'all90 complete native audits passed; original admission unchanged',flush=True)
        assert all(pin(original/n)==v for n,v in before.items()) and all(pin(ROOT/n)==v for n,v in built['source_pins'].items());state.update(status='passed-three-JVM-90-complete-retained-GSEApy-native-audits-only',artifact=built['artifact'],source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve()),driver.relative_to(ROOT).as_posix():pin(driver)},original_file_pins=pin(out/'original-file-pins.json'))
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2

if __name__=='__main__':raise SystemExit(main())
