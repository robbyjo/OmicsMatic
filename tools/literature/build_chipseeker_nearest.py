# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Add only the new native nearest-feature API to an exact selected parent; change no existing entries."""
import argparse
import json
from pathlib import Path
import subprocess
import sys
import zipfile

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from validate_mashr_native import JAVA21,pin
from setup_deseq2_reference import save


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--parent-build',type=Path,required=True);parser.add_argument('--out',type=Path,required=True)
    args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/chipseeker-runtime') and not out.exists();out.mkdir()
    parent_proof=json.loads((args.parent_build/'COMPLETED.json').read_bytes());artifact=parent_proof['artifact'];parent=ROOT/artifact['path']
    assert pin(parent)=={k:artifact[k] for k in ['bytes','sha256']};canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')
    source=ROOT/'src/main/java/org/jlinalg/genetics/ChipSeekerNearestFeatures.java'
    pins={p.relative_to(ROOT).as_posix():pin(p) for p in [source,Path(__file__)]}
    for name in pins:
        target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
    classes=out/'classes';classes.mkdir()
    command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-d',str(classes),str(source)]
    r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=120);(out/'compile.stdout').write_bytes(r.stdout);(out/'compile.stderr').write_bytes(r.stderr)
    save(out/'compile.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,r.stderr.decode('utf8',errors='replace')
    additions={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')};assert additions
    jar=out/'omicsmatic-0.3.7-chipseeker-nearest-development.jar'
    with zipfile.ZipFile(parent) as old,zipfile.ZipFile(jar,'x') as current:
        assert set(additions).isdisjoint(old.namelist())
        for entry in old.infolist():current.writestr(entry,old.read(entry.filename))
        for name,raw in sorted(additions.items()):current.writestr(name,raw,compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(parent) as old,zipfile.ZipFile(jar) as current:
        assert current.testzip() is None and len(current.namelist())==len(set(current.namelist()))
        assert all(old.read(n)==current.read(n) for n in old.namelist()) and set(current.namelist())-set(old.namelist())==set(additions)
        count=len(old.namelist())
    assert all(pin(ROOT/n)==wanted for n,wanted in pins.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical
    save(out/'COMPLETED.json',dict(status='compiled-additive-ChIPseeker-nearest-component-not-workflow-admission',artifact=dict(path=jar.relative_to(ROOT).as_posix(),**pin(jar)),
        parent_artifact=artifact,source_pins=pins,unchanged_parent_entries=count,added_entries=sorted(additions),canonical_executable_unchanged=canonical,implementation_credit=False))
    print('Compiled native nearest-feature API; every parent entry preserved')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable artifact/source preservation checks')
    main()
