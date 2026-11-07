# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build exact root dispatch plus native ORDER facade over the immutable validated workflow parent."""
import argparse
import json
from pathlib import Path
import subprocess
import zipfile
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21
from catalog_source_integrity import reverse_delta


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve()
    base=ROOT/'build/literature-review/monocle-runtime';assert out.is_relative_to(base) and not out.exists();out.mkdir()
    parent_record=json.loads((base/'workflow-build-3/COMPLETED.json').read_bytes())['artifact'];parent=ROOT/parent_record['path'];assert pin(parent)=={k:parent_record[k] for k in ['bytes','sha256']}
    canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');root=ROOT/'src/main/java/org/jlinalg/cli/JLinAlgCli.java'
    delta=ROOT/'docs/literature/omics-2023-2026/monocle-root-shared-source-compatibility.json';compat=json.loads(delta.read_bytes());assert len(compat['paths'])==1
    before=reverse_delta(root.read_bytes(),compat['paths'][0]);assert b'monocle-tools' not in before
    sources=[root,ROOT/'src/main/java/org/jlinalg/cli/MonocleToolsCli.java',ROOT/'src/main/java/org/jlinalg/cli/Monocle3OrderCli.java']
    pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),delta,*sources]}
    for n in pins:
        target=out/'source-snapshot'/n;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/n).read_bytes())
    classes=out/'classes';classes.mkdir()
    command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-cp',str(parent),'-sourcepath',str(out/'source-snapshot/src/main/java'),'-d',str(classes),*map(str,sources)]
    r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=120);(out/'compile.stdout').write_bytes(r.stdout);(out/'compile.stderr').write_bytes(r.stderr);save(out/'compile.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,r.stderr.decode('utf8',errors='replace')
    additions={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')};root_entry='org/jlinalg/cli/JLinAlgCli.class';assert len(additions)==3 and root_entry in additions
    # Independently compile the exact inverse root source to show that the parent
    # root entry is precisely the pre-route source, rather than an unrelated class.
    oldsrc=out/'inverse-root/JLinAlgCli.java';oldsrc.parent.mkdir();oldsrc.write_bytes(before);oldclasses=out/'inverse-classes';oldclasses.mkdir()
    command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-cp',str(parent),'-sourcepath',str(oldsrc.parent),'-d',str(oldclasses),str(oldsrc)]
    r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=120);(out/'inverse-compile.stdout').write_bytes(r.stdout);(out/'inverse-compile.stderr').write_bytes(r.stderr);assert r.returncode==0,r.stderr.decode('utf8',errors='replace')
    jar=out/'omicsmatic-0.3.7-monocle-root-development.jar'
    with zipfile.ZipFile(parent) as old,zipfile.ZipFile(jar,'x') as new:
        assert old.read(root_entry)==(oldclasses/root_entry).read_bytes() and set(additions)&set(old.namelist())=={root_entry}
        for entry in old.infolist():new.writestr(entry,additions.get(entry.filename,old.read(entry.filename)))
        for n in sorted(set(additions)-set(old.namelist())):new.writestr(n,additions[n],compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(parent) as old,zipfile.ZipFile(jar) as new:
        assert new.testzip() is None and len(new.namelist())==len(set(new.namelist()))
        assert [n for n in old.namelist() if old.read(n)!=new.read(n)]==[root_entry]
        assert set(new.namelist())-set(old.namelist())==set(additions)-{root_entry}
        count=len(old.namelist())-1
    assert all(pin(ROOT/n)==v for n,v in pins.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical
    save(out/'COMPLETED.json',dict(status='compiled-exact-Monocle-root-dispatch-and-native-order-not-validation',artifact=dict(path=jar.relative_to(ROOT).as_posix(),**pin(jar)),parent_artifact=parent_record,
        source_pins=pins,unchanged_parent_entries=count,replaced_entries=[root_entry],added_entries=sorted(set(additions)-{root_entry}),canonical_executable_unchanged=canonical,
        original_workflow_entries_unchanged=True,implementation_credit=False,whole_goal_complete=False))
    print('Built exact root routing plus native ORDER; all scientific workflow/native parent entries preserved')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact parent/source checks')
    main()
