# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Add only native binary read presence to the immutable original-file-count candidate."""
import argparse
import subprocess
import zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import byte_pin
from build_htseq_file_count_development import BASE,TARGET as PARENT
from validate_deseq2_inference import JAVA21

JAR=BASE/'binary-development/omicsmatic-0.3.7-htseq-binary-development.jar'


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True)
    out=parser.parse_args().out.resolve();assert out.is_relative_to(BASE) and not out.exists() and not JAR.exists();out.mkdir()
    source=ROOT/'src/main/java/org/jlinalg/sequence/HtseqBinaryAccessibility.java';classes=out/'classes';classes.mkdir()
    argv=list(map(str,[Path(JAVA21).with_name('javac.exe'),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-cp',PARENT,'-d',classes,source]))
    result=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=120)
    (out/'compile.stdout').write_bytes(result.stdout);(out/'compile.stderr').write_bytes(result.stderr);save(out/'compile.process.json',dict(command=argv,exit_code=result.returncode));assert result.returncode==0
    added={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')}
    assert all(name.startswith('org/jlinalg/sequence/HtseqBinaryAccessibility') for name in added)
    for path in (ROOT/'src/main/resources/org/jlinalg/omics/htseq-binary-accessibility').iterdir():
        if path.is_file():added['org/jlinalg/omics/htseq-binary-accessibility/'+path.name]=path.read_bytes()
    JAR.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(PARENT) as old,zipfile.ZipFile(JAR,'x') as new:
        assert not set(added)&set(old.namelist())
        for entry in old.infolist():new.writestr(entry,old.read(entry.filename))
        for name,body in sorted(added.items()):
            info=zipfile.ZipInfo(name,(2026,11,5,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;new.writestr(info,body)
    with zipfile.ZipFile(PARENT) as old,zipfile.ZipFile(JAR) as new:
        assert new.testzip() is None and len(new.namelist())==len(set(new.namelist())) and all(old.read(n)==new.read(n) for n in old.namelist());unchanged=len(old.namelist())
    save(out/'COMPLETED.json',dict(status='built-native-binary-accessibility-development',artifact=pin(JAR),parent=pin(PARENT),source=pin(source),
        unchanged_parent_entries=unchanged,added_entries={name:byte_pin(body) for name,body in added.items()},root_dispatch_changed=False,catalog_credit=False))
    print('Built isolated native binary presence; all original-count parent entries preserved')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable parent preservation checks')
    main()
