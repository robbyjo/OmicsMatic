# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build analytical normal fitting additively; preserve the validated parent and canonical executable."""
import argparse
import json
from pathlib import Path
import subprocess
import zipfile
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve()
    assert out.is_relative_to(ROOT/'build/literature-review/fitdistrplus-runtime') and not out.exists();out.mkdir()
    proof=json.loads((ROOT/'docs/literature/cicero-prediction-development/VALIDATION.json').read_bytes());assert proof['workflow_admitted'] and proof['independent_membership_decisions']==43317
    record=proof['artifact'];parent=ROOT/record['path'];assert pin(parent)=={k:record[k] for k in ['bytes','sha256']};canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')
    sources=[ROOT/'src/main/java/org/jlinalg/distributional/NormalFit.java',ROOT/'src/main/java/org/jlinalg/cli/NormalFitToolsCli.java']
    pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),*sources]}
    for name in pins:
        path=out/'source-snapshot'/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes((ROOT/name).read_bytes())
    classes=out/'classes';classes.mkdir();command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-cp',str(parent),'-d',str(classes),*map(str,sources)]
    r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=120);(out/'compile.stdout').write_bytes(r.stdout);(out/'compile.stderr').write_bytes(r.stderr);save(out/'compile.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,r.stderr.decode('utf8',errors='replace')
    additions={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')};jar=out/'omicsmatic-0.3.7-normal-fit-development.jar'
    with zipfile.ZipFile(parent) as old,zipfile.ZipFile(jar,'x') as new:
        assert set(additions).isdisjoint(old.namelist())
        for e in old.infolist():new.writestr(e,old.read(e.filename))
        for name,raw in sorted(additions.items()):new.writestr(name,raw,compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(parent) as old,zipfile.ZipFile(jar) as new:
        assert new.testzip() is None and len(new.namelist())==len(set(new.namelist()));assert all(old.read(n)==new.read(n) for n in old.namelist());assert set(new.namelist())-set(old.namelist())==set(additions);count=len(old.namelist())
    assert all(pin(ROOT/n)==v for n,v in pins.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical
    save(out/'COMPLETED.json',dict(status='compiled-additive-normal-fit-not-validation',artifact=dict(path=jar.relative_to(ROOT).as_posix(),**pin(jar)),parent_artifact=record,source_pins=pins,unchanged_parent_entries=count,added_entries=sorted(additions),canonical_executable_unchanged=canonical,implementation_credit=False,whole_goal_complete=False));print('Built',jar)


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/artifact bindings')
    main()
