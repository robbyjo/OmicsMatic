# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build isolated MTAG workflow development classes; never rebuild the canonical executable or claim a complete adapter."""
import argparse
import shutil
import subprocess
import zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_mtag_reference import BASE


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--compiler',choices=['javac21','javac25'],required=True);a=p.parse_args();out=a.out.resolve()
    assert out.is_relative_to(BASE) and not out.exists();out.mkdir(parents=True)
    state=dict(status='FAIL2_MTAG_WORKFLOW_DEVELOPMENT_BUILD',new_catalog_credit=False,full_adapter_available=False)
    canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';before=pin(canonical)
    try:
        assert before['sha256']=='545917aaf820f2fe404873e1f0bc72186ebe90d3a11fd7f6f26f592115fbf2f2'
        native=ROOT/'docs/literature/mtag-native-development/mtag-api-javac21.jar'
        from verify_mtag_native_evidence import inspect
        inspect()
        sources=sorted((ROOT/'src/main/java/org/jlinalg/cli').glob('Mtag*.java'))+[ROOT/'src/test/java/org/jlinalg/cli/MtagWorkflowReferenceProbe.java',
            ROOT/'src/main/java/org/jlinalg/genetics/MtagScipy121NormalTail.java',ROOT/'src/test/java/org/jlinalg/genetics/MtagTailReferenceProbe.java']
        resource=ROOT/'src/main/resources/org/jlinalg/omics/mtag'
        tracked=[*sources,*[p for p in resource.iterdir() if p.is_file()],Path(__file__)]
        pins={p.relative_to(ROOT).as_posix():pin(p) for p in tracked}
        for name in pins:
            path=out/'source-snapshot'/name;path.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(ROOT/name,path)
        tools=Path('C:/Program Files')/('Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin' if a.compiler=='javac21' else 'Common Files/Oracle/Java/javapath')
        classes=out/'classes';classes.mkdir()
        command=[str(tools/'javac.exe'),'--release','17','-sourcepath','','-encoding','UTF-8','-Xlint:all','-Werror','-cp',str(canonical)+';'+str(native),'-d',str(classes),*map(str,sources)]
        process=subprocess.run(command,stdin=subprocess.DEVNULL,capture_output=True,timeout=180)
        (out/'javac.stdout').write_bytes(process.stdout);(out/'javac.stderr').write_bytes(process.stderr);save(out/'javac.process.json',dict(command=command,exit_code=process.returncode))
        assert process.returncode==0,process.stderr[-5000:]
        payload={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')}
        with zipfile.ZipFile(canonical) as z:payload['org/jlinalg/cli/SimpleJson.class']=z.read('org/jlinalg/cli/SimpleJson.class')
        with zipfile.ZipFile(native) as z:
            for name in z.namelist():
                if name.startswith('org/jlinalg/genetics/Mtag') and name.endswith('.class'):payload[name]=z.read(name)
        for p in resource.iterdir():
            if p.is_file() and p.name!='.gitattributes':payload['org/jlinalg/omics/mtag/'+p.name]=p.read_bytes()
        payload['META-INF/LICENSE']=(ROOT/'LICENSE').read_bytes()
        payload['META-INF/NOTICE']=b'MTAG1.0.8 workflow development classes only; original algorithm attribution Maghzian,Walters,Turley GPL3. Native component and SimpleJson classes retain exact predecessor bytes. Separate canonical0.3.7 dependency is required for a complete development classpath. No full adapter, catalog or whole-goal completion claim.\n'
        payload['META-INF/MANIFEST.MF']=b'Manifest-Version: 1.0\nMain-Class: org.jlinalg.cli.MtagWorkflowReferenceProbe\n\n'
        jar=out/'mtag-workflow-development.jar'
        with zipfile.ZipFile(jar,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
            for name,raw in sorted(payload.items()):
                entry=zipfile.ZipInfo(name,(1980,1,1,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;entry.external_attr=0o100644<<16;z.writestr(entry,raw)
        assert all(pin(ROOT/name)==wanted for name,wanted in pins.items())
        state.update(status='PASS_MTAG_WORKFLOW_DEVELOPMENT_BUILD_ONLY',compiler=a.compiler,source_pins=pins,artifact=pin(jar),
                     retained_native_api=pin(native),canonical_dependency=before)
        print(state['status'])
    except Exception as error:state['cause']=repr(error);print(state['status']+': '+repr(error))
    assert pin(canonical)==before
    save(out/'COMPLETED.json',state)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable build/source/canonical integrity checks')
    raise SystemExit(main())
