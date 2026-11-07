# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Add the guarded original background worker and native GC request adapter to the validated candidate."""
import argparse,json,subprocess,zipfile
from pathlib import Path
from validate_mashr_native import JAVA21,pin
ROOT=Path(__file__).resolve().parents[2];BASE=ROOT/'build/literature-review/signac-runtime'
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);a=parser.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='upstream-background-adapter-build-in-progress'))
    try:
        parent=json.loads((ROOT/'docs/literature/chromvar-gc-development/VALIDATION.json').read_bytes());jar=ROOT/parent['artifact']['path'];assert pin(jar)=={k:parent['artifact'][k] for k in ['bytes','sha256']} and all(pin(ROOT/n)==v for n,v in parent['source_pins'].items());canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')
        resources=ROOT/'src/main/resources/org/jlinalg/omics';files=[resources/'chromvar_background.R']+[p for p in (resources/'chromvar-background').rglob('*') if p.is_file() and p.name!='.gitattributes'];names=['src/main/java/org/jlinalg/cli/ChromVarBackgroundRequestCli.java','tools/literature/build_chromvar_background.py']+[p.relative_to(ROOT).as_posix() for p in files];sources={n:pin(ROOT/n) for n in names};save(out/'SOURCE-PINS.json',sources)
        for n in sources:
            t=out/'source-snapshot'/n;t.parent.mkdir(parents=True,exist_ok=True);t.write_bytes((ROOT/n).read_bytes())
        def command(name,argv):
            argv=list(map(str,argv));r=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=180,check=False);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=argv,exit_code=r.returncode));assert r.returncode==0,(name,r.stderr.decode('utf8',errors='replace')[-1800:])
        classes=out/'classes';classes.mkdir();command('javac',[JAVA21/'javac.exe','--release','17','-g','-encoding','UTF-8','-Xlint:all','-Werror','-cp',jar,'-d',classes,ROOT/names[0]])
        command('javadoc',[JAVA21/'javadoc.exe','-quiet','-Xdoclint:none','-Werror','-encoding','UTF-8','-classpath',jar,'-d',out/'javadoc',ROOT/names[0]])
        additions={p.relative_to(classes).as_posix():p for p in classes.rglob('*.class')};additions.update({'org/jlinalg/omics/'+p.relative_to(resources).as_posix():p for p in files});candidate=out/'omicsmatic-0.3.7-chromvar-background-development.jar'
        with zipfile.ZipFile(jar) as before,zipfile.ZipFile(candidate,'x') as after:
            assert not set(additions).intersection(before.namelist())
            for info in before.infolist():after.writestr(info,before.read(info.filename))
            for name,path in sorted(additions.items()):
                info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;after.writestr(info,path.read_bytes())
        with zipfile.ZipFile(jar) as before,zipfile.ZipFile(candidate) as after:assert after.testzip() is None and len(after.namelist())==len(set(after.namelist())) and set(after.namelist())-set(before.namelist())==set(additions) and all(before.read(n)==after.read(n) for n in before.namelist())
        assert all(pin(ROOT/n)==v for n,v in sources.items()) and all(pin(ROOT/n)==v for n,v in parent['source_pins'].items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical
        result=dict(status='built-original-background-adapter-not-yet-workflow-validated',source_pins=sources,artifact=dict(path=candidate.relative_to(ROOT).as_posix(),**pin(candidate)),parent_artifact=parent['artifact'],added_classes=len(list(classes.rglob('*.class'))),added_resources=len(files),canonical_executable_preserved=canonical,root_dispatch_available=False,catalog_implementation_credit=False,full_Signac_workflow_available=False)
        save(out/'BUILD.json',result);(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status=result['status'],artifact=result['artifact'],added_classes=result['added_classes'],added_resources=len(files))),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_ORIGINAL_BACKGROUND_ADAPTER_BUILD',error=str(error)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable background build checks')
    main()
