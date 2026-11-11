# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compile the additive native matrix component over the unchanged admitted hifiasm parent."""
import argparse,json,subprocess,traceback,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/rsubread-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_RSUBREAD_NATIVE_BUILD',analysis_admitted=False,implementation_credit=False,full_adapter_available=False,whole_goal_complete=False)
    try:
        from hifiasm_execution_evidence import verify
        parentproof=verify(False);parent=ROOT/parentproof['artifact']['path'];assert pin(parent)=={k:parentproof['artifact'][k] for k in ['bytes','sha256']};canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');sources=[ROOT/'src/main/java/org/jlinalg/sequence/RsubreadCountMatrix.java'];pins={q.relative_to(ROOT).as_posix():pin(q) for q in [Path(__file__).resolve(),*sources]}
        for name in pins:copy=out/'source-snapshot'/name;copy.parent.mkdir(parents=True,exist_ok=True);copy.write_bytes((ROOT/name).read_bytes())
        classes=out/'classes';classes.mkdir();command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-cp',str(parent),'-d',str(classes),*map(str,sources)];run=subprocess.run(command,capture_output=True,timeout=120);(out/'compile.stdout').write_bytes(run.stdout);(out/'compile.stderr').write_bytes(run.stderr);save(out/'compile.json',dict(command=command,exit_code=run.returncode));assert run.returncode==0,run.stderr.decode(errors='replace');added={q.relative_to(classes).as_posix():q.read_bytes() for q in classes.rglob('*.class')};jar=out/'omicsmatic-0.3.7-rsubread-native-development.jar'
        with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar,'x') as after:
            assert added and set(added).isdisjoint(before.namelist())
            for info in before.infolist():after.writestr(info,before.read(info.filename))
            for name,raw in sorted(added.items()):after.writestr(name,raw,compress_type=zipfile.ZIP_DEFLATED)
        with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar) as after:assert after.testzip() is None and all(before.read(name)==after.read(name) for name in before.namelist());count=len(before.namelist())
        assert all(pin(ROOT/name)==value for name,value in pins.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical;state.update(status='passed-additive-native-Rsubread-count-matrix-component-only',artifact=dict(path=jar.relative_to(ROOT).as_posix(),**pin(jar)),parent_artifact=parentproof['artifact'],source_pins=pins,unchanged_parent_entries=count,added_entries=sorted(added),canonical_executable_unchanged=canonical)
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable parent/source/compile gates')
    raise SystemExit(main())
