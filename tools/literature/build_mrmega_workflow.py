# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build native file workflow while retaining sealed ancestry/regression class bytes."""
import argparse,json,subprocess,traceback,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True)
    parser.add_argument('--compiler',choices=['javac21','javac25'],required=True);args=parser.parse_args()
    out=args.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/mrmega-runtime');out.mkdir(parents=True)
    state=dict(status='FAIL2_NATIVE_MRMEGA_WORKFLOW_BUILD',implementation_credit=False,whole_goal_complete=False)
    try:
        canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';before=pin(canonical)
        parent=ROOT/'docs/literature/mrmega-ancestry-development';proof=json.loads((parent/'VALIDATION.json').read_bytes())
        kernel=parent/('mrmega-ancestry-'+args.compiler+'.jar');assert pin(kernel)==proof['artifacts'][kernel.name]
        javac='C:/Program Files/'+('Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/javac.exe' if args.compiler=='javac21' else 'Common Files/Oracle/Java/javapath/javac.exe')
        sources=[ROOT/'src/main/java/org/jlinalg/meta/MrMegaWorkflow.java',ROOT/'src/main/java/org/jlinalg/cli/MrMegaWorkflowCli.java']
        paths=[Path(__file__).resolve(),*sources];pins={p.relative_to(ROOT).as_posix():pin(p) for p in paths}
        for p in paths:
            target=out/'source-snapshot'/p.relative_to(ROOT);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(p.read_bytes())
        classes=out/'classes';classes.mkdir();command=[javac,'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-cp',str(kernel)+';'+str(canonical),'-d',str(classes),*map(str,sources)]
        run=subprocess.run(command,capture_output=True,timeout=120);save(out/'compile.json',dict(command=command,exit_code=run.returncode));(out/'compile.stdout').write_bytes(run.stdout);(out/'compile.stderr').write_bytes(run.stderr)
        assert run.returncode==0,run.stderr.decode(errors='replace')
        with zipfile.ZipFile(kernel) as opened:payload={n:opened.read(n) for n in opened.namelist()}
        old=dict(payload)
        for p in classes.rglob('*.class'):
            name=p.relative_to(classes).as_posix();assert name not in payload and name.startswith(('org/jlinalg/meta/MrMegaWorkflow','org/jlinalg/cli/MrMegaWorkflowCli'));payload[name]=p.read_bytes()
        artifact=out/'mrmega-workflow-development.jar'
        with zipfile.ZipFile(artifact,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as opened:
            for name,raw in sorted(payload.items()):
                info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;opened.writestr(info,raw)
        with zipfile.ZipFile(artifact) as opened:assert all(opened.read(n)==raw for n,raw in old.items())
        assert pin(canonical)==before and all(pin(ROOT/n)==v for n,v in pins.items())
        state.update(status='PASS_NATIVE_MRMEGA_WORKFLOW_BUILD_ONLY',artifact=dict(path=artifact.relative_to(ROOT).as_posix(),**pin(artifact)),compiler=args.compiler,
            source_pins=pins,all_inherited_entries_unchanged=True,canonical_executable_unchanged=before,reference_validation_complete=False)
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable inherited-byte checks')
    raise SystemExit(main())
