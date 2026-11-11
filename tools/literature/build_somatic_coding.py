# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Add native coding arithmetic to the validated parent JAR without changing its existing entries."""
import argparse,json,subprocess,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/dndscv-runtime') and not out.exists();out.mkdir();parent_proof=json.loads((ROOT/'docs/literature/soupx-workflow-development/VALIDATION.json').read_bytes());assert parent_proof['adapter_admitted'];record=parent_proof['artifact'];parent=ROOT/record['path'];assert pin(parent)=={k:record[k] for k in ['bytes','sha256']};source=ROOT/'src/main/java/org/jlinalg/genetics/SomaticCoding.java';pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),source]};canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')
    for name in pins:
        target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
    classes=out/'classes';classes.mkdir();command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-cp',str(parent),'-d',str(classes),str(source)];r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=120);(out/'compile.stdout').write_bytes(r.stdout);(out/'compile.stderr').write_bytes(r.stderr);save(out/'compile.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,r.stderr.decode('utf8',errors='replace');added={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')};jar=out/'omicsmatic-0.3.7-somatic-coding-development.jar'
    with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar,'x') as after:
        assert len(before.namelist())==len(set(before.namelist())) and set(added).isdisjoint(before.namelist())
        for info in before.infolist():after.writestr(info,before.read(info.filename))
        for name,raw in sorted(added.items()):after.writestr(name,raw,compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar) as after:assert after.testzip() is None and len(after.namelist())==len(set(after.namelist())) and all(before.read(n)==after.read(n) for n in before.namelist());count=len(before.namelist())
    assert all(pin(ROOT/n)==v for n,v in pins.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical;save(out/'COMPLETED.json',dict(status='compiled-native-somatic-coding-not-validation',artifact=dict(path=jar.relative_to(ROOT).as_posix(),**pin(jar)),parent_artifact=record,source_pins=pins,unchanged_parent_entries=count,added_entries=sorted(added),canonical_executable_unchanged=canonical,component_admitted=False,dndscv_workflow_admitted=False,implementation_credit=False,whole_goal_complete=False));print('Built additive native coding candidate;',len(added),'new classes')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/artifact checks')
    main()
