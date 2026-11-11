# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build a normal project executable into a private directory and audit every inherited file entry."""
import json,os,subprocess,zipfile
from pathlib import Path
from validate_mashr_native import pin
from audit_chromvar_shared_build import CHANGED as STUDY_CHANGES
from catalog_source_integrity import verify_source
ROOT=Path(__file__).resolve().parents[2];BASE=ROOT/'build/literature-review/signac-runtime';OUT=BASE/'chromvar-public-workflow-1'
def read(p):return json.loads(p.read_bytes())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
    assert OUT.is_dir() and not (OUT/'BUILD.json').exists() and not (OUT/'INCOMPLETE.json').exists();save(OUT/'INCOMPLETE.json',dict(status='normal-private-chromvar-executable-build-in-progress'))
    try:
        parent=read(ROOT/'docs/literature/chromvar-background-development/VALIDATION.json');source_controls=read(BASE/'chromvar-paper-source-controls-1/COMPLETED.json');assert source_controls['numeric_method_comparisons']==306 and source_controls['background_source_and_public_protocols']==45 and all(pin(ROOT/n)==v for r in [parent,source_controls] for n,v in r['source_pins'].items());canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';assert pin(canonical)==read(OUT/'CANONICAL-BEFORE.json')
        sources=['src/main/java/org/jlinalg/cli/JLinAlgCli.java','src/main/java/org/jlinalg/cli/ChromVarToolsCli.java','tools/literature/build_chromvar_public.py','tools/literature/chromvar_dispatch_bridge.py','build.gradle','docs/literature/omics-2023-2026/chromvar-dispatch-shared-source-compatibility.json']+[p.relative_to(ROOT).as_posix() for p in (ROOT/'src/main/resources/org/jlinalg/omics/chromvar-paper-sources').rglob('*') if p.is_file()];pins={n:pin(ROOT/n) for n in sources};save(OUT/'SOURCE-PINS.json',pins)
        for n in sources:
            t=OUT/'source-snapshot'/n;t.parent.mkdir(parents=True,exist_ok=True);t.write_bytes((ROOT/n).read_bytes())
        verify_source(ROOT,ROOT/'docs/literature/omics-2023-2026','src/main/java/org/jlinalg/cli/JLinAlgCli.java',pin(OUT/'JLinAlgCli.before.java')['sha256'])
        init=OUT/'isolated-executable.init.gradle';init.write_text('''gradle.projectsEvaluated {
    rootProject.tasks.named('executableJar', org.gradle.api.tasks.bundling.Jar).configure {
        destinationDirectory.set(rootProject.file('build/literature-review/signac-runtime/chromvar-public-workflow-1'))
        archiveFileName.set('omicsmatic-0.3.7-chromvar-tools-development.jar')
    }
}
''',encoding='utf8',newline='\n');candidate=OUT/'omicsmatic-0.3.7-chromvar-tools-development.jar';assert not candidate.exists();env=os.environ.copy();env['JAVA_HOME']='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot';argv=['cmd.exe','/d','/c',str(ROOT/'gradlew.bat'),'--offline','--no-daemon','--no-configuration-cache','--init-script',str(init),'executableJar'];r=subprocess.run(argv,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=300,check=False);(OUT/'gradle.stdout').write_bytes(r.stdout);(OUT/'gradle.stderr').write_bytes(r.stderr);save(OUT/'gradle-process.json',dict(command=argv,exit_code=r.returncode,requested_client_JAVA_HOME=env['JAVA_HOME']));assert r.returncode==0,r.stderr.decode('utf8',errors='replace')[-1500:]
        new={'org/jlinalg/cli/ChromVarToolsCli.class':ROOT/'build/classes/java/main/org/jlinalg/cli/ChromVarToolsCli.class'};resource_root=ROOT/'src/main/resources'
        for p in (resource_root/'org/jlinalg/omics/chromvar-paper-sources').rglob('*'):
            if p.is_file() and p.name!='.gitattributes':new[p.relative_to(resource_root).as_posix()]=p
        dispatcher='org/jlinalg/cli/JLinAlgCli.class';changes=[]
        with zipfile.ZipFile(ROOT/parent['artifact']['path']) as p,zipfile.ZipFile(canonical) as c,zipfile.ZipFile(candidate) as n:
            files=lambda z:{i.filename for i in z.infolist() if not i.is_dir()};pp,cc,nn=map(files,[p,c,n]);assert n.testzip() is None and len(n.namelist())==len(set(n.namelist()));assert nn==pp|cc|set(new),(sorted(nn-(pp|cc|set(new))),sorted((pp|cc|set(new))-nn))
            for name in sorted(nn):
                if name==dispatcher:assert n.read(name)==(ROOT/'build/classes/java/main'/name).read_bytes();continue
                if name in new:assert n.read(name)==new[name].read_bytes();continue
                if name in pp:
                    if name in cc and p.read(name)!=c.read(name):assert name in STUDY_CHANGES and n.read(name)==c.read(name);changes.append(name)
                    else:assert n.read(name)==p.read(name),'Inherited scientific/file bytes differ: '+name
                else:assert name in cc and n.read(name)==c.read(name)
            assert set(changes)==STUDY_CHANGES
        assert pin(canonical)==read(OUT/'CANONICAL-BEFORE.json') and all(pin(ROOT/n)==v for n,v in pins.items()) and all(pin(ROOT/n)==v for r in [parent,source_controls] for n,v in r['source_pins'].items());save(OUT/'BUILD.json',dict(status='built-normal-project-chromvar-main-executable-not-yet-public-workflow-validated',source_pins=pins,artifact=dict(path=candidate.relative_to(ROOT).as_posix(),**pin(candidate)),parent_scientific_artifact=parent['artifact'],canonical_executable_preserved=pin(canonical),scientific_parent_entries_unchanged=True,only_scientific_parent_class_replaced=dispatcher,carried_existing_canonical_study_class_changes=changes,new_requested_entries=sorted(new),new_requested_entry_count=len(new),canonical_unrelated_extra_entries_carried_exactly=True,normal_Gradle_executableJar_build=True,root_dispatch_available=True,catalog_implementation_credit=False,released=False,pushed=False));(OUT/'INCOMPLETE.json').unlink();print(json.dumps(dict(status='built-normal-project-chromvar-main-executable-not-yet-public-workflow-validated',artifact=pin(candidate),new_entries=len(new),carried_existing_study_changes=len(changes))),flush=True)
    except BaseException as error:save(OUT/'FAILURE.json',dict(status='FAILED_NORMAL_PUBLIC_CHROMVAR_BUILD',error=str(error)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable normal executable/source audit checks')
    main()
