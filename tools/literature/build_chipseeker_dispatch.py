# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compile only root/facade dispatch, preserving every selected annotation/native backend byte."""
import argparse
import json
from pathlib import Path
import subprocess
import zipfile
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--parent-build',type=Path,required=True);p.add_argument('--out',type=Path,required=True)
    args=p.parse_args();out=args.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/chipseeker-runtime') and not out.exists();out.mkdir()
    proof=json.loads((args.parent_build/'COMPLETED.json').read_bytes());parent=ROOT/proof['artifact']['path'];assert pin(parent)=={k:proof['artifact'][k] for k in ['bytes','sha256']}
    sources=[ROOT/'src/main/java/org/jlinalg/cli'/name for name in ['JLinAlgCli.java','ChipSeekerToolsCli.java']]
    pins={file.relative_to(ROOT).as_posix():pin(file) for file in [Path(__file__).resolve(),*sources]}
    for name in pins:
        target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
    classes=out/'classes';classes.mkdir()
    command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-cp',str(parent),'-d',str(classes),*map(str,sources)]
    r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=120);(out/'compile.stdout').write_bytes(r.stdout);(out/'compile.stderr').write_bytes(r.stderr)
    assert r.returncode==0,r.stderr.decode('utf-8',errors='replace')
    updates={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')}
    root='org/jlinalg/cli/JLinAlgCli.class';facade='org/jlinalg/cli/ChipSeekerToolsCli.class';assert set(updates)=={root,facade}
    jar=out/'omicsmatic-0.3.7-chipseeker-root-development.jar'
    with zipfile.ZipFile(parent) as old,zipfile.ZipFile(jar,'x') as new:
        assert facade not in old.namelist()
        for entry in old.infolist():new.writestr(entry,updates.get(entry.filename,old.read(entry.filename)))
        new.writestr(facade,updates[facade],compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(parent) as old,zipfile.ZipFile(jar) as new:
        assert new.testzip() is None
        changed=[name for name in old.namelist() if old.read(name)!=new.read(name)];assert changed==[root]
        assert set(new.namelist())-set(old.namelist())=={facade};unchanged=len(old.namelist())-1
    assert all(pin(ROOT/name)==value for name,value in pins.items())
    save(out/'COMPLETED.json',dict(status='compiled-root-dispatch-with-byte-identical-annotation-and-native-backends',artifact=dict(path=jar.relative_to(ROOT).as_posix(),**pin(jar)),
        parent_artifact=proof['artifact'],source_pins=pins,changed_parent_entries=changed,added_entries=[facade],unchanged_parent_entries=unchanged,
        implementation_credit=False,canonical_executable_changed=False))
    print('Compiled root/facade only; every annotation/native backend byte unchanged')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable scientific-parent preservation checks')
    main()
