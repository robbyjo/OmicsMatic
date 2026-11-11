# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Add only the UTF8 request entry point; every validated LSI class/resource stays byte-identical."""
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
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);args=p.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='request-entry-point-build-in-progress'))
    try:
        parent=json.loads((BASE/'lsi-build-5/BUILD.json').read_bytes());assert all(pin(ROOT/n)==v for n,v in parent['source_pins'].items());jar=ROOT/parent['artifact']['path'];assert pin(jar)=={k:parent['artifact'][k] for k in ['bytes','sha256']}
        source=ROOT/'src/main/java/org/jlinalg/cli/SignacLsiRequestCli.java';sources={source.relative_to(ROOT).as_posix():pin(source),'tools/literature/build_signac_lsi_request.py':pin(Path(__file__))};save(out/'SOURCE-PINS.json',sources)
        for name in sources:
            target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
        classes=out/'classes';classes.mkdir()
        def command(name,argv):
            argv=list(map(str,argv));r=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=120,check=False);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=argv,exit_code=r.returncode));assert r.returncode==0,(name,r.returncode)
        command('compile',[JAVA21/'javac.exe','--release','17','-g','-encoding','UTF-8','-Xlint:all','-Werror','-cp',jar,'-d',classes,source])
        command('javadoc',[JAVA21/'javadoc.exe','-quiet','-Xdoclint:all,-missing','-Werror','-encoding','UTF-8','-classpath',jar,'-d',out/'javadoc',source])
        additions={p.relative_to(classes).as_posix():p for p in classes.rglob('*.class')};assert set(additions)=={'org/jlinalg/cli/SignacLsiRequestCli.class'}
        candidate=out/'omicsmatic-0.3.7-signac-lsi-request-development.jar'
        with zipfile.ZipFile(jar) as old,zipfile.ZipFile(candidate,'x') as new:
            assert not set(additions).intersection(old.namelist())
            for info in old.infolist():new.writestr(info,old.read(info.filename))
            for name,path in additions.items():
                info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;new.writestr(info,path.read_bytes())
        with zipfile.ZipFile(jar) as old,zipfile.ZipFile(candidate) as new:
            assert new.testzip() is None and len(new.namelist())==len(set(new.namelist()))
            assert set(new.namelist())-set(old.namelist())==set(additions) and all(old.read(n)==new.read(n) for n in old.namelist());unchanged=len(old.namelist())
        assert all(pin(ROOT/n)==v for n,v in sources.items())
        save(out/'BUILD.json',dict(status='built-UTF8-request-entry-point-with-unchanged-LSI-core',source_pins=sources,artifact=dict(path=candidate.relative_to(ROOT).as_posix(),**pin(candidate)),parent_artifact=parent['artifact'],unchanged_parent_entries=unchanged,added_classes=1,root_dispatch_available=False,catalog_implementation_credit=False))
        (out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status='built-UTF8-request-entry-point-with-unchanged-LSI-core',artifact=dict(path=candidate.relative_to(ROOT).as_posix(),**pin(candidate)))))
    except BaseException as e:save(out/'FAILURE.json',dict(status='FAILED_UTF8_REQUEST_ENTRY_POINT_BUILD',error=str(e)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Do not disable unchanged-core checks')
    main()
