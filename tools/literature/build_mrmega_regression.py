# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build only the native prepared-cohort regression kernel and reference probe."""
import argparse,json,subprocess,traceback,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True)
    p.add_argument('--javac',type=Path,default=Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/javac.exe'));a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/mrmega-runtime');out.mkdir(parents=True)
    state=dict(status='FAIL2_MRMEGA_REGRESSION_BUILD',implementation_credit=False,whole_goal_complete=False)
    canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';before=pin(canonical)
    try:
        sources=[ROOT/'src/main/java/org/jlinalg/meta/MrMegaRegression.java',ROOT/'src/test/java/org/jlinalg/meta/MrMegaRegressionReferenceProbe.java']
        paths=[Path(__file__).resolve(),*sources,ROOT/'LICENSE'];pins={path.relative_to(ROOT).as_posix():pin(path) for path in paths}
        for path in paths:
            copy=out/'source-snapshot'/path.relative_to(ROOT);copy.parent.mkdir(parents=True,exist_ok=True);copy.write_bytes(path.read_bytes())
        classes=out/'classes';classes.mkdir()
        command=[str(a.javac),'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-cp',str(canonical),'-d',str(classes),*map(str,sources)]
        run=subprocess.run(command,capture_output=True,timeout=120);save(out/'compile.json',dict(command=command,exit_code=run.returncode,compiler=pin(a.javac.resolve())))
        (out/'compile.stdout').write_bytes(run.stdout);(out/'compile.stderr').write_bytes(run.stderr);assert run.returncode==0,run.stderr.decode(errors='replace')
        artifact=out/'mrmega-regression-development.jar'
        with zipfile.ZipFile(artifact,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as opened:
            payload={path.relative_to(classes).as_posix():path.read_bytes() for path in classes.rglob('*.class')}
            payload['META-INF/LICENSE']=(ROOT/'LICENSE').read_bytes()
            for name,raw in sorted(payload.items()):
                item=zipfile.ZipInfo(name,(1980,1,1,0,0,0));item.compress_type=zipfile.ZIP_DEFLATED;item.external_attr=0o100644<<16;opened.writestr(item,raw)
        assert all(pin(ROOT/name)==value for name,value in pins.items()) and pin(canonical)==before
        state.update(status='PASS_ISOLATED_JAVA17_MRMEGA_REGRESSION_BUILD_ONLY',artifact=dict(path=artifact.relative_to(ROOT).as_posix(),**pin(artifact)),
            source_pins=pins,canonical_executable_unchanged=before,
            external_Java_dependency=dict(path=canonical.relative_to(ROOT).as_posix(),**before,scope='Existing project JDistlib chi-square survival implementation. Thin development component is not a self-contained user-data workflow.'))
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable isolated source/dependency checks')
    raise SystemExit(main())
