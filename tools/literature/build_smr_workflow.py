# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compile the typed original SMR/HEIDI workflow additively; compilation gives no integration credit."""
import argparse,json,subprocess,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();base=ROOT/'build/literature-review/smr-runtime';assert out.is_relative_to(base) and not out.exists();out.mkdir(parents=True);native=json.loads((base/'statistics-validation-1/COMPLETED.json').read_bytes());assert native['component_admitted'] and not native['SMR_or_HEIDI_workflow_admitted'];record=native['artifact'];parent=ROOT/record['path'];assert pin(parent)=={k:record[k] for k in ['bytes','sha256']};canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');sources=[ROOT/f'src/main/java/org/jlinalg/cli/SmrWorkflow{n}.java' for n in ['Request','Inputs','Besd','Runtime','Audit','Cli']]+[ROOT/'src/main/java/org/jlinalg/cli/SmrQfilePreparation.java'];resources=ROOT/'src/main/resources/org/jlinalg/omics/smr-workflow';pins={f.relative_to(ROOT).as_posix():pin(f) for f in [Path(__file__).resolve(),*sources,*resources.glob('*')]}
    for name in pins:
        target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
    classes=out/'classes';classes.mkdir();command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-cp',str(parent),'-d',str(classes),*map(str,sources)];r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=120);(out/'compile.stdout').write_bytes(r.stdout);(out/'compile.stderr').write_bytes(r.stderr);save(out/'compile.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,r.stderr.decode('utf8',errors='replace');added={f.relative_to(classes).as_posix():f.read_bytes() for f in classes.rglob('*.class')};added.update({f.relative_to(ROOT/'src/main/resources').as_posix():f.read_bytes() for f in resources.glob('*') if f.is_file()});jar=out/'omicsmatic-0.3.7-smr-workflow-development.jar'
    with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar,'x') as after:
        assert set(added).isdisjoint(before.namelist())
        for info in before.infolist():after.writestr(info,before.read(info.filename))
        for name,raw in sorted(added.items()):after.writestr(name,raw,compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar) as after:assert after.testzip() is None and len(after.namelist())==len(set(after.namelist())) and all(before.read(n)==after.read(n) for n in before.namelist());count=len(before.namelist())
    assert all(pin(ROOT/n)==v for n,v in pins.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical;save(out/'COMPLETED.json',dict(status='compiled-original-SMR-HEIDI-workflow-not-validation',artifact=dict(path=jar.relative_to(ROOT).as_posix(),**pin(jar)),parent_artifact=record,source_pins=pins,unchanged_parent_entries=count,added_entries=sorted(added),canonical_executable_unchanged=canonical,adapter_admitted=False,implementation_credit=False,whole_goal_complete=False));print('Compiled',len(added),'entries; parent and canonical unchanged')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/artifact checks')
    main()
