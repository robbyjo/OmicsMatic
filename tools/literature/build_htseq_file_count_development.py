# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compile only new HTSeq file-count classes and overlay the immutable native-count parent."""
import argparse
import json
import subprocess
import zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_htseq_read_count_native import BASE,JAR as PARENT
from validate_deseq2_inference import JAVA21

TARGET=BASE/'file-count-development-3/omicsmatic-0.3.7-htseq-file-count-development.jar'


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True)
    args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE) and not out.exists() and not TARGET.exists();out.mkdir()
    parent=json.loads((ROOT/'docs/literature/htseq-read-count-development/VALIDATION.json').read_bytes())
    assert pin(PARENT)=={k:parent['artifact'][k] for k in ['bytes','sha256']}
    names=['HtseqFileContract','HtseqCountTable','HtseqCountRuntime','HtseqFileCounting','HtseqToolsCli']
    sources=[ROOT/'src/main/java/org/jlinalg/cli'/(name+'.java') for name in names]
    classes=out/'classes';classes.mkdir()
    argv=list(map(str,[Path(JAVA21).with_name('javac.exe'),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-cp',PARENT,'-d',classes,*sources]))
    result=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=120)
    (out/'compile.stdout').write_bytes(result.stdout);(out/'compile.stderr').write_bytes(result.stderr)
    save(out/'compile.process.json',dict(command=argv,exit_code=result.returncode));assert result.returncode==0,'compile'
    added={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')}
    assert added and all(n.startswith(tuple('org/jlinalg/cli/'+name for name in names)) for n in added)
    for path in (ROOT/'src/main/resources/org/jlinalg/omics/htseq-file-count').iterdir():
        if path.is_file():added['org/jlinalg/omics/htseq-file-count/'+path.name]=path.read_bytes()
    TARGET.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(PARENT) as old,zipfile.ZipFile(TARGET,'x') as candidate:
        assert not set(added)&set(old.namelist()) and old.testzip() is None
        for entry in old.infolist():candidate.writestr(entry,old.read(entry.filename))
        for name,body in sorted(added.items()):
            info=zipfile.ZipInfo(name,(2026,11,5,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;candidate.writestr(info,body)
    with zipfile.ZipFile(PARENT) as old,zipfile.ZipFile(TARGET) as candidate:
        assert candidate.testzip() is None and len(candidate.namelist())==len(set(candidate.namelist()))
        assert all(old.read(name)==candidate.read(name) for name in old.namelist())
        unchanged=len(old.namelist())
    save(out/'COMPLETED.json',dict(status='built-isolated-HTSeq-file-count-development-candidate',artifact=pin(TARGET),parent=pin(PARENT),
        unchanged_parent_entries=unchanged,added_entries={n:dict(bytes=len(b),sha256=__import__('hashlib').sha256(b).hexdigest()) for n,b in added.items()},
        source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in sources},root_dispatch_changed=False,catalog_credit=False))
    print(json.dumps(dict(status='built-isolated-HTSeq-file-count-candidate',artifact=pin(TARGET),unchanged_parent_entries=unchanged)))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable isolated artifact checks')
    main()
