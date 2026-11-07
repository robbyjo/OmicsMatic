# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Add the captured annotation request workflow to the validated native API artifact."""
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
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='annotation-request-workflow-build-in-progress'))
    try:
        parent=json.loads((BASE/'annotation-build-3/BUILD.json').read_bytes());passed=json.loads((BASE/'annotation-matrix-4/COMPLETED.json').read_bytes());assert passed['artifact']==parent['artifact'] and all(pin(ROOT/n)==v for record in [parent,passed] for n,v in record['source_pins'].items());jar=ROOT/parent['artifact']['path'];assert pin(jar)=={k:parent['artifact'][k] for k in ['bytes','sha256']}
        names=['src/main/java/org/jlinalg/cli/SignacAnnotationsRequestCli.java','tools/literature/build_signac_annotation_workflow.py'];sources={n:pin(ROOT/n) for n in names};save(out/'SOURCE-PINS.json',sources)
        for name in names:
            target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
        def command(name,argv):
            argv=list(map(str,argv));r=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=120,check=False);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=argv,exit_code=r.returncode));assert r.returncode==0,(name,r.returncode)
        classes=out/'classes';classes.mkdir();command('compile',[JAVA21/'javac.exe','--release','17','-g','-encoding','UTF-8','-Xlint:all','-Werror','-cp',jar,'-d',classes,ROOT/names[0]]);command('javadoc',[JAVA21/'javadoc.exe','-quiet','-Xdoclint:all,-missing','-Werror','-encoding','UTF-8','-classpath',jar,'-d',out/'javadoc',ROOT/names[0]])
        additions={p.relative_to(classes).as_posix():p for p in classes.rglob('*.class')};assert additions and all(n.startswith('org/jlinalg/cli/SignacAnnotationsRequestCli') for n in additions);candidate=out/'omicsmatic-0.3.7-signac-annotation-workflow-development.jar'
        with zipfile.ZipFile(jar) as old,zipfile.ZipFile(candidate,'x') as new:
            assert not set(additions).intersection(old.namelist())
            for info in old.infolist():new.writestr(info,old.read(info.filename))
            for name,path in sorted(additions.items()):
                info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;new.writestr(info,path.read_bytes())
        with zipfile.ZipFile(jar) as old,zipfile.ZipFile(candidate) as new:
            assert new.testzip() is None and len(new.namelist())==len(set(new.namelist())) and set(new.namelist())-set(old.namelist())==set(additions) and all(old.read(n)==new.read(n) for n in old.namelist());unchanged=len(old.namelist())
        assert all(pin(ROOT/n)==v for n,v in sources.items());save(out/'BUILD.json',dict(status='built-annotation-request-workflow-not-yet-end-to-end-validated',source_pins=sources,artifact=dict(path=candidate.relative_to(ROOT).as_posix(),**pin(candidate)),parent_artifact=parent['artifact'],added_classes=len(additions),unchanged_parent_entries=unchanged,canonical_executable_unchanged=parent['canonical_executable_unchanged'],root_dispatch_available=False,catalog_implementation_credit=False));(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status='built-annotation-request-workflow-not-yet-end-to-end-validated',artifact=pin(candidate),added_classes=len(additions))),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_ANNOTATION_WORKFLOW_BUILD',error=str(error)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable additive workflow build checks')
    main()
