# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Add typed native prepared-background chromVAR requests to the validated numerical candidate."""
import argparse,json,subprocess,zipfile
from pathlib import Path
from validate_mashr_native import JAVA21,pin
ROOT=Path(__file__).resolve().parents[2];BASE=ROOT/'build/literature-review/signac-runtime'
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='native-chromVAR-request-build-in-progress'))
    try:
        parent=json.loads((BASE/'chromvar-native-build-2/BUILD.json').read_bytes());passed=json.loads((BASE/'chromvar-native-matrix-2/COMPLETED.json').read_bytes());assert passed['artifact']==parent['artifact'] and all(v['tests']==v['passed']==320 for v in passed['platform_validation']) and all(pin(ROOT/n)==v for r in [parent,passed] for n,v in r['source_pins'].items());jar=ROOT/parent['artifact']['path'];assert pin(jar)=={k:parent['artifact'][k] for k in ['bytes','sha256']}
        names=['src/main/java/org/jlinalg/sequence/ChromVarIO.java','src/main/java/org/jlinalg/cli/ChromVarRequestCli.java','tools/literature/build_chromvar_workflow.py'];sources={n:pin(ROOT/n) for n in names};save(out/'SOURCE-PINS.json',sources)
        for n in sources:
            t=out/'source-snapshot'/n;t.parent.mkdir(parents=True,exist_ok=True);t.write_bytes((ROOT/n).read_bytes())
        def command(name,argv):
            argv=list(map(str,argv));r=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=120,check=False);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=argv,exit_code=r.returncode));assert r.returncode==0,(name,r.stderr.decode('utf8',errors='replace')[-2500:])
        classes=out/'classes';classes.mkdir();command('compile',[JAVA21/'javac.exe','--release','17','-g','-encoding','UTF-8','-Xlint:all','-Werror','-cp',jar,'-d',classes,*[ROOT/n for n in names[:2]]]);command('javadoc',[JAVA21/'javadoc.exe','-quiet','-Xdoclint:all,-missing','-Werror','-encoding','UTF-8','-classpath',jar,'-d',out/'javadoc',*[ROOT/n for n in names[:2]]])
        additions={p.relative_to(classes).as_posix():p for p in classes.rglob('*.class')};candidate=out/'omicsmatic-0.3.7-chromvar-workflow-development.jar'
        with zipfile.ZipFile(jar) as before,zipfile.ZipFile(candidate,'x') as after:
            assert not set(additions).intersection(before.namelist())
            for info in before.infolist():after.writestr(info,before.read(info.filename))
            for name,path in sorted(additions.items()):
                info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;after.writestr(info,path.read_bytes())
        with zipfile.ZipFile(jar) as before,zipfile.ZipFile(candidate) as after:assert after.testzip() is None and len(after.namelist())==len(set(after.namelist())) and set(after.namelist())-set(before.namelist())==set(additions) and all(before.read(n)==after.read(n) for n in before.namelist())
        assert all(pin(ROOT/n)==v for n,v in sources.items()) and all(pin(ROOT/n)==v for r in [parent,passed] for n,v in r['source_pins'].items());save(out/'BUILD.json',dict(status='built-native-chromVAR-requests-not-yet-workflow-validated',source_pins=sources,artifact=dict(path=candidate.relative_to(ROOT).as_posix(),**pin(candidate)),parent_artifact=parent['artifact'],added_classes=len(additions),canonical_executable_unchanged=parent['canonical_executable_unchanged'],root_dispatch_available=False,catalog_implementation_credit=False,full_Signac_workflow_available=False));(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status='built-native-chromVAR-requests-not-yet-workflow-validated',artifact=pin(candidate))),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_NATIVE_CHROMVAR_REQUEST_BUILD',error=str(error)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable request candidate checks')
    main()
