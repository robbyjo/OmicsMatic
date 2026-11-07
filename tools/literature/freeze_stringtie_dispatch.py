# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze the exact root dispatcher overlay without changing the standalone scientific checkpoint."""
import hashlib
import json
from pathlib import Path,PurePosixPath
import stat
import zipfile

ROOT=Path(__file__).resolve().parents[2]
def read(p):return json.loads(p.read_bytes())
def pin(p):
    with p.open('rb') as f:return dict(bytes=p.stat().st_size,sha256=hashlib.file_digest(f,'sha256').hexdigest())
def rawpin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())

def main():
    base=ROOT/'build/literature-review/stringtie-runtime';matrix=base/'root-matrix-1';proof=read(matrix/'COMPLETED.json');target=ROOT/'docs/literature/stringtie-main-workflow';assert not target.exists()
    assert proof['status']=='passed-public-root-StringTie-dispatch-and-actual-main-workflows' and not(matrix/'INCOMPLETE.json').exists() and not(matrix/'FAILURE.json').exists()
    assert proof['unchanged_parent_entries']==19618 and set(proof['changed_classes'])=={'org/jlinalg/cli/JLinAlgCli.class'} and proof['scientific_classes_unchanged'] and proof['root_dispatch_available']
    assert len(proof['platform_validation'])==3 and all(r['passed']==1361 and r['skipped']==0 and r['actual_main_controls']==5 for r in proof['platform_validation'])
    assert (proof['actual_main_fits'],proof['actual_main_native_verifies'],proof['actual_main_copied_windows_verifies'])==(3,3,2)
    assert all(pin(ROOT/n)==p for n,p in proof['source_pins'].items()) and pin(ROOT/proof['artifact']['path'])=={k:proof['artifact'][k] for k in ['bytes','sha256']} and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==proof['canonical_executable_unchanged']
    parent=ROOT/'docs/literature/stringtie-typed-workflow';assert pin(parent/'VALIDATION.json')==proof['parent_validation'] and pin(parent/'controls.zip')==proof['parent_archive']
    with zipfile.ZipFile(ROOT/proof['parent_artifact']['path']) as old,zipfile.ZipFile(ROOT/proof['artifact']['path']) as new:
        assert new.testzip() is None and old.namelist()==new.namelist() and len(new.namelist())==len(set(new.namelist()))==19619
        assert {n for n in old.namelist() if old.read(n)!=new.read(n)}==set(proof['changed_classes']) and all(rawpin(new.read(n))==p for n,p in proof['changed_classes'].items())
    sources=dict(proof['source_pins'])
    for n in ['docs/literature/STRINGTIE_TOOLS.md','tools/literature/freeze_stringtie_dispatch.py']:sources[n]=pin(ROOT/n)
    entries={}
    def add(path,name):
        p=PurePosixPath(name);assert path.is_file() and not path.is_symlink() and name not in entries and not p.is_absolute() and '..' not in p.parts and '\\' not in name;entries[name]=path
    for n in sources:add(ROOT/n,'source/'+n)
    for folder,label in [(matrix,'root-validation'),(base/'root-dispatch-1','root-dispatch-source')]:
        for path in sorted(folder.rglob('*')):
            assert not path.is_symlink()
            if path.is_file() and path.suffix!='.jar' and 'javadoc' not in path.relative_to(folder).parts:add(path,label+'/'+path.relative_to(folder).as_posix())
    for n in ['VALIDATION.json','SHA256SUMS']:add(parent/n,'standalone-checkpoint/'+n)
    target.mkdir();archive=target/'controls.zip';members={}
    with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for n,p in sorted(entries.items()):
            raw=p.read_bytes();info=zipfile.ZipInfo(n,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=(stat.S_IFREG|0o644)<<16;z.writestr(info,raw);members[n]=rawpin(raw)
    with zipfile.ZipFile(archive) as z:assert z.testzip() is None and len(z.namelist())==len(set(z.namelist()))==len(members) and all(rawpin(z.read(n))==p for n,p in members.items())
    assert archive.stat().st_size<95000000 and all(pin(ROOT/n)==p for n,p in sources.items())
    result=dict(proof,source_pins=sources,archive=dict(path=archive.name,**pin(archive),entries=len(members)),archive_files=members,original_StringTie_executable_bundled=False,standalone_scientific_checkpoint_unchanged=True,required_action='Use STRINGTIE_TOOLS.md with the exact development JAR and stringtie-tools. Linux Java is required for execution; Windows also verifies complete copied bundles and exports setup. Install/download only where permitted through the separate kit. SKIP3 means skip dependent analysis until the exact named requirement is permitted and available; FAIL2 means preserve the failed attempt, read diagnostics and correct into NEW paths. Require complete manifests, no partial/failure guards and successful --verify. Scientific methods and earlier standalone evidence remain unchanged. No whole-paper, release or whole-goal completion is claimed.')
    (target/'VALIDATION.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8',newline='\n');(target/'SHA256SUMS').write_text(''.join(pin(target/n)['sha256']+'  '+n+'\n' for n in ['VALIDATION.json','controls.zip']),encoding='ascii',newline='\n');(target/'.gitattributes').write_text('controls.zip -text\nVALIDATION.json -text\nSHA256SUMS -text\n',encoding='ascii',newline='\n')
    print(json.dumps(dict(status=result['status'],archive=result['archive'],source_pins=len(sources),root_fits=3,scientific_entries_unchanged=19618)))

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact root evidence checks')
    main()
