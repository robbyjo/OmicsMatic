# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Add the complete typed original dndscv workflow to the validated native coding candidate; no admission yet."""
import argparse,json,subprocess,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();base=ROOT/'build/literature-review/dndscv-runtime';out=a.out.resolve();assert out.is_relative_to(base) and not out.exists();out.mkdir();native=json.loads((base/'coding-validation-3/COMPLETED.json').read_bytes());assert native['component_admitted'] and not native['dndscv_workflow_admitted'];record=native['artifact'];parent=ROOT/record['path'];assert pin(parent)=={k:record[k] for k in ['bytes','sha256']};canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');sources=[ROOT/f'src/main/java/org/jlinalg/cli/DndscvWorkflow{n}.java' for n in ['Request','Inputs','Runtime','Transport','Audit','Cli']];resources=ROOT/'src/main/resources/org/jlinalg/omics/dndscv-workflow';pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),*sources,*resources.glob('*')]}
    for name in pins:
        target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
    classes=out/'classes';classes.mkdir();command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-cp',str(parent),'-d',str(classes),*map(str,sources)];r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=120);(out/'compile.stdout').write_bytes(r.stdout);(out/'compile.stderr').write_bytes(r.stderr);save(out/'compile.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,r.stderr.decode('utf8',errors='replace');added={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')};added.update({p.relative_to(ROOT/'src/main/resources').as_posix():p.read_bytes() for p in resources.glob('*') if p.is_file()});jar=out/'omicsmatic-0.3.7-dndscv-workflow-development.jar'
    with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar,'x') as after:
        assert set(added).isdisjoint(before.namelist())
        for info in before.infolist():after.writestr(info,before.read(info.filename))
        for name,raw in sorted(added.items()):after.writestr(name,raw,compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar) as after:assert after.testzip() is None and len(after.namelist())==len(set(after.namelist())) and all(before.read(n)==after.read(n) for n in before.namelist());count=len(before.namelist())
    assert all(pin(ROOT/n)==v for n,v in pins.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical;save(out/'COMPLETED.json',dict(status='compiled-additive-original-dndscv-workflow-not-validation',artifact=dict(path=jar.relative_to(ROOT).as_posix(),**pin(jar)),parent_artifact=record,source_pins=pins,unchanged_parent_entries=count,added_entries=sorted(added),canonical_executable_unchanged=canonical,dndscv_workflow_admitted=False,implementation_credit=False,whole_goal_complete=False));print('Built typed original dndscv candidate;',len(added),'new entries')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/artifact checks')
    main()
