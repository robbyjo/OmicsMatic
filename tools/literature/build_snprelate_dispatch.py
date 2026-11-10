# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build the SNPRelate root CLI supplement while retaining every predecessor scientific class byte."""
import argparse
import json
import subprocess
import traceback
import zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
BASE=ROOT/'build/literature-review/snprelate-runtime'


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--build',type=Path,required=True)
    p.add_argument('--compiler',choices=['javac21','javac25'],required=True);a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='FAIL2_SNPRELATE_ROOT_BUILD',new_catalog_credit=False,whole_goal_complete=False)
    try:
        metadata=json.loads((a.build.resolve()/'COMPLETED.json').read_bytes());assert metadata['status']=='PASS_ISOLATED_SNPRELATE_ADAPTER_BUILD_ONLY'
        standalone=ROOT/metadata['artifact']['path'];assert pin(standalone)=={key:metadata['artifact'][key] for key in ['bytes','sha256']}
        assert all(pin(ROOT/name)==value for name,value in metadata['source_pins'].items())
        canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';prior=ROOT/'docs/literature/diamond-development'/('diamond-root-'+a.compiler+'.jar')
        proof=json.loads((prior.parent/'VALIDATION.json').read_bytes());assert pin(prior)==proof['artifacts'][prior.name]
        dependencies={f.relative_to(ROOT).as_posix():pin(f) for f in [canonical,prior,standalone]}
        with zipfile.ZipFile(prior) as z:payload={name:z.read(name) for name in z.namelist()}
        old=dict(payload);added={}
        with zipfile.ZipFile(standalone) as z:
            for name in z.namelist():
                raw=z.read(name)
                if name.endswith('.class') or name.startswith('org/jlinalg/omics/snprelate/'):
                    assert name not in payload or payload[name]==raw,('Scientific/common overlap differs',name)
                    payload[name]=raw;added[name]=raw
                elif name.startswith('META-INF/'):payload['META-INF/snprelate/'+name[len('META-INF/'):]]=raw
        source=ROOT/'src/main/java/org/jlinalg/cli/JLinAlgCli.java';pins={f.relative_to(ROOT).as_posix():pin(f) for f in [source,Path(__file__)]}
        for name in pins:
            target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
        classes=out/'classes';classes.mkdir();tools=Path('C:/Program Files')/('Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin' if a.compiler=='javac21' else 'Common Files/Oracle/Java/javapath')
        command=[str(tools/'javac.exe'),'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-cp',';'.join(map(str,[standalone,prior,canonical])),'-d',str(classes),str(source)]
        r=subprocess.run(command,capture_output=True,timeout=120);(out/'compile.stdout').write_bytes(r.stdout);(out/'compile.stderr').write_bytes(r.stderr)
        save(out/'compile.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,r.stderr[-3500:]
        compiled={f.relative_to(classes).as_posix():f.read_bytes() for f in classes.rglob('*.class')};assert set(compiled)=={'org/jlinalg/cli/JLinAlgCli.class'};payload.update(compiled)
        payload['META-INF/snprelate/DEPENDENCIES.json']=(json.dumps(dependencies,indent=2)+'\n').encode()
        artifact=out/'snprelate-root-supplement.jar'
        with zipfile.ZipFile(artifact,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
            for name,raw in sorted(payload.items()):
                item=zipfile.ZipInfo(name,(1980,1,1,0,0,0));item.compress_type,item.external_attr=zipfile.ZIP_DEFLATED,0o100644<<16;z.writestr(item,raw)
        with zipfile.ZipFile(artifact) as z:
            assert all(z.read(name)==raw for name,raw in old.items() if name!='org/jlinalg/cli/JLinAlgCli.class')
            assert all(z.read(name)==raw for name,raw in added.items())
        assert all(pin(ROOT/name)==value for name,value in dependencies.items()) and all(pin(ROOT/name)==value for name,value in pins.items())
        state.update(status='PASS_SNPRELATE_ROOT_PREDECESSOR_SCIENTIFIC_BYTES_UNCHANGED',artifact=dict(path=artifact.relative_to(ROOT).as_posix(),**pin(artifact)),
            compiler=a.compiler,dependencies=dependencies,source_pins=pins,predecessor_entries=len(old),added_scientific_entries=len(added),
            canonical_executable_unchanged=dependencies[canonical.relative_to(ROOT).as_posix()])
    except Exception as error:
        state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error','')[:2500],flush=True)
    return 0 if state['status'].startswith('PASS_SNPRELATE_ROOT_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact predecessor scientific-byte preservation')
    raise SystemExit(main())
