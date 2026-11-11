# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Add native chromVAR numerical cores to the immutable validated Signac scan candidate."""
import argparse,json,subprocess,zipfile
from pathlib import Path
from validate_mashr_native import JAVA21,pin
ROOT=Path(__file__).resolve().parents[2];BASE=ROOT/'build/literature-review/signac-runtime'
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='native-chromVAR-build-in-progress'))
    try:
        parent_path=ROOT/'docs/literature/signac-scan-development/VALIDATION.json';parent=json.loads(parent_path.read_bytes());assert all(pin(ROOT/n)==v for n,v in parent['source_pins'].items());artifact=parent['artifact'];jar=ROOT/artifact['path'];assert pin(jar)=={k:artifact[k] for k in ['bytes','sha256']}
        resource=ROOT/'src/main/resources/org/jlinalg/omics/chromvar-deviations';names=['src/main/java/org/jlinalg/sequence/ChromVarDeviations.java','tools/literature/build_chromvar_native.py']+[p.relative_to(ROOT).as_posix() for p in resource.rglob('*') if p.is_file()];sources={n:pin(ROOT/n) for n in names};save(out/'SOURCE-PINS.json',sources)
        for n in sources:
            t=out/'source-snapshot'/n;t.parent.mkdir(parents=True,exist_ok=True);t.write_bytes((ROOT/n).read_bytes())
        def command(name,argv):
            argv=list(map(str,argv));r=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=120,check=False);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=argv,exit_code=r.returncode));assert r.returncode==0,(name,r.stderr.decode('utf8',errors='replace')[-2000:])
        classes=out/'classes';classes.mkdir();command('compile',[JAVA21/'javac.exe','--release','17','-g','-encoding','UTF-8','-Xlint:all','-Werror','-cp',jar,'-d',classes,ROOT/names[0]]);command('javadoc',[JAVA21/'javadoc.exe','-quiet','-Xdoclint:all,-missing','-Werror','-encoding','UTF-8','-classpath',jar,'-d',out/'javadoc',ROOT/names[0]])
        additions={p.relative_to(classes).as_posix():p for p in classes.rglob('*.class')};class_count=len(additions)
        for path in resource.rglob('*'):
            if path.is_file() and path.name!='.gitattributes':additions['org/jlinalg/omics/chromvar-deviations/'+path.relative_to(resource).as_posix()]=path
        candidate=out/'omicsmatic-0.3.7-chromvar-native-development.jar'
        with zipfile.ZipFile(jar) as before,zipfile.ZipFile(candidate,'x') as after:
            assert not set(additions).intersection(before.namelist())
            for info in before.infolist():after.writestr(info,before.read(info.filename))
            for name,path in sorted(additions.items()):
                info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;after.writestr(info,path.read_bytes())
        with zipfile.ZipFile(jar) as before,zipfile.ZipFile(candidate) as after:assert after.testzip() is None and len(after.namelist())==len(set(after.namelist())) and set(after.namelist())-set(before.namelist())==set(additions) and all(before.read(n)==after.read(n) for n in before.namelist())
        assert all(pin(ROOT/n)==v for n,v in sources.items()) and all(pin(ROOT/n)==v for n,v in parent['source_pins'].items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==parent['canonical_executable_unchanged'];save(out/'BUILD.json',dict(status='built-native-chromVAR-cores-not-yet-validated',source_pins=sources,artifact=dict(path=candidate.relative_to(ROOT).as_posix(),**pin(candidate)),parent_artifact=artifact,parent_scan_validation=pin(parent_path),added_classes=class_count,added_resources=len(additions)-class_count,canonical_executable_unchanged=parent['canonical_executable_unchanged'],root_dispatch_available=False,catalog_implementation_credit=False,full_Signac_workflow_available=False));(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status='built-native-chromVAR-cores-not-yet-validated',artifact=pin(candidate))),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_NATIVE_CHROMVAR_BUILD',error=str(error)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable candidate guards')
    main()
