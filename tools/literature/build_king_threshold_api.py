# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build an isolated exact-cutoff API and test a fresh external consumer on Windows and Linux."""
import argparse
import json
import shutil
import subprocess
import traceback
import zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from probe_king_runtime import BASE, DISTRO, linux


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True)
    p.add_argument('--compiler',choices=['javac21','javac25'],required=True)
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE)
    out.mkdir(parents=True);state=dict(status='FAIL2_EXACT_KING_CUTOFF_PUBLIC_API',new_catalog_credit=False,whole_goal_complete=False)
    try:
        canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';before=pin(canonical)
        sources=[ROOT/'src/main/java/org/jlinalg/genetics'/(name+'.java') for name in ['KingRobustKinship','KingKinshipThreshold']]
        example=ROOT/'examples/native-king/KingThresholdExample.java'
        pins={f.relative_to(ROOT).as_posix():pin(f) for f in [*sources,example,Path(__file__)]}
        for name in pins:
            target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(ROOT/name,target)
        tools=Path('C:/Program Files')/('Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin' if a.compiler=='javac21' else 'Common Files/Oracle/Java/javapath')
        classes=out/'classes';classes.mkdir();consumer=out/'external-consumer';consumer.mkdir()
        command=[str(tools/'javac.exe'),'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-d',str(classes),*map(str,sources)]
        r=subprocess.run(command,capture_output=True,timeout=120)
        (out/'compile.stdout').write_bytes(r.stdout);(out/'compile.stderr').write_bytes(r.stderr);save(out/'compile.json',dict(command=command,exit_code=r.returncode))
        assert r.returncode==0,r.stderr[-2500:]
        jar=out/'king-threshold-api.jar'
        payload={f.relative_to(classes).as_posix():f.read_bytes() for f in classes.rglob('*.class')}
        payload['META-INF/MANIFEST.MF']=b'Manifest-Version: 1.0\n\n';payload['META-INF/LICENSE']=(ROOT/'LICENSE').read_bytes()
        payload['META-INF/NOTICE']=b'Own native exact decimal cutoff comparator and unchanged existing KING count component. No original KING software/source is bundled or required. Not a source-output repair, sample-selection policy, historical paper reproduction or biological certificate.\n'
        with zipfile.ZipFile(jar,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
            for name,raw in sorted(payload.items()):
                item=zipfile.ZipInfo(name,(1980,1,1,0,0,0));item.compress_type,item.external_attr=zipfile.ZIP_DEFLATED,0o100644<<16;z.writestr(item,raw)
        command=[str(tools/'javac.exe'),'--release','17','-Xlint:all','-Werror','-encoding','UTF-8','-cp',str(jar),'-d',str(consumer),str(example)]
        r=subprocess.run(command,capture_output=True,timeout=120)
        (out/'consumer-compile.stdout').write_bytes(r.stdout);(out/'consumer-compile.stderr').write_bytes(r.stderr)
        save(out/'consumer-compile.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,r.stderr[-2500:]
        invocations=[]
        for platform in ['windows','linux']:
            convert=linux if platform=='linux' else lambda p:str(p.resolve())
            launch=['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java'] if platform=='linux' else [str(tools/'java.exe')]
            command=[*launch,'-cp',(':' if platform=='linux' else ';').join(convert(p) for p in [consumer,jar]),'KingThresholdExample']
            r=subprocess.run(command,capture_output=True,timeout=120)
            (out/(platform+'.stdout')).write_bytes(r.stdout);(out/(platform+'.stderr')).write_bytes(r.stderr)
            save(out/(platform+'.process.json'),dict(command=command,exit_code=r.returncode))
            assert r.returncode==0 and r.stdout.strip()==b'221/2500 EQUAL strict=false inclusive=true',(platform,r.returncode,r.stdout,r.stderr)
            assert b'Unassessable pairs cannot become unrelatedness decisions' in r.stderr
            invocations.append(dict(platform=platform,standalone_external_consumer=True,exact_boundary= '221/2500 = 0.0884',strict=False,inclusive=True))
        assert pin(canonical)==before and all(pin(ROOT/name)==value for name,value in pins.items())
        state.update(status='PASS_STANDALONE_EXACT_KING_CUTOFF_API_AND_EXTERNAL_CONSUMERS',compiler=a.compiler,source_pins=pins,
                     artifact=dict(path=jar.relative_to(ROOT).as_posix(),**pin(jar)),invocations=invocations,canonical_executable_unchanged=before)
    except Exception as error:
        state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error','')[:2200],flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable standalone API and external-consumer checks')
    raise SystemExit(main())
