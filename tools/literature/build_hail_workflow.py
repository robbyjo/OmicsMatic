# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Add typed Hail workflow development classes/resources without changing parent/canonical entries. No full adapter credit."""
import argparse,json,subprocess,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21,linux

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/hail-runtime') and not out.exists();out.mkdir(parents=True);parent_proof=json.loads((ROOT/'docs/literature/hail-sample-qc-development/VALIDATION.json').read_bytes());parent=ROOT/parent_proof['artifact']['path'];assert pin(parent)=={k:parent_proof['artifact'][k] for k in ['bytes','sha256']};sources=[*(ROOT/'src/main/java/org/jlinalg/cli').glob('HailWorkflow*.java'),ROOT/'tools/literature/java/org/jlinalg/cli/HailWorkflowRequestMatrix.java'];resources=sorted((ROOT/'src/main/resources/org/jlinalg/omics/hail-workflow').rglob('*'));resources=[f for f in resources if f.is_file()];pins={f.relative_to(ROOT).as_posix():pin(f) for f in [Path(__file__).resolve(),*sources,*resources]};canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')
    for name in pins:target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
    classes=out/'classes';classes.mkdir();command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-cp',str(parent),'-d',str(classes),*map(str,sources)];r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=120);(out/'compile.stdout').write_bytes(r.stdout);(out/'compile.stderr').write_bytes(r.stderr);save(out/'compile.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,r.stderr.decode('utf8',errors='replace');added={f.relative_to(classes).as_posix():f.read_bytes() for f in classes.rglob('*.class')};added.update({f.relative_to(ROOT/'src/main/resources').as_posix():f.read_bytes() for f in resources});jar=out/'omicsmatic-0.3.7-hail-workflow-development.jar'
    with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar,'x') as after:
        assert set(added).isdisjoint(before.namelist())
        for info in before.infolist():after.writestr(info,before.read(info.filename))
        for name,raw in sorted(added.items()):after.writestr(name,raw,compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar) as after:assert after.testzip() is None and all(after.read(n)==before.read(n) for n in before.namelist());unchanged=len(before.namelist())
    assert all(pin(ROOT/name)==value for name,value in pins.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical;save(out/'COMPLETED.json',dict(status='compiled-typed-Hail-workflow-development-not-public-analysis-validation',artifact=dict(path=jar.relative_to(ROOT).as_posix(),**pin(jar)),source_pins=pins,parent_artifact=parent_proof['artifact'],unchanged_parent_entries=unchanged,added_entries=sorted(added),canonical_executable_unchanged=canonical,implementation_credit=False,whole_goal_complete=False));print('Compiled typed Hail workflow candidate;',len(added),'new entries; no full adapter credit')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable additive build/source checks')
    main()
