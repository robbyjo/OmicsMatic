# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Validate real owned R timeout/output-limit/cancellation/interruption on both supported Windows JVMs."""
import argparse,json,os,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['build','out']:p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/rsubread-runtime');out.mkdir(parents=True)
    state=dict(status='FAIL2_RSUBREAD_ORIGINAL_PROCESS_CONTROLS',analysis_admitted=False,implementation_credit=False,whole_goal_complete=False,runs={})
    try:
        build=json.loads((a.build/'COMPLETED.json').read_bytes());assert build['status']=='passed-additive-JDK-only-Rsubread-original-counting-workflow-build';jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']}
        jdk=Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin');source=ROOT/'tools/literature/java/org/jlinalg/cli/RsubreadProcessAudit.java';classes=out/'classes';classes.mkdir()
        command=[str(jdk/'javac.exe'),'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-cp',str(jar),'-d',str(classes),str(source)];run=subprocess.run(command,capture_output=True,timeout=120);(out/'compile.stdout').write_bytes(run.stdout);(out/'compile.stderr').write_bytes(run.stderr);save(out/'compile.process.json',dict(command=command,exit_code=run.returncode));assert run.returncode==0,run.stderr.decode(errors='replace')
        for label,java in [('windows21',str(jdk/'java.exe')),('windows25','C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')]:
            command=[java,'-Xmx128m','-cp',str(classes)+os.pathsep+str(jar),'org.jlinalg.cli.RsubreadProcessAudit','C:/Program Files/R/R-4.6.1/bin/Rscript.exe','C:/Program Files/R/R-4.6.1',str(out/label)]
            run=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=120,creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0);(out/(label+'.stdout')).write_bytes(run.stdout);(out/(label+'.stderr')).write_bytes(run.stderr);save(out/(label+'.process.json'),dict(command=command,exit_code=run.returncode));assert run.returncode==0,label
            audit=json.loads((out/label/'audit.json').read_bytes());assert audit['status']=='passed-four-actual-original-R-owned-process-controls' and audit['actual_original_R_processes']==4;state['runs'][label]=audit
        assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items());state.update(status='passed-eight-actual-original-R-owned-process-controls-on-two-JVMs',artifact=build['artifact'],source_pins={q.relative_to(ROOT).as_posix():pin(q) for q in [Path(__file__).resolve(),source]},scientific_counting_in_these_process_tests=False)
    except Exception as e:state['error']=repr(e);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable actual-live-process and cleanup checks')
    raise SystemExit(main())
