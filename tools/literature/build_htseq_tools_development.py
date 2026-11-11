# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Overlay only the root HTSeq route on the native-binary/file-count development parent."""
import argparse
import json
import subprocess
import zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import byte_pin
from build_htseq_binary_development import BASE,JAR as PARENT
from validate_deseq2_inference import JAVA21

JAR=BASE/'tools-development/omicsmatic-0.3.7-htseq-tools-development.jar'


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True)
    out=parser.parse_args().out.resolve();assert out.is_relative_to(BASE) and not out.exists() and not JAR.exists();out.mkdir()
    source=ROOT/'src/main/java/org/jlinalg/cli/JLinAlgCli.java';classes=out/'classes';classes.mkdir()
    argv=list(map(str,[Path(JAVA21).with_name('javac.exe'),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-cp',PARENT,'-d',classes,source]))
    result=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=120)
    (out/'compile.stdout').write_bytes(result.stdout);(out/'compile.stderr').write_bytes(result.stderr);save(out/'compile.process.json',dict(command=argv,exit_code=result.returncode));assert result.returncode==0
    own={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')};assert set(own)=={'org/jlinalg/cli/JLinAlgCli.class'}
    JAR.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(PARENT) as old,zipfile.ZipFile(JAR,'x') as new:
        for entry in old.infolist():new.writestr(entry,own.get(entry.filename,old.read(entry.filename)))
    with zipfile.ZipFile(PARENT) as old,zipfile.ZipFile(JAR) as new:
        assert new.testzip() is None and old.namelist()==new.namelist();changed=[n for n in old.namelist() if old.read(n)!=new.read(n)];assert changed==list(own)
        before=byte_pin(old.read(changed[0]));after=byte_pin(new.read(changed[0]));unchanged=len(old.namelist())-1
    descriptor=ROOT/'docs/literature/omics-2023-2026/htseq-dispatch-shared-source-compatibility.json'
    save(out/'COMPLETED.json',dict(status='built-isolated-HTSeq-root-development-candidate',artifact=pin(JAR),parent=pin(PARENT),
        unchanged_parent_entries=unchanged,only_changed_entry=changed[0],before_entry=before,after_entry=after,
        dispatcher_source=pin(source),dispatcher_exact_inverse=pin(descriptor),all_scientific_and_other_parent_entries_preserved=True,catalog_credit=False))
    print(json.dumps(dict(status='built-isolated-HTSeq-root-development-candidate',artifact=pin(JAR),unchanged_parent_entries=unchanged)))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable isolated dispatcher checks')
    main()
