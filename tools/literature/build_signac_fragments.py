# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build native fragment APIs as an additive private artifact, preserving the validated LSI parent."""
import argparse
import json
from pathlib import Path
import subprocess
import sys
import zipfile
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from validate_mashr_native import JAVA21,pin
BASE=ROOT/'build/literature-review/signac-runtime'
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);args=p.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='native-fragment-build-in-progress'))
    try:
        parent=json.loads((ROOT/'docs/literature/signac-lsi-development/VALIDATION.json').read_bytes());assert all(pin(ROOT/n)==v for n,v in parent['source_pins'].items());jar=ROOT/parent['artifact']['path'];assert pin(jar)=={k:parent['artifact'][k] for k in ['bytes','sha256']}
        names=['src/main/java/org/jlinalg/sequence/SignacFragments.java','src/main/java/org/jlinalg/sequence/SignacFragmentQc.java','tools/literature/build_signac_fragments.py']
        pins={n:pin(ROOT/n) for n in names};save(out/'SOURCE-PINS.json',pins)
        for name in names:
            target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
        def command(name,argv):
            argv=list(map(str,argv));r=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=120,check=False);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=argv,exit_code=r.returncode));assert r.returncode==0,(name,r.returncode)
        compiled=out/'classes';compiled.mkdir();command('compile',[JAVA21/'javac.exe','--release','17','-g','-encoding','UTF-8','-Xlint:all','-Werror','-cp',jar,'-d',compiled,*[ROOT/n for n in names[:2]]])
        command('javadoc',[JAVA21/'javadoc.exe','-quiet','-Xdoclint:all,-missing','-Werror','-encoding','UTF-8','-classpath',jar,'-d',out/'javadoc',*[ROOT/n for n in names[:2]]])
        additions={p.relative_to(compiled).as_posix():p for p in compiled.rglob('*.class')};assert additions and all(n.startswith(('org/jlinalg/sequence/SignacFragments','org/jlinalg/sequence/SignacFragmentQc')) for n in additions)
        candidate=out/'omicsmatic-0.3.7-signac-fragments-development.jar'
        with zipfile.ZipFile(jar) as old,zipfile.ZipFile(candidate,'x') as new:
            assert not set(additions).intersection(old.namelist())
            for info in old.infolist():new.writestr(info,old.read(info.filename))
            for name,path in sorted(additions.items()):
                info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;new.writestr(info,path.read_bytes())
        with zipfile.ZipFile(jar) as old,zipfile.ZipFile(candidate) as new:
            assert new.testzip() is None and len(new.namelist())==len(set(new.namelist()))
            assert set(new.namelist())-set(old.namelist())==set(additions) and all(old.read(n)==new.read(n) for n in old.namelist());unchanged=len(old.namelist())
        assert all(pin(ROOT/n)==v for n,v in pins.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==parent['canonical_executable_unchanged']
        result=dict(status='built-native-Signac-fragment-APIs-not-yet-validated',source_pins=pins,parent_artifact=parent['artifact'],parent_validation=pin(ROOT/'docs/literature/signac-lsi-development/VALIDATION.json'),artifact=dict(path=candidate.relative_to(ROOT).as_posix(),**pin(candidate)),added_classes=len(additions),unchanged_parent_entries=unchanged,additions={n:pin(p) for n,p in additions.items()},canonical_executable_unchanged=parent['canonical_executable_unchanged'],public_workflow_available=False,catalog_implementation_credit=False)
        save(out/'BUILD.json',result);(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status=result['status'],artifact=result['artifact'],added_classes=len(additions))))
    except BaseException as e:save(out/'FAILURE.json',dict(status='FAILED_NATIVE_FRAGMENT_BUILD',error=str(e)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Do not disable additive artifact checks')
    main()
