# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Add native ancestry classes without rebuilding or changing the frozen regression."""
import argparse,json,subprocess,traceback,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True)
    p.add_argument('--compiler',choices=['javac21','javac25'],required=True);a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/mrmega-runtime');out.mkdir(parents=True)
    canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';before=pin(canonical)
    state=dict(status='FAIL2_MRMEGA_ANCESTRY_BUILD',implementation_credit=False,whole_goal_complete=False)
    try:
        parent=ROOT/'docs/literature/mrmega-regression-development';proof=json.loads((parent/'VALIDATION.json').read_bytes())
        jar_name='mrmega-regression-'+a.compiler+'.jar';kernel=parent/jar_name;assert pin(kernel)==proof['artifacts'][jar_name]
        javac='C:/Program Files/'+('Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/javac.exe' if a.compiler=='javac21' else 'Common Files/Oracle/Java/javapath/javac.exe')
        sources=[ROOT/'src/main/java/org/jlinalg/meta/MrMegaAncestry.java',ROOT/'src/test/java/org/jlinalg/meta/MrMegaAncestryReferenceProbe.java']
        paths=[Path(__file__).resolve(),*sources];pins={path.relative_to(ROOT).as_posix():pin(path) for path in paths}
        for path in paths:
            target=out/'source-snapshot'/path.relative_to(ROOT);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(path.read_bytes())
        classes=out/'classes';classes.mkdir();command=[javac,'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-cp',str(kernel)+';'+str(canonical),'-d',str(classes),*map(str,sources)]
        run=subprocess.run(command,capture_output=True,timeout=120);save(out/'compile.json',dict(command=command,exit_code=run.returncode));(out/'compile.stdout').write_bytes(run.stdout);(out/'compile.stderr').write_bytes(run.stderr);assert run.returncode==0,run.stderr.decode(errors='replace')
        with zipfile.ZipFile(kernel) as opened:payload={name:opened.read(name) for name in opened.namelist()}
        original=dict(payload)
        for path in classes.rglob('*.class'):
            name=path.relative_to(classes).as_posix();assert name.startswith('org/jlinalg/meta/MrMegaAncestry') and name not in payload
            payload[name]=path.read_bytes()
        artifact=out/'mrmega-ancestry-development.jar'
        with zipfile.ZipFile(artifact,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as opened:
            for name,raw in sorted(payload.items()):
                info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;opened.writestr(info,raw)
        with zipfile.ZipFile(artifact) as opened:assert all(opened.read(name)==raw for name,raw in original.items())
        assert pin(canonical)==before and all(pin(ROOT/name)==value for name,value in pins.items())
        state.update(status='PASS_ISOLATED_MRMEGA_ANCESTRY_BUILD_WITH_UNCHANGED_REGRESSION_BYTES_ONLY',
            artifact=dict(path=artifact.relative_to(ROOT).as_posix(),**pin(artifact)),compiler=a.compiler,source_pins=pins,
            inherited_kernel=dict(path=kernel.relative_to(ROOT).as_posix(),**pin(kernel)),all_inherited_entries_unchanged=True,
            canonical_executable_unchanged=before,complete_file_workflow_available=False)
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable inherited regression/source byte checks')
    raise SystemExit(main())
