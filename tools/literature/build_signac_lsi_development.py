# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build a private typed Signac LSI candidate without replacing earlier validated artifacts."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import zipfile
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from validate_mashr_native import JAVA21,pin
BASE=ROOT/'build/literature-review/signac-runtime'
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);args=p.parse_args();out=args.out.resolve()
    assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='building-development-artifact-no-analysis-credit'))
    try:
        parent=ROOT/'docs/literature/signac-normalization-development/VALIDATION.json';previous=json.loads(parent.read_bytes())
        assert all(pin(ROOT/n)==v for n,v in previous['source_pins'].items());parent_jar=ROOT/previous['artifact']['path'];assert pin(parent_jar)=={k:previous['artifact'][k] for k in ['bytes','sha256']}
        java=['src/main/java/org/jlinalg/sequence/SignacLsi.java','src/main/java/org/jlinalg/sequence/SignacMatrixIO.java','src/main/java/org/jlinalg/cli/SignacLsiToolsCli.java']
        resources=[ROOT/'src/main/resources/org/jlinalg/omics/signac_lsi.R',*sorted((ROOT/'src/main/resources/org/jlinalg/omics/signac-lsi').iterdir())]
        names=[*java,'tools/literature/build_signac_lsi_development.py','tools/literature/prepare_signac_lsi_sources.py',*[f.relative_to(ROOT).as_posix() for f in resources if f.is_file()]]
        sources={n:pin(ROOT/n) for n in names};save(out/'SOURCE-PINS.json',sources)
        for name in names:
            target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
        def command(name,args):
            args=list(map(str,args))
            result=subprocess.run(args,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=120,check=False)
            (out/(name+'.stdout')).write_bytes(result.stdout);(out/(name+'.stderr')).write_bytes(result.stderr);save(out/(name+'.process.json'),dict(command=args,exit_code=result.returncode));assert result.returncode==0,(name,result.returncode)
        compiled=out/'source-compiled';compiled.mkdir();command('compile',[JAVA21/'javac.exe','--release','17','-g','-encoding','UTF-8','-Xlint:all','-Werror','-cp',parent_jar,'-d',compiled,*[ROOT/n for n in java]])
        command('javadoc',[JAVA21/'javadoc.exe','-quiet','-Xdoclint:all,-missing','-Werror','-encoding','UTF-8','-classpath',parent_jar,'-d',out/'javadoc',*[ROOT/n for n in java]])
        additions={p.relative_to(compiled).as_posix():p for p in compiled.rglob('*.class')};class_count=len(additions)
        additions.update({p.relative_to(ROOT/'src/main/resources').as_posix():p for p in resources if p.is_file() and p.name!='.gitattributes'})
        jar=out/'omicsmatic-0.3.7-signac-lsi-development.jar'
        with zipfile.ZipFile(parent_jar) as old,zipfile.ZipFile(jar,'x') as new:
            assert not set(additions).intersection(old.namelist())
            for info in old.infolist():new.writestr(info,old.read(info.filename))
            for name,path in sorted(additions.items()):
                info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;new.writestr(info,path.read_bytes())
        with zipfile.ZipFile(parent_jar) as old,zipfile.ZipFile(jar) as new:
            assert new.testzip() is None and len(new.namelist())==len(set(new.namelist()))
            assert set(new.namelist())-set(old.namelist())==set(additions) and all(old.read(n)==new.read(n) for n in old.namelist())
            assert all(new.read(n)==path.read_bytes() for n,path in additions.items());unchanged=len(old.namelist())
        assert all(pin(ROOT/n)==v for n,v in sources.items()) and all(pin(ROOT/n)==v for n,v in previous['source_pins'].items())
        assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==previous['canonical_executable_unchanged']
        result=dict(status='built-development-Signac-LSI-artifact-not-yet-analysis-validation',source_pins=sources,parent_source_pins=previous['source_pins'],parent_validation=pin(parent),parent_artifact=previous['artifact'],
                    artifact=dict(path=jar.relative_to(ROOT).as_posix(),**pin(jar)),added_classes=class_count,added_resources=len(additions)-class_count,unchanged_parent_entries=unchanged,
                    additions={n:pin(path) for n,path in additions.items()},canonical_executable_unchanged=previous['canonical_executable_unchanged'],root_dispatch_available=False,catalog_implementation_credit=False)
        save(out/'BUILD.json',result);(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status=result['status'],artifact=result['artifact'],added_classes=class_count,added_resources=len(additions)-class_count)))
    except BaseException as e:save(out/'FAILURE.json',dict(status='FAILED_NO_ARTIFACT_OR_ANALYSIS_CREDIT',error=str(e)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Do not disable private artifact source checks')
    main()
