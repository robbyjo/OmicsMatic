# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build isolated native recovery classes; canonical/runtime and unrelated code stay unchanged."""
import argparse,json,subprocess,traceback,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--compiler',choices=['javac21','javac25'],required=True);a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/cistarget-runtime');out.mkdir(parents=True)
    state=dict(status='FAIL2_CISTARGET_COMPONENT_BUILD',implementation_credit=False,whole_goal_complete=False)
    try:
        canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';before=pin(canonical)
        compiler='C:/Program Files/'+('Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/javac.exe' if a.compiler=='javac21' else 'Common Files/Oracle/Java/javapath/javac.exe')
        sources=[ROOT/'src/main/java/org/jlinalg/enrichment/CisTargetRecovery.java',ROOT/'src/test/java/org/jlinalg/enrichment/CisTargetRecoveryProbe.java']
        paths=[Path(__file__).resolve(),*sources];pins={p.relative_to(ROOT).as_posix():pin(p) for p in paths}
        for p in paths:target=out/'source-snapshot'/p.relative_to(ROOT);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(p.read_bytes())
        classes=out/'classes';classes.mkdir();command=[compiler,'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-d',str(classes),*map(str,sources)]
        run=subprocess.run(command,capture_output=True,timeout=120);save(out/'compile.json',dict(command=command,exit_code=run.returncode));(out/'compile.stdout').write_bytes(run.stdout);(out/'compile.stderr').write_bytes(run.stderr);assert run.returncode==0,run.stderr.decode(errors='replace')
        jar=out/'cistarget-recovery-development.jar'
        with zipfile.ZipFile(jar,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
            for f in sorted(classes.rglob('*.class')):
                info=zipfile.ZipInfo(f.relative_to(classes).as_posix(),(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,f.read_bytes())
            z.writestr('META-INF/LICENSE',(ROOT/'LICENSE').read_bytes())
        assert pin(canonical)==before and all(pin(ROOT/n)==v for n,v in pins.items())
        state.update(status='PASS_ISOLATED_JAVA17_CISTARGET_RECOVERY_COMPONENT_BUILD_ONLY',compiler=a.compiler,artifact=dict(path=jar.relative_to(ROOT).as_posix(),**pin(jar)),source_pins=pins,canonical_executable_unchanged=before,full_file_workflow_available=False)
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source and canonical preservation checks')
    raise SystemExit(main())
